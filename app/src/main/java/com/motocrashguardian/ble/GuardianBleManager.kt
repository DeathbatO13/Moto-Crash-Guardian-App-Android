package com.motocrashguardian.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import com.motocrashguardian.ble.protocol.BleProtocol
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ktx.suspend
import java.util.UUID

/**
 * Cliente GATT del módulo (docs/03, ADR-002). Extiende [BleManager] de
 * Nordic BLE-KTX: la librería maneja la cola de requests, el descubrimiento
 * de servicios y la reconexión. El MVP no exige cifrado (docs/03 §2): ninguna
 * característica pide emparejamiento, así que no aparece diálogo de bonding.
 *
 * Expone tramas crudas ([ByteArray]); la decodificación a modelos de dominio
 * vive en [DeviceRepository] sobre `BleProtocol`.
 */
@SuppressLint("MissingPermission")
class GuardianBleManager(context: Context) : BleManager(context) {

    // Características: sólo válidas entre el descubrimiento de servicios
    // y onServicesInvalidated().
    private var deviceInfoCharacteristic: BluetoothGattCharacteristic? = null
    private var telemetryCharacteristic: BluetoothGattCharacteristic? = null
    private var eventCharacteristic: BluetoothGattCharacteristic? = null
    private var configCharacteristic: BluetoothGattCharacteristic? = null
    private var controlPointCharacteristic: BluetoothGattCharacteristic? = null
    private var gpsCharacteristic: BluetoothGattCharacteristic? = null
    private var traceCharacteristic: BluetoothGattCharacteristic? = null
    private var batteryLevelCharacteristic: BluetoothGattCharacteristic? = null

    private val _telemetry = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    /** TELEMETRY (notify, 5 Hz). */
    val telemetry: Flow<ByteArray> = _telemetry

    private val _gps = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    /** GPS (notify, 1 Hz si hay fix). */
    val gps: Flow<ByteArray> = _gps

    private val _events = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    /** EVENT (indicate: entrega garantizada, docs/03 §4.3). */
    val events: Flow<ByteArray> = _events

    private val _controlResponses = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    /** Respuestas de CONTROL_POINT (indicate, docs/03 §4.5). */
    val controlResponses: Flow<ByteArray> = _controlResponses

    private val _traceChunks = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    /** TRACE (notify; ráfaga de chunks de 234 B, docs/03 §4.7). */
    val traceChunks: Flow<ByteArray> = _traceChunks

