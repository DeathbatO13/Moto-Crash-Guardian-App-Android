package com.motocrashguardian.ble

import com.motocrashguardian.ble.protocol.BleProtocol
import com.motocrashguardian.ble.protocol.TraceAssembly
import com.motocrashguardian.ble.protocol.TraceChunk
import com.motocrashguardian.ble.protocol.TraceMetadata
import com.motocrashguardian.ble.protocol.assembleTrace
import com.motocrashguardian.ble.protocol.decodeTraceMetadata
import com.motocrashguardian.ble.protocol.validateTraceMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Traza completa y validada, lista para guardarse. */
class DownloadedTrace(val metadata: TraceMetadata, val samples: ByteArray)

sealed interface TraceResult {
    class Downloaded(val trace: DownloadedTrace) : TraceResult

    /** El modulo no tiene (o ya no tiene) la traza de ese evento; reintentar no cambia nada. */
    data object NotAvailable : TraceResult

    /** Se agotaron los intentos. */
    data class Failed(val reason: String) : TraceResult
}

fun interface TraceSource {
    suspend fun download(eventId: Int): TraceResult
}

/**
 * Descarga la caja negra de un evento: `REQUEST_TRACE` devuelve los metadatos y los chunks llegan
 * por notificacion. Un intento fallido (timeout, `BUSY`, chunks faltantes o incoherentes) se
 * repite hasta [maxRetries] veces; un `NOT_FOUND` no, porque no va a aparecer.
 */
class TraceDownloader(
    private val device: DeviceRepository,
    private val maxRetries: Int = DefaultMaxRetries,
    private val retryDelayMillis: Long = DefaultRetryDelayMillis
) : TraceSource {

    override suspend fun download(eventId: Int): TraceResult {
        var lastReason = "sin intentos"
        for (attempt in 0..maxRetries) {
            if (attempt > 0) delay(retryDelayMillis)
            when (val outcome = attemptDownload(eventId)) {
                is Attempt.Done -> return TraceResult.Downloaded(outcome.trace)
                Attempt.NotAvailable -> return TraceResult.NotAvailable
                is Attempt.Retry -> lastReason = outcome.reason
            }
        }
        return TraceResult.Failed(lastReason)
    }

    private suspend fun attemptDownload(eventId: Int): Attempt = coroutineScope {
        val received = MutableStateFlow<Map<Int, TraceChunk>>(emptyMap())
        // UNDISPATCHED: la suscripcion queda hecha antes de pedir la traza; si no, los primeros
        // chunks (que pueden llegar antes que la respuesta) se perderian.
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            device.traceChunks
                .filter { it.eventId == eventId }
                .collect { chunk ->
                    received.update { if (chunk.chunkIndex in it) it else it + (chunk.chunkIndex to chunk) }
                }
        }
        try {
            val response = try {
                device.requestTrace(eventId)
            } catch (error: TimeoutCancellationException) {
                return@coroutineScope Attempt.Retry("el modulo no respondio a tiempo")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                return@coroutineScope Attempt.Retry("error de comunicacion: ${error.javaClass.simpleName}")
            }

            when (response.result) {
                BleProtocol.RESULT_OK -> Unit
                BleProtocol.RESULT_NOT_FOUND,
                BleProtocol.RESULT_INVALID_PARAM,
                BleProtocol.RESULT_UNSUPPORTED -> return@coroutineScope Attempt.NotAvailable
                else -> return@coroutineScope Attempt.Retry("el modulo respondio ${response.result}")
            }

            val metadata = try {
                decodeTraceMetadata(response.payload)
            } catch (error: IllegalArgumentException) {
                return@coroutineScope Attempt.Retry("metadatos ilegibles")
            }
            validateTraceMetadata(metadata)?.let { return@coroutineScope Attempt.Retry(it) }

            val arrived = withTimeoutOrNull(transferTimeoutMillis(metadata)) {
                received.first { it.size >= metadata.chunkCount }
            }
            when (val assembly = assembleTrace(metadata, (arrived ?: received.value).values)) {
                is TraceAssembly.Complete ->
                    Attempt.Done(DownloadedTrace(metadata, assembly.samples))
                is TraceAssembly.Incomplete ->
                    Attempt.Retry("faltan ${assembly.missingChunks.size} de ${metadata.chunkCount} chunks")
                is TraceAssembly.Invalid -> Attempt.Retry(assembly.reason)
            }
        } finally {
            collector.cancel()
        }
    }

    private fun transferTimeoutMillis(metadata: TraceMetadata): Long =
        (metadata.chunkCount * PerChunkMillis).coerceIn(MinTransferMillis, MaxTransferMillis)

    private sealed interface Attempt {
        class Done(val trace: DownloadedTrace) : Attempt
        data object NotAvailable : Attempt
        data class Retry(val reason: String) : Attempt
    }

    companion object {
        /** "Reintentar hasta dos veces" (ROADMAP Fase 6): 1 intento + 2 reintentos. */
        const val DefaultMaxRetries = 2
        const val DefaultRetryDelayMillis = 500L
        private const val PerChunkMillis = 100L
        private const val MinTransferMillis = DeviceRepository.TRACE_TIMEOUT_MS
        private const val MaxTransferMillis = 30_000L
    }
}
