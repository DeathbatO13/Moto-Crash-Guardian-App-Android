package com.motocrashguardian.ble

import com.motocrashguardian.ble.protocol.TraceMetadata
import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.IncidentStatus
import com.motocrashguardian.core.model.IncidentType
import com.motocrashguardian.core.model.Trace
import com.motocrashguardian.core.model.TriggerType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class TraceFetcherTest {

    private val now = Instant.parse("2026-10-19T15:30:00Z")
    private var bootCount: Int? = 57
    private val requestedEvents = mutableListOf<Int>()
    private var sourceResult: () -> TraceResult = { downloaded() }
    private val attached = mutableListOf<Trace>()
    private var incidents = mutableListOf<Incident>()
    private var requestedLimit: Int? = null
    private var alreadyHasTrace = false

    private val source = TraceSource { eventId ->
        requestedEvents += eventId
        sourceResult()
    }

    private val store = object : TraceStore {
        override suspend fun incidentsMissingTrace(limit: Int): List<Incident> {
            requestedLimit = limit
            return incidents.take(limit)
        }

        override suspend fun attachTrace(trace: Trace): Boolean {
            if (alreadyHasTrace) return false
            attached += trace
            return true
        }
    }

    // "Ahora" es 10 s despues del incidente: la traza del modulo ya esta congelada.
    private var clockNow: Instant = now.plusSeconds(10)
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?) = this
        override fun instant(): Instant = clockNow
    }

    private fun fetcher(maxPerRun: Int = 5) = TraceFetcher(
        source,
        store,
        currentBootCount = { bootCount },
        maxPerRun = maxPerRun,
        clock = clock
    )

    private fun incident(
        key: String?,
        offset: Long = 0,
        status: IncidentStatus = IncidentStatus.DISPATCHED,
        trigger: TriggerType = TriggerType.IMPACT
    ) =
        Incident(
            type = IncidentType.REAL,
            triggerType = trigger,
            status = status,
            deviceEventKey = key,
            detectedAt = now.plusSeconds(offset)
        )

    private fun downloaded(total: Int = 2) = TraceResult.Downloaded(
        DownloadedTrace(
            TraceMetadata(
                sampleRateHz = 200,
                preTriggerSamples = 1,
                totalSamples = total,
                accelLsbPerG = 2048,
                gyroLsbPerDpsX10 = 164,
                chunkCount = 1,
                samplesPerChunk = 19
            ),
            ByteArray(total * 12) { it.toByte() }
        )
    )

    // --- Clave del evento ---

    @Test
    fun `interpreta la clave bootCount dos puntos eventId`() {
        assertEquals(DeviceEventRef(57, 3), parseDeviceEventKey("57:3"))
        assertEquals(DeviceEventRef(65535, 65535), parseDeviceEventKey("65535:65535"))
        assertEquals(DeviceEventRef(0, 0), parseDeviceEventKey("0:0"))
    }

    @Test
    fun `rechaza claves mal formadas o fuera de 16 bits`() {
        listOf("", "57", "57:3:1", "a:3", "57:b", "-1:3", "57:-1", "65536:1", "1:65536", ":").forEach {
            assertNull(parseDeviceEventKey(it), "deberia rechazar '$it'")
        }
    }

    // --- Que se descarga ---

    @Test
    fun `guarda la traza de un incidente del mismo arranque del modulo`() = runTest {
        val target = incident("57:3")
        incidents += target

        val saved = fetcher().fetchPending()

        assertEquals(1, saved)
        assertEquals(listOf(3), requestedEvents)
        val trace = attached.single()
        assertEquals(target.id, trace.incidentId)
        assertEquals(200, trace.sampleRateHz)
        assertEquals(1, trace.preTriggerSamples)
        assertEquals(2, trace.totalSamples)
        assertEquals(24, trace.samples.size)
    }

    @Test
    fun `no pide trazas de otro arranque porque el eventId podria repetirse`() = runTest {
        incidents += incident("56:3") // arranque anterior

        assertEquals(0, fetcher().fetchPending())

        assertTrue(requestedEvents.isEmpty())
    }

    @Test
    fun `sin modulo conectado no hace nada`() = runTest {
        bootCount = null
        incidents += incident("57:3")

        assertEquals(0, fetcher().fetchPending())

        assertTrue(requestedEvents.isEmpty())
    }

    @Test
    fun `ignora incidentes sin clave o con clave invalida`() = runTest {
        incidents += listOf(incident(null), incident("basura"), incident("57:4"))

        fetcher().fetchPending()

        assertEquals(listOf(4), requestedEvents)
    }

    @Test
    fun `respeta el maximo por corrida al consultar la base`() = runTest {
        incidents += List(10) { incident("57:$it", offset = it.toLong()) }

        fetcher(maxPerRun = 3).fetchPending()

        assertEquals(3, requestedLimit)
        assertEquals(3, requestedEvents.size)
    }

    // --- Intentos ---

    @Test
    fun `cada evento se intenta una sola vez por conexion`() = runTest {
        incidents += incident("57:3")
        val fetcher = fetcher()

        fetcher.fetchPending()
        fetcher.fetchPending()

        assertEquals(listOf(3), requestedEvents)
    }

    @Test
    fun `tras reiniciar los intentos vuelve a probar`() = runTest {
        incidents += incident("57:3")
        sourceResult = { TraceResult.Failed("sin respuesta") }
        val fetcher = fetcher()
        fetcher.fetchPending()

        fetcher.resetAttempts()
        fetcher.fetchPending()

        assertEquals(listOf(3, 3), requestedEvents)
    }

    @Test
    fun `una traza no disponible o fallida no se guarda pero cuenta como intentada`() = runTest {
        incidents += listOf(incident("57:1"), incident("57:2"))
        var call = 0
        sourceResult = { if (call++ == 0) TraceResult.NotAvailable else TraceResult.Failed("x") }
        val fetcher = fetcher()

        assertEquals(0, fetcher.fetchPending())
        fetcher.fetchPending()

        assertTrue(attached.isEmpty())
        assertEquals(listOf(1, 2), requestedEvents)
    }

    @Test
    fun `una descarga cancelada no se marca como intentada y se reanuda`() = runTest {
        incidents += incident("57:3")
        sourceResult = { throw CancellationException("empezo una alerta") }
        val fetcher = fetcher()

        var cancelled = false
        try {
            fetcher.fetchPending()
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        sourceResult = { downloaded() }

        assertEquals(1, fetcher.fetchPending())
        assertEquals(listOf(3, 3), requestedEvents)
    }

    @Test
    fun `si la base ya tenia traza no cuenta como guardada`() = runTest {
        incidents += incident("57:3")
        alreadyHasTrace = true

        assertEquals(0, fetcher().fetchPending())
    }

    // --- Con el firmware real (TRIGGER_TEST_EVENT y fin de captura) ---

    @Test
    fun `los eventos TEST no se piden porque el firmware los emite sin traza`() = runTest {
        incidents += incident("57:3", trigger = TriggerType.TEST)

        assertEquals(0, fetcher().fetchPending())

        assertTrue(requestedEvents.isEmpty())
    }

    @Test
    fun `un incidente recien creado espera a que el modulo termine de capturar`() = runTest {
        clockNow = now.plusSeconds(1) // el incidente tiene 1 s
        incidents += incident("57:3")

        fetcher().fetchPending()

        // Faltan 4 s para llegar a los 5 s de edad minima (3 s de captura + margen).
        assertEquals(4_000, currentTime)
        assertEquals(listOf(3), requestedEvents)
    }

    @Test
    fun `un incidente con edad suficiente se pide sin esperar`() = runTest {
        clockNow = now.plusSeconds(5)
        incidents += incident("57:3")

        fetcher().fetchPending()

        assertEquals(0, currentTime)
    }

    @Test
    fun `un reloj atrasado no provoca esperas mayores a la edad minima`() = runTest {
        clockNow = now.minusSeconds(3600) // el incidente parece del futuro
        incidents += incident("57:3")

        fetcher().fetchPending()

        assertEquals(5_000, currentTime)
    }

    @Test
    fun `cancelar durante la espera no marca el evento como intentado`() = runTest {
        clockNow = now.plusSeconds(1)
        incidents += incident("57:3")
        val fetcher = fetcher()
        val job = backgroundScope.launch { fetcher.fetchPending() }
        runCurrent()
        advanceTimeBy(2_000)

        job.cancel() // empezo una alerta
        runCurrent()
        assertTrue(requestedEvents.isEmpty())

        clockNow = now.plusSeconds(10)
        assertEquals(1, fetcher.fetchPending())
        assertEquals(listOf(3), requestedEvents)
    }

    @Test
    fun `descarga varias en orden y cuenta las guardadas`() = runTest {
        incidents += listOf(incident("57:5", 20), incident("57:4", 10), incident("57:3", 0))

        assertEquals(3, fetcher().fetchPending())

        assertEquals(listOf(5, 4, 3), requestedEvents)
    }
}
