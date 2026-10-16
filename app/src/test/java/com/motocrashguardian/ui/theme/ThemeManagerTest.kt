package com.motocrashguardian.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.datastore.core.DataStore
import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.data.settings.SettingsRepository
import com.motocrashguardian.data.settings.proto.AppSettingsProto
import com.motocrashguardian.data.settings.toProto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThemeManagerTest {

    @get:org.junit.Rule
    val composeRule = createComposeRule()

    private class FakeDataStore(initial: AppSettingsProto) : DataStore<AppSettingsProto> {
        private val current = MutableStateFlow(initial)
        override val data: Flow<AppSettingsProto> = current
        override suspend fun updateData(transform: suspend (t: AppSettingsProto) -> AppSettingsProto) =
            transform(current.value).also { current.value = it }
    }

    private fun manager(settings: AppSettings = AppSettings()) =
        ThemeManager(SettingsRepository(FakeDataStore(settings.toProto())))

    @Test
    fun `el tema claro es el predeterminado`() = runTest {
        assertEquals(false, manager().darkTheme.first())
    }

    @Test
    fun `guarda el tema elegido y lo puede revertir`() = runTest {
        val manager = manager()

        manager.setDarkTheme(true)
        assertEquals(true, manager.darkTheme.first())

        manager.setDarkTheme(false)
        assertEquals(false, manager.darkTheme.first())
    }

    @Test
    fun `cambiar el tema no toca el resto de los ajustes`() = runTest {
        val original = AppSettings(riderName = "Ana", countdownSeconds = 30, onboardingCompleted = true)
        val repository = SettingsRepository(FakeDataStore(original.toProto()))

        ThemeManager(repository).setDarkTheme(true)

        assertEquals(original.copy(darkTheme = true), repository.settings.first())
    }

    @Test
    fun `sin indicar nada el tema aplicado es el claro`() {
        var background = androidx.compose.ui.graphics.Color.Unspecified
        composeRule.setContent {
            MotoCrashGuardianTheme {
                background = MaterialTheme.colorScheme.background
                Text("x")
            }
        }
        assertEquals(LightColorScheme.background, background)
    }

    @Test
    fun `el boton alterna el tema y describe la accion`() {
        var toggles = 0
        composeRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalThemeController provides ThemeController(darkTheme = false, onToggle = { toggles++ })
            ) { MotoCrashGuardianTheme { ThemeToggleButton() } }
        }

        composeRule.onNodeWithContentDescription("Cambiar a tema oscuro").performClick()

        assertEquals(1, toggles)
    }
}
