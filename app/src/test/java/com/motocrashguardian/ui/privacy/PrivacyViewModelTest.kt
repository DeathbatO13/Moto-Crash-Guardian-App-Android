package com.motocrashguardian.ui.privacy

import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.core.model.ContactRole
import com.motocrashguardian.core.model.EmergencyContact
import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.IncidentStatus
import com.motocrashguardian.core.model.IncidentType
import com.motocrashguardian.core.model.SyncState
import com.motocrashguardian.core.model.TriggerType
import com.motocrashguardian.data.incidents.IncidentRepository
import com.motocrashguardian.data.privacy.DataErasing
import com.motocrashguardian.data.privacy.EraseResult
import com.motocrashguardian.data.remote.AuthSession
import com.motocrashguardian.data.settings.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-10-19T15:30:00Z")
    private val settingsFlow = MutableStateFlow(AppSettings(consentAcceptedAt = now))
    private val incidentsFlow = MutableStateFlow<List<Incident>>(emptyList())
    private var installationId: String? = null
    private var eraseResult: suspend () -> EraseResult = { EraseResult.Success(true) }
    private var eraseCalls = 0

    private val auth = object : AuthSession {
        override fun installationId() = installationId
        override fun token() = null
        override fun save(installationId: String, token: String) = Unit
        override fun clear() = Unit
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun incident(sync: SyncState = SyncState.PENDING) = Incident(
        type = IncidentType.REAL,
        triggerType = TriggerType.IMPACT,
        status = IncidentStatus.DISPATCHED,
        detectedAt = now,
        syncState = sync
    )

    private fun TestScope.viewModel(): PrivacyViewModel {
        val settingsRepository = mockk<SettingsRepository>()
        every { settingsRepository.settings } returns settingsFlow
        val incidentRepository = mockk<IncidentRepository>()
        every { incidentRepository.incidents } returns incidentsFlow
        val vm = PrivacyViewModel(
            settingsRepository,
            incidentRepository,
            auth,
            DataErasing {
                eraseCalls++
                eraseResult()
            }
        )
        backgroundScope.launch { vm.state.collect {} }
        return vm
    }

    // --- Estado ---

    @Test
    fun `resume lo que hay guardado`() = runTest(dispatcher) {
        settingsFlow.value = AppSettings(
            riderName = "Carlos",
            primaryContact = EmergencyContact(ContactRole.PRIMARY, "Ana", "+573001112233"),
            secondaryContact = EmergencyContact(ContactRole.SECONDARY, "Luis", "+573004445566"),
            consentAcceptedAt = now,
            configDirty = true
        )
        incidentsFlow.value = listOf(incident(), incident(SyncState.SYNCED))
        installationId = "inst-1"
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.state.value

        assertEquals(now, state.consentAcceptedAt)
        assertEquals(2, state.contactCount)
        assertEquals(true, state.hasRiderName)
        assertEquals(2, state.incidentCount)
        assertEquals(true, state.hasServerCopy)
        assertEquals(2, state.pendingUploads) // 1 incidente + la config
    }

    @Test
    fun `sin registro no hay copia en el servidor`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(false, vm.state.value.hasServerCopy)
        assertEquals(0, vm.state.value.pendingUploads)
    }

    @Test
    fun `un incidente ACTIVE no cuenta como pendiente de subir`() {
        val active = incident().copy(status = IncidentStatus.ACTIVE)

        val state = buildPrivacyState(AppSettings(), listOf(active), false, EraseUiState.Idle)

        assertEquals(0, state.pendingUploads)
    }

    // --- Borrado ---

    @Test
    fun `borrar exige confirmacion antes de llamar al borrado`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.confirmErase() // sin pedir confirmacion
        advanceUntilIdle()
        assertEquals(0, eraseCalls)

        vm.requestErase()
        advanceUntilIdle()
        assertEquals(EraseUiState.Confirming, vm.state.value.erase)
        assertEquals(0, eraseCalls)
    }

    @Test
    fun `cancelar la confirmacion vuelve al estado inicial sin borrar`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.requestErase()
        advanceUntilIdle()

        vm.dismissErase()
        advanceUntilIdle()

        assertEquals(EraseUiState.Idle, vm.state.value.erase)
        assertEquals(0, eraseCalls)
    }

    @Test
    fun `confirmar borra y termina en Done`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.requestErase()
        vm.confirmErase()
        advanceUntilIdle()

        assertEquals(1, eraseCalls)
        assertEquals(EraseUiState.Done, vm.state.value.erase)
    }

    @Test
    fun `mientras borra no se puede cancelar ni repetir`() = runTest(dispatcher) {
        val gate = CompletableDeferred<EraseResult>()
        eraseResult = { gate.await() }
        val vm = viewModel()
        vm.requestErase()
        vm.confirmErase()
        advanceUntilIdle()
        assertEquals(EraseUiState.Erasing, vm.state.value.erase)

        vm.dismissErase()
        vm.confirmErase()
        vm.requestErase()
        advanceUntilIdle()

        assertEquals(EraseUiState.Erasing, vm.state.value.erase)
        assertEquals(1, eraseCalls)
        gate.complete(EraseResult.Success(true))
        advanceUntilIdle()
        assertEquals(EraseUiState.Done, vm.state.value.erase)
    }

    @Test
    fun `los errores se muestran y permiten reintentar`() = runTest(dispatcher) {
        val vm = viewModel()
        val expected = listOf(
            EraseResult.NoConnection to EraseUiState.NoConnection,
            EraseResult.ServerRejected(403) to EraseUiState.Rejected(403),
            EraseResult.LocalFailure to EraseUiState.LocalFailure
        )
        for ((result, uiState) in expected) {
            eraseResult = { result }
            vm.requestErase()
            vm.confirmErase()
            advanceUntilIdle()
            assertEquals(uiState, vm.state.value.erase)
        }

        eraseResult = { EraseResult.Success(true) }
        vm.requestErase() // reintentar desde un error
        advanceUntilIdle()
        assertEquals(EraseUiState.Confirming, vm.state.value.erase)
        vm.confirmErase()
        advanceUntilIdle()
        assertEquals(EraseUiState.Done, vm.state.value.erase)
    }

    @Test
    fun `cerrar un error vuelve al inicio`() = runTest(dispatcher) {
        eraseResult = { EraseResult.NoConnection }
        val vm = viewModel()
        vm.requestErase()
        vm.confirmErase()
        advanceUntilIdle()

        vm.dismissErase()
        advanceUntilIdle()

        assertEquals(EraseUiState.Idle, vm.state.value.erase)
    }
}
