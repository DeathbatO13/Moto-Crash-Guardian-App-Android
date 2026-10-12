package com.motocrashguardian.ble.protocol

import com.motocrashguardian.core.model.DeviceEvent
import com.motocrashguardian.core.model.DeviceEventType
import com.motocrashguardian.core.model.DeviceInfo
import com.motocrashguardian.core.model.DeviceState
import com.motocrashguardian.core.model.Telemetry
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant

/**
 * Protocolo BLE v1 del dispositivo (docs/03). Kotlin puro, sin Android:
 * se prueba en JVM con los mismos vectores de bytes que el firmware
 * (specs/features/telemetry-streaming.md §5).
 *
 * Todas las tramas son little-endian y de longitud fija salvo el
 * payload de CONTROL_POINT y los chunks de TRACE.
 */
object BleProtocol {
    // --- UUIDs (docs/03 §3) ---
    const val SERVICE_UUID = "a3c1f000-5b2e-4d7a-9f41-6c2e8b7d0e10"
    const val DEVICE_INFO_UUID = "a3c1f001-5b2e-4d7a-9f41-6c2e8b7d0e10"
    const val TELEMETRY_UUID = "a3c1f002-5b2e-4d7a-9f41-6c2e8b7d0e10"
    const val EVENT_UUID = "a3c1f003-5b2e-4d7a-9f41-6c2e8b7d0e10"
    const val CONFIG_UUID = "a3c1f004-5b2e-4d7a-9f41-6c2e8b7d0e10"
    const val CONTROL_POINT_UUID = "a3c1f005-5b2e-4d7a-9f41-6c2e8b7d0e10"
    const val GPS_UUID = "a3c1f006-5b2e-4d7a-9f41-6c2e8b7d0e10"
    const val TRACE_UUID = "a3c1f007-5b2e-4d7a-9f41-6c2e8b7d0e10"
    const val BATTERY_SERVICE_UUID = "0000180f-0000-1000-8000-00805f9b34fb"
    const val BATTERY_LEVEL_UUID = "00002a19-0000-1000-8000-00805f9b34fb"

    const val PROTOCOL_VERSION = 1

    // --- Tamaños de trama (bytes) ---
    const val DEVICE_INFO_SIZE = 16
    const val TELEMETRY_SIZE = 16
    const val EVENT_SIZE = 20
    const val CONFIG_SIZE = 16
    const val GPS_SIZE = 16
    const val IMU_SAMPLE_SIZE = 12
    const val TRACE_METADATA_SIZE = 13
    const val TRACE_CHUNK_HEADER_SIZE = 6
    const val TRACE_SAMPLES_PER_CHUNK = 19   // (MTU 247 − 3 − 6) / 12
    const val CONTROL_POINT_MAX = 20

    // --- Flags de TELEMETRY (docs/03 §4.2) ---
    const val FLAG_IMU_OK = 0x01
    const val FLAG_GPS_PRESENT = 0x02
    const val FLAG_GPS_FIX = 0x04
    const val FLAG_CHARGING = 0x08
    const val FLAG_BATTERY_LOW = 0x10
    const val FLAG_SATURATED = 0x20

    // --- Flags de EVENT (docs/03 §4.3) ---
    const val EVENT_FLAG_SATURATED = 0x01
    const val EVENT_FLAG_TRACE = 0x02
    const val EVENT_FLAG_RESENT = 0x04

    // --- Códigos de fallo (docs/03 §4.3) ---
    const val FAULT_IMU_NO_RESPONSE = 1
    const val FAULT_I2C_BUS = 2
    const val FAULT_BATTERY_CRIT = 3

    // --- Estados (docs/03 §5) ---
    const val STATE_BOOT = 0
    const val STATE_DISARMED = 1
    const val STATE_ARMED = 2
    const val STATE_POST_TRIGGER = 3
    const val STATE_COOLDOWN = 4
    const val STATE_CALIBRATING = 5
    const val STATE_FAULT = 6

    // --- Tipos de evento (docs/03 §4.3) ---
    const val EVENT_IMPACT = 1
    const val EVENT_TILT = 2
    const val EVENT_TEST = 3
    const val EVENT_FAULT = 4

    // --- Opcodes de CONTROL_POINT (docs/03 §4.5) ---
    const val OP_TARE = 0x01
    const val OP_ARM = 0x02
    const val OP_DISARM = 0x03
    const val OP_TRIGGER_TEST_EVENT = 0x04
    const val OP_REQUEST_TRACE = 0x05
    const val OP_ACK_EVENT = 0x06
    const val OP_REBOOT = 0x07
    const val OP_FACTORY_RESET = 0x08

