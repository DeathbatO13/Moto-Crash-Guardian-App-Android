package com.motocrashguardian

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.data.settings.SettingsRepository
import com.motocrashguardian.ui.navigation.AppNavigation
import com.motocrashguardian.ui.theme.LocalThemeController
import com.motocrashguardian.ui.theme.MotoCrashGuardianTheme
import com.motocrashguardian.ui.theme.ThemeController
import com.motocrashguardian.ui.theme.ThemeManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var themeManager: ThemeManager

    /** `null` mientras no se haya leido el tema guardado. */
    private var darkTheme by mutableStateOf<Boolean?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La pantalla de carga se mantiene hasta conocer el tema guardado, para no mostrar
        // un destello del tema equivocado al abrir la app.
        installSplashScreen().setKeepOnScreenCondition { darkTheme == null }
        setContent {
            val storedTheme by themeManager.darkTheme.collectAsStateWithLifecycle(initialValue = null)
            LaunchedEffect(storedTheme) { darkTheme = storedTheme }
            val dark = storedTheme ?: false
            val scope = rememberCoroutineScope()

            // Iconos de las barras del sistema segun el tema elegido, no el del sistema.
            LaunchedEffect(dark) {
                val bar = if (dark) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
            }

            // Mientras no termine el onboarding (S-01..S-04) la app
            // arranca en la primera pantalla del flujo (docs/02 UF-01).
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(
                initialValue = AppSettings()
            )
            val controller = remember(dark) {
                ThemeController(
                    darkTheme = dark,
                    onToggle = { scope.launch { themeManager.setDarkTheme(!dark) } }
                )
            }
            MotoCrashGuardianTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalThemeController provides controller) {
                    // Sin esta superficie el texto de las primeras pantallas no tenia fondo ni
                    // color de contenido del tema: se pintaba negro sobre el fondo de la ventana.
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground
                    ) {
                        AppNavigation(
                            onboardingCompleted = settings.onboardingCompleted,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
