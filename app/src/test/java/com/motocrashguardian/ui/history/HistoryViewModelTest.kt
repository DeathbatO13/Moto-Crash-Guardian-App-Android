package com.motocrashguardian.ui.history

import androidx.lifecycle.SavedStateHandle
import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.core.model.DetectionConfig
import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.IncidentStatus
import com.motocrashguardian.core.model.IncidentType
import com.motocrashguardian.core.model.Trace
import com.motocrashguardian.core.model.TriggerType
import com.motocrashguardian.data.incidents.IncidentRepository
import com.motocrashguardian.data.settings.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val base = Instant.parse("2026-10-19T15:30:00Z")

    private fun incident(
        offsetSeconds: Long = 0,
        type: IncidentType = IncidentType.REAL,
        id: UUID = UUID.randomUUID()
    ) = Incident(
        id = id,
        type = type,
        triggerType = TriggerType.IMPACT,
        status = IncidentStatus.DISPATCHED,
        detectedAt = base.plusSeconds(offsetSeconds)
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Lista ---

    @Test
    fun `la lista va del mas reciente al mas antiguo`() {
        val old = incident(0)
        val newest = incident(120)
        val middle = incident(60)

        val state = buildHistoryState(listOf(old, newest, middle), HistoryFilter.ALL)

        assertEquals(listOf(newest, middle, old), state.items)
    }

    @Test
    fun `el filtro separa reales de simulacros`() {
        val real = incident(type = IncidentType.REAL)
        val drill = incident(type = IncidentType.DRILL)
        val all = listOf(real, drill)

        assertEquals(listOf(real), buildHistoryState(all, HistoryFilter.REAL).items)
        assertEquals(listOf(drill), buildHistoryState(all, HistoryFilter.DRILL).items)
        assertEquals(2, buildHistoryState(all, HistoryFilter.ALL).items.size)
    }

    @Test
    fun `el total no depende del filtro`() {
        val all = listOf(incident(type = IncidentType.REAL), incident(type = IncidentType.DRILL))

        val state = buildHistoryState(all, HistoryFilter.DRILL)

        assertEquals(1, state.items.size)
        assertEquals(2, state.totalCount)
    }

    @Test
    fun `el ViewModel reacciona a nuevos incidentes y a cambios de filtro`() = runTest(dispatcher) {
        val flow = MutableStateFlow(listOf(incident(type = IncidentType.REAL)))
        val repository = mockk<IncidentRepository>()
        every { repository.incidents } returns flow
        val vm = HistoryViewModel(repository)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        assertEquals(1, vm.state.value.items.size)

        flow.value = flow.value + incident(10, IncidentType.DRILL)
        advanceUntilIdle()
        assertEquals(2, vm.state.value.items.size)

        vm.setFilter(HistoryFilter.DRILL)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.items.size)
        assertEquals(IncidentType.DRILL, vm.state.value.items.single().type)
    }

    // --- Detalle ---

    private fun trace(incidentId: UUID) = Trace(
        incidentId = incidentId,
        sampleRateHz = 100,
        preTriggerSamples = 1,
        totalSamples = 2,
        accelLsbPerG = 2048,
        gyroLsbPerDpsX10 = 164,
        samples = ByteArray(24)
    )

    @Test
    fun `sin incidente el detalle es NotFound`() {
        assertEquals(DetailUiState.NotFound, buildDetailState(null, null, AppSettings()))
    }

    @Test
    fun `el detalle trae la traza decodificada y el umbral actual en g`() {
        val incident = incident()
        val settings = AppSettings(detection = DetectionConfig(impactThresholdMg = 4_200))

        val state = buildDetailState(incident, trace(incident.id), settings) as DetailUiState.Loaded

        assertEquals(incident, state.incident)
        assertEquals(4.2f, state.impactThresholdG, 0.001f)
        assertEquals(2, state.trace?.points?.size)
    }

    @Test
    fun `un incidente sin traza descargada se muestra sin grafica`() {
        val state = buildDetailState(incident(), null, AppSettings()) as DetailUiState.Loaded

        assertNull(state.trace)
    }

    private fun TestScope.detailViewModel(
        id: String?,
        incident: Incident?,
        trace: Trace? = null
    ): IncidentDetailViewModel {
        val repository = mockk<IncidentRepository>()
        every { repository.observeIncident(any()) } returns MutableStateFlow(incident)
        coEvery { repository.getTrace(any()) } returns trace
        val settings = mockk<SettingsRepository>()
        every { settings.settings } returns MutableStateFlow(AppSettings())
        val handle = SavedStateHandle(if (id == null) emptyMap() else mapOf("incidentId" to id))
        val vm = IncidentDetailViewModel(handle, repository, settings)
        backgroundScope.launch { vm.state.collect {} }
        return vm
    }

    @Test
    fun `el ViewModel de detalle carga el incidente y su traza por id`() = runTest(dispatcher) {
        val incident = incident()
        val vm = detailViewModel(incident.id.toString(), incident, trace(incident.id))
        advanceUntilIdle()

        val state = vm.state.value as DetailUiState.Loaded

        assertEquals(incident, state.incident)
        assertNotNull(state.trace)
    }

    @Test
    fun `un id invalido o ausente da NotFound en lugar de fallar`() = runTest(dispatcher) {
        val invalid = detailViewModel("no-es-un-uuid", incident = null)
        val missing = detailViewModel(null, incident = null)
        advanceUntilIdle()

        assertEquals(DetailUiState.NotFound, invalid.state.value)
        assertEquals(DetailUiState.NotFound, missing.state.value)
    }

    @Test
    fun `el detalle empieza en Loading antes de leer la base`() = runTest(dispatcher) {
        val vm = detailViewModel(UUID.randomUUID().toString(), incident())

        assertTrue(vm.state.value is DetailUiState.Loading)
    }
}
