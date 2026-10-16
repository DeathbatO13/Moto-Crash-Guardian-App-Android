package com.motocrashguardian

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.data.settings.SettingsRepository
import com.motocrashguardian.ui.navigation.AppNavigation
import com.motocrashguardian.ui.theme.MotoCrashGuardianTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Mientras no termine el onboarding (S-01..S-04) la app
            // arranca en la primera pantalla del flujo (docs/02 UF-01).
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(
                initialValue = AppSettings()
            )
            MotoCrashGuardianTheme {
                AppNavigation(
                    onboardingCompleted = settings.onboardingCompleted,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
