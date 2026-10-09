package com.motocrashguardian.ui.alert

import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.core.model.IncidentStatus
import com.motocrashguardian.core.model.IncidentType
import com.motocrashguardian.detection.GuardianState
import java.time.Instant

sealed interface AlertUiState {
    /** No hay una alerta que mostrar. */
    data object Hidden : AlertUiState

    data class Countdown(
        val remainingSeconds: Int,
        val totalSeconds: Int,
        val contactNames: List<String>,
        val locationAccuracyMeters: Int?,
        val isSimulation: Boolean
    ) : AlertUiState

    data object Dispatching : AlertUiState

    data class Result(val status: IncidentStatus, val isSimulation: Boolean) : AlertUiState
}

fun GuardianState.toAlertUiState(now: Instant, settings: AppSettings): AlertUiState = when (this) {
    is GuardianState.Countdown -> {
        val remaining = remainingSeconds(now)
        AlertUiState.Countdown(
            remainingSeconds = remaining,
            totalSeconds = maxOf(settings.countdownSeconds, remaining),
            contactNames = listOfNotNull(
                settings.primaryContact?.name,
                settings.secondaryContact?.name
            ),
            locationAccuracyMeters = incident.locationAccuracyMeters?.toInt(),
            isSimulation = incident.type == IncidentType.DRILL
        )
    }
    is GuardianState.Dispatching -> AlertUiState.Dispatching
    is GuardianState.Dispatched -> AlertUiState.Result(
        status = incident.status,
        isSimulation = incident.type == IncidentType.DRILL
    )
    GuardianState.Idle,
    GuardianState.Monitoring,
    GuardianState.ConnectionLost,
    is GuardianState.Confirming -> AlertUiState.Hidden
}
