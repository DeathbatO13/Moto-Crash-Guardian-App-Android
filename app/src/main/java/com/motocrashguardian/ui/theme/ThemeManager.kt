package com.motocrashguardian.ui.theme

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.motocrashguardian.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Guarda y expone el tema elegido por el usuario. El valor por defecto es el tema claro. */
@Singleton
class ThemeManager @Inject constructor(
    private val settings: SettingsRepository
) {
    /** `true` si el tema elegido es el oscuro. */
    val darkTheme: Flow<Boolean> = settings.settings
        .map { it.darkTheme }
        .distinctUntilChanged()

    suspend fun setDarkTheme(dark: Boolean) {
        settings.updateSettings { it.copy(darkTheme = dark) }
    }
}

/** Lo que las pantallas necesitan para mostrar y cambiar el tema sin conocer al [ThemeManager]. */
data class ThemeController(
    val darkTheme: Boolean = false,
    val onToggle: () -> Unit = {}
)

val LocalThemeController = compositionLocalOf { ThemeController() }

/** Boton sol/luna que alterna entre el tema claro y el oscuro. */
@Composable
fun ThemeToggleButton(modifier: Modifier = Modifier) {
    val controller = LocalThemeController.current
    IconButton(onClick = controller.onToggle, modifier = modifier) {
        Icon(
            imageVector = if (controller.darkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
            contentDescription = if (controller.darkTheme) "Cambiar a tema claro" else "Cambiar a tema oscuro",
            modifier = Modifier.size(24.dp)
        )
    }
}
