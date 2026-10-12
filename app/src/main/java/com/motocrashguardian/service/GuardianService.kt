package com.motocrashguardian.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.motocrashguardian.R
import com.motocrashguardian.ble.DeviceConnectionState
import com.motocrashguardian.ble.DeviceRepository
import com.motocrashguardian.data.settings.SettingsRepository
import com.motocrashguardian.detection.GuardianStateMachine
import com.motocrashguardian.ui.alert.AlertActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.motocrashguardian.detection.GuardianEffect

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
        observeStateMachineEffects()
        
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
        deviceRepository.disconnect()
        serviceScope.cancel()
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
            runCatching { deviceRepository.connect(address) }
        }
    }

    private fun reconnectIfTripActive() {
        serviceScope.launch {
            val settings = settingsRepository.settings.first()
            if (settings.tripActive || settings.onboardingCompleted) {
                val address = settings.pairedDeviceAddress
                if (address != null) {
                    runCatching { deviceRepository.connect(address) }
                }
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

    private fun observeStateMachineEffects() {
        serviceScope.launch {
            stateMachine.effects.collect { effect ->
                when (effect) {
                    is GuardianEffect.CountdownStarted -> {
                        val intent = Intent(this@GuardianService, AlertActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    }
                    else -> { /* El envío de SMS/Llamada es gestionado por EmergencyFlowController */ }
                }
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
        private const val NOTIFICATION_ID = 1
        const val ACTION_CONNECT = "com.motocrashguardian.action.CONNECT"
        const val ACTION_DISCONNECT = "com.motocrashguardian.action.DISCONNECT"
    }
}
