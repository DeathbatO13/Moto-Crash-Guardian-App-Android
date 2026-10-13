package com.motocrashguardian.ui.privacy

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.motocrashguardian.ui.theme.MotoCrashGuardianTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
// Pantalla alta: assertIsDisplayed exige que el nodo este dentro del viewport.
@Config(qualifiers = "w411dp-h2000dp-xxhdpi")
class PrivacyScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var erased = 0
    private var requested = 0
    private var confirmed = 0
    private var dismissed = 0
    private var backs = 0

    private fun show(state: PrivacyUiState) {
        composeRule.setContent {
            MotoCrashGuardianTheme {
                PrivacyContent(
                    state = state,
                    onBack = { backs++ },
                    onRequestErase = { requested++ },
                    onDismissErase = { dismissed++ },
                    onConfirmErase = { confirmed++ },
                    onErased = { erased++ }
                )
            }
        }
    }

    @Test
    fun muestraQueSeGuardaYDondeSeGuarda() {
        show(
            PrivacyUiState(
                consentAcceptedAt = Instant.parse("2026-10-19T15:30:00Z"),
                contactCount = 2,
                hasRiderName = true,
                incidentCount = 3,
                hasServerCopy = true,
                pendingUploads = 0
            )
        )

        composeRule.onNodeWithText("Privacidad y datos").assertIsDisplayed()
        composeRule.onNodeWithText("Tu nombre").assertIsDisplayed()
        composeRule.onNodeWithText("2 contactos de emergencia").assertIsDisplayed()
        composeRule.onNodeWithText("3 incidentes, con su ubicación y datos del sensor").assertIsDisplayed()
        composeRule.onNodeWithText("Todo está al día.").assertIsDisplayed()
        composeRule.onNodeWithText("La emergencia nunca depende del servidor: funciona sin internet.")
            .assertIsDisplayed()
    }

    @Test
    fun sinDatosNiCopiaLoIndicaClaramente() {
        show(PrivacyUiState())

        composeRule.onNodeWithText("Tu nombre (aún sin configurar)").assertIsDisplayed()
        composeRule.onNodeWithText("Contactos de emergencia (aún sin configurar)").assertIsDisplayed()
        composeRule.onNodeWithText("Todavía no se ha creado ninguna copia en el servidor.").assertIsDisplayed()
        composeRule.onNodeWithText("Aún no has dado tu consentimiento.").assertIsDisplayed()
    }

    @Test
    fun avisaCuantoFaltaPorSubir() {
        show(PrivacyUiState(hasServerCopy = true, pendingUploads = 3))

        composeRule.onNodeWithText("3 elemento(s) pendientes de subir.").assertIsDisplayed()
    }

    @Test
    fun elBotonDeBorrarPideConfirmacionPeroNoBorra() {
        show(PrivacyUiState())

        composeRule.onNodeWithText("BORRAR MIS DATOS").performClick()

        assertEquals(1, requested)
        assertEquals(0, confirmed)
    }

    @Test
    fun elDialogoDeConfirmacionExplicaLoQueSeBorra() {
        show(PrivacyUiState(erase = EraseUiState.Confirming))

        composeRule.onNodeWithText("¿Borrar todos tus datos?").assertIsDisplayed()
        composeRule.onNodeWithText("BORRAR TODO").performClick()
        assertEquals(1, confirmed)
        composeRule.onNodeWithText("Cancelar").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun mientrasBorraMuestraProgresoSinBotonesDeConfirmar() {
        show(PrivacyUiState(erase = EraseUiState.Erasing))

        composeRule.onNodeWithText("Borrando…").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodes(hasText("BORRAR TODO")).fetchSemanticsNodes().size)
    }

    @Test
    fun losErroresExplicanQueNoSeBorroNada() {
        show(PrivacyUiState(erase = EraseUiState.NoConnection))

        composeRule.onNodeWithText("No se pudo completar").assertIsDisplayed()
        composeRule.onNodeWithText(
            "No se pudo conectar con el servidor, así que no se borró nada. " +
                "Vuelve a intentarlo con conexión a internet."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Reintentar").performClick()
        assertEquals(1, requested)
    }

    @Test
    fun alTerminarElBorradoNotificaParaVolverAlInicio() {
        show(PrivacyUiState(erase = EraseUiState.Done))
        composeRule.waitForIdle()

        assertEquals(1, erased)
    }

    @Test
    fun noNotificaBorradoSiNoTermino() {
        show(PrivacyUiState(erase = EraseUiState.Confirming))
        composeRule.waitForIdle()

        assertEquals(0, erased)
    }

    @Test
    fun volverLlamaAlCallback() {
        show(PrivacyUiState())

        composeRule.onNodeWithContentDescription("Volver").performClick()

        assertEquals(1, backs)
    }
}