    private val _battery = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    /** Battery Level estándar (notify), si el módulo lo expone. */
    val battery: Flow<ByteArray> = _battery

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        val service = gatt.getService(uuid(BleProtocol.SERVICE_UUID)) ?: return false
        deviceInfoCharacteristic = service.getCharacteristic(uuid(BleProtocol.DEVICE_INFO_UUID))
        telemetryCharacteristic = service.getCharacteristic(uuid(BleProtocol.TELEMETRY_UUID))
        eventCharacteristic = service.getCharacteristic(uuid(BleProtocol.EVENT_UUID))
        configCharacteristic = service.getCharacteristic(uuid(BleProtocol.CONFIG_UUID))
        controlPointCharacteristic = service.getCharacteristic(uuid(BleProtocol.CONTROL_POINT_UUID))
        gpsCharacteristic = service.getCharacteristic(uuid(BleProtocol.GPS_UUID))
        traceCharacteristic = service.getCharacteristic(uuid(BleProtocol.TRACE_UUID))
        // El Battery Service es opcional (capabilities bit2, docs/03 §4.1).
        batteryLevelCharacteristic = gatt
            .getService(uuid(BleProtocol.BATTERY_SERVICE_UUID))
            ?.getCharacteristic(uuid(BleProtocol.BATTERY_LEVEL_UUID))
        return deviceInfoCharacteristic != null &&
            telemetryCharacteristic != null &&
            eventCharacteristic != null &&
            configCharacteristic != null &&
            controlPointCharacteristic != null &&
            gpsCharacteristic != null &&
            traceCharacteristic != null
    }

    override fun initialize() {
        // MTU 247 primero (docs/03 §2): los chunks de TRACE caben en 234 B.
        requestMtu(247).enqueue()

        // Notificaciones: telemetría, GPS, traza y batería.
        subscribeToNotifications(telemetryCharacteristic) { bytes -> _telemetry.tryEmit(bytes) }
        subscribeToNotifications(gpsCharacteristic) { bytes -> _gps.tryEmit(bytes) }
        subscribeToNotifications(traceCharacteristic) { bytes -> _traceChunks.tryEmit(bytes) }
        subscribeToNotifications(batteryLevelCharacteristic) { bytes -> _battery.tryEmit(bytes) }

        // Indicaciones: eventos y respuestas de CONTROL_POINT.
        subscribeToIndications(eventCharacteristic) { bytes -> _events.tryEmit(bytes) }
        subscribeToIndications(controlPointCharacteristic) { bytes -> _controlResponses.tryEmit(bytes) }
    }

    override fun onServicesInvalidated() {
        deviceInfoCharacteristic = null
        telemetryCharacteristic = null
        eventCharacteristic = null
        configCharacteristic = null
        controlPointCharacteristic = null
        gpsCharacteristic = null
        traceCharacteristic = null
        batteryLevelCharacteristic = null
    }

    override fun getMinLogPriority(): Int = Log.DEBUG

    override fun log(priority: Int, message: String) {
        Log.println(priority, TAG, message)
    }

    // --- API pública (consumida por DeviceRepository) ---

    /**
     * Conecta a una dirección MAC y suspende hasta que el módulo está listo
     * (servicios descubiertos e inicialización completa). No requiere
     * bonding (docs/03 §2).
     */
    suspend fun connectToDevice(address: String) {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val device: BluetoothDevice = bluetoothManager.adapter.getRemoteDevice(address)
        connect(device)
            .useAutoConnect(false)
            .retry(3, 100)
            .timeout(15_000L)
            .suspend()
    }

    /** Desconecta; el manager puede reutilizarse para reconectar. */
    fun disconnectDevice() {
        disconnect().enqueue()
    }

    /** Lee DEVICE_INFO (16 B; característica abierta). */
    suspend fun readDeviceInfo(): ByteArray =
        readBytes(deviceInfoCharacteristic, "DEVICE_INFO")

    /** Lee CONFIG (16 B). */
    suspend fun readConfig(): ByteArray =
        readBytes(configCharacteristic, "CONFIG")

    /** Escribe CONFIG (16 B); la validación de rangos la hace el repository. */
    suspend fun writeConfig(data: ByteArray) =
        writeBytes(configCharacteristic, "CONFIG", data)

    /** Escribe en CONTROL_POINT (≤ 20 B); la respuesta llega por indicate. */
    suspend fun sendControlPoint(data: ByteArray) =
        writeBytes(controlPointCharacteristic, "CONTROL_POINT", data)

    // --- Suscripciones internas ---

    private fun subscribeToNotifications(
        characteristic: BluetoothGattCharacteristic?,
        onValue: (ByteArray) -> Unit
    ) {
        val char = characteristic ?: return
        setNotificationCallback(char)
            .with { _, data -> data.value?.let(onValue) }
        enableNotifications(char).enqueue()
    }

    private fun subscribeToIndications(
        characteristic: BluetoothGattCharacteristic?,
        onValue: (ByteArray) -> Unit
    ) {
        val char = characteristic ?: return
        setIndicationCallback(char)
            .with { _, data -> data.value?.let(onValue) }
        enableIndications(char).enqueue()
    }

    private suspend fun readBytes(
        characteristic: BluetoothGattCharacteristic?,
        name: String
    ): ByteArray {
        val char = characteristic
            ?: throw IllegalStateException("Característica $name no disponible (¿conectado?)")
        return readCharacteristic(char).suspend().value ?: ByteArray(0)
    }

    private suspend fun writeBytes(
        characteristic: BluetoothGattCharacteristic?,
        name: String,
        data: ByteArray
    ) {
        val char = characteristic
            ?: throw IllegalStateException("Característica $name no disponible (¿conectado?)")
        writeCharacteristic(char, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT).suspend()
    }

    private fun uuid(value: String): UUID = UUID.fromString(value)

    companion object {
        private const val TAG = "GuardianBleManager"
    }
}
