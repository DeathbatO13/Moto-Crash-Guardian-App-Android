package com.motocrashguardian.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.core.model.ContactRole
import com.motocrashguardian.core.model.EmergencyContact
import com.motocrashguardian.ui.theme.MotoCrashGuardianTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Pantalla alta: assertIsDisplayed exige que el nodo este dentro del viewport.
@Config(qualifiers = "w411dp-h2000dp-xxhdpi")
class DrillSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var started = 0
    private var confirmed = 0
    private var dismissed = 0
    private var callToggles = mutableListOf<Boolean>()

    private val primary = EmergencyContact(ContactRole.PRIMARY, "Ana", "+573001112233")
    private val secondary = EmergencyContact(ContactRole.SECONDARY, "Luis", "+573004445566")

    private fun show(
        drill: DrillUiState = DrillUiState.Idle,
        settings: AppSettings = AppSettings(primaryContact = primary, secondaryContact = secondary)
    ) {
        composeRule.setContent {
            MotoCrashGuardianTheme {
                DrillSection(
                    drill = drill,
                    settings = settings,
                    onDrillPlaceCallChange = { callToggles += it },
                    onStartDrill = { started++ },
                    onConfirmDrill = { confirmed++ },
                    onDismissDrill = { dismissed++ }
                )
            }
        }
    }

    @Test
    fun explicaQueLosSmsVanMarcadosComoPrueba() {
        show()

        composeRule.onNodeWithText("Simulacro de alerta").assertIsDisplayed()
        composeRule.onNode(hasText("[PRUEBA]", substring = true)).assertIsDisplayed()
    }

    @Test
    fun elBotonIniciaLaConfirmacionSinIniciarElSimulacro() {
        show()

        composeRule.onNodeWithText("INICIAR SIMULACRO").assertIsEnabled().performClick()

        assertEquals(1, started)
        assertEquals(0, confirmed)
    }

    @Test
    fun elBotonSeDeshabilitaMientrasHayUnDialogo() {
        show(drill = DrillUiState.Confirming)

        composeRule.onNodeWithText("INICIAR SIMULACRO").assertIsNotEnabled()
    }

    @Test
    fun elInterruptorGuardaLaOpcionDeLlamadaReal() {
        show()

        composeRule.onNode(isToggleable()).performClick()

        assertEquals(listOf(true), callToggles)
    }

    @Test
    fun laConfirmacionAvisaCuantosContactosRecibenElSms() {
        show(drill = DrillUiState.Confirming)

        composeRule.onNodeWithText("¿Iniciar un simulacro?").assertIsDisplayed()
        composeRule.onNode(hasText("a tus 2 contacto(s)", substring = true)).assertIsDisplayed()
        composeRule.onNodeWithText("INICIAR").performClick()
        assertEquals(1, confirmed)
        composeRule.onNodeWithText("Cancelar").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun laConfirmacionAvisaCuandoSeLlamaDeVerdad() {
        show(
            drill = DrillUiState.Confirming,
            settings = AppSettings(primaryContact = primary, drillPlaceCall = true)
        )

        composeRule.onNode(hasText("se llamará de verdad a Ana", substring = true)).assertIsDisplayed()
    }

    @Test
    fun sinLlamadaHabilitadaLaConfirmacionNoLaMenciona() {
        show(drill = DrillUiState.Confirming)

        val mentions = composeRule.onAllNodes(hasText("se llamará de verdad", substring = true))
            .fetchSemanticsNodes().size
        assertTrue(mentions == 0)
    }

    @Test
    fun elMensajeDeProteccionApagadaEstaDisponible() {
        show(drill = DrillUiState.Blocked(DrillUiState.Reason.PROTECTION_OFF))

        composeRule.onNode(hasText("Activa la protección", substring = true)).assertIsDisplayed()
        composeRule.onNodeWithText("Entendido").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun elMensajeDeSinContactosEstaDisponible() {
        show(
            drill = DrillUiState.Blocked(DrillUiState.Reason.NO_CONTACTS),
            settings = AppSettings()
        )

        composeRule.onNode(hasText("Configura al menos un contacto", substring = true)).assertIsDisplayed()
    }

    @Test
    fun elMensajeDeAlertaEnCursoEstaDisponible() {
        show(drill = DrillUiState.Blocked(DrillUiState.Reason.ALERT_IN_PROGRESS))

        composeRule.onNode(hasText("Ya hay una alerta en curso", substring = true)).assertIsDisplayed()
    }
}
