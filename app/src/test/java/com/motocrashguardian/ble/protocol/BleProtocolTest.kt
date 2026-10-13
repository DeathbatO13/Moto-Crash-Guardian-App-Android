package com.motocrashguardian.ble.protocol

import com.motocrashguardian.bytesOf
import com.motocrashguardian.core.model.DeviceEventType
import com.motocrashguardian.core.model.DeviceState
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Vectores de bytes de specs/features/telemetry-streaming.md §5:
 * los mismos que el firmware empaqueta (test/test_protocol) y la
 * app parsea aquí. Si un lado cambia, el test del otro lado falla.
 */
class BleProtocolTest {

    private val receivedAt = Instant.parse("2026-01-01T00:00:00Z")

    // TELEMETRY #1: reposo
    private val telemetryRest = bytesOf(
        0x01, 0x2A, 0x02, 0x07, 0xD7.toByte(), 0xFF.toByte(), 0x78, 0x00,
        0xF2, 0x03, 0x46, 0x05, 0x0C, 0x00, 0x52, 0x00
    )

    // TELEMETRY #2: moto caída y saturación
    private val telemetryCrash = bytesOf(
        0x01, 0xFF.toByte(), 0x03, 0x21, 0x82, 0x05, 0x1C.toByte(), 0xDF.toByte(),
        0x80.toByte(), 0x3E.toByte(), 0x80.toByte(), 0x3E.toByte(), 0xE2, 0x04,
        0xFF.toByte(), 0x00
    )

    // EVENT #1: impacto
    private val eventImpact = bytesOf(
        0x01, 0x01, 0x03, 0x00, 0x39, 0x00, 0x40, 0xE2,
        0x01, 0x00, 0x6E, 0x14, 0x9C, 0x01, 0xA2.toByte(), 0xFE.toByte(),
        0xE4, 0x20, 0x00, 0x00
    )

    // CONFIG: valores por defecto
    private val configDefault = bytesOf(
        0x01, 0x01, 0xAC.toByte(), 0x0D, 0x2C, 0x01, 0x41, 0x14,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    )

    // CONTROL_POINT: respuesta a TARE (pitch −14.10°, roll −7.30°)
    private val tareResponse = bytesOf(
        0x01, 0x07, 0x00, 0x7E, 0xFA.toByte(), 0x26, 0xFD.toByte()
    )

    @Test
    fun `decodifica telemetria de reposo`() {
        val tel = decodeTelemetry(telemetryRest, receivedAt)
        assertEquals(1, tel.protocolVersion)
        assertEquals(42, tel.seq)
        assertEquals(DeviceState.ARMED, tel.state)
        assertEquals(
            BleProtocol.FLAG_IMU_OK or BleProtocol.FLAG_GPS_PRESENT or
                BleProtocol.FLAG_GPS_FIX,
            tel.flags
        )
        assertEquals(-41, tel.pitchCdeg)      // −0.41°
        assertEquals(120, tel.rollCdeg)       // +1.20°
        assertEquals(1010, tel.accelMagMg)    // 1.010 g
        assertEquals(1350, tel.peakAccelMg)   // 1.350 g
        assertEquals(12, tel.peakGyroDps)
        assertEquals(82, tel.batteryPercent)
        assertEquals(receivedAt, tel.receivedAt)
    }

    @Test
    fun `decodifica telemetria de caida saturada`() {
        val tel = decodeTelemetry(telemetryCrash, receivedAt)
        assertEquals(255, tel.seq)
        assertEquals(DeviceState.POST_TRIGGER, tel.state)
        assertEquals(BleProtocol.FLAG_IMU_OK or BleProtocol.FLAG_SATURATED, tel.flags)
        assertEquals(1410, tel.pitchCdeg)     // +14.10°
        assertEquals(-8420, tel.rollCdeg)     // −84.20°
        assertEquals(16000, tel.accelMagMg)   // 16.000 g
        assertEquals(16000, tel.peakAccelMg)
        assertEquals(1250, tel.peakGyroDps)
        assertNull(tel.batteryPercent)        // 255 = desconocida
    }

