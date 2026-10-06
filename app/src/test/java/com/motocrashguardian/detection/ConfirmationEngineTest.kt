package com.motocrashguardian.detection

import com.motocrashguardian.core.model.DetectionConfig
import com.motocrashguardian.core.model.DeviceEvent
import com.motocrashguardian.core.model.DeviceEventType
import com.motocrashguardian.core.model.DeviceState
import com.motocrashguardian.core.model.Telemetry
import com.motocrashguardian.core.model.TriggerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class ConfirmationEngineTest {
    private val engine = ConfirmationEngine()
    private val eventTime = Instant.parse("2026-10-06T12:00:00Z")
    private val config = DetectionConfig()

    @Test
    fun `caida clara se confirma al completar el hold`() {
        val result = evaluate(
            event = event(DeviceEventType.IMPACT, peakAccelMg = 5_200),
            telemetry = samples(count = 11, rollCdeg = 8_500),
            now = eventTime.plusSeconds(2)
        )

        assertEquals(
            ConfirmationDecision.Confirmed(TriggerType.IMPACT, eventTime.plusSeconds(2)),
            result
        )
    }

    @Test
    fun `bache sin inclinacion se rechaza al cerrar la ventana`() {
        val result = evaluate(
            telemetry = samples(count = 25, rollCdeg = 900, accelMagMg = 1_500),
            now = eventTime.plusSeconds(5)
        )

        assertEquals(
            ConfirmationDecision.Rejected(RejectionReason.NOT_TILTED),
            result
        )
    }

    @Test
    fun `inclinacion con aceleracion variable se rechaza como no quieta`() {
        val result = evaluate(
            telemetry = samples(count = 25, rollCdeg = 7_000, accelMagMg = 1_500),
            now = eventTime.plusSeconds(5)
        )

        assertEquals(
            ConfirmationDecision.Rejected(RejectionReason.NOT_STILL),
            result
        )
    }

    @Test
    fun `evento TILT solo necesita un segundo aunque config tenga otro hold`() {
        val result = evaluate(
            event = event(DeviceEventType.TILT),
            telemetry = samples(count = 6, rollCdeg = 7_000),
            now = eventTime.plusSeconds(1)
        )

        assertEquals(
            ConfirmationDecision.Confirmed(TriggerType.TILT, eventTime.plusSeconds(1)),
            result
        )
    }

    @Test
    fun `impacto severo se confirma inmediatamente`() {
        val result = evaluate(
            event = event(DeviceEventType.IMPACT, peakAccelMg = config.severeImpactThresholdMg),
            eventAgeAtReceipt = Duration.ofHours(1)
        )

        assertEquals(
            ConfirmationDecision.Confirmed(TriggerType.SEVERE_IMPACT, eventTime),
            result
        )
    }

    @Test
    fun `velocidad fiable alta bloquea un tramo que cumple inclinacion y quietud`() {
        val samples = samples(count = 11, rollCdeg = 7_000)
        val result = evaluate(
            telemetry = samples,
            speed = SpeedSample(40.0, 5f, samples.last().receivedAt),
            now = eventTime.plusSeconds(2)
        )

        assertEquals(
            ConfirmationDecision.Rejected(RejectionReason.PHONE_MOVING),
            result
        )
    }

    @Test
    fun `velocidad desconocida no bloquea la confirmacion`() {
        val result = evaluate(
            telemetry = samples(count = 11, rollCdeg = 7_000),
            speed = null,
            now = eventTime.plusSeconds(2)
        )

        assertTrue(result is ConfirmationDecision.Confirmed)
    }

    @Test
    fun `muestra de velocidad vieja o imprecisa no bloquea la confirmacion`() {
        val telemetry = samples(count = 11, rollCdeg = 7_000)
        val result = evaluate(
            telemetry = telemetry,
            speed = SpeedSample(40.0, 31f, telemetry.last().receivedAt),
            now = eventTime.plusSeconds(2)
        )

        assertTrue(result is ConfirmationDecision.Confirmed)
    }

    @Test
    fun `cobertura menor a la mitad confirma con lowTelemetry`() {
        val result = evaluate(
            telemetry = samples(count = 12, rollCdeg = 0),
            now = eventTime.plusSeconds(5)
        )

        assertEquals(
            ConfirmationDecision.Confirmed(
                trigger = TriggerType.IMPACT,
                confirmedAt = eventTime.plusSeconds(5),
                lowTelemetry = true
            ),
            result
        )
    }

    @Test
    fun `modo demo confirma con un segundo de inclinacion e ignora velocidad`() {
        val telemetry = samples(count = 6, rollCdeg = 7_000)
        val result = evaluate(
            telemetry = telemetry,
            speed = SpeedSample(40.0, 5f, telemetry.last().receivedAt),
            demoMode = true,
            now = eventTime.plusSeconds(1)
        )

        assertEquals(
            ConfirmationDecision.Confirmed(TriggerType.IMPACT, eventTime.plusSeconds(1)),
            result
        )
        assertEquals(5_000, config.confirmWindowMs)
        assertEquals(2_000, config.tiltHoldMs)
    }

    @Test
    fun `evento reenviado de mas de cinco minutos se rechaza`() {
        val result = evaluate(eventAgeAtReceipt = Duration.ofMinutes(5).plusMillis(1))

        assertEquals(ConfirmationDecision.Rejected(RejectionReason.STALE), result)
    }

    @Test
    fun `evento de prueba inmediato tiene prioridad sobre antiguedad`() {
        val result = evaluate(
            event = event(DeviceEventType.TEST),
            eventAgeAtReceipt = Duration.ofHours(1)
        )

        assertEquals(ConfirmationDecision.Confirmed(TriggerType.TEST, eventTime), result)
    }

    @Test
    fun `evento no severo que tiene exactamente cinco minutos no es stale`() {
        val result = evaluate(
            telemetry = emptyList(),
            eventAgeAtReceipt = Duration.ofMinutes(5)
        )

        assertEquals(ConfirmationDecision.Pending, result)
    }

    @Test
    fun `hueco largo reinicia la racha de inclinacion y quietud`() {
        val firstRun = samples(count = 6, rollCdeg = 7_000)
        val secondRun = samples(count = 10, rollCdeg = 7_000, sequenceStart = 6).mapIndexed { index, sample ->
            sample.copy(receivedAt = eventTime.plusSeconds(3).plusMillis(index * 200L))
        }
        val telemetry = firstRun + secondRun
        val result = evaluate(telemetry = telemetry, now = eventTime.plusSeconds(5))

        assertEquals(
            ConfirmationDecision.Rejected(RejectionReason.NOT_STILL),
            result
        )
    }

    @Test
    fun `evento FAULT no se trata como candidato de accidente`() {
        val result = evaluate(event = event(DeviceEventType.FAULT))

        assertEquals(
            ConfirmationDecision.Rejected(RejectionReason.UNSUPPORTED_EVENT),
            result
        )
    }

    @Test
    fun `pendiente mientras ventana esta abierta y no hay hold completo`() {
        val result = evaluate(
            telemetry = samples(count = 5, rollCdeg = 7_000),
            now = eventTime.plusMillis(800)
        )

        assertFalse(result is ConfirmationDecision.Confirmed)
        assertEquals(ConfirmationDecision.Pending, result)
    }

    private fun evaluate(
        event: DeviceEvent = event(DeviceEventType.IMPACT, peakAccelMg = 5_200),
        telemetry: List<Telemetry> = emptyList(),
        speed: SpeedSample? = null,
        demoMode: Boolean = false,
        eventAgeAtReceipt: Duration? = null,
        now: Instant = eventTime
    ): ConfirmationDecision = engine.evaluate(
        event = event,
        telemetry = telemetry,
        speed = speed,
        config = config,
        demoMode = demoMode,
        eventAgeAtReceipt = eventAgeAtReceipt,
        now = now
    )

    private fun event(
        type: DeviceEventType,
        peakAccelMg: Int = 5_200
    ) = DeviceEvent(
        protocolVersion = 1,
        type = type,
        eventId = 1,
        bootCount = 1,
        uptimeMs = 10_000,
        peakAccelMg = peakAccelMg,
        peakGyroDps = 0,
        pitchCdeg = 0,
        rollCdeg = 0,
        flags = 0,
        faultCode = null,
        receivedAt = eventTime
    )

    private fun samples(
        count: Int,
        rollCdeg: Int,
        accelMagMg: Int = 1_000,
        sequenceStart: Int = 0
    ) = (0 until count).map { index ->
        Telemetry(
            protocolVersion = 1,
            seq = sequenceStart + index,
            state = DeviceState.ARMED,
            flags = 0,
            pitchCdeg = 0,
            rollCdeg = rollCdeg,
            accelMagMg = accelMagMg,
            peakAccelMg = 0,
            peakGyroDps = 0,
            batteryPercent = 100,
            receivedAt = eventTime.plusMillis(index * 200L)
        )
    }
}
