package com.motocrashguardian.ui.home

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motocrashguardian.ble.DeviceConnectionState
import com.motocrashguardian.ble.DeviceRepository
import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.core.model.ContactRole
import com.motocrashguardian.core.model.EmergencyContact
import com.motocrashguardian.core.model.Telemetry
import com.motocrashguardian.data.settings.SettingsRepository
import com.motocrashguardian.detection.GuardianState
import com.motocrashguardian.detection.GuardianStateMachine
import com.motocrashguardian.emergency.DrillOutcome
import com.motocrashguardian.emergency.DrillStarter
import com.motocrashguardian.service.GuardianService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceRepository: DeviceRepository,
    private val settingsRepository: SettingsRepository,
    private val stateMachine: GuardianStateMachine,
    private val drillStarter: DrillStarter
) : ViewModel() {

    private val mutableDrill = MutableStateFlow<DrillUiState>(DrillUiState.Idle)
    val drill: StateFlow<DrillUiState> = mutableDrill.asStateFlow()

    val connectionState: StateFlow<DeviceConnectionState> = deviceRepository.connectionState
    val telemetry: StateFlow<Telemetry?> = deviceRepository.telemetry
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    
    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val guardianState: StateFlow<GuardianState> = stateMachine.state

    fun tare() {
        viewModelScope.launch {
            runCatching { deviceRepository.tare() }
        }
    }

    /** Pide confirmacion: el simulacro envia SMS reales (marcados como prueba) a los contactos. */
    fun requestDrill() {
        if (mutableDrill.value == DrillUiState.Idle) mutableDrill.value = DrillUiState.Confirming
    }

    fun dismissDrill() {
        if (mutableDrill.value != DrillUiState.Starting) mutableDrill.value = DrillUiState.Idle
    }

    fun confirmDrill() {
        if (mutableDrill.value != DrillUiState.Confirming) return
        mutableDrill.value = DrillUiState.Starting
        viewModelScope.launch {
            val current = settingsRepository.settings.first()
            mutableDrill.value = when (drillStarter.start(current)) {
                // La cuenta regresiva ya sigue por la pantalla de alerta.
                is DrillOutcome.Started -> DrillUiState.Idle
                DrillOutcome.NoContacts -> DrillUiState.Blocked(DrillUiState.Reason.NO_CONTACTS)
                DrillOutcome.ProtectionOff -> DrillUiState.Blocked(DrillUiState.Reason.PROTECTION_OFF)
                DrillOutcome.AlertInProgress ->
                    DrillUiState.Blocked(DrillUiState.Reason.ALERT_IN_PROGRESS)
            }
        }
    }

    /** Si el simulacro tambien debe llamar de verdad al contacto principal. */
    fun setDrillPlaceCall(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(drillPlaceCall = enabled) }
        }
    }

    fun updateProfile(
        name: String,
        primaryName: String,
        primaryPhone: String,
        secondaryName: String,
        secondaryPhone: String
    ) {
        viewModelScope.launch {
            settingsRepository.updateSettings { current ->
                val primary = EmergencyContact(ContactRole.PRIMARY, primaryName, primaryPhone)
                val secondary = if (secondaryName.isNotBlank() || secondaryPhone.isNotBlank()) {
                    EmergencyContact(ContactRole.SECONDARY, secondaryName, secondaryPhone)
                } else {
                    null
                }
                current.copy(
                    riderName = name,
                    primaryContact = primary,
                    secondaryContact = secondary,
                    // Pendiente de subir al backend solo si algo cambio de verdad.
                    configDirty = current.configDirty || current.riderName != name,
                    contactsDirty = current.contactsDirty ||
                        current.primaryContact != primary ||
                        current.secondaryContact != secondary
                )
            }
        }
    }
    
    fun toggleProtection() {
        viewModelScope.launch {
            val isMonitoring = guardianState.value != GuardianState.Idle
            if (isMonitoring) {
                stateMachine.stopTrip()
                context.stopService(Intent(context, GuardianService::class.java))
            } else {
                stateMachine.startTrip()
                val intent = Intent(context, GuardianService::class.java).apply {
                    action = GuardianService.ACTION_CONNECT
                }
                context.startForegroundService(intent)
            }
        }
    }
}