    @Test
    fun `decodifica evento de impacto`() {
        val ev = decodeEvent(eventImpact, receivedAt)
        assertEquals(DeviceEventType.IMPACT, ev.type)
        assertEquals(3, ev.eventId)
        assertEquals(57, ev.bootCount)
        assertEquals(123456L, ev.uptimeMs)
        assertEquals(5230, ev.peakAccelMg)
        assertEquals(412, ev.peakGyroDps)
        assertEquals(-350, ev.pitchCdeg)      // −3.50°
        assertEquals(8420, ev.rollCdeg)       // +84.20°
        assertEquals(0, ev.flags)
        assertNull(ev.faultCode)
        assertEquals("57:3", ev.deduplicationKey)
    }

    @Test
    fun `codifica config por defecto`() {
        val packet = DeviceConfigPacket(
            tiltDetectionEnabled = true,
            impactThresholdMg = 3500,
            gyroThresholdDps = 300,
            tiltThresholdDeg = 65,
            tiltHoldMs = 2000,
            pitchOffsetCdeg = 0,
            rollOffsetCdeg = 0,
            configRevision = 0
        )
        assertArrayEquals(configDefault, encodeConfig(packet))
        assertTrue(validateConfig(packet))
    }

    @Test
    fun `decodifica config por defecto`() {
        val packet = decodeConfig(configDefault)
        assertEquals(0, packet.configRevision)
        assertTrue(packet.tiltDetectionEnabled)
        assertEquals(3500, packet.impactThresholdMg)
        assertEquals(300, packet.gyroThresholdDps)
        assertEquals(65, packet.tiltThresholdDeg)
        assertEquals(2000, packet.tiltHoldMs)
        assertEquals(0, packet.pitchOffsetCdeg)
        assertEquals(0, packet.rollOffsetCdeg)
    }

    @Test
    fun `decodifica respuesta de TARE`() {
        val resp = decodeControlResponse(tareResponse)
        assertEquals(BleProtocol.OP_TARE, resp.opcode)
        assertEquals(7, resp.requestId)
        assertEquals(BleProtocol.RESULT_OK, resp.result)
        // pitchOffset −1410 cdec = 0xFA7E · rollOffset −730 cdec = 0xFD26
        assertArrayEquals(
            bytesOf(0x7E, 0xFA.toByte(), 0x26, 0xFD.toByte()),
            resp.payload
        )
    }

    @Test
    fun `decodifica gps`() {
        // fix 3D · 12 satélites · HDOP 1.5 · 40.4000000, −37.0000000 ·
        // 55.30 km/h · fix de hace 2 s
        val bytes = bytesOf(
            0x01, 0x03, 0x0C, 0x0F,
            0x00, 0x8D, 0x14, 0x18,             // latE7 = 404000000
            0xC0.toByte(), 0x6C, 0xCB.toByte(), 0xFD.toByte(),  // lonE7 = −37000000
            0x9A, 0x15,                          // speedKmhX100 = 5530
            0x02, 0x00                           // fixAgeS = 2
        )
        val gps = decodeGps(bytes)
        assertEquals(3, gps.fixType)
        assertEquals(12, gps.satellites)
        assertEquals(15, gps.hdopX10)
        assertEquals(404000000, gps.latE7)
        assertEquals(-37000000, gps.lonE7)
        assertEquals(5530, gps.speedKmhX100)
        assertEquals(2, gps.fixAgeS)
    }

    @Test
    fun `decodifica metadatos de traza`() {
        val bytes = bytesOf(
            0xC8.toByte(), 0x00,                 // 200 Hz
            0x58, 0x02,                          // 600 pre-trigger
            0xB0.toByte(), 0x04,                 // 1200 total
            0x00, 0x08,                          // 2048 LSB/g
            0xA4.toByte(), 0x00,                 // 164 (16.4 × 10)
            0x40.toByte(), 0x00,                 // 64 chunks
            0x13                                 // 19 muestras/chunk
        )
        val meta = decodeTraceMetadata(bytes)
        assertEquals(200, meta.sampleRateHz)
        assertEquals(600, meta.preTriggerSamples)
        assertEquals(1200, meta.totalSamples)
        assertEquals(2048, meta.accelLsbPerG)
        assertEquals(164, meta.gyroLsbPerDpsX10)
        assertEquals(64, meta.chunkCount)
        assertEquals(19, meta.samplesPerChunk)
    }

