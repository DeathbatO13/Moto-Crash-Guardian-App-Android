package com.motocrashguardian.ui.alert

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motocrashguardian.ui.theme.MotoCrashGuardianTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Destino de la notificacion de cuenta regresiva (full-screen intent).
 *
 * Refleja el estado de `GuardianStateMachine`; se cierra sola cuando ya no hay una alerta
 * (cancelada o resultado descartado).
 */
@AndroidEntryPoint
class AlertActivity : ComponentActivity() {
    private val viewModel: AlertViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge()
        setContent {
            MotoCrashGuardianTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(state) {
                    if (state is AlertUiState.Hidden) finish()
                }
                when (val current = state) {
                    is AlertUiState.Countdown -> CountdownScreen(
                        remainingSeconds = current.remainingSeconds,
                        totalSeconds = current.totalSeconds,
                        emergencyContactNames = current.contactNames,
                        locationAccuracyMeters = current.locationAccuracyMeters,
                        isSimulation = current.isSimulation,
                        onCancelConfirmed = viewModel::cancelCountdown,
                        onSendHelpNow = viewModel::sendHelpNow
                    )
                    AlertUiState.Dispatching -> DispatchingScreen()
                    is AlertUiState.Result -> ResultScreen(
                        status = current.status,
                        isSimulation = current.isSimulation,
                        onClose = viewModel::dismissResult
                    )
                    AlertUiState.Hidden -> Unit
                }
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }
}
