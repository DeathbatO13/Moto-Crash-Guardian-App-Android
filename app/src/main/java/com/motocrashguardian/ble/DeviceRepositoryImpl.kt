package com.motocrashguardian.ble

import android.util.Log
import com.motocrashguardian.ble.protocol.BleProtocol
import com.motocrashguardian.ble.protocol.ControlResponse
import com.motocrashguardian.ble.protocol.DeviceConfigPacket
import com.motocrashguardian.ble.protocol.GpsPacket
import com.motocrashguardian.ble.protocol.TraceChunk
import com.motocrashguardian.ble.protocol.ackEventParam
import com.motocrashguardian.ble.protocol.controlPointRequest
import com.motocrashguardian.ble.protocol.decodeConfig
import com.motocrashguardian.ble.protocol.decodeControlResponse
import com.motocrashguardian.ble.protocol.decodeDeviceInfo
import com.motocrashguardian.ble.protocol.decodeEvent
import com.motocrashguardian.ble.protocol.decodeGps
import com.motocrashguardian.ble.protocol.decodeTelemetry
import com.motocrashguardian.ble.protocol.decodeTraceChunk
import com.motocrashguardian.ble.protocol.encodeConfig
import com.motocrashguardian.ble.protocol.factoryResetParam
import com.motocrashguardian.ble.protocol.requestTraceParam
import com.motocrashguardian.ble.protocol.validateConfig
import com.motocrashguardian.core.model.DeviceEvent
import com.motocrashguardian.core.model.DeviceInfo
import com.motocrashguardian.core.model.Telemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import no.nordicsemi.android.ble.ktx.state.ConnectionState
import no.nordicsemi.android.ble.ktx.stateAsFlow
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Named
import javax.inject.Singleton

private const val TAG = "DeviceRepository"

/**
 * Implementación de [DeviceRepository] sobre [GuardianBleManager].
 */
