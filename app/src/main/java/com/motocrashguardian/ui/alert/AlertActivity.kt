package com.motocrashguardian.ui.alert

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.motocrashguardian.ui.theme.MotoCrashGuardianTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Destino de la notificacion de cuenta regresiva (full-screen intent).
 *
 * Por ahora muestra [CountdownScreen] con valores fijos; el estado real de
 * `GuardianStateMachine` se conectara cuando exista `GuardianService`.
 */
@AndroidEntryPoint
class AlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge()
        setContent {
            MotoCrashGuardianTheme {
                CountdownScreen(
                    remainingSeconds = DefaultCountdownSeconds,
                    totalSeconds = DefaultCountdownSeconds,
                    emergencyContactNames = emptyList(),
                    locationAccuracyMeters = null,
                    isSimulation = false,
                    onCancelConfirmed = ::finish,
                    onSendHelpNow = {}
                )
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

    private companion object {
        const val DefaultCountdownSeconds = 20
    }
}
