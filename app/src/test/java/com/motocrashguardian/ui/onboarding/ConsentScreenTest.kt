package com.motocrashguardian.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasToggleableAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.motocrashguardian.ui.theme.MotoCrashGuardianTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** S-02 Consentimiento de datos (docs/06 §4). */
@RunWith(RobolectricTestRunner::class)
class ConsentScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun muestraQueRecopilamosYMarcoLegal() {
        composeRule.setContent { MotoCrashGuardianTheme { ConsentScreen() } }
        composeRule.onNodeWithText("Consentimiento de datos").assertIsDisplayed()
        composeRule.onNodeWithText("¿QUÉ RECOPILAMOS?").assertIsDisplayed()
        composeRule.onNodeWithText("Tu nombre").assertIsDisplayed()
        composeRule.onNodeWithText("Dos contactos de emergencia").assertIsDisplayed()
        composeRule.onNodeWithText("Ubicación").assertIsDisplayed()
        composeRule.onNodeWithText("Datos del sensor").assertIsDisplayed()
        composeRule.onNodeWithText("Marco legal de habeas data").assertIsDisplayed()
    }

    @Test
    fun aceptoSeHabilitaConElConsentimiento() {
        composeRule.setContent { MotoCrashGuardianTheme { ConsentScreen() } }
        val buttonText = "ACEPTO"
        composeRule.onNodeWithText(buttonText).assertIsDisplayed()
        composeRule.onNodeWithText(buttonText).assertIsNotEnabled()

        composeRule.onNode(hasToggleableAction()).performClick()

        composeRule.onNodeWithText(buttonText).assertIsEnabled()
    }

    @Test
    fun noAceptoExplicaQueLaAppNoPuedeFuncionar() {
        composeRule.setContent { MotoCrashGuardianTheme { ConsentScreen() } }
        composeRule.onNodeWithText("No acepto").performClick()
        composeRule.onNodeWithText(
            "Sin tus datos de contacto la app no puede funcionar: " +
                "no habría a quién avisar en una emergencia. " +
                "Puedes cambiar de opinión en cualquier momento."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Entendido").performClick()
        composeRule.onNodeWithText("ACEPTO").assertIsDisplayed()
    }
}
