package com.motocrashguardian.ui.alert

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.semantics.SemanticsActions
import com.motocrashguardian.ui.theme.MotoCrashGuardianTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CountdownScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun muestraCuentaRegresivaSimulacroContactosYPrecisionSinDatosSensibles() {
        composeRule.setContent {
            MotoCrashGuardianTheme {
                CountdownScreen(
                    remainingSeconds = 18,
                    totalSeconds = 30,
                    emergencyContactNames = listOf("Ana", "Luis"),
                    locationAccuracyMeters = 12,
                    isSimulation = true,
                    onCancelConfirmed = {},
                    onSendHelpNow = {}
                )
            }
        }

        composeRule.onNodeWithText("SIMULACRO").assertIsDisplayed()
        composeRule.onNodeWithText("18").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Cuenta regresiva: 18 segundos").assertIsDisplayed()
        composeRule
            .onNodeWithText("Se enviará una alerta SOS a Ana, Luis.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Ubicación obtenida ±12 m").performScrollTo().assertIsDisplayed()

        composeRule.onAllNodesWithText("10.20.30.40").assertCountEquals(0)
        composeRule.onAllNodesWithText("+15551234567").assertCountEquals(0)
    }

    @Test
    fun muestraUbicacionNoDisponibleYContactosPredeterminados() {
        composeRule.setContent {
            MotoCrashGuardianTheme {
                CountdownScreen(
                    remainingSeconds = 0,
                    totalSeconds = 30,
                    emergencyContactNames = listOf(" ", ""),
                    locationAccuracyMeters = null,
                    isSimulation = false,
                    onCancelConfirmed = {},
                    onSendHelpNow = {}
                )
            }
        }

        composeRule.onAllNodesWithText("SIMULACRO").assertCountEquals(0)
        composeRule.onNodeWithText("Ubicación no disponible").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            "Se enviará una alerta SOS a tus contactos de emergencia."
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun enviarAyudaEsUnaAccionAccesibleDeClic() {
        var sendHelpCalls = 0
        composeRule.setContent {
            MotoCrashGuardianTheme {
                CountdownScreen(
                    remainingSeconds = 10,
                    totalSeconds = 30,
                    emergencyContactNames = emptyList(),
                    locationAccuracyMeters = null,
                    isSimulation = false,
                    onCancelConfirmed = {},
                    onSendHelpNow = { sendHelpCalls++ }
                )
            }
        }

        composeRule.onNodeWithText("ENVIAR AYUDA AHORA").performScrollTo().performClick()

        assertEquals(1, sendHelpCalls)
    }

    @Test
    fun cancelarExponeAccionAccesibleDePulsacionLarga() {
        var cancelCalls = 0
        composeRule.setContent {
            MotoCrashGuardianTheme {
                CountdownScreen(
                    remainingSeconds = 10,
                    totalSeconds = 30,
                    emergencyContactNames = emptyList(),
                    locationAccuracyMeters = null,
                    isSimulation = false,
                    onCancelConfirmed = { cancelCalls++ },
                    onSendHelpNow = {}
                )
            }
        }

        composeRule
            .onNodeWithText("MANTÉN PRESIONADO: ESTOY BIEN")
            .performSemanticsAction(SemanticsActions.OnLongClick) { action -> action() }

        assertEquals(1, cancelCalls)
    }

    @Test
    fun cancelarRequiereMantenerLaPulsacionUnSegundo() {
        var cancelCalls = 0
        composeRule.setContent {
            MotoCrashGuardianTheme {
                CountdownScreen(
                    remainingSeconds = 10,
                    totalSeconds = 30,
                    emergencyContactNames = emptyList(),
                    locationAccuracyMeters = null,
                    isSimulation = false,
                    onCancelConfirmed = { cancelCalls++ },
                    onSendHelpNow = {}
                )
            }
        }

        val cancelButton = composeRule.onNodeWithText("MANTÉN PRESIONADO: ESTOY BIEN")
        cancelButton.performScrollTo()
        cancelButton.performTouchInput {
            down(center)
            advanceEventTime(500)
            up()
        }
        assertEquals(0, cancelCalls)

        cancelButton.performTouchInput {
            down(center)
            advanceEventTime(1_100)
            up()
        }
        assertEquals(1, cancelCalls)
    }
}