    // --- Resultados de CONTROL_POINT (docs/03 §4.5) ---
    const val RESULT_OK = 0
    const val RESULT_UNSUPPORTED = 1
    const val RESULT_INVALID_PARAM = 2
    const val RESULT_BUSY = 3
    const val RESULT_FAILED = 4
    const val RESULT_NOT_FOUND = 5

    // --- Rangos válidos de CONFIG (docs/03 §4.4) ---
    const val CONFIG_IMPACT_MIN_MG = 2000
    const val CONFIG_IMPACT_MAX_MG = 6000
    const val CONFIG_GYRO_MIN_DPS = 150
    const val CONFIG_GYRO_MAX_DPS = 1000
    const val CONFIG_TILT_MIN_DEG = 50
    const val CONFIG_TILT_MAX_DEG = 80
    const val CONFIG_TILT_HOLD_MIN_DS = 5
    const val CONFIG_TILT_HOLD_MAX_DS = 50
    const val CONFIG_OFFSET_MAX_CDEG = 9000

    // --- Batería ---
    const val BATTERY_UNKNOWN = 0xFF
}

/** CONFIG tal como viaja por el aire (docs/03 §4.4). */
data class DeviceConfigPacket(
    val tiltDetectionEnabled: Boolean,
    val impactThresholdMg: Int,
    val gyroThresholdDps: Int,
    val tiltThresholdDeg: Int,
    val tiltHoldMs: Int,
    val pitchOffsetCdeg: Int,
    val rollOffsetCdeg: Int,
    val configRevision: Int
)

/** GPS tal como viaja por el aire (docs/03 §4.6). */
data class GpsPacket(
    val fixType: Int,          // 0 sin fix · 2 2D · 3 3D
    val satellites: Int,
    val hdopX10: Int,
    val latE7: Int,
    val lonE7: Int,
    val speedKmhX100: Int,
    val fixAgeS: Int           // 65535 = nunca
)

/** Muestra cruda de la IMU (12 B); también es el formato de la caja negra. */
data class ImuSample(
    val ax: Int,
    val ay: Int,
    val az: Int,
    val gx: Int,
    val gy: Int,
    val gz: Int
)

/** Metadatos de traza: respuesta a REQUEST_TRACE (docs/03 §4.7). */
data class TraceMetadata(
    val sampleRateHz: Int,
    val preTriggerSamples: Int,
    val totalSamples: Int,
    val accelLsbPerG: Int,
    val gyroLsbPerDpsX10: Int,
    val chunkCount: Int,
    val samplesPerChunk: Int
)

/** Chunk de traza: header + hasta 19 muestras crudas. */
data class TraceChunk(
    val eventId: Int,
    val chunkIndex: Int,
    val chunkCount: Int,
    val samples: List<ImuSample>
)

/** Respuesta de CONTROL_POINT (docs/03 §4.5). */
data class ControlResponse(
    val opcode: Int,
    val requestId: Int,
    val result: Int,
    val payload: ByteArray
)

// --- Lectura little-endian desde ByteArray ---

private fun ByteArray.u16(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

private fun ByteArray.i16(offset: Int): Int = u16(offset).toShort().toInt()

private fun ByteArray.u32(offset: Int): Long =
    u16(offset).toLong() or (u16(offset + 2).toLong() shl 16)

private fun ByteArray.i32(offset: Int): Int = u32(offset).toInt()

private fun ByteArray.u8(offset: Int): Int = this[offset].toUByte().toInt()

private fun byteArrayOfU16(value: Int): ByteArray =
    byteArrayOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())

// --- Decodificadores (dispositivo → app) ---

fun decodeDeviceInfo(bytes: ByteArray): DeviceInfo {
    require(bytes.size >= BleProtocol.DEVICE_INFO_SIZE) {
        "DEVICE_INFO truncada: ${bytes.size} B"
    }
    val mac = bytes.copyOfRange(8, 14)
    return DeviceInfo(
        protocolVersion = bytes.u8(0),
        firmwareMajor = bytes.u8(1),
        firmwareMinor = bytes.u8(2),
        firmwarePatch = bytes.u8(3),
        hardwareRevision = bytes.u8(4),
        capabilities = bytes.u8(5),
        bootCount = bytes.u16(6),
        deviceId = mac.joinToString(":") { "%02X".format(it) }
    )
}

