package com.motocrashguardian.ui.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.IncidentStatus
import com.motocrashguardian.core.model.IncidentType
import com.motocrashguardian.core.model.TriggerType
import com.motocrashguardian.ui.theme.MotoCrashGuardianTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
// Pantalla alta: assertIsDisplayed exige que el nodo este dentro del viewport.
@Config(qualifiers = "w411dp-h2000dp-xxhdpi")
class HistoryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val opened = mutableListOf<UUID>()
    private val filters = mutableListOf<HistoryFilter>()
    private var backs = 0

    private fun incident(
        trigger: TriggerType = TriggerType.SEVERE_IMPACT,
        status: IncidentStatus = IncidentStatus.DISPATCHED,
        type: IncidentType = IncidentType.REAL
    ) = Incident(
        type = type,
        triggerType = trigger,
        status = status,
        detectedAt = Instant.parse("2026-10-19T15:30:00Z")
    )

    private fun show(state: HistoryUiState) {
        composeRule.setContent {
            MotoCrashGuardianTheme {
                HistoryContent(
                    state = state,
                    onBack = { backs++ },
                    onFilterChange = { filters += it },
                    onOpenIncident = { opened += it }
                )
            }
        }
    }

    @Test
    fun sinIncidentesExplicaCuandoAparecenYNoMuestraLista() {
        show(HistoryUiState())

        composeRule.onNodeWithText("Historial de incidentes").assertIsDisplayed()
        composeRule.onNodeWithText("Aún no hay incidentes").assertIsDisplayed()
    }

    @Test
    fun conFiltroSinCoincidenciasSugiereOtroFiltro() {
        show(HistoryUiState(items = emptyList(), filter = HistoryFilter.DRILL, totalCount = 3))

        composeRule.onNodeWithText("Ningún incidente de este tipo").assertIsDisplayed()
    }

    @Test
    fun cadaFilaMuestraDetonanteEstadoYSimulacro() {
        val real = incident(TriggerType.SEVERE_IMPACT, IncidentStatus.DISPATCHED)
        val drill = incident(TriggerType.TEST, IncidentStatus.CANCELLED_BY_USER, IncidentType.DRILL)
        show(HistoryUiState(items = listOf(real, drill), totalCount = 2))

        composeRule.onNodeWithText("Impacto severo").assertIsDisplayed()
        composeRule.onNodeWithText("Alerta enviada").assertIsDisplayed()
        composeRule.onNodeWithText("Prueba").assertIsDisplayed()
        composeRule.onNodeWithText("Cancelado por ti").assertIsDisplayed()
        composeRule.onNodeWithText("SIMULACRO").assertIsDisplayed()
    }

    @Test
    fun soloLosSimulacrosLlevanLaEtiqueta() {
        show(HistoryUiState(items = listOf(incident()), totalCount = 1))

        assertEquals(0, composeRule.onAllNodes(hasText("SIMULACRO")).fetchSemanticsNodes().size)
    }

    @Test
    fun tocarUnaFilaAbreSuDetalle() {
        val target = incident()
        show(HistoryUiState(items = listOf(target), totalCount = 1))

        composeRule.onNodeWithText("Impacto severo").performClick()

        assertEquals(listOf(target.id), opened)
    }

    @Test
    fun losFiltrosNotificanLaSeleccion() {
        show(HistoryUiState(items = listOf(incident()), totalCount = 1))

        composeRule.onNodeWithText("Simulacros").performClick()
        composeRule.onNodeWithText("Reales").performClick()
        composeRule.onNodeWithText("Todos").performClick()

        assertEquals(listOf(HistoryFilter.DRILL, HistoryFilter.REAL, HistoryFilter.ALL), filters)
    }

    @Test
    fun volverLlamaAlCallback() {
        show(HistoryUiState())

        composeRule.onNodeWithContentDescription("Volver").performClick()

        assertTrue(backs == 1)
    }
}
