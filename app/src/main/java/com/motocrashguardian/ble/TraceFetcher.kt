package com.motocrashguardian.ble

import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.Trace

/** Lo que [TraceFetcher] necesita de la base local. */
interface TraceStore {
    suspend fun incidentsMissingTrace(limit: Int): List<Incident>
    suspend fun attachTrace(trace: Trace): Boolean
}

/** Clave de evento del modulo: `bootCount:eventId`, ambos de 16 bits. */
data class DeviceEventRef(val bootCount: Int, val eventId: Int)

fun parseDeviceEventKey(key: String): DeviceEventRef? {
    val parts = key.split(':')
    if (parts.size != 2) return null
    val bootCount = parts[0].toIntOrNull() ?: return null
    val eventId = parts[1].toIntOrNull() ?: return null
    if (bootCount !in 0..0xFFFF || eventId !in 0..0xFFFF) return null
    return DeviceEventRef(bootCount, eventId)
}

/**
 * Descarga y guarda las cajas negras de los incidentes que aun no la tienen.
 *
 * `eventId` es de 16 bits y el contador de eventos puede reiniciarse al reiniciar el modulo,
 * asi que solo se pide la traza de incidentes del MISMO arranque (`bootCount`) que el modulo
 * conectado: de otro arranque el mismo id podria devolver la traza de un evento distinto y se
 * mostraria una grafica falsa. Cada evento se intenta una vez por conexion.
 */
class TraceFetcher(
    private val source: TraceSource,
    private val store: TraceStore,
    /** `bootCount` del modulo conectado (de DEVICE_INFO); `null` sin conexion. */
    private val currentBootCount: () -> Int?,
    private val maxPerRun: Int = DefaultMaxPerRun
) {
    private val attempted = mutableSetOf<String>()

    /** Olvida lo intentado; se llama al perder la conexion para reintentar en la siguiente. */
    fun resetAttempts() {
        synchronized(attempted) { attempted.clear() }
    }

    /** Devuelve cuantas trazas se guardaron. Es cancelable: una alerta puede interrumpirla. */
    suspend fun fetchPending(): Int {
        val bootCount = currentBootCount() ?: return 0
        val candidates = store.incidentsMissingTrace(maxPerRun)
        var saved = 0
        for (incident in candidates) {
            val key = incident.deviceEventKey ?: continue
            val ref = parseDeviceEventKey(key) ?: continue
            if (ref.bootCount != bootCount) continue
            if (synchronized(attempted) { key in attempted }) continue

            // Si se cancela a mitad de la descarga no se marca como intentado: se reanuda luego.
            val result = source.download(ref.eventId)
            synchronized(attempted) { attempted += key }

            if (result is TraceResult.Downloaded) {
                val trace = result.trace
                val stored = store.attachTrace(
                    Trace(
                        incidentId = incident.id,
                        sampleRateHz = trace.metadata.sampleRateHz,
                        preTriggerSamples = trace.metadata.preTriggerSamples,
                        totalSamples = trace.metadata.totalSamples,
                        accelLsbPerG = trace.metadata.accelLsbPerG,
                        gyroLsbPerDpsX10 = trace.metadata.gyroLsbPerDpsX10,
                        samples = trace.samples
                    )
                )
                if (stored) saved++
            }
        }
        return saved
    }

    companion object {
        const val DefaultMaxPerRun = 5
    }
}
