package com.motocrashguardian

import android.app.Application
import android.util.Log
import com.motocrashguardian.ble.TraceDownloadCoordinator
import com.motocrashguardian.data.incidents.IncidentRepository
import com.motocrashguardian.data.sync.SyncCoordinator
import com.motocrashguardian.data.sync.SyncScheduler
import com.motocrashguardian.detection.GuardianStateMachine
import com.motocrashguardian.emergency.EmergencyFlowController
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GuardianApp : Application() {
    @Inject lateinit var emergencyFlow: EmergencyFlowController
    @Inject lateinit var stateMachine: GuardianStateMachine
    @Inject lateinit var syncCoordinator: SyncCoordinator
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var incidentRepository: IncidentRepository
    @Inject lateinit var traceDownloads: TraceDownloadCoordinator

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Interino: pasara a GuardianService cuando exista (T-1.05).
        emergencyFlow.start(appScope)
        // Sincronizacion en segundo plano con el backend: nunca bloquea ni retrasa la emergencia.
        syncCoordinator.start(appScope)
        syncScheduler.ensurePeriodic()
        // Cajas negras del modulo: se descargan solo sin alerta en curso (ver TraceDownloadCoordinator).
        traceDownloads.start(appScope)
        appScope.launch { purgeExpiredTraces() }
        appScope.launch {
            try {
                // Reanuda una cuenta regresiva o un despacho interrumpidos por la muerte del proceso.
                stateMachine.restore()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "No se pudo restaurar el estado de la alerta.", error)
            }
        }
    }

    /** Retencion: las trazas se guardan 90 dias. Un fallo aqui no debe afectar a la app. */
    private suspend fun purgeExpiredTraces() {
        try {
            incidentRepository.purgeExpiredTraces()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "No se pudo aplicar la retencion de trazas.", error)
        }
    }

    private companion object {
        const val TAG = "GuardianApp"
    }
}
