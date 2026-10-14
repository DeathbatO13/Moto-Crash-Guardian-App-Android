package com.motocrashguardian.ble

import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.Trace
import com.motocrashguardian.core.model.TriggerType
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Duration

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
    private val maxPerRun: Int = DefaultMaxPerRun,
    private val clock: Clock = Clock.systemUTC()
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
            // TRIGGER_TEST_EVENT emite el evento sin traza (eventFlags = 0): no hay nada que pedir.
            if (incident.triggerType == TriggerType.TEST) continue
            val ref = parseDeviceEventKey(key) ?: continue
            if (ref.bootCount != bootCount) continue
            if (synchronized(attempted) { key in attempted }) continue

            waitUntilTraceIsFrozen(incident)

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

    /**
     * El modulo solo ofrece la traza cuando termina de capturar (pasa a COOLDOWN, 3 s despues del
     * disparo; POST_TRIGGER_MS del firmware). Antes responde NOT_FOUND, que aqui es definitivo,
     * asi que se espera a que el incidente tenga la edad suficiente. Es cancelable.
     */
    private suspend fun waitUntilTraceIsFrozen(incident: Incident) {
        val age = Duration.between(incident.detectedAt, clock.instant())
        val remaining = MinIncidentAge.minus(age.coerceAtLeast(Duration.ZERO))
        if (!remaining.isNegative && !remaining.isZero) delay(remaining.toMillis())
    }

    companion object {
        const val DefaultMaxPerRun = 5
        /** 3 s de captura posterior al disparo + margen para la latencia BLE. */
        val MinIncidentAge: Duration = Duration.ofSeconds(5)
    }
}