    @Test
    fun `decodifica chunk de traza`() {
        val sample1 = bytesOf(
            0x01, 0x00, 0x02, 0x00, 0x00, 0x08,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
        val sample2 = bytesOf(
            0xFF.toByte(), 0xFF, 0x00, 0x00, 0x00, 0x08,
            0x10, 0x00, 0x20, 0x00, 0x30, 0x00
        )
        val bytes = bytesOf(
            0x03, 0x00,                          // eventId 3
            0x00, 0x00,                          // chunkIndex 0
            0x40.toByte(), 0x00                  // chunkCount 64
        ) + sample1 + sample2

        val chunk = decodeTraceChunk(bytes)
        assertEquals(3, chunk.eventId)
        assertEquals(0, chunk.chunkIndex)
        assertEquals(64, chunk.chunkCount)
        assertEquals(2, chunk.samples.size)
        assertEquals(
            ImuSample(ax = 1, ay = 2, az = 2048, gx = 0, gy = 0, gz = 0),
            chunk.samples[0]
        )
        assertEquals(
            ImuSample(ax = -1, ay = 0, az = 2048, gx = 16, gy = 32, gz = 48),
            chunk.samples[1]
        )
    }

    @Test
    fun `codifica solicitudes de control point`() {
        // ACK_EVENT 57:3 con requestId 9
        assertArrayEquals(
            bytesOf(0x06, 0x09, 0x39, 0x00, 0x03, 0x00),
            controlPointRequest(
                BleProtocol.OP_ACK_EVENT, 9, ackEventParam(57, 3)
            )
        )
        // REQUEST_TRACE del evento 3
        assertArrayEquals(
            bytesOf(0x05, 0x01, 0x03, 0x00),
            controlPointRequest(
                BleProtocol.OP_REQUEST_TRACE, 1, requestTraceParam(3)
            )
        )
        // ARM sin parámetro
        assertArrayEquals(
            bytesOf(0x02, 0x00),
            controlPointRequest(BleProtocol.OP_ARM, 0)
        )
        // FACTORY_RESET con 0xDEAD
        assertArrayEquals(
            bytesOf(0x08, 0x00, 0xDE.toByte(), 0xAD.toByte()),
            controlPointRequest(
                BleProtocol.OP_FACTORY_RESET, 0, factoryResetParam()
            )
        )
    }

    @Test
    fun `rechaza tramas truncadas`() {
        assertThrows(IllegalArgumentException::class.java) {
            decodeTelemetry(bytesOf(0x01, 0x02), receivedAt)
        }
        assertThrows(IllegalArgumentException::class.java) {
            decodeEvent(bytesOf(0x01, 0x01, 0x03, 0x00), receivedAt)
        }
        assertThrows(IllegalArgumentException::class.java) {
            decodeTraceChunk(bytesOf(0x01, 0x00, 0x00))
        }
        assertThrows(IllegalArgumentException::class.java) {
            decodeTraceChunk(
                bytesOf(0x01, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00)
            )   // header + 1 B: muestra incompleta
        }
    }

    @Test
    fun `valida rangos de config`() {
        val base = DeviceConfigPacket(
            tiltDetectionEnabled = true,
            impactThresholdMg = 3500,
            gyroThresholdDps = 300,
            tiltThresholdDeg = 65,
            tiltHoldMs = 2000,
            pitchOffsetCdeg = 0,
            rollOffsetCdeg = 0,
            configRevision = 1
        )
        assertTrue(validateConfig(base))

        val invalid = base.copy(impactThresholdMg = 6001)
        assertTrue(!validateConfig(invalid))

        val invalidHold = base.copy(tiltHoldMs = 50)   // < 5 ds
        assertTrue(!validateConfig(invalidHold))
    }
}
