package com.motocrashguardian.ble

import com.motocrashguardian.bytesOf
import com.motocrashguardian.ble.protocol.BleProtocol
import com.motocrashguardian.ble.protocol.DeviceConfigPacket
import com.motocrashguardian.ble.protocol.encodeConfig
import com.motocrashguardian.core.model.DeviceState
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import no.nordicsemi.android.ble.ktx.state.ConnectionState
import no.nordicsemi.android.ble.ktx.stateAsFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * DeviceRepository sobre un manager mockeado (Robolectric da el entorno
 * Android que necesita cargar BleManager). Los vectores son los mismos de
 * BleProtocolTest (specs/features/telemetry-streaming.md §5): la app los
 * parsea igual que el firmware los empaqueta.
 */
@RunWith(RobolectricTestRunner::class)
class DeviceRepositoryImplTest {

    private val manager = mockk<GuardianBleManager>(relaxed = true)

    private val telemetryFlow = MutableSharedFlow<ByteArray>()
    private val eventsFlow = MutableSharedFlow<ByteArray>()
    private val gpsFlow = MutableSharedFlow<ByteArray>()
    private val traceFlow = MutableSharedFlow<ByteArray>()
    private val batteryFlow = MutableSharedFlow<ByteArray>()
    private val controlFlow = MutableSharedFlow<ByteArray>()

    private val testScope = TestScope()

    private val libraryState = MutableStateFlow<ConnectionState>(
        ConnectionState.Disconnected(ConnectionState.Disconnected.Reason.LINK_LOSS)
    )

