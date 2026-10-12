package com.motocrashguardian.ble

import com.motocrashguardian.ble.protocol.ControlResponse
import com.motocrashguardian.ble.protocol.DeviceConfigPacket
import com.motocrashguardian.ble.protocol.GpsPacket
import com.motocrashguardian.ble.protocol.TraceChunk
import com.motocrashguardian.core.model.DeviceEvent
import com.motocrashguardian.core.model.DeviceInfo
import com.motocrashguardian.core.model.Telemetry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * API de alto nivel del módulo (docs/05 §3.2). La UI y el servicio consumen
 * esto; no tocan el GATT directamente. Las tramas se decodifican con
 * `BleProtocol` (Kotlin puro, probado en JVM con los vectores del firmware).
 */
interface DeviceRepository {

    /** Estado simplificado de la conexión. */
    val connectionState: StateFlow<DeviceConnectionState>

    /** DEVICE_INFO leída al conectar; `null` sin conexión. */
    val deviceInfo: StateFlow<DeviceInfo?>

    /** Última CONFIG leída del dispositivo; `null` sin conexión. */
    val config: StateFlow<DeviceConfigPacket?>

    /** Batería del módulo (Battery Service); `null` si no la expone. */
    val batteryPercent: StateFlow<Int?>

    /** Telemetría a 5 Hz con replay = 1 (docs/05 §3.2). */
    val telemetry: Flow<Telemetry>

    /** Eventos deduplicados por `bootCount:eventId` (docs/05 §3.2). */
    val events: Flow<DeviceEvent>

    /** Última fix GPS; `null` hasta la primera. */
    val gps: StateFlow<GpsPacket?>

    /** Chunks de la caja negra (docs/03 §4.7). */
    val traceChunks: Flow<TraceChunk>

    /**
     * Conecta, espera a que el módulo esté listo y lee DEVICE_INFO y CONFIG.
     * Lanza si la conexión, la lectura o el firmware fallan.
     */
    suspend fun connect(address: String)

    /** Desconecta y limpia el estado del dispositivo. */
    fun disconnect()

    /** Lee la CONFIG vigente en el dispositivo. */
    suspend fun readConfig(): DeviceConfigPacket

    /**
     * Escribe CONFIG tras validar los rangos del dispositivo (docs/03 §4.4)
     * y confirma con relectura (el módulo asigna la revisión).
     */
    suspend fun writeConfig(packet: DeviceConfigPacket)

    /**
     * Escribe un comando en CONTROL_POINT y suspende hasta su respuesta
     * (timeout 3 s, docs/03 §8).
     */
    suspend fun sendCommand(
        opcode: Int,
        param: ByteArray = ByteArray(0),
        timeoutMs: Long = COMMAND_TIMEOUT_MS
    ): ControlResponse

    /** TARE: calibra el nivel neutro del manillar (docs/03 §4.5). */
    suspend fun tare(): ControlResponse

    /** Arma la detección de accidentes. */
    suspend fun arm(): ControlResponse

    /** Desarma la detección de accidentes. */
    suspend fun disarm(): ControlResponse

    /** Genera un evento de prueba para el simulacro (S-14). */
    suspend fun triggerTestEvent(): ControlResponse

    /** Confirma la recepción de un evento (docs/03 §4.3). */
    suspend fun ackEvent(bootCount: Int, eventId: Int): ControlResponse

    /**
     * Solicita la caja negra de un evento (timeout 5 s, docs/03 §8). El
     * payload de la respuesta son los metadatos (13 B, docs/03 §4.7); los
     * chunks llegan por [traceChunks].
     */
    suspend fun requestTrace(eventId: Int): ControlResponse

    /** Reinicia el módulo. */
    suspend fun reboot(): ControlResponse

    /** Restaura los valores de fábrica (marca 0xDEAD). */
    suspend fun factoryReset(): ControlResponse

    companion object {
        /** Timeout por comando (docs/03 §8). */
        const val COMMAND_TIMEOUT_MS = 3_000L

        /** Timeout de REQUEST_TRACE: respuesta + transferencia (docs/03 §8). */
        const val TRACE_TIMEOUT_MS = 5_000L
    }
}

/** Estado de conexión simplificado para la UI (docs/05 §3.2). */
enum class DeviceConnectionState {
    DISCONNECTED,
    CONNECTING,
    READY,
    DISCONNECTING,

    /** El `protocolVersion` del firmware no es compatible con esta app. */
    INCOMPATIBLE_FIRMWARE
}
