package com.motocrashguardian.emergency

import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.core.model.ContactRole
import com.motocrashguardian.core.model.EmergencyContact
import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.IncidentStatus
import com.motocrashguardian.core.model.IncidentType
import com.motocrashguardian.core.model.TriggerType
import com.motocrashguardian.detection.GuardianState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DrillStarterTest {

    private val now = Instant.parse("2026-10-19T15:30:00Z")
    private val state = MutableStateFlow<GuardianState>(GuardianState.Monitoring)
    private var deviceReady = false
    private var deviceAnswersOk = true
    private var deviceThrows = false
    private var deviceRaisesCountdown = true
    private var deviceRequests = 0
    private var localDrills = 0

    private val withContacts = AppSettings(
        primaryContact = EmergencyContact(ContactRole.PRIMARY, "Ana", "+573001112233")
    )

    private val drillIncident = Incident(
        type = IncidentType.DRILL,
        triggerType = TriggerType.TEST,
        status = IncidentStatus.ACTIVE,
        detectedAt = now
    )

    private fun starter(timeoutMillis: Long = 5_000) = DrillStarter(
        guardianState = state,
        deviceReady = { deviceReady },
        triggerDeviceTest = {
            deviceRequests++
            if (deviceThrows) error("BLE caido")
            if (deviceAnswersOk && deviceRaisesCountdown) {
                state.value = GuardianState.Countdown(drillIncident, now.plusSeconds(20))
            }
            deviceAnswersOk
        },
        startLocalDrill = {
            localDrills++
            state.value = GuardianState.Countdown(drillIncident, now.plusSeconds(20))
        },
        deviceEventTimeoutMillis = timeoutMillis
    )

    // --- Condiciones previas ---

    @Test
    fun `sin contactos no hay nada que probar`() = runTest {
        val outcome = starter().start(AppSettings())

        assertEquals(DrillOutcome.NoContacts, outcome)
        assertEquals(0, localDrills + deviceRequests)
    }

    @Test
    fun `con proteccion apagada pide activarla`() = runTest {
        state.value = GuardianState.Idle

        assertEquals(DrillOutcome.ProtectionOff, starter().start(withContacts))
        assertEquals(0, localDrills)
    }

    @Test
    fun `no inicia otro simulacro si ya hay una alerta`() = runTest {
        listOf(
            GuardianState.Countdown(drillIncident, now),
            GuardianState.Dispatching(drillIncident),
            GuardianState.Dispatched(drillIncident)
        ).forEach {
            state.value = it
            assertEquals(DrillOutcome.AlertInProgress, starter().start(withContacts))
        }
        assertEquals(0, localDrills + deviceRequests)
    }

    // --- Sin modulo: simulacro local ---

    @Test
    fun `sin modulo hace el simulacro local`() = runTest {
        deviceReady = false

        val outcome = starter().start(withContacts)

        assertEquals(DrillOutcome.Started(viaDevice = false, fellBack = false), outcome)
        assertEquals(1, localDrills)
        assertEquals(0, deviceRequests)
    }

    @Test
    fun `con la conexion perdida tambien funciona en local`() = runTest {
        state.value = GuardianState.ConnectionLost

        val outcome = starter().start(withContacts)

        assertEquals(DrillOutcome.Started(viaDevice = false, fellBack = false), outcome)
    }

    // --- Con modulo: TRIGGER_TEST_EVENT ---

    @Test
    fun `con modulo pide el evento de prueba y no hace simulacro local`() = runTest {
        deviceReady = true

        val outcome = starter().start(withContacts)

        assertEquals(DrillOutcome.Started(viaDevice = true, fellBack = false), outcome)
        assertEquals(1, deviceRequests)
        assertEquals(0, localDrills)
    }

    @Test
    fun `si el modulo responde error cae al simulacro local`() = runTest {
        deviceReady = true
        deviceAnswersOk = false

        val outcome = starter().start(withContacts)

        assertEquals(DrillOutcome.Started(viaDevice = false, fellBack = true), outcome)
        assertEquals(1, localDrills)
    }

    @Test
    fun `si el BLE lanza una excepcion cae al simulacro local`() = runTest {
        deviceReady = true
        deviceThrows = true

        val outcome = starter().start(withContacts)

        assertEquals(DrillOutcome.Started(viaDevice = false, fellBack = true), outcome)
        assertEquals(1, localDrills)
    }

    @Test
    fun `si el modulo acepta pero el evento no llega espera y luego hace local`() = runTest {
        deviceReady = true
        deviceRaisesCountdown = false

        val outcome = starter(timeoutMillis = 5_000).start(withContacts)

        assertEquals(DrillOutcome.Started(viaDevice = false, fellBack = true), outcome)
        assertEquals(5_000, currentTime)
        assertEquals(1, localDrills)
    }

    @Test
    fun `espera el evento del modulo antes de rendirse`() = runTest {
        deviceReady = true
        deviceRaisesCountdown = false
        var outcome: DrillOutcome? = null
        val job = backgroundScope.launch { outcome = starter(5_000).start(withContacts) }
        runCurrent()
        advanceTimeBy(2_000)
        assertEquals(null, outcome)

        // El evento llega por BLE a los 2 s.
        state.value = GuardianState.Countdown(drillIncident, now.plusSeconds(20))
        runCurrent()
        job.join()

        assertEquals(DrillOutcome.Started(viaDevice = true, fellBack = false), outcome)
        assertTrue(localDrills == 0)
    }
}
