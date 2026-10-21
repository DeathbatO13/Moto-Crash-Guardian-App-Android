package com.motocrashguardian.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.motocrashguardian.R
import com.motocrashguardian.ble.DeviceConnectionState
import com.motocrashguardian.ble.DeviceRepository
import com.motocrashguardian.data.settings.SettingsRepository
import com.motocrashguardian.detection.GuardianStateMachine
import com.motocrashguardian.ui.alert.AlertActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import com.motocrashguardian.detection.GuardianState

/**
 * Foreground service que mantiene viva la conexión BLE con el módulo durante
 * el viaje y gestiona la detección de accidentes.
 * 
 * Nota: Se ha simplificado para no requerir permisos de ubicación del teléfono,
 * confiando en el GPS del dispositivo físico.
 */
@AndroidEntryPoint
class GuardianService : Service() {

    @Inject
    lateinit var deviceRepository: DeviceRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var stateMachine: GuardianStateMachine

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var notifications: GuardianNotifications

    /** Bucle que mantiene la conexión con [connectedAddress]; uno solo a la vez. */
    private var connectionJob: Job? = null
    private var connectedAddress: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifications = GuardianNotifications(this)
        notifications.createChannel()
        startForeground(
            NOTIFICATION_ID,
            notifications.build(R.string.service_state_disconnected)
        )
        
        observeConnection()
        observeTelemetry()
        observeEvents()
        openAlertOnCountdown()
        
        serviceScope.launch {
            stateMachine.restore()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> connectPairedDevice()
            ACTION_DISCONNECT -> {
                deviceRepository.disconnect()
                stopSelf()
            }
            else -> reconnectIfTripActive()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        // Primero se detiene el bucle, para que no reconecte tras desconectar.
        serviceScope.cancel()
        deviceRepository.disconnect()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun connectPairedDevice() {
        serviceScope.launch {
            val address = settingsRepository.settings.first().pairedDeviceAddress
            if (address == null) {
                stopSelf()
                return@launch
            }
            keepConnected(address)
        }
    }

    private fun reconnectIfTripActive() {
        serviceScope.launch {
            val settings = settingsRepository.settings.first()
            if (settings.tripActive || settings.onboardingCompleted) {
                settings.pairedDeviceAddress?.let(::keepConnected)
            }
        }
    }

    /**
     * Conecta y reconecta al módulo mientras el servicio viva (docs/03 §6,
     * reconexión ≤ 10 s). Si ya se mantiene esa misma dirección no hace nada;
     * si cambió (p. ej. se vinculó otro módulo) suelta la conexión anterior.
     */
    private fun keepConnected(address: String) {
        if (connectionJob?.isActive == true && connectedAddress == address) return
        val previous = connectionJob
        connectedAddress = address
        connectionJob = serviceScope.launch {
            previous?.cancel()
            if (deviceRepository.connectionState.value != DeviceConnectionState.DISCONNECTED) {
                deviceRepository.disconnect()
                withTimeoutOrNull(DISCONNECT_TIMEOUT_MS) {
                    deviceRepository.connectionState.first { it == DeviceConnectionState.DISCONNECTED }
                }
            }
            while (true) {
                if (deviceRepository.connectionState.value == DeviceConnectionState.DISCONNECTED) {
                    runCatching { deviceRepository.connect(address) }
                        .onFailure { Log.w(TAG, "No se pudo conectar con el módulo: ${it.message}") }
                }
                // Tras un fallo esto vuelve de inmediato; conectado, espera a perder el enlace.
                deviceRepository.connectionState.first { it == DeviceConnectionState.DISCONNECTED }
                delay(RECONNECT_DELAY_MS)
            }
        }
    }

    private fun observeConnection() {
        serviceScope.launch {
            deviceRepository.connectionState.collect { state ->
                startForeground(NOTIFICATION_ID, notifications.build(stateText(state)))
                if (state == DeviceConnectionState.READY) {
                    stateMachine.onReconnected()
                } else if (state == DeviceConnectionState.DISCONNECTED) {
                    stateMachine.onConnectionLost()
                }
            }
        }
    }

    private fun observeTelemetry() {
        serviceScope.launch {
            deviceRepository.telemetry.collect { sample ->
                stateMachine.onTelemetry(sample)
            }
        }
    }

    private fun observeEvents() {
        serviceScope.launch {
            deviceRepository.events.collect { event ->
                stateMachine.onCandidate(event)
            }
        }
    }

    /**
     * Abre la pantalla de alerta al empezar una cuenta regresiva. Observa `state`
     * y NO `effects`: ese canal entrega cada efecto a un solo consumidor y es de
     * EmergencyFlowController (alarma, notificación y despacho). La vía principal
     * es el full-screen intent de la notificación; esto solo ayuda con la app
     * visible (Android bloquea abrir actividades desde segundo plano).
     */
    private fun openAlertOnCountdown() {
        serviceScope.launch {
            stateMachine.state
                .map { (it as? GuardianState.Countdown)?.incident?.id }
                .distinctUntilChanged()
                .filterNotNull()
                .collect {
                    runCatching {
                        startActivity(
                            Intent(this@GuardianService, AlertActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }.onFailure { Log.w(TAG, "No se pudo abrir la alerta: ${it.message}") }
                }
        }
    }

    private fun stateText(state: DeviceConnectionState): Int = when (state) {
        DeviceConnectionState.DISCONNECTED -> R.string.service_state_disconnected
        DeviceConnectionState.CONNECTING -> R.string.service_state_connecting
        DeviceConnectionState.DISCONNECTING -> R.string.service_state_disconnecting
        DeviceConnectionState.READY -> R.string.service_state_ready
        DeviceConnectionState.INCOMPATIBLE_FIRMWARE -> R.string.service_state_incompatible
    }

    companion object {
        private const val TAG = "GuardianService"
        private const val NOTIFICATION_ID = 1
        private const val RECONNECT_DELAY_MS = 3_000L
        private const val DISCONNECT_TIMEOUT_MS = 5_000L
        const val ACTION_CONNECT = "com.motocrashguardian.action.CONNECT"
        const val ACTION_DISCONNECT = "com.motocrashguardian.action.DISCONNECT"
    }
}
