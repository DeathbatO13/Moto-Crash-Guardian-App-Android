package com.motocrashguardian.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.core.model.ContactRole
import com.motocrashguardian.core.model.EmergencyContact
import com.motocrashguardian.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado compartido del flujo de onboarding (S-01..S-04). */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /**
     * Guarda el consentimiento de datos (S-02, docs/06 §4).
     * Se persiste al pulsar **Acepto**, no al marcar la casilla.
     */
    fun acceptConsent() {
        viewModelScope.launch {
            settingsRepository.updateSettings {
                it.copy(consentAcceptedAt = Instant.now())
            }
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
                    secondaryContact = if (secondaryName.isNotBlank() && secondaryPhone.isNotBlank()) {
                        EmergencyContact(ContactRole.SECONDARY, secondaryName, secondaryPhone)
                    } else {
                        current.secondaryContact
                    }
                )
            }
        }
    }
}
