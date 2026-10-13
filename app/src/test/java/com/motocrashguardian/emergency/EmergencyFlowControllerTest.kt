package com.motocrashguardian.emergency

import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.IncidentStatus
import com.motocrashguardian.core.model.IncidentType
import com.motocrashguardian.core.model.LocationSource
import com.motocrashguardian.core.model.TriggerType
import com.motocrashguardian.detection.GuardianEffect
import com.motocrashguardian.detection.GuardianStateMachine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class EmergencyFlowControllerTest {
    private val start = Instant.parse("2026-10-19T15:30:00Z")
    private val effects = Channel<GuardianEffect>(Channel.UNLIMITED)
    private val machine = mockk<GuardianStateMachine>(relaxed = true)
    private val orchestrator = mockk<DispatchOrchestrator>()

    private val log = mutableListOf<String>()
    private val shown = mutableListOf<Int>()
    private var alarmFails = false
    private var notificationFails = false
    private var phoneFix: LocationFix? = null

    private val incident = Incident(
        type = IncidentType.REAL,
        triggerType = TriggerType.IMPACT,
        status = IncidentStatus.ACTIVE,
        detectedAt = start
    )

    private fun TestScope.clock(): Clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant(): Instant = start.plusMillis(currentTime)
    }

    private fun TestScope.controller() = EmergencyFlowController(
        machine = machine,
        orchestrator = orchestrator,
        alarm = AlarmPlayer(object : AlarmOutput {
            override fun start() {
                if (alarmFails) error("sin altavoz")
                log += "alarm:start"
            }

            override fun stop() {
                log += "alarm:stop"
            }
        }),
        showCountdown = {
            if (notificationFails) error("sin notificaciones")
            shown += it
        },
        cancelCountdownNotification = { log += "notification:cancel" },
        locationAcquirer = LocationAcquirer(
            scope = backgroundScope,
            phoneSource = PhoneLocationSource { phoneFix },
            lastKnownSource = LastKnownLocationSource { null },
            deviceGps = DeviceGpsSource { null },
            clock = clock()
        ),
        clock = clock()
    ).also { every { machine.effects } returns effects.receiveAsFlow() }

    @Test
    fun `al iniciar la cuenta suena la alarma y la notificacion baja cada segundo`() = runTest {
        controller().start(backgroundScope)

        effects.send(GuardianEffect.CountdownStarted(incident, start.plusSeconds(3)))
        runCurrent()
        assertEquals(listOf("alarm:start"), log)
        assertEquals(listOf(3), shown)

        advanceTimeBy(1_000)
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(3, 2, 1), shown)

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf(3, 2, 1), shown)
    }

    @Test
    fun `cancelar detiene alarma y notificacion y la cuenta no sigue actualizando`() = runTest {
        controller().start(backgroundScope)
        effects.send(GuardianEffect.CountdownStarted(incident, start.plusSeconds(10)))
        runCurrent()

        effects.send(GuardianEffect.CountdownCancelled(incident))
        runCurrent()
        val shownAtCancel = shown.toList()
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(listOf("alarm:start", "alarm:stop", "notification:cancel"), log)
        assertEquals(shownAtCancel, shown)
    }

    @Test
    fun `solicitud de despacho apaga la alarma, despacha y reporta el resultado`() = runTest {
        val outcome = DispatchOutcome(
            incident = incident,
            status = IncidentStatus.DISPATCH_PARTIAL,
            failures = emptyList()
        )
        coEvery { orchestrator.dispatch(incident) } returns outcome
        controller().start(backgroundScope)

        effects.send(GuardianEffect.CountdownStarted(incident, start.plusSeconds(10)))
        effects.send(GuardianEffect.DispatchRequested(incident))
        runCurrent()

        assertTrue(log.indexOf("alarm:stop") > log.indexOf("alarm:start"))
        coVerify(exactly = 1) { orchestrator.dispatch(incident) }
        coVerify(exactly = 1) { machine.onDispatchCompleted(IncidentStatus.DISPATCH_PARTIAL) }
    }

    @Test
    fun `el despacho recibe el incidente con la ubicacion obtenida durante la cuenta`() = runTest {
        phoneFix = LocationFix(
            latitude = 4.7109,
            longitude = -74.0721,
            accuracyMeters = 9f,
            source = LocationSource.PHONE_GPS,
            fixAt = start
        )
        coEvery { orchestrator.dispatch(any()) } returns DispatchOutcome(
            incident, IncidentStatus.DISPATCHED, emptyList()
        )
        controller().start(backgroundScope)

        effects.send(GuardianEffect.CountdownStarted(incident, start.plusSeconds(10)))
        runCurrent()
        effects.send(GuardianEffect.DispatchRequested(incident))
        runCurrent()

        coVerify(exactly = 1) {
            orchestrator.dispatch(
                match {
                    it.latitude == 4.7109 && it.longitude == -74.0721 &&
                        it.locationSource == LocationSource.PHONE_GPS
                }
            )
        }
    }

    @Test
    fun `sin ubicacion el despacho sale igual con el incidente original`() = runTest {
        coEvery { orchestrator.dispatch(any()) } returns DispatchOutcome(
            incident, IncidentStatus.DISPATCHED, emptyList()
        )
        controller().start(backgroundScope)

        effects.send(GuardianEffect.CountdownStarted(incident, start.plusSeconds(10)))
        effects.send(GuardianEffect.DispatchRequested(incident))
        runCurrent()

        coVerify(exactly = 1) { orchestrator.dispatch(incident) }
    }

    @Test
    fun `si el despacho lanza una excepcion se reporta DISPATCH_FAILED`() = runTest {
        coEvery { orchestrator.dispatch(incident) } throws IllegalStateException("boom")
        controller().start(backgroundScope)

        effects.send(GuardianEffect.DispatchRequested(incident))
        runCurrent()

        coVerify(exactly = 1) { machine.onDispatchCompleted(IncidentStatus.DISPATCH_FAILED) }
    }

    @Test
    fun `una alarma que falla no impide que la cuenta ni el despacho continuen`() = runTest {
        alarmFails = true
        coEvery { orchestrator.dispatch(incident) } returns DispatchOutcome(
            incident, IncidentStatus.DISPATCHED, emptyList()
        )
        controller().start(backgroundScope)

        effects.send(GuardianEffect.CountdownStarted(incident, start.plusSeconds(5)))
        runCurrent()
        assertEquals(listOf(5), shown)

        effects.send(GuardianEffect.DispatchRequested(incident))
        runCurrent()
        coVerify(exactly = 1) { machine.onDispatchCompleted(IncidentStatus.DISPATCHED) }
    }

    @Test
    fun `una notificacion que falla no impide el despacho`() = runTest {
        notificationFails = true
        coEvery { orchestrator.dispatch(incident) } returns DispatchOutcome(
            incident, IncidentStatus.DISPATCHED, emptyList()
        )
        controller().start(backgroundScope)

        effects.send(GuardianEffect.CountdownStarted(incident, start.plusSeconds(5)))
        effects.send(GuardianEffect.DispatchRequested(incident))
        runCurrent()

        coVerify(exactly = 1) { machine.onDispatchCompleted(IncidentStatus.DISPATCHED) }
    }

    @Test
    fun `una nueva cuenta reemplaza a la anterior sin duplicar actualizaciones`() = runTest {
        controller().start(backgroundScope)
        effects.send(GuardianEffect.CountdownStarted(incident, start.plusSeconds(10)))
        runCurrent()
        effects.send(GuardianEffect.CountdownStarted(incident, start.plusSeconds(4)))
        runCurrent()
        shown.clear()

        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(listOf(3), shown)
    }
}
