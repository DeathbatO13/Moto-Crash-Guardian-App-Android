package com.motocrashguardian.ui.home

/** Etapas del simulacro que ve el usuario en la pantalla de contactos. */
sealed interface DrillUiState {
    data object Idle : DrillUiState
    data object Confirming : DrillUiState
    data object Starting : DrillUiState

    /** El simulacro no pudo empezar; [reason] explica por que y que hacer. */
    data class Blocked(val reason: Reason) : DrillUiState

    enum class Reason { NO_CONTACTS, PROTECTION_OFF, ALERT_IN_PROGRESS }
}
