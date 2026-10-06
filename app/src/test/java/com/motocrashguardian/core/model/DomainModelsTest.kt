package com.motocrashguardian.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class DomainModelsTest {

    @Test
    fun `configuracion de deteccion usa los valores por defecto documentados`() {
        val config = DetectionConfig()

        assertEquals(3_500, config.impactThresholdMg)
        assertEquals(300, config.gyroThresholdDps)
        assertEquals(65, config.tiltThresholdDeg)
        assertEquals(2_000, config.tiltHoldMs)
        assertTrue(config.tiltDetectionEnabled)
        assertEquals(8_000, config.severeImpactThresholdMg)
        assertEquals(5_000, config.confirmWindowMs)
        assertEquals(200, config.stillnessToleranceMg)
        assertEquals(0, config.pitchOffsetCdeg)
        assertEquals(0, config.rollOffsetCdeg)
    }

    @Test
    fun `configuracion rechaza valores fuera de los rangos del contrato`() {
        assertThrows(IllegalArgumentException::class.java) {
            DetectionConfig(impactThresholdMg = 1_999)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DetectionConfig(tiltHoldMs = 5_001)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DetectionConfig(pitchOffsetCdeg = 9_001)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AppSettings(countdownSeconds = 9)
        }
    }

    @Test
    fun `evento forma clave estable de deduplicacion`() {
        val event = DeviceEvent(
            protocolVersion = 1,
            type = DeviceEventType.IMPACT,
            eventId = 3,
            bootCount = 57,
            uptimeMs = 123_456,
            peakAccelMg = 5_230,
            peakGyroDps = 412,
            pitchCdeg = -350,
            rollCdeg = 8_420,
            flags = 0,
            faultCode = null,
            receivedAt = Instant.parse("2026-10-05T20:00:00Z")
        )

        assertEquals("57:3", event.deduplicationKey)
    }

    @Test
    fun `settings parten desactivados y sin informacion de identificacion`() {
        val settings = AppSettings()

        assertFalse(settings.tripActive)
        assertFalse(settings.demoMode)
        assertFalse(settings.onboardingCompleted)
        assertEquals(null, settings.primaryContact)
        assertEquals(null, settings.pairedDeviceAddress)
        assertEquals(null, settings.consentAcceptedAt)
        assertEquals(20, settings.countdownSeconds)
    }

    @Test
    fun `traza valida longitud de muestras crudas de seis ejes`() {
        val trace = Trace(
            incidentId = UUID.randomUUID(),
            sampleRateHz = 200,
            preTriggerSamples = 600,
            totalSamples = 1_200,
            accelLsbPerG = 2_048,
            gyroLsbPerDpsX10 = 164,
            samples = ByteArray(1_200 * 12)
        )

        assertEquals(14_400, trace.samples.size)
        assertThrows(IllegalArgumentException::class.java) {
            trace.copy(samples = ByteArray(1))
        }
    }
}