fun decodeTelemetry(bytes: ByteArray, receivedAt: Instant): Telemetry {
    require(bytes.size >= BleProtocol.TELEMETRY_SIZE) {
        "TELEMETRY truncada: ${bytes.size} B"
    }
    return Telemetry(
        protocolVersion = bytes.u8(0),
        seq = bytes.u8(1),
        state = deviceStateOf(bytes.u8(2)),
        flags = bytes.u8(3),
        pitchCdeg = bytes.i16(4),
        rollCdeg = bytes.i16(6),
        accelMagMg = bytes.u16(8),
        peakAccelMg = bytes.u16(10),
        peakGyroDps = bytes.u16(12),
        batteryPercent = bytes.u8(14).let {
            if (it == BleProtocol.BATTERY_UNKNOWN) null else it
        },
        receivedAt = receivedAt
    )
}

fun decodeEvent(bytes: ByteArray, receivedAt: Instant): DeviceEvent {
    require(bytes.size >= BleProtocol.EVENT_SIZE) {
        "EVENT truncada: ${bytes.size} B"
    }
    return DeviceEvent(
        protocolVersion = bytes.u8(0),
        type = deviceEventTypeOf(bytes.u8(1)),
        eventId = bytes.u16(2),
        bootCount = bytes.u16(4),
        uptimeMs = bytes.u32(6),
        peakAccelMg = bytes.u16(10),
        peakGyroDps = bytes.u16(12),
        pitchCdeg = bytes.i16(14),
        rollCdeg = bytes.i16(16),
        flags = bytes.u8(18),
        faultCode = bytes.u8(19).let { if (it == 0) null else it },
        receivedAt = receivedAt
    )
}

fun decodeConfig(bytes: ByteArray): DeviceConfigPacket {
    require(bytes.size >= BleProtocol.CONFIG_SIZE) {
        "CONFIG truncada: ${bytes.size} B"
    }
    return DeviceConfigPacket(
        tiltDetectionEnabled = bytes.u8(1) and 0x01 != 0,
        impactThresholdMg = bytes.u16(2),
        gyroThresholdDps = bytes.u16(4),
        tiltThresholdDeg = bytes.u8(6),
        tiltHoldMs = bytes.u8(7) * 100,
        pitchOffsetCdeg = bytes.i16(8),
        rollOffsetCdeg = bytes.i16(10),
        configRevision = bytes.u16(12)
    )
}

fun decodeGps(bytes: ByteArray): GpsPacket {
    require(bytes.size >= BleProtocol.GPS_SIZE) {
        "GPS truncada: ${bytes.size} B"
    }
    return GpsPacket(
        fixType = bytes.u8(1),
        satellites = bytes.u8(2),
        hdopX10 = bytes.u8(3),
        latE7 = bytes.i32(4),
        lonE7 = bytes.i32(8),
        speedKmhX100 = bytes.u16(12),
        fixAgeS = bytes.u16(14)
    )
}

fun decodeTraceMetadata(bytes: ByteArray): TraceMetadata {
    require(bytes.size >= BleProtocol.TRACE_METADATA_SIZE) {
        "TraceMetadata truncada: ${bytes.size} B"
    }
    return TraceMetadata(
        sampleRateHz = bytes.u16(0),
        preTriggerSamples = bytes.u16(2),
        totalSamples = bytes.u16(4),
        accelLsbPerG = bytes.u16(6),
        gyroLsbPerDpsX10 = bytes.u16(8),
        chunkCount = bytes.u16(10),
        samplesPerChunk = bytes.u8(12)
    )
}

fun decodeTraceChunk(bytes: ByteArray): TraceChunk {
    val headerSize = BleProtocol.TRACE_CHUNK_HEADER_SIZE
    require(bytes.size >= headerSize) { "Chunk de traza truncado" }
    val body = bytes.size - headerSize
    require(body % BleProtocol.IMU_SAMPLE_SIZE == 0) {
        "Chunk de traza con muestras incompletas: $body B"
    }
    val count = body / BleProtocol.IMU_SAMPLE_SIZE
    val samples = buildList(count) {
        for (i in 0 until count) {
            val o = headerSize + i * BleProtocol.IMU_SAMPLE_SIZE
            add(
                ImuSample(
                    ax = bytes.i16(o),
                    ay = bytes.i16(o + 2),
                    az = bytes.i16(o + 4),
                    gx = bytes.i16(o + 6),
                    gy = bytes.i16(o + 8),
                    gz = bytes.i16(o + 10)
                )
            )
        }
    }
    return TraceChunk(
        eventId = bytes.u16(0),
        chunkIndex = bytes.u16(2),
        chunkCount = bytes.u16(4),
        samples = samples
    )
}

