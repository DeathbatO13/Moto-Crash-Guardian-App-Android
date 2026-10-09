package com.motocrashguardian.detection

import androidx.room.Room
import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.core.model.CallStatus
import com.motocrashguardian.core.model.SmsStatus
import com.motocrashguardian.core.model.DeviceEvent
import com.motocrashguardian.core.model.DeviceEventType
import com.motocrashguardian.core.model.DeviceState
import com.motocrashguardian.core.model.IncidentStatus
import com.motocrashguardian.core.model.IncidentType
import com.motocrashguardian.core.model.Telemetry
import com.motocrashguardian.core.model.TriggerType
import com.motocrashguardian.data.incidents.GuardianDatabase
import com.motocrashguardian.data.incidents.IncidentRepository
import com.motocrashguardian.data.settings.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class GuardianStateMachineTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private var database: GuardianDatabase? = null
    private var machine: GuardianStateMachine? = null

    @After
    fun closeResources() {
        machine?.close()
        machine = null
        database?.close()
        database = null
    }

    @Test
    fun `viaje confirmado, TEST inicia countdown y cancelar persiste antes de volver a monitoring`() =
        runTest {
            val repository = newIncidentRepository()
            val settings = newSettingsRepository()
            val testMachine = newMachine(settings, repository)

            testMachine.startTrip()
            assertEquals(GuardianState.Monitoring, testMachine.state.value)
            assertTrue(settings.value.value.tripActive)

            testMachine.onCandidate(event(DeviceEventType.TEST))
            val countdown = testMachine.state.value as GuardianState.Countdown
            assertEquals(IncidentType.DRILL, countdown.incident.type)
            assertEquals(TriggerType.TEST, countdown.incident.triggerType)

            advanceTimeBy(19_900)
            runCurrent()
            assertEquals(1, (testMachine.state.value as GuardianState.Countdown)
                .remainingSeconds(clock().instant()))

            testMachine.cancelCountdown()

            assertEquals(GuardianState.Monitoring, testMachine.state.value)
            assertEquals(
                IncidentStatus.CANCELLED_BY_USER,
                repository.getIncident(countdown.incident.id)?.status
            )
            assertTrue(testMachine.effects.first() is GuardianEffect.CountdownStarted)
            assertTrue(testMachine.effects.first() is GuardianEffect.CountdownCancelled)
        }

    @Test
    fun `countdown despacha exactamente al deadline y no permite cancelar despues`() = runTest {
        val repository = newIncidentRepository()
        val testMachine = newMachine(newSettingsRepository(), repository)
        testMachine.startTrip()
        testMachine.onCandidate(event(DeviceEventType.TEST))

        advanceTimeBy(19_999)
        runCurrent()
        assertTrue(testMachine.state.value is GuardianState.Countdown)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(testMachine.state.value is GuardianState.Dispatching)

        testMachine.cancelCountdown()
        assertTrue(testMachine.state.value is GuardianState.Dispatching)
        val requested = testMachine.effects.first()
        assertTrue(requested is GuardianEffect.CountdownStarted)
        assertTrue(testMachine.effects.first() is GuardianEffect.DispatchRequested)
    }

    @Test
    fun `despacho finaliza y el resultado devuelve a monitoring`() = runTest {
        val repository = newIncidentRepository()
        val testMachine = newMachine(newSettingsRepository(), repository)
        testMachine.startTrip()
        testMachine.onCandidate(event(DeviceEventType.TEST))
        val incident = (testMachine.state.value as GuardianState.Countdown).incident
        testMachine.sendHelpNow()

        testMachine.onDispatchCompleted(IncidentStatus.DISPATCHED)

        assertEquals(GuardianState.Dispatched(incident.copy(
            status = IncidentStatus.DISPATCHED,
            resolvedAt = clock().instant()
        )), testMachine.state.value)
        assertEquals(IncidentStatus.DISPATCHED, repository.getIncident(incident.id)?.status)
        testMachine.dismissResult()
        assertEquals(GuardianState.Monitoring, testMachine.state.value)
    }

    @Test
    fun `finalizar el despacho conserva los estados de SMS y llamada ya persistidos`() = runTest {
        val repository = newIncidentRepository()
        val testMachine = newMachine(newSettingsRepository(), repository)
        testMachine.startTrip()
        testMachine.onCandidate(event(DeviceEventType.TEST))
        val incident = (testMachine.state.value as GuardianState.Countdown).incident
        testMachine.sendHelpNow()
        repository.updateIncident(
            incident.copy(
                primarySmsStatus = SmsStatus.SENT,
                secondarySmsStatus = SmsStatus.FAILED,
                callStatus = CallStatus.PLACED
            )
        )

        testMachine.onDispatchCompleted(IncidentStatus.DISPATCH_PARTIAL)

        val stored = repository.getIncident(incident.id)!!
        assertEquals(IncidentStatus.DISPATCH_PARTIAL, stored.status)
        assertEquals(SmsStatus.SENT, stored.primarySmsStatus)
        assertEquals(SmsStatus.FAILED, stored.secondarySmsStatus)
        assertEquals(CallStatus.PLACED, stored.callStatus)
        assertEquals(stored, (testMachine.state.value as GuardianState.Dispatched).incident)
    }

    @Test
    fun `restaurar cuenta calcula el deadline original y despacha si ya vencio`() = runTest {
        val repository = newIncidentRepository()
        val settings = newSettingsRepository()
        val firstMachine = newMachine(settings, repository)
        firstMachine.startTrip()
        firstMachine.onCandidate(event(DeviceEventType.TEST))
        val incident = (firstMachine.state.value as GuardianState.Countdown).incident
        settings.value.value = settings.value.value.copy(countdownSeconds = 35)
        advanceTimeBy(12_000)
        runCurrent()
        firstMachine.close()

        val restoredMachine = newMachine(settings, repository)
        restoredMachine.restore()

        val countdown = restoredMachine.state.value as GuardianState.Countdown
        assertEquals(incident.id, countdown.incident.id)
        assertEquals(incident.countdownDeadline, countdown.deadline)
        assertEquals(8, countdown.remainingSeconds(clock().instant()))
        advanceTimeBy(8_000)
        runCurrent()
        assertTrue(restoredMachine.state.value is GuardianState.Dispatching)
    }

    @Test
    fun `evento duplicado no crea un segundo incidente y picos mayores se anexan`() = runTest {
        val repository = newIncidentRepository()
        val testMachine = newMachine(newSettingsRepository(), repository)
        testMachine.startTrip()
        val original = event(DeviceEventType.TEST, peakAccelMg = 8_500)
        testMachine.onCandidate(original)
        testMachine.onCandidate(original.copy(peakAccelMg = 9_000, peakGyroDps = 500))

        val current = testMachine.state.value as GuardianState.Countdown
        assertEquals(9_000, current.incident.peakAccelMg)
        assertEquals(1, database?.incidentDao()?.countIncidents())
    }

    @Test
    fun `inclinacion confirma desde telemetry y bache registra NOT_CONFIRMED`() = runTest {
        val repository = newIncidentRepository()
        val testMachine = newMachine(newSettingsRepository(), repository)
        testMachine.startTrip()
        val impact = event(DeviceEventType.IMPACT)
        testMachine.onCandidate(impact)
        assertTrue(testMachine.state.value is GuardianState.Confirming)
        repeat(11) { index ->
            advanceTimeBy(200)
            runCurrent()
            testMachine.onTelemetry(telemetry(index, 8_500))
        }
        assertTrue(testMachine.state.value is GuardianState.Countdown)
        testMachine.cancelCountdown()

        val bump = event(
            DeviceEventType.IMPACT,
            eventId = 2,
            receivedAt = clock().instant()
        )
        testMachine.onCandidate(bump)
        repeat(25) { index ->
            advanceTimeBy(200)
            runCurrent()
            testMachine.onTelemetry(telemetry(index, 0, seqOffset = 20, baseTime = bump.receivedAt))
        }
        advanceTimeBy(200)
        runCurrent()
        assertEquals(GuardianState.Monitoring, testMachine.state.value)
        assertEquals(IncidentStatus.NOT_CONFIRMED, repository.getIncidentByDeviceEventKey("1:2")?.status)
    }

    @Test
    fun `conexion perdida permite solicitar ayuda pero transiciones invalidas no cambian estado`() =
        runTest {
            val repository = newIncidentRepository()
            val testMachine = newMachine(newSettingsRepository(), repository)
            testMachine.cancelCountdown()
            assertEquals(GuardianState.Idle, testMachine.state.value)
            testMachine.startTrip()
            testMachine.onConnectionLost()
            assertEquals(GuardianState.ConnectionLost, testMachine.state.value)
            testMachine.requestHelp()

            assertTrue(testMachine.state.value is GuardianState.Countdown)
            val incident = (testMachine.state.value as GuardianState.Countdown).incident
            assertEquals(TriggerType.CONNECTION_LOST, incident.triggerType)
            assertEquals(IncidentStatus.ACTIVE, repository.getIncident(incident.id)?.status)
        }

    private fun TestScope.newIncidentRepository(): IncidentRepository {
        database = Room.databaseBuilder(
            RuntimeEnvironment.getApplication(),
            GuardianDatabase::class.java,
            java.io.File(tempFolder.root, "guardian-${UUID.randomUUID()}.db").absolutePath
        )
            .allowMainThreadQueries()
            .setQueryExecutor(Executor { it.run() })
            .setTransactionExecutor(Executor { it.run() })
            .build()
        return IncidentRepository(requireNotNull(database))
    }

    private fun newSettingsRepository(): TestSettingsRepository {
        val settingsState = MutableStateFlow(AppSettings())
        val repository = mockk<SettingsRepository>()
        every { repository.settings } returns settingsState
        coEvery { repository.updateSettings(any()) } coAnswers {
            settingsState.value = arg<(AppSettings) -> AppSettings>(0).invoke(settingsState.value)
        }
        return TestSettingsRepository(repository, settingsState)
    }

    private fun TestScope.newMachine(
        settings: TestSettingsRepository,
        incidentRepository: IncidentRepository
    ): GuardianStateMachine = GuardianStateMachine(
        incidentRepository = incidentRepository,
        settingsRepository = settings.repository,
        confirmationEngine = ConfirmationEngine(clock()),
        scope = backgroundScope,
        clock = clock()
    ).also { machine = it }

    private fun TestScope.clock(): TestClock = TestClock(testScheduler)

    private fun event(
        type: DeviceEventType,
        peakAccelMg: Int = 5_200,
        eventId: Int = 1,
        receivedAt: Instant = TestClock.Epoch
    ) = DeviceEvent(
        protocolVersion = 1,
        type = type,
        eventId = eventId,
        bootCount = 1,
        uptimeMs = 10_000,
        peakAccelMg = peakAccelMg,
        peakGyroDps = 400,
        pitchCdeg = 0,
        rollCdeg = 0,
        flags = 0,
        faultCode = null,
        receivedAt = receivedAt
    )

    private fun telemetry(
        index: Int,
        rollCdeg: Int,
        seqOffset: Int = 0,
        baseTime: Instant = TestClock.Epoch
    ) = Telemetry(
        protocolVersion = 1,
        seq = seqOffset + index,
        state = DeviceState.ARMED,
        flags = 0,
        pitchCdeg = 0,
        rollCdeg = rollCdeg,
        accelMagMg = 1_000,
        peakAccelMg = 0,
        peakGyroDps = 0,
        batteryPercent = 100,
        receivedAt = baseTime.plusMillis(index * 200L)
    )

    private data class TestSettingsRepository(
        val repository: SettingsRepository,
        val value: MutableStateFlow<AppSettings>
    )

    private class TestClock(private val scheduler: TestCoroutineScheduler) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = Epoch.plusMillis(scheduler.currentTime)

        companion object {
            val Epoch: Instant = Instant.parse("2026-10-06T12:00:00Z")
        }
    }

}
