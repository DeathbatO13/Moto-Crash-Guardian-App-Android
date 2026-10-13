package com.motocrashguardian.ui.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.motocrashguardian.core.model.CallStatus
import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.IncidentStatus
import com.motocrashguardian.core.model.IncidentType
import com.motocrashguardian.core.model.LocationSource
import com.motocrashguardian.core.model.SmsStatus
import com.motocrashguardian.core.model.SyncState
import com.motocrashguardian.core.model.TriggerType
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
@Config(qualifiers = "w411dp-h2400dp-xxhdpi")
class IncidentDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val maps = mutableListOf<Pair<Double, Double>>()
    private var backs = 0
    private val detectedAt = Instant.parse("2026-10-19T15:30:00Z")

    private val full = Incident(
        type = IncidentType.REAL,
        triggerType = TriggerType.SEVERE_IMPACT,
        status = IncidentStatus.DISPATCH_PARTIAL,
        detectedAt = detectedAt,
        peakAccelMg = 9_500,
        peakGyroDps = 410,
        pitchCdeg = -4_550,
        rollCdeg = 1_200,
        latitude = 4.710989,
        longitude = -74.072092,
        locationAccuracyMeters = 12f,
        locationSource = LocationSource.PHONE_GPS,
        locationFixAt = detectedAt,
        primarySmsStatus = SmsStatus.SENT,
        secondarySmsStatus = SmsStatus.FAILED,
        callStatus = CallStatus.PLACED,
        firmwareVersion = "1.2.3",
        syncState = SyncState.SYNCED
    )

    private val trace = DecodedTrace(
        points = listOf(
            TracePoint(-1f, 1f, 0f, false),
            TracePoint(0f, 9.5f, 410f, true),
            TracePoint(1f, 2f, 0f, false)
        ),
        sampleRateHz = 100
    )

    private fun show(state: DetailUiState) {
        composeRule.setContent {
            MotoCrashGuardianTheme {
                IncidentDetailContent(
                    state = state,
                    onBack = { backs++ },
                    onOpenMap = { lat, lon -> maps += lat to lon }
                )
            }
        }
    }

    @Test
    fun muestraResumenDeLoQuePaso() {
        show(DetailUiState.Loaded(full, trace = null, impactThresholdG = 3.5f))

        composeRule.onNodeWithText("Detalle del incidente").assertIsDisplayed()
        composeRule.onNodeWithText("Enviada parcialmente").assertIsDisplayed()
        composeRule.onNodeWithText("Impacto severo").assertIsDisplayed()
        composeRule.onNodeWithText("9,5 g").assertIsDisplayed()
        composeRule.onNodeWithText("410 °/s").assertIsDisplayed()
        composeRule.onNode(hasText("cabeceo -45,5°", substring = true)).assertIsDisplayed()
    }

    @Test
    fun muestraElResultadoDelAvisoPorCanal() {
        show(DetailUiState.Loaded(full, trace = null, impactThresholdG = 3.5f))

        composeRule.onNodeWithText("Enviado").assertIsDisplayed()
        composeRule.onNodeWithText("Falló").assertIsDisplayed()
        composeRule.onNodeWithText("Llamada realizada").assertIsDisplayed()
    }

    @Test
    fun conUbicacionOfreceAbrirElMapa() {
        show(DetailUiState.Loaded(full, trace = null, impactThresholdG = 3.5f))

        composeRule.onNodeWithText("GPS del teléfono").assertIsDisplayed()
        composeRule.onNodeWithText("4.710989, -74.072092").assertIsDisplayed()
        composeRule.onNodeWithText("Abrir en el mapa").performClick()

        assertEquals(listOf(4.710989 to -74.072092), maps)
    }

    @Test
    fun sinUbicacionLoDiceYNoOfreceMapa() {
        val noLocation = full.copy(
            latitude = null,
            longitude = null,
            locationSource = LocationSource.NONE,
            locationAccuracyMeters = null,
            locationFixAt = null
        )
        show(DetailUiState.Loaded(noLocation, trace = null, impactThresholdG = 3.5f))

        composeRule.onNodeWithText("No se pudo obtener la ubicación.").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodes(hasText("Abrir en el mapa")).fetchSemanticsNodes().size)
    }

    @Test
    fun sinTrazaIndicaQueNoHayDatosDelSensor() {
        show(DetailUiState.Loaded(full, trace = null, impactThresholdG = 3.5f))

        composeRule.onNodeWithText("Sin datos del sensor para este incidente.").assertIsDisplayed()
    }

    @Test
    fun conTrazaDibujaLaGraficaConSuResumenAccesible() {
        show(DetailUiState.Loaded(full, trace = trace, impactThresholdG = 3.5f))

        composeRule.onNode(hasContentDescription("Gráfica de aceleración", substring = true))
            .assertIsDisplayed()
        composeRule.onNode(hasContentDescription("Pico de 9,5 g", substring = true)).assertIsDisplayed()
        composeRule.onNodeWithText("Disparo").assertIsDisplayed()
        composeRule.onNode(hasText("Umbral de impacto actual (3,5 g)", substring = true)).assertIsDisplayed()
    }

    @Test
    fun avisaCuandoElSensorSeSaturo() {
        show(DetailUiState.Loaded(full, trace = trace, impactThresholdG = 3.5f))

        composeRule.onNode(hasText("Sensor saturado en 1 muestra(s)", substring = true)).assertIsDisplayed()
    }

    @Test
    fun unSimulacroSeMarcaComoTal() {
        val drill = full.copy(type = IncidentType.DRILL, triggerType = TriggerType.TEST)
        show(DetailUiState.Loaded(drill, trace = null, impactThresholdG = 3.5f))

        composeRule.onNodeWithText("SIMULACRO").assertIsDisplayed()
    }

    @Test
    fun muestraElEstadoDelRespaldoYElFirmware() {
        show(DetailUiState.Loaded(full, trace = null, impactThresholdG = 3.5f))

        composeRule.onNodeWithText("Respaldado en el servidor").assertIsDisplayed()
        composeRule.onNodeWithText("1.2.3").assertIsDisplayed()
    }

    @Test
    fun siElIncidenteYaNoExisteLoExplica() {
        show(DetailUiState.NotFound)

        composeRule.onNodeWithText("Este incidente ya no existe en el historial.").assertIsDisplayed()
    }

    @Test
    fun volverLlamaAlCallback() {
        show(DetailUiState.NotFound)

        composeRule.onNodeWithContentDescription("Volver").performClick()

        assertEquals(1, backs)
    }
}