    // Los colectores del repositorio no terminan nunca: van en un scope aparte (mismo scheduler)
    // que se cancela al terminar, para que runTest no espere por ellos.
    private val repositoryScope =
        CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScope.testScheduler))
    private lateinit var repository: DeviceRepositoryImpl

    // TELEMETRY #1: reposo (mismo vector que BleProtocolTest)
    private val telemetryRest = bytesOf(
        0x01, 0x2A, 0x02, 0x07, 0xD7.toByte(), 0xFF.toByte(), 0x78, 0x00,
        0xF2, 0x03, 0x46, 0x05, 0x0C, 0x00, 0x52, 0x00
    )

    // EVENT #1: impacto (bootCount 57, eventId 3)
    private val eventImpact = bytesOf(
        0x01, 0x01, 0x03, 0x00, 0x39, 0x00, 0x40, 0xE2,
        0x01, 0x00, 0x6E, 0x14, 0x9C, 0x01, 0xA2.toByte(), 0xFE.toByte(),
        0xE4, 0x20, 0x00, 0x00
    )

    // CONFIG por defecto
    private val configDefault = bytesOf(
        0x01, 0x01, 0xAC.toByte(), 0x0D, 0x2C, 0x01, 0x41, 0x14,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    )

    // DEVICE_INFO v1: fw 1.2.3, hw 1, capabilities 7, boot 57,
    // MAC 00:11:22:33:44:55
    private val deviceInfoV1 = bytesOf(
        0x01, 0x01, 0x02, 0x03, 0x01, 0x07, 0x39, 0x00,
        0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x00, 0x00
    )

    // DEVICE_INFO con protocolVersion 2 (incompatible con la app)
    private val deviceInfoV2 = deviceInfoV1.copyOf().also { it[0] = 0x02 }

    @Before
    fun setUp() {
        // stateAsFlow es una extension estatica que exige un BleManager real; se simula el estado.
        mockkStatic(BLE_MANAGER_EXT)
        every { manager.stateAsFlow() } returns libraryState
        every { manager.telemetry } returns telemetryFlow
        every { manager.events } returns eventsFlow
        every { manager.gps } returns gpsFlow
        every { manager.traceChunks } returns traceFlow
        every { manager.battery } returns batteryFlow
        every { manager.controlResponses } returns controlFlow
        repository = DeviceRepositoryImpl(manager, repositoryScope)
    }

    @After
    fun tearDown() {
        repositoryScope.cancel()
        unmockkStatic(BLE_MANAGER_EXT)
    }

    @Test
    fun `empieza desconectado`() = testScope.runTest {
        advanceUntilIdle()   // el collector procesa el DISCONNECTED inicial
        assertEquals(DeviceConnectionState.DISCONNECTED, repository.connectionState.value)
    }

    @Test
    fun `mapea cada estado de la libreria`() {
        assertEquals(
            DeviceConnectionState.CONNECTING,
            ConnectionState.Connecting.toDeviceConnectionState()
        )
        assertEquals(
            DeviceConnectionState.CONNECTING,
            ConnectionState.Initializing.toDeviceConnectionState()
        )
        assertEquals(
            DeviceConnectionState.READY,
            ConnectionState.Ready.toDeviceConnectionState()
        )
        assertEquals(
            DeviceConnectionState.DISCONNECTING,
            ConnectionState.Disconnecting.toDeviceConnectionState()
        )
        assertEquals(
            DeviceConnectionState.DISCONNECTED,
            ConnectionState.Disconnected(ConnectionState.Disconnected.Reason.LINK_LOSS)
                .toDeviceConnectionState()
        )
    }

    @Test
    fun `decodifica la telemetria`() = testScope.runTest {
        advanceUntilIdle()
        telemetryFlow.emit(telemetryRest)
        advanceUntilIdle()
        val tel = repository.telemetry.first()
        assertEquals(42, tel.seq)
        assertEquals(DeviceState.ARMED, tel.state)
        assertEquals(1010, tel.accelMagMg)
        assertEquals(1350, tel.peakAccelMg)
        assertEquals(82, tel.batteryPercent)
    }

    @Test
    fun `deduplica eventos por bootCount y eventId`() = testScope.runTest {
        advanceUntilIdle()
        val received = async { repository.events.take(1).toList() }
        advanceUntilIdle()
        eventsFlow.emit(eventImpact)
        eventsFlow.emit(eventImpact)   // mismo bootCount:eventId
        advanceUntilIdle()
        val events = received.await()
        assertEquals(1, events.size)
        assertEquals("57:3", events[0].deduplicationKey)
    }

    @Test
    fun `conecta y lee info y config`() = testScope.runTest {
        advanceUntilIdle()
        coEvery { manager.connectToDevice(any()) } coAnswers { libraryState.value = ConnectionState.Ready }
        coEvery { manager.readDeviceInfo() } returns deviceInfoV1
        coEvery { manager.readConfig() } returns configDefault
        repository.connect("AA:BB:CC:DD:EE:FF")
        advanceUntilIdle()
        assertEquals(DeviceConnectionState.READY, repository.connectionState.value)
        assertEquals("00:11:22:33:44:55", repository.deviceInfo.value?.deviceId)
        assertEquals(57, repository.deviceInfo.value?.bootCount)
        assertEquals(3500, repository.config.value?.impactThresholdMg)
    }

    @Test
    fun `marca firmware incompatible si el protocolo difiere`() = testScope.runTest {
        advanceUntilIdle()
        coEvery { manager.connectToDevice(any()) } just Runs
        coEvery { manager.readDeviceInfo() } returns deviceInfoV2
        coEvery { manager.readConfig() } returns configDefault
        repository.connect("AA:BB:CC:DD:EE:FF")
        assertEquals(
            DeviceConnectionState.INCOMPATIBLE_FIRMWARE,
            repository.connectionState.value
        )
    }

    @Test
    fun `escribe config validada y confirma por relectura`() = testScope.runTest {
        advanceUntilIdle()
        coEvery { manager.writeConfig(any()) } just Runs
        coEvery { manager.readConfig() } returns configDefault
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
        repository.writeConfig(packet)
        coVerify { manager.writeConfig(match { it.contentEquals(encodeConfig(packet)) }) }
        assertEquals(0, repository.config.value?.configRevision)
    }

    @Test
    fun `rechaza config fuera de rango sin escribir`() = testScope.runTest {
        advanceUntilIdle()
        coEvery { manager.writeConfig(any()) } just Runs
        val invalid = DeviceConfigPacket(
            tiltDetectionEnabled = true,
            impactThresholdMg = 6001,   // fuera de 2000..6000
            gyroThresholdDps = 300,
            tiltThresholdDeg = 65,
            tiltHoldMs = 2000,
            pitchOffsetCdeg = 0,
            rollOffsetCdeg = 0,
            configRevision = 0
        )
        val error = runCatching { repository.writeConfig(invalid) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        coVerify(exactly = 0) { manager.writeConfig(any()) }
    }

    @Test
    fun `envia comando y espera su respuesta`() = testScope.runTest {
        runCurrent()
        coEvery { manager.sendControlPoint(any()) } just Runs
        val pending = async { repository.sendCommand(BleProtocol.OP_ARM) }
        runCurrent()
        // Respuesta OK al primer comando (requestId 0)
        controlFlow.emit(
            bytesOf(
                BleProtocol.OP_ARM.toByte(), 0x00, BleProtocol.RESULT_OK.toByte()
            )
        )
        runCurrent()
        val response = pending.await()
        assertEquals(BleProtocol.OP_ARM, response.opcode)
        assertEquals(0, response.requestId)
        assertEquals(BleProtocol.RESULT_OK, response.result)
        coVerify {
            manager.sendControlPoint(match { it.contentEquals(bytesOf(0x02, 0x00)) })
        }
    }

    @Test
    fun `descarta la respuesta de un comando anterior`() = testScope.runTest {
        runCurrent()
        coEvery { manager.sendControlPoint(any()) } just Runs
        // Resto de un comando previo con timeout (requestId 99)
        controlFlow.emit(bytesOf(0x02, 99, 0x00))
        runCurrent()
        val pending = async { repository.sendCommand(BleProtocol.OP_DISARM) }
        runCurrent()
        controlFlow.emit(bytesOf(0x03, 0x00, 0x00))   // requestId 0: DISARM OK
        runCurrent()
        assertEquals(BleProtocol.RESULT_OK, pending.await().result)
    }
}

private const val BLE_MANAGER_EXT = "no.nordicsemi.android.ble.ktx.BleManagerExtKt"
