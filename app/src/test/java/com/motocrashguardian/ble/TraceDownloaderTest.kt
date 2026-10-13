package com.motocrashguardian.ble

import com.motocrashguardian.ble.protocol.BleProtocol
import com.motocrashguardian.ble.protocol.ControlResponse
import com.motocrashguardian.ble.protocol.ImuSample
import com.motocrashguardian.ble.protocol.TraceChunk
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TraceDownloaderTest {

    private val chunkFlow = MutableSharedFlow<TraceChunk>(extraBufferCapacity = 256)
    private val device = mockk<DeviceRepository> {
        every { traceChunks } returns chunkFlow
    }
    private val eventId = 7

    private fun sample(i: Int) = ImuSample(i, i, i, i, i, i)

    private fun metadataBytes(total: Int, perChunk: Int = 19): ByteArray {
        val chunkCount = (total + perChunk - 1) / perChunk
        fun u16(value: Int) = byteArrayOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())
        return u16(200) + u16(10) + u16(total) + u16(2048) + u16(164) + u16(chunkCount) +
            byteArrayOf(perChunk.toByte())
    }

    private fun chunks(total: Int, event: Int = eventId, perChunk: Int = 19): List<TraceChunk> {
        val count = (total + perChunk - 1) / perChunk
        return (0 until count).map { index ->
            val start = index * perChunk
            TraceChunk(
                eventId = event,
                chunkIndex = index,
                chunkCount = count,
                samples = (start until minOf(start + perChunk, total)).map(::sample)
            )
        }
    }

    private fun ok(total: Int) = ControlResponse(
        opcode = BleProtocol.OP_REQUEST_TRACE,
        requestId = 0,
        result = BleProtocol.RESULT_OK,
        payload = metadataBytes(total)
    )

    private fun failure(result: Int) = ControlResponse(
        opcode = BleProtocol.OP_REQUEST_TRACE,
        requestId = 0,
        result = result,
        payload = ByteArray(0)
    )

    private fun downloader() = TraceDownloader(device, maxRetries = 2, retryDelayMillis = 500)

    // --- Camino feliz ---

    @Test
    fun `descarga una traza completa y la valida`() = runTest {
        coEvery { device.requestTrace(eventId) } coAnswers {
            chunks(50).forEach { chunkFlow.emit(it) }
            ok(50)
        }

        val result = downloader().download(eventId) as TraceResult.Downloaded

        assertEquals(50, result.trace.metadata.totalSamples)
        assertEquals(50 * 12, result.trace.samples.size)
        coVerify(exactly = 1) { device.requestTrace(eventId) }
    }

    @Test
    fun `los chunks que llegan despues de la respuesta tambien se aceptan`() = runTest {
        coEvery { device.requestTrace(eventId) } coAnswers {
            backgroundScope.launch {
                chunks(50).forEachIndexed { index, chunk ->
                    delay(20L * (index + 1))
                    chunkFlow.emit(chunk)
                }
            }
            ok(50)
        }

        val result = downloader().download(eventId)

        assertTrue(result is TraceResult.Downloaded, result.toString())
    }

    @Test
    fun `ignora los chunks de otros eventos`() = runTest {
        coEvery { device.requestTrace(eventId) } coAnswers {
            chunks(50, event = 99).forEach { chunkFlow.emit(it) } // de otro evento
            chunks(50).forEach { chunkFlow.emit(it) }
            ok(50)
        }

        val result = downloader().download(eventId) as TraceResult.Downloaded

        assertEquals(50 * 12, result.trace.samples.size)
    }

    // --- Reintentos ---

    @Test
    fun `reintenta cuando faltan chunks y termina bien al segundo intento`() = runTest {
        var calls = 0
        coEvery { device.requestTrace(eventId) } coAnswers {
            calls++
            val all = chunks(50)
            (if (calls == 1) all.take(2) else all).forEach { chunkFlow.emit(it) }
            ok(50)
        }

        val result = downloader().download(eventId)

        assertTrue(result is TraceResult.Downloaded, result.toString())
        assertEquals(2, calls)
    }

    @Test
    fun `espera la transferencia antes de dar por incompleto un intento`() = runTest {
        coEvery { device.requestTrace(eventId) } coAnswers {
            chunks(50).take(1).forEach { chunkFlow.emit(it) }
            ok(50)
        }

        downloader().download(eventId)

        // 3 intentos x 5 s de espera de chunks + 2 pausas de 500 ms.
        assertEquals(3 * 5_000L + 2 * 500L, currentTime)
    }

    @Test
    fun `reintenta cuando el modulo esta ocupado`() = runTest {
        var calls = 0
        coEvery { device.requestTrace(eventId) } coAnswers {
            calls++
            if (calls == 1) {
                failure(BleProtocol.RESULT_BUSY)
            } else {
                chunks(50).forEach { chunkFlow.emit(it) }
                ok(50)
            }
        }

        assertTrue(downloader().download(eventId) is TraceResult.Downloaded)
        assertEquals(2, calls)
    }

    @Test
    fun `reintenta tras un timeout del comando`() = runTest {
        var calls = 0
        coEvery { device.requestTrace(eventId) } coAnswers {
            calls++
            if (calls == 1) {
                withTimeout(100) { awaitCancellation() }
            } else {
                chunks(50).forEach { chunkFlow.emit(it) }
                ok(50)
            }
        }

        assertTrue(downloader().download(eventId) is TraceResult.Downloaded)
        assertEquals(2, calls)
    }

    @Test
    fun `reintenta tras un error de comunicacion`() = runTest {
        var calls = 0
        coEvery { device.requestTrace(eventId) } coAnswers {
            calls++
            if (calls == 1) {
                error("GATT cerrado")
            } else {
                chunks(50).forEach { chunkFlow.emit(it) }
                ok(50)
            }
        }

        assertTrue(downloader().download(eventId) is TraceResult.Downloaded)
    }

    @Test
    fun `se rinde tras el intento inicial y dos reintentos`() = runTest {
        coEvery { device.requestTrace(eventId) } returns failure(BleProtocol.RESULT_BUSY)

        val result = downloader().download(eventId)

        assertTrue(result is TraceResult.Failed, result.toString())
        coVerify(exactly = 3) { device.requestTrace(eventId) }
    }

    @Test
    fun `metadatos incoherentes cuentan como intento fallido`() = runTest {
        coEvery { device.requestTrace(eventId) } returns ok(50).copy(payload = metadataBytes(50).also {
            it[10] = 9 // chunkCount no coincide con el total
        })

        assertTrue(downloader().download(eventId) is TraceResult.Failed)
        coVerify(exactly = 3) { device.requestTrace(eventId) }
    }

    // --- Sin reintento ---

    @Test
    fun `NOT_FOUND no se reintenta`() = runTest {
        coEvery { device.requestTrace(eventId) } returns failure(BleProtocol.RESULT_NOT_FOUND)

        assertEquals(TraceResult.NotAvailable, downloader().download(eventId))
        coVerify(exactly = 1) { device.requestTrace(eventId) }
    }

    @Test
    fun `parametro invalido o no soportado tampoco se reintentan`() = runTest {
        for (result in listOf(BleProtocol.RESULT_INVALID_PARAM, BleProtocol.RESULT_UNSUPPORTED)) {
            coEvery { device.requestTrace(eventId) } returns failure(result)
            assertEquals(TraceResult.NotAvailable, downloader().download(eventId))
        }
    }

    // --- Cancelacion ---

    @Test
    fun `cancelar la descarga no devuelve un resultado ni reintenta`() = runTest {
        var calls = 0
        coEvery { device.requestTrace(eventId) } coAnswers {
            calls++
            ok(50) // pero los chunks nunca llegan
        }
        var finished = false
        val job = backgroundScope.launch {
            downloader().download(eventId)
            finished = true
        }
        runCurrent()
        advanceTimeBy(1_000)

        job.cancel()
        runCurrent()
        advanceTimeBy(60_000)

        assertTrue(job.isCancelled)
        assertEquals(false, finished)
        assertEquals(1, calls)
    }
}
