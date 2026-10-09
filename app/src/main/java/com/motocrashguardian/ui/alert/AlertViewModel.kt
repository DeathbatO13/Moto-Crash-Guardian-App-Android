package com.motocrashguardian.ui.alert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.data.settings.SettingsRepository
import com.motocrashguardian.detection.GuardianState
import com.motocrashguardian.detection.GuardianStateMachine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class AlertViewModel @Inject constructor(
    private val machine: GuardianStateMachine,
    settingsRepository: SettingsRepository
) : ViewModel() {
    private val clock: Clock = Clock.systemUTC()

    /**
     * Arranca con el estado real de la maquina para que una alerta ya activa no se confunda
     * con "sin alerta" mientras se cargan los ajustes.
     */
    val uiState: StateFlow<AlertUiState> = combine(
        machine.state.flatMapLatest(::ticking),
        settingsRepository.settings
    ) { state, settings -> state.toAlertUiState(clock.instant(), settings) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = machine.state.value.toAlertUiState(clock.instant(), AppSettings())
        )

    fun cancelCountdown() {
        viewModelScope.launch { machine.cancelCountdown() }
    }

    fun sendHelpNow() {
        viewModelScope.launch { machine.sendHelpNow() }
    }

    fun dismissResult() {
        viewModelScope.launch { machine.dismissResult() }
    }

    /** Reemite el estado cuando cambia el segundo visible de la cuenta regresiva. */
    private fun ticking(state: GuardianState): Flow<GuardianState> {
        if (state !is GuardianState.Countdown) return flowOf(state)
        return flow {
            while (true) {
                emit(state)
                val remainingMillis = Duration.between(clock.instant(), state.deadline).toMillis()
                if (remainingMillis <= 0) break
                delay((remainingMillis % 1_000).takeIf { it > 0 } ?: 1_000L)
            }
        }
    }
}