@Singleton
class DeviceRepositoryImpl(
    private val manager: GuardianBleManager,
    @param:Named("ble") private val scope: CoroutineScope
) : DeviceRepository {

    // --- Estado ---
    private val _connectionState = MutableStateFlow(DeviceConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<DeviceConnectionState> = _connectionState.asStateFlow()

    private val _deviceInfo = MutableStateFlow<DeviceInfo?>(null)
    override val deviceInfo: StateFlow<DeviceInfo?> = _deviceInfo.asStateFlow()

    private val _config = MutableStateFlow<DeviceConfigPacket?>(null)
    override val config: StateFlow<DeviceConfigPacket?> = _config.asStateFlow()

    private val _batteryPercent = MutableStateFlow<Int?>(null)
    override val batteryPercent: StateFlow<Int?> = _batteryPercent.asStateFlow()

    private val _gps = MutableStateFlow<GpsPacket?>(null)
    override val gps: StateFlow<GpsPacket?> = _gps.asStateFlow()

    // --- Flujos en vivo ---
    override val telemetry: Flow<Telemetry> = manager.telemetry
        .decodeTimed(::decodeTelemetry)
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    private val seenEventKeys = HashSet<String>()
    override val events: Flow<DeviceEvent> = manager.events
        .decodeTimed(::decodeEvent)
        .filter { seenEventKeys.add(it.deduplicationKey) }
        .shareIn(scope, SharingStarted.Eagerly)

    override val traceChunks: Flow<TraceChunk> = manager.traceChunks
        .decode(::decodeTraceChunk)
        .shareIn(scope, SharingStarted.Eagerly)

    // --- Respuestas de CONTROL_POINT ---
    private var responseChannel = Channel<ControlResponse>(Channel.BUFFERED)
    private val requestId = AtomicInteger(0)

    init {
        scope.launch {
            manager.stateAsFlow().collect { state ->
                val mapped = state.toDeviceConnectionState()
                val current = _connectionState.value
                if (current != DeviceConnectionState.INCOMPATIBLE_FIRMWARE ||
                    mapped == DeviceConnectionState.DISCONNECTED
                ) {
                    _connectionState.value = mapped
                }
            }
        }
        scope.launch {
            manager.controlResponses
                .decode(::decodeControlResponse)
                .collect { response -> responseChannel.trySend(response) }
        }
        scope.launch {
            manager.gps.collect { bytes ->
                runCatching { decodeGps(bytes) }
                    .onFailure { Log.w(TAG, "Trama GPS descartada: ${it.message}") }
                    .getOrNull()
                    ?.let { _gps.value = it }
            }
        }
        scope.launch {
            manager.battery.collect { bytes ->
                runCatching { decodeBatteryLevel(bytes) }
                    .onFailure { Log.w(TAG, "Batería descartada: ${it.message}") }
                    .getOrNull()
                    ?.let { _batteryPercent.value = it }
            }
        }
    }

    // --- Conexión ---

    override suspend fun connect(address: String) {
        manager.connectToDevice(address)
        val info = decodeDeviceInfo(manager.readDeviceInfo())
        _deviceInfo.value = info
        if (info.protocolVersion != BleProtocol.PROTOCOL_VERSION) {
            _connectionState.value = DeviceConnectionState.INCOMPATIBLE_FIRMWARE
        }
        _config.value = runCatching { readConfig() }
            .onFailure { Log.w(TAG, "No se pudo leer CONFIG: ${it.message}") }
            .getOrNull()
    }

    override fun disconnect() {
        manager.disconnectDevice()
        _deviceInfo.value = null
        _config.value = null
        _batteryPercent.value = null
        _gps.value = null
        responseChannel.close()
        responseChannel = Channel(Channel.BUFFERED)
    }

    // --- Configuración ---

    override suspend fun readConfig(): DeviceConfigPacket =
        decodeConfig(manager.readConfig())

    override suspend fun writeConfig(packet: DeviceConfigPacket) {
        require(validateConfig(packet)) {
            "CONFIG fuera de los rangos del dispositivo (docs/03 §4.4)"
        }
        manager.writeConfig(encodeConfig(packet))
        _config.value = readConfig()
    }

    // --- CONTROL_POINT ---

    override suspend fun sendCommand(
        opcode: Int,
        param: ByteArray,
        timeoutMs: Long
    ): ControlResponse {
        val id = requestId.getAndIncrement() and 0xFF
        manager.sendControlPoint(controlPointRequest(opcode, id, param))
        return withTimeout(timeoutMs) {
            var found: ControlResponse? = null
            while (found == null) {
                val response = responseChannel.receive()
                if (response.requestId == id) {
                    found = response
                }
            }
            found!!
        }
    }

    override suspend fun tare(): ControlResponse =
        sendCommand(BleProtocol.OP_TARE, ByteArray(0), DeviceRepository.COMMAND_TIMEOUT_MS)

    override suspend fun arm(): ControlResponse =
        sendCommand(BleProtocol.OP_ARM, ByteArray(0), DeviceRepository.COMMAND_TIMEOUT_MS)

    override suspend fun disarm(): ControlResponse =
        sendCommand(BleProtocol.OP_DISARM, ByteArray(0), DeviceRepository.COMMAND_TIMEOUT_MS)

    override suspend fun triggerTestEvent(): ControlResponse =
        sendCommand(BleProtocol.OP_TRIGGER_TEST_EVENT, ByteArray(0), DeviceRepository.COMMAND_TIMEOUT_MS)

    override suspend fun ackEvent(bootCount: Int, eventId: Int): ControlResponse =
        sendCommand(BleProtocol.OP_ACK_EVENT, ackEventParam(bootCount, eventId), DeviceRepository.COMMAND_TIMEOUT_MS)

    override suspend fun requestTrace(eventId: Int): ControlResponse =
        sendCommand(BleProtocol.OP_REQUEST_TRACE, requestTraceParam(eventId), DeviceRepository.TRACE_TIMEOUT_MS)

    override suspend fun reboot(): ControlResponse =
        sendCommand(BleProtocol.OP_REBOOT, ByteArray(0), DeviceRepository.COMMAND_TIMEOUT_MS)

    override suspend fun factoryReset(): ControlResponse =
        sendCommand(BleProtocol.OP_FACTORY_RESET, factoryResetParam(), DeviceRepository.COMMAND_TIMEOUT_MS)

    // --- Helpers ---

    private fun decodeBatteryLevel(bytes: ByteArray): Int? {
        require(bytes.isNotEmpty()) { "Battery Level vacío" }
        val percent = bytes[0].toUByte().toInt()
        return if (percent in 0..100) percent else null
    }
}

internal fun ConnectionState.toDeviceConnectionState(): DeviceConnectionState = when (this) {
    ConnectionState.Connecting, ConnectionState.Initializing -> DeviceConnectionState.CONNECTING
    ConnectionState.Ready -> DeviceConnectionState.READY
    ConnectionState.Disconnecting -> DeviceConnectionState.DISCONNECTING
    is ConnectionState.Disconnected -> DeviceConnectionState.DISCONNECTED
}

private inline fun <reified T> Flow<ByteArray>.decodeTimed(
    crossinline decode: (ByteArray, Instant) -> T
): Flow<T> = map { bytes ->
    runCatching { decode(bytes, Instant.now()) }
        .onFailure { Log.w(TAG, "Trama descartada: ${it.message}") }
        .getOrNull()
}.filterNotNull()

private inline fun <reified T> Flow<ByteArray>.decode(
    crossinline decode: (ByteArray) -> T
): Flow<T> = map { bytes ->
    runCatching { decode(bytes) }
        .onFailure { Log.w(TAG, "Trama descartada: ${it.message}") }
        .getOrNull()
}.filterNotNull()
