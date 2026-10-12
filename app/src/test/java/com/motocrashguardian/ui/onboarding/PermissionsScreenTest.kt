package com.motocrashguardian.ui.onboarding

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.motocrashguardian.ui.theme.MotoCrashGuardianTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** S-03 Asistente de permisos (docs/06 §4). */
@RunWith(RobolectricTestRunner::class)
class PermissionsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context =
        RuntimeEnvironment.getApplication<Application>()

    @Test
    fun muestraLaListaDePermisosYElProgreso() {
        val viewModel = PermissionsViewModel(context)
        composeRule.setContent {
            MotoCrashGuardianTheme {
                PermissionsScreen(viewModel = viewModel)
            }
        }
        composeRule.onNodeWithText("Permisos del sistema")
            .assertIsDisplayed()
        composeRule.onNodeWithText("PASO 3 DE 4 · PERMISOS")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Notificaciones").assertIsDisplayed()
        composeRule.onNodeWithText("Dispositivos cercanos")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Ubicación precisa")
            .assertIsDisplayed()
        composeRule.onNodeWithText("SMS").assertIsDisplayed()
        composeRule.onNodeWithText("Llamadas").assertIsDisplayed()
        composeRule.onNodeWithText("Pantalla completa")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Batería sin restricciones")
            .assertIsDisplayed()
        composeRule.onNodeWithText("ESTADO DE INSTALACIÓN")
            .assertIsDisplayed()
        composeRule.onNodeWithText("COMPROBAR PERMISOS DE NUEVO")
            .assertIsDisplayed()
    }

    @Test
    fun continuarEstaDeshabilitadoSinPermisos() {
        val viewModel = PermissionsViewModel(context)
        composeRule.setContent {
            MotoCrashGuardianTheme {
                PermissionsScreen(viewModel = viewModel)
            }
        }
        composeRule.onNodeWithText("CONTINUAR").assertIsDisplayed()
        composeRule.onNodeWithText("CONTINUAR").assertIsNotEnabled()
    }

    @Test
    fun continuarSeHabilitaAlConcederLosP0() {
        val viewModel = PermissionsViewModel(context)
        composeRule.setContent {
            MotoCrashGuardianTheme {
                PermissionsScreen(viewModel = viewModel)
            }
        }
        composeRule.onNodeWithText("CONTINUAR").assertIsNotEnabled()

        viewModel.onPermissionsResult(
            mapOf(
                Manifest.permission.POST_NOTIFICATIONS to true,
                Manifest.permission.BLUETOOTH_SCAN to true,
                Manifest.permission.BLUETOOTH_CONNECT to true,
                Manifest.permission.ACCESS_FINE_LOCATION to true,
                Manifest.permission.ACCESS_COARSE_LOCATION to true,
                Manifest.permission.SEND_SMS to true,
                Manifest.permission.CALL_PHONE to true
            )
        )

        composeRule.onNodeWithText("CONTINUAR").assertIsEnabled()
    }
}
