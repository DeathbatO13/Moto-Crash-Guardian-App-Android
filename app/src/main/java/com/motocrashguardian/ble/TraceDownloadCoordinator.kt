package com.motocrashguardian.ble

import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.IncidentStatus
import com.motocrashguardian.detection.GuardianState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.UUID

/** La descarga no debe competir con una alerta: solo corre cuando no hay cuenta ni despacho. */
fun GuardianState.allowsTraceDownload(): Boolean = when (this) {
    is GuardianState.Confirming,
    is GuardianState.Countdown,
    is GuardianState.Dispatching -> false
    GuardianState.Idle,
    GuardianState.Monitoring,
    is GuardianState.Dispatched,
    GuardianState.ConnectionLost -> true
}

/** Incidentes del modulo ya cerrados: cuando aparece uno nuevo vale la pena volver a mirar. */
internal fun traceCandidateIds(incidents: List<Incident>): Set<UUID> =
    incidents
        .filter { it.deviceEventKey != null && it.status != IncidentStatus.ACTIVE }
        .map { it.id }
        .toSet()

/**
 * Mantiene la descarga de trazas en segundo plano: corre con el modulo conectado y sin alerta en
 * curso, y se cancela en cuanto empieza una cuenta regresiva (la emergencia manda sobre el
 * historial). Reintenta cuando cambia la conexion, el estado o aparece un incidente nuevo.
 */
class TraceDownloadCoordinator(
    private val connectionState: Flow<DeviceConnectionState>,
    private val guardianState: Flow<GuardianState>,
    private val incidents: Flow<List<Incident>>,
    private val fetcher: TraceFetcher
) {
    fun start(scope: CoroutineScope): Job = scope.launch {
        launch {
            connectionState.collect { if (it != DeviceConnectionState.READY) fetcher.resetAttempts() }
        }
        combine(connectionState, guardianState, incidents) { connection, state, all ->
            Conditions(
                canDownload = connection == DeviceConnectionState.READY && state.allowsTraceDownload(),
                candidates = traceCandidateIds(all)
            )
        }
            .distinctUntilChanged()
            // collectLatest cancela la descarga en curso en cuanto llega una condicion nueva.
            .collectLatest { conditions ->
                if (!conditions.canDownload) return@collectLatest
                try {
                    fetcher.fetchPending()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Un fallo de descarga o de base de datos no debe afectar a la app.
                }
            }
    }

    private data class Conditions(val canDownload: Boolean, val candidates: Set<UUID>)
}
