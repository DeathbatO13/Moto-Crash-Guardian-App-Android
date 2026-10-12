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
import com.motocrashguardian.service.GuardianService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceRepository: DeviceRepository,
    private val settingsRepository: SettingsRepository,
    private val stateMachine: GuardianStateMachine
) : ViewModel() {

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

    fun triggerTest() {
        viewModelScope.launch {
            runCatching { deviceRepository.triggerTestEvent() }
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
                current.copy(
                    riderName = name,
                    primaryContact = EmergencyContact(ContactRole.PRIMARY, primaryName, primaryPhone),
                    secondaryContact = if (secondaryName.isNotBlank() || secondaryPhone.isNotBlank()) {
                        EmergencyContact(ContactRole.SECONDARY, secondaryName, secondaryPhone)
                    } else {
                        null
                    }
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
