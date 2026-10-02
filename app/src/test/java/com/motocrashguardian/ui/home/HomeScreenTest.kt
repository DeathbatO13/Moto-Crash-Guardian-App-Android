package com.motocrashguardian.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.motocrashguardian.ui.theme.MotoCrashGuardianTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Humo de la pila Robolectric + Compose UI Test (JUnit 4 sobre JUnit Platform via Vintage). */
@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun muestraElNombreDeLaApp() {
        composeRule.setContent { MotoCrashGuardianTheme { HomeScreen() } }
        composeRule.onNodeWithText("Moto Crash Guardian").assertIsDisplayed()
    }
}
