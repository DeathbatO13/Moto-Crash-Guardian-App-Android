package com.motocrashguardian.ble

import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.IncidentStatus
import com.motocrashguardian.core.model.IncidentType
import com.motocrashguardian.core.model.TriggerType
import com.motocrashguardian.detection.GuardianState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TraceDownloadCoordinatorTest {

    private val now = Instant.parse("2026-10-19T15:30:00Z")
    private val connection = MutableStateFlow(DeviceConnectionState.READY)
    private val guardian = MutableStateFlow<GuardianState>(GuardianState.Monitoring)
    private val incidents = MutableStateFlow<List<Incident>>(emptyList())
    private val fetcher = mockk<TraceFetcher>(relaxed = true)

    private fun incident(
        key: String? = "57:1",
        status: IncidentStatus = IncidentStatus.DISPATCHED
    ) = Incident(
        type = IncidentType.REAL,
        triggerType = TriggerType.IMPACT,
        status = status,
        deviceEventKey = key,
        detectedAt = now
    )

    private fun coordinator() = TraceDownloadCoordinator(connection, guardian, incidents, fetcher)

    // --- Cuando se permite descargar ---

    @Test
    fun `no compite con una alerta en curso`() {
        val incident = incident(status = IncidentStatus.ACTIVE)
        val blocked = listOf(
            GuardianState.Countdown(incident, now),
            GuardianState.Dispatching(incident)
        )
        val allowed = listOf(
            GuardianState.Idle,
            GuardianState.Monitoring,
            GuardianState.Dispatched(incident),
            GuardianState.ConnectionLost
        )

        blocked.forEach { assertFalse(it.allowsTraceDownload(), it.toString()) }
        allowed.forEach { assertTrue(it.allowsTraceDownload(), it.toString()) }
    }

    @Test
    fun `solo cuentan los incidentes del modulo ya cerrados`() {
        val closed = incident("57:1")
        val list = listOf(
            closed,
            incident(null),
            incident("57:2", IncidentStatus.ACTIVE)
        )

        assertEquals(setOf(closed.id), traceCandidateIds(list))
    }

    // --- Coordinador ---

    @Test
    fun `descarga con el modulo listo y sin alerta`() = runTest {
        coordinator().start(backgroundScope)
        runCurrent()

        coVerify(exactly = 1) { fetcher.fetchPending() }
    }

    @Test
    fun `no descarga sin conexion`() = runTest {
        connection.value = DeviceConnectionState.DISCONNECTED
        coordinator().start(backgroundScope)
        runCurrent()

        coVerify(exactly = 0) { fetcher.fetchPending() }
    }

    @Test
    fun `no descarga con una alerta en curso`() = runTest {
        guardian.value = GuardianState.Countdown(incident(status = IncidentStatus.ACTIVE), now)
        coordinator().start(backgroundScope)
        runCurrent()

        coVerify(exactly = 0) { fetcher.fetchPending() }
    }

    @Test
    fun `una alerta interrumpe la descarga en curso`() = runTest {
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        coEvery { fetcher.fetchPending() } coAnswers {
            started.complete(Unit)
            try {
                kotlinx.coroutines.awaitCancellation()
            } catch (error: kotlinx.coroutines.CancellationException) {
                cancelled = true
                throw error
            }
        }
        coordinator().start(backgroundScope)
        runCurrent()
        assertTrue(started.isCompleted)

        guardian.value = GuardianState.Countdown(incident(status = IncidentStatus.ACTIVE), now)
        runCurrent()

        assertTrue(cancelled)
    }

    @Test
    fun `al terminar la alerta retoma la descarga`() = runTest {
        coordinator().start(backgroundScope)
        runCurrent()
        guardian.value = GuardianState.Countdown(incident(status = IncidentStatus.ACTIVE), now)
        runCurrent()
        coVerify(exactly = 1) { fetcher.fetchPending() }

        guardian.value = GuardianState.Monitoring
        runCurrent()

        coVerify(exactly = 2) { fetcher.fetchPending() }
    }

    @Test
    fun `un incidente nuevo del modulo dispara otra descarga`() = runTest {
        coordinator().start(backgroundScope)
        runCurrent()

        incidents.value = listOf(incident("57:1"))
        runCurrent()

        coVerify(exactly = 2) { fetcher.fetchPending() }
    }

    @Test
    fun `los cambios que no afectan a las trazas no repiten la descarga`() = runTest {
        val closed = incident("57:1")
        incidents.value = listOf(closed)
        coordinator().start(backgroundScope)
        runCurrent()

        // Mismo incidente con otro estado de respaldo: no hay nada nuevo que descargar.
        incidents.value = listOf(closed.copy(syncAttempts = 1))
        runCurrent()

        coVerify(exactly = 1) { fetcher.fetchPending() }
    }

    @Test
    fun `al perder la conexion olvida lo intentado para reintentar en la siguiente`() = runTest {
        coordinator().start(backgroundScope)
        runCurrent()

        connection.value = DeviceConnectionState.DISCONNECTED
        runCurrent()
        verify(exactly = 1) { fetcher.resetAttempts() }

        connection.value = DeviceConnectionState.READY
        runCurrent()
        coVerify(exactly = 2) { fetcher.fetchPending() }
    }

    @Test
    fun `un fallo de la descarga no detiene al coordinador`() = runTest {
        var calls = 0
        coEvery { fetcher.fetchPending() } coAnswers {
            if (calls++ == 0) error("base de datos") else 0
        }
        coordinator().start(backgroundScope)
        runCurrent()

        incidents.value = listOf(incident("57:1"))
        runCurrent()

        assertEquals(2, calls)
    }
}
