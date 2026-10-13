package com.motocrashguardian.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.motocrashguardian.ui.theme.MotoCrashGuardianTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** S-01 Bienvenida (docs/06 §4): contenido y casilla de responsabilidad. */
@RunWith(RobolectricTestRunner::class)
// Pantalla alta: assertIsDisplayed exige que el nodo este dentro del viewport (por defecto 320x470 dp).
@Config(qualifiers = "w411dp-h2000dp-xxhdpi")
class WelcomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun muestraTituloYAdvertenciaDePrototipo() {
        composeRule.setContent { MotoCrashGuardianTheme { WelcomeScreen() } }
        composeRule.onNodeWithText("Moto Crash Guardian").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Este sistema es un prototipo de apoyo. No reemplaza a " +
                "los servicios de emergencia ni garantiza la detección " +
                "de todos los accidentes."
        ).assertIsDisplayed()
    }

    @Test
    fun botonDeContinuarSeHabilitaAlAceptar() {
        composeRule.setContent { MotoCrashGuardianTheme { WelcomeScreen() } }
        val buttonText = "COMENZAR CONFIGURACIÓN"
        composeRule.onNodeWithText(buttonText).assertIsDisplayed()
        composeRule.onNodeWithText(buttonText).assertIsNotEnabled()

        // La casilla es el único nodo con acción de conmutar.
        composeRule.onNode(isToggleable()).performClick()

        composeRule.onNodeWithText(buttonText).assertIsEnabled()
    }
}
