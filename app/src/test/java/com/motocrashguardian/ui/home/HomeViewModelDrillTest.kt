package com.motocrashguardian.ui.home

import android.content.Context
import com.motocrashguardian.ble.DeviceConnectionState
import com.motocrashguardian.ble.DeviceRepository
import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.core.model.ContactRole
import com.motocrashguardian.core.model.EmergencyContact
import com.motocrashguardian.data.settings.SettingsRepository
import com.motocrashguardian.detection.GuardianState
import com.motocrashguardian.detection.GuardianStateMachine
import com.motocrashguardian.emergency.DrillOutcome
import com.motocrashguardian.emergency.DrillStarter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelDrillTest {

    private val dispatcher = StandardTestDispatcher()
    private val settings = AppSettings(
        primaryContact = EmergencyContact(ContactRole.PRIMARY, "Ana", "+573001112233")
    )
    private val settingsFlow = MutableStateFlow(settings)
    private val settingsRepository = mockk<SettingsRepository>()
    private val drillStarter = mockk<DrillStarter>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { settingsRepository.settings } returns settingsFlow
        coEvery { settingsRepository.updateSettings(any()) } coAnswers {
            settingsFlow.value = firstArg<(AppSettings) -> AppSettings>()(settingsFlow.value)
        }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): HomeViewModel {
        val deviceRepository = mockk<DeviceRepository>(relaxed = true) {
            every { connectionState } returns MutableStateFlow(DeviceConnectionState.DISCONNECTED)
            every { telemetry } returns emptyFlow()
        }
        val stateMachine = mockk<GuardianStateMachine> {
            every { state } returns MutableStateFlow(GuardianState.Monitoring)
        }
        return HomeViewModel(
            context = mockk<Context>(relaxed = true),
            deviceRepository = deviceRepository,
            settingsRepository = settingsRepository,
            stateMachine = stateMachine,
            drillStarter = drillStarter
        )
    }

    @Test
    fun `pedir el simulacro solo abre la confirmacion y no lo inicia`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.requestDrill()

        assertEquals(DrillUiState.Confirming, vm.drill.value)
        coVerify(exactly = 0) { drillStarter.start(any()) }
    }

    @Test
    fun `confirmar sin haber pedido no hace nada`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.confirmDrill()
        advanceUntilIdle()

        assertEquals(DrillUiState.Idle, vm.drill.value)
        coVerify(exactly = 0) { drillStarter.start(any()) }
    }

    @Test
    fun `cancelar la confirmacion vuelve a Idle`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.requestDrill()

        vm.dismissDrill()

        assertEquals(DrillUiState.Idle, vm.drill.value)
    }

    @Test
    fun `al empezar el simulacro la interfaz queda libre para mostrar la alerta`() = runTest(dispatcher) {
        coEvery { drillStarter.start(any()) } returns DrillOutcome.Started(viaDevice = true, fellBack = false)
        val vm = viewModel()

        vm.requestDrill()
        vm.confirmDrill()
        advanceUntilIdle()

        assertEquals(DrillUiState.Idle, vm.drill.value)
        coVerify(exactly = 1) { drillStarter.start(settings) }
    }

    @Test
    fun `cada motivo de bloqueo se traduce a su mensaje`() = runTest(dispatcher) {
        val expected = mapOf(
            DrillOutcome.NoContacts to DrillUiState.Reason.NO_CONTACTS,
            DrillOutcome.ProtectionOff to DrillUiState.Reason.PROTECTION_OFF,
            DrillOutcome.AlertInProgress to DrillUiState.Reason.ALERT_IN_PROGRESS
        )
        for ((outcome, reason) in expected) {
            coEvery { drillStarter.start(any()) } returns outcome
            val vm = viewModel()
            vm.requestDrill()
            vm.confirmDrill()
            advanceUntilIdle()
            assertEquals(DrillUiState.Blocked(reason), vm.drill.value)

            vm.dismissDrill()
            assertEquals(DrillUiState.Idle, vm.drill.value)
        }
    }

    @Test
    fun `mientras arranca no se puede cancelar ni repetir`() = runTest(dispatcher) {
        val gate = CompletableDeferred<DrillOutcome>()
        coEvery { drillStarter.start(any()) } coAnswers { gate.await() }
        val vm = viewModel()
        vm.requestDrill()
        vm.confirmDrill()
        advanceUntilIdle()
        assertEquals(DrillUiState.Starting, vm.drill.value)

        vm.dismissDrill()
        vm.requestDrill()
        vm.confirmDrill()
        advanceUntilIdle()

        assertEquals(DrillUiState.Starting, vm.drill.value)
        coVerify(exactly = 1) { drillStarter.start(any()) }
        gate.complete(DrillOutcome.Started(viaDevice = false, fellBack = false))
        advanceUntilIdle()
        assertEquals(DrillUiState.Idle, vm.drill.value)
    }

    @Test
    fun `la llamada real del simulacro se guarda en los ajustes`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.setDrillPlaceCall(true)
        advanceUntilIdle()
        assertEquals(true, settingsFlow.value.drillPlaceCall)

        vm.setDrillPlaceCall(false)
        advanceUntilIdle()
        assertEquals(false, settingsFlow.value.drillPlaceCall)
    }
}
