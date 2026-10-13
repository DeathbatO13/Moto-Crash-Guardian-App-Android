package com.motocrashguardian.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.motocrashguardian.ble.DeviceConnectionState
import com.motocrashguardian.ble.DeviceRepository
import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.data.settings.SettingsRepository
import com.motocrashguardian.detection.GuardianState
import com.motocrashguardian.detection.GuardianStateMachine
import com.motocrashguardian.ui.theme.MotoCrashGuardianTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Humo de la pila Robolectric + Compose UI Test (JUnit 4 sobre JUnit Platform via Vintage). */
@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** HomeScreen usa hiltViewModel() por defecto; sin Hilt en el test se le inyecta uno armado a mano. */
    private fun homeViewModel(): HomeViewModel {
        val deviceRepository = mockk<DeviceRepository>(relaxed = true) {
            every { connectionState } returns MutableStateFlow(DeviceConnectionState.DISCONNECTED)
            every { telemetry } returns emptyFlow()
        }
        val settingsRepository = mockk<SettingsRepository> {
            every { settings } returns flowOf(AppSettings())
        }
        val stateMachine = mockk<GuardianStateMachine> {
            every { state } returns MutableStateFlow(GuardianState.Idle)
        }
        return HomeViewModel(
            context = RuntimeEnvironment.getApplication(),
            deviceRepository = deviceRepository,
            settingsRepository = settingsRepository,
            stateMachine = stateMachine
        )
    }

    @Test
    fun muestraElNombreDeLaApp() {
        val viewModel = homeViewModel()
        composeRule.setContent {
            MotoCrashGuardianTheme { HomeScreen(viewModel = viewModel) }
        }
        composeRule.onNodeWithText("Moto Crash Guardian").assertIsDisplayed()
    }
}