fun decodeControlResponse(bytes: ByteArray): ControlResponse {
    require(bytes.size >= 3) { "Respuesta CONTROL_POINT truncada" }
    return ControlResponse(
        opcode = bytes.u8(0),
        requestId = bytes.u8(1),
        result = bytes.u8(2),
        payload = bytes.copyOfRange(3, bytes.size)
    )
}

// --- Codificadores (app → dispositivo) ---

fun encodeConfig(packet: DeviceConfigPacket): ByteArray {
    val buf = ByteBuffer.allocate(BleProtocol.CONFIG_SIZE)
        .order(ByteOrder.LITTLE_ENDIAN)
    buf.put(BleProtocol.PROTOCOL_VERSION.toByte())
    buf.put((if (packet.tiltDetectionEnabled) 0x01 else 0x00).toByte())
    buf.putShort(packet.impactThresholdMg.toShort())
    buf.putShort(packet.gyroThresholdDps.toShort())
    buf.put(packet.tiltThresholdDeg.toByte())
    buf.put((packet.tiltHoldMs / 100).toByte())
    buf.putShort(packet.pitchOffsetCdeg.toShort())
    buf.putShort(packet.rollOffsetCdeg.toShort())
    buf.putShort(packet.configRevision.toShort())
    buf.putShort(0)
    return buf.array()
}

fun controlPointRequest(
    opcode: Int,
    requestId: Int,
    param: ByteArray = ByteArray(0)
): ByteArray = byteArrayOf(opcode.toByte(), requestId.toByte()) + param

fun ackEventParam(bootCount: Int, eventId: Int): ByteArray =
    byteArrayOfU16(bootCount) + byteArrayOfU16(eventId)

fun requestTraceParam(eventId: Int): ByteArray = byteArrayOfU16(eventId)

fun factoryResetParam(): ByteArray = byteArrayOf(0xDE.toByte(), 0xAD.toByte())

/** Valida los rangos de CONFIG antes de escribir (docs/03 §4.4). */
fun validateConfig(packet: DeviceConfigPacket): Boolean =
    packet.impactThresholdMg in BleProtocol.CONFIG_IMPACT_MIN_MG..BleProtocol.CONFIG_IMPACT_MAX_MG &&
        packet.gyroThresholdDps in BleProtocol.CONFIG_GYRO_MIN_DPS..BleProtocol.CONFIG_GYRO_MAX_DPS &&
        packet.tiltThresholdDeg in BleProtocol.CONFIG_TILT_MIN_DEG..BleProtocol.CONFIG_TILT_MAX_DEG &&
        packet.tiltHoldMs / 100 in BleProtocol.CONFIG_TILT_HOLD_MIN_DS..BleProtocol.CONFIG_TILT_HOLD_MAX_DS &&
        packet.pitchOffsetCdeg in -BleProtocol.CONFIG_OFFSET_MAX_CDEG..BleProtocol.CONFIG_OFFSET_MAX_CDEG &&
        packet.rollOffsetCdeg in -BleProtocol.CONFIG_OFFSET_MAX_CDEG..BleProtocol.CONFIG_OFFSET_MAX_CDEG

// --- Mapeos de enumeraciones ---

private fun deviceStateOf(wire: Int): DeviceState = when (wire) {
    BleProtocol.STATE_BOOT -> DeviceState.BOOT
    BleProtocol.STATE_DISARMED -> DeviceState.DISARMED
    BleProtocol.STATE_ARMED -> DeviceState.ARMED
    BleProtocol.STATE_POST_TRIGGER -> DeviceState.POST_TRIGGER
    BleProtocol.STATE_COOLDOWN -> DeviceState.COOLDOWN
    BleProtocol.STATE_CALIBRATING -> DeviceState.CALIBRATING
    BleProtocol.STATE_FAULT -> DeviceState.FAULT
    else -> throw IllegalArgumentException("Estado desconocido: $wire")
}

private fun deviceEventTypeOf(wire: Int): DeviceEventType = when (wire) {
    BleProtocol.EVENT_IMPACT -> DeviceEventType.IMPACT
    BleProtocol.EVENT_TILT -> DeviceEventType.TILT
    BleProtocol.EVENT_TEST -> DeviceEventType.TEST
    BleProtocol.EVENT_FAULT -> DeviceEventType.FAULT
    else -> throw IllegalArgumentException("Tipo de evento desconocido: $wire")
}
