package com.motocrashguardian.ble

import com.motocrashguardian.core.model.DeviceState
import com.motocrashguardian.detection.GuardianState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModuleArmerTest {

    private val connected = MutableStateFlow(false)
    private val guardian = MutableStateFlow<GuardianState>(GuardianState.Idle)
    private val device = MutableStateFlow<DeviceState?>(null)

    private var armCalls = 0
    private var disarmCalls = 0
    private var armResults: (Int) -> Boolean = { true }
    private var disarmResults: (Int) -> Boolean = { true }

    private fun armer() = ModuleArmer(
        connected = connected,
        guardianState = guardian,
        deviceState = device,
        arm = {
            armCalls++
            armResults(armCalls)
        },
        disarm = {
            disarmCalls++
            disarmResults(disarmCalls)
        },
        maxAttempts = 3,
        retryDelayMillis = 1_000
    )

    private fun input(
        connected: Boolean = true,
        trip: Boolean = true,
        device: DeviceState? = DeviceState.DISARMED
    ) = ArmingInput(connected, trip, device)

    // --- Decision ---

    @Test
    fun `arma con viaje activo y modulo conectado pero desarmado`() {
        assertEquals(ArmingAction.ARM, decideArming(input(device = DeviceState.DISARMED), false))
        assertEquals(ArmingAction.ARM, decideArming(input(device = null), false))
    }

    @Test
    fun `no arma si el modulo ya esta armado o ocupado con un evento`() {
        listOf(
            DeviceState.ARMED,
            DeviceState.POST_TRIGGER,
            DeviceState.COOLDOWN,
            DeviceState.CALIBRATING,
            DeviceState.BOOT,
            DeviceState.FAULT
        ).forEach { state ->
            assertEquals(ArmingAction.NONE, decideArming(input(device = state), false), state.name)
        }
    }

    @Test
    fun `sin conexion no hace nada`() {
        assertEquals(ArmingAction.NONE, decideArming(input(connected = false), false))
        assertEquals(ArmingAction.NONE, decideArming(input(connected = false, trip = false), true))
    }

    @Test
    fun `desarma solo si quedo pendiente y el modulo no esta ya desarmado`() {
        assertEquals(
            ArmingAction.DISARM,
            decideArming(input(trip = false, device = DeviceState.ARMED), pendingDisarm = true)
        )
        assertEquals(
            ArmingAction.NONE,
            decideArming(input(trip = false, device = DeviceState.DISARMED), pendingDisarm = true)
        )
        assertEquals(
            ArmingAction.NONE,
            decideArming(input(trip = false, device = DeviceState.ARMED), pendingDisarm = false)
        )
    }

    // --- Comportamiento ---

    @Test
    fun `al iniciar un viaje con el modulo conectado lo arma`() = runTest {
        connected.value = true
        device.value = DeviceState.DISARMED
        armer().start(backgroundScope)
        runCurrent()
        assertEquals(0, armCalls) // sin viaje no se arma

        guardian.value = GuardianState.Monitoring
        runCurrent()

        assertEquals(1, armCalls)
    }

    @Test
    fun `al conectar durante un viaje lo arma`() = runTest {
        guardian.value = GuardianState.Monitoring
        armer().start(backgroundScope)
        runCurrent()
        assertEquals(0, armCalls)

        connected.value = true
        device.value = DeviceState.DISARMED
        runCurrent()

        assertEquals(1, armCalls)
    }

    @Test
    fun `no vuelve a armar un modulo que ya reporta ARMED`() = runTest {
        connected.value = true
        device.value = DeviceState.ARMED
        guardian.value = GuardianState.Monitoring
        armer().start(backgroundScope)
        runCurrent()

        assertEquals(0, armCalls)
    }

    @Test
    fun `si el modulo vuelve a DISARMED durante el viaje lo rearma`() = runTest {
        connected.value = true
        device.value = DeviceState.ARMED
        guardian.value = GuardianState.Monitoring
        armer().start(backgroundScope)
        runCurrent()

        device.value = DeviceState.FAULT
        runCurrent()
        device.value = DeviceState.DISARMED // el firmware se recupero del fallo
        runCurrent()

        assertEquals(1, armCalls)
    }

    @Test
    fun `no toca al modulo mientras captura un evento`() = runTest {
        connected.value = true
        device.value = DeviceState.ARMED
        guardian.value = GuardianState.Monitoring
        armer().start(backgroundScope)
        runCurrent()

        device.value = DeviceState.POST_TRIGGER
        runCurrent()
        device.value = DeviceState.COOLDOWN
        runCurrent()
        device.value = DeviceState.ARMED
        runCurrent()

        assertEquals(0, armCalls)
        assertEquals(0, disarmCalls)
    }

    @Test
    fun `al terminar el viaje lo desarma una sola vez`() = runTest {
        connected.value = true
        device.value = DeviceState.ARMED
        guardian.value = GuardianState.Monitoring
        armer().start(backgroundScope)
        runCurrent()

        guardian.value = GuardianState.Idle
        runCurrent()
        device.value = DeviceState.DISARMED
        runCurrent()

        assertEquals(1, disarmCalls)
    }

    @Test
    fun `al abrir la app sin viaje no desarma un modulo que ya estaba armado`() = runTest {
        connected.value = true
        device.value = DeviceState.ARMED
        armer().start(backgroundScope)
        runCurrent()

        assertEquals(0, disarmCalls)
        assertEquals(0, armCalls)
    }

    @Test
    fun `si el viaje termina sin conexion desarma al reconectar`() = runTest {
        connected.value = true
        device.value = DeviceState.ARMED
        guardian.value = GuardianState.Monitoring
        armer().start(backgroundScope)
        runCurrent()
        connected.value = false
        device.value = null
        runCurrent()
        guardian.value = GuardianState.Idle
        runCurrent()
        assertEquals(0, disarmCalls)

        connected.value = true
        device.value = DeviceState.ARMED
        runCurrent()

        assertEquals(1, disarmCalls)
    }

    // --- Reintentos ---

    @Test
    fun `reintenta con pausa cuando el modulo esta ocupado`() = runTest {
        connected.value = true
        device.value = DeviceState.DISARMED
        guardian.value = GuardianState.Monitoring
        val attemptTimes = mutableListOf<Long>()
        armResults = { call ->
            attemptTimes += currentTime
            call >= 3
        }
        armer().start(backgroundScope)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(3, armCalls)
        assertEquals(listOf(0L, 1_000L, 2_000L), attemptTimes) // pausa de 1 s entre intentos
    }

    @Test
    fun `se rinde tras el maximo de intentos y no falla`() = runTest {
        connected.value = true
        device.value = DeviceState.DISARMED
        guardian.value = GuardianState.Monitoring
        armResults = { false }
        armer().start(backgroundScope)
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(3, armCalls)
    }

    @Test
    fun `una excepcion cuenta como intento fallido`() = runTest {
        connected.value = true
        device.value = DeviceState.DISARMED
        guardian.value = GuardianState.Monitoring
        armResults = { call -> if (call == 1) error("GATT cerrado") else true }
        armer().start(backgroundScope)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(2, armCalls)
    }

    @Test
    fun `un desarmado fallido queda pendiente y se reintenta en el siguiente cambio`() = runTest {
        connected.value = true
        device.value = DeviceState.ARMED
        guardian.value = GuardianState.Monitoring
        armer().start(backgroundScope)
        runCurrent()
        disarmResults = { false }
        guardian.value = GuardianState.Idle
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(3, disarmCalls)

        disarmResults = { true }
        device.value = DeviceState.COOLDOWN // cualquier cambio vuelve a evaluar
        runCurrent()

        assertEquals(4, disarmCalls)
    }

    @Test
    fun `un cambio de contexto cancela el reintento en curso`() = runTest {
        connected.value = true
        device.value = DeviceState.DISARMED
        guardian.value = GuardianState.Monitoring
        armResults = { false }
        armer().start(backgroundScope)
        runCurrent()
        assertEquals(1, armCalls)

        connected.value = false // se cae la conexion durante la pausa
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(1, armCalls)
    }
}
