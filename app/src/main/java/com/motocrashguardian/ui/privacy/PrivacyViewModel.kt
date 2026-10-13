package com.motocrashguardian.ui.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.IncidentStatus
import com.motocrashguardian.core.model.SyncState
import com.motocrashguardian.data.incidents.IncidentRepository
import com.motocrashguardian.data.privacy.DataErasing
import com.motocrashguardian.data.privacy.EraseResult
import com.motocrashguardian.data.remote.AuthSession
import com.motocrashguardian.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** Etapas del borrado de datos que ve el usuario. */
sealed interface EraseUiState {
    data object Idle : EraseUiState
    data object Confirming : EraseUiState
    data object Erasing : EraseUiState
    data object NoConnection : EraseUiState
    data class Rejected(val httpCode: Int) : EraseUiState
    data object LocalFailure : EraseUiState
    data object Done : EraseUiState
}

data class PrivacyUiState(
    val consentAcceptedAt: Instant? = null,
    val contactCount: Int = 0,
    val hasRiderName: Boolean = false,
    val incidentCount: Int = 0,
    /** Hay una copia en el servidor (la instalacion se registro alguna vez). */
    val hasServerCopy: Boolean = false,
    /** Elementos (incidentes, perfil, contactos) que aun no se han subido. */
    val pendingUploads: Int = 0,
    val erase: EraseUiState = EraseUiState.Idle
)

internal fun buildPrivacyState(
    settings: AppSettings,
    incidents: List<Incident>,
    hasServerCopy: Boolean,
    erase: EraseUiState
): PrivacyUiState {
    val pendingIncidents = incidents.count {
        it.syncState == SyncState.PENDING && it.status != IncidentStatus.ACTIVE
    }
    return PrivacyUiState(
        consentAcceptedAt = settings.consentAcceptedAt,
        contactCount = listOfNotNull(settings.primaryContact, settings.secondaryContact).size,
        hasRiderName = settings.riderName.isNotBlank(),
        incidentCount = incidents.size,
        hasServerCopy = hasServerCopy,
        pendingUploads = pendingIncidents +
            (if (settings.configDirty) 1 else 0) +
            (if (settings.contactsDirty) 1 else 0),
        erase = erase
    )
}

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    incidentRepository: IncidentRepository,
    private val auth: AuthSession,
    private val eraser: DataErasing
) : ViewModel() {

    private val erase = MutableStateFlow<EraseUiState>(EraseUiState.Idle)

    val state: StateFlow<PrivacyUiState> = combine(
        settingsRepository.settings,
        incidentRepository.incidents,
        erase
    ) { settings, incidents, eraseState ->
        buildPrivacyState(settings, incidents, auth.installationId() != null, eraseState)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrivacyUiState())

    fun requestErase() {
        if (erase.value == EraseUiState.Idle || erase.value.isFailure()) {
            erase.value = EraseUiState.Confirming
        }
    }

    fun dismissErase() {
        if (erase.value != EraseUiState.Erasing && erase.value != EraseUiState.Done) {
            erase.value = EraseUiState.Idle
        }
    }

    fun confirmErase() {
        if (erase.value != EraseUiState.Confirming) return
        erase.value = EraseUiState.Erasing
        viewModelScope.launch {
            erase.value = when (val result = eraser.erase()) {
                is EraseResult.Success -> EraseUiState.Done
                EraseResult.NoConnection -> EraseUiState.NoConnection
                is EraseResult.ServerRejected -> EraseUiState.Rejected(result.httpCode)
                EraseResult.LocalFailure -> EraseUiState.LocalFailure
            }
        }
    }

    private fun EraseUiState.isFailure(): Boolean =
        this == EraseUiState.NoConnection || this == EraseUiState.LocalFailure ||
            this is EraseUiState.Rejected
}
