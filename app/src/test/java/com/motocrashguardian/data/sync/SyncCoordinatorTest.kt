package com.motocrashguardian.data.sync

import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.IncidentStatus
import com.motocrashguardian.core.model.IncidentType
import com.motocrashguardian.core.model.SyncState
import com.motocrashguardian.core.model.TriggerType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SyncCoordinatorTest {

    private val now = Instant.parse("2026-10-19T15:30:00Z")
    private val consenting = AppSettings(consentAcceptedAt = now)

    private fun incident(
        status: IncidentStatus = IncidentStatus.DISPATCHED,
        sync: SyncState = SyncState.PENDING
    ) = Incident(
        type = IncidentType.REAL,
        triggerType = TriggerType.IMPACT,
        status = status,
        detectedAt = now,
        syncState = sync
    )

    // --- syncSignature ---

    @Test
    fun `sin consentimiento nunca hay nada que sincronizar`() {
        assertNull(syncSignature(listOf(incident()), AppSettings(configDirty = true)))
    }

    @Test
    fun `sin pendientes ni cambios no hay firma`() {
        assertNull(syncSignature(listOf(incident(sync = SyncState.SYNCED)), consenting))
        assertNull(syncSignature(listOf(incident(sync = SyncState.FAILED_PERMANENT)), consenting))
        assertNull(syncSignature(listOf(incident(status = IncidentStatus.ACTIVE)), consenting))
    }

    @Test
    fun `un incidente pendiente o un cambio de perfil generan firma`() {
        val pending = incident()

        assertEquals(
            SyncSignature(setOf(pending.id), configDirty = false, contactsDirty = false),
            syncSignature(listOf(pending), consenting)
        )
        assertEquals(
            SyncSignature(emptySet(), configDirty = true, contactsDirty = false),
            syncSignature(emptyList(), consenting.copy(configDirty = true))
        )
    }

    // --- Coordinador ---

    @Test
    fun `pide sincronizar una sola vez cuando aparece trabajo`() = runTest {
        val incidents = MutableStateFlow<List<Incident>>(emptyList())
        val settings = MutableStateFlow(consenting)
        var requests = 0
        SyncCoordinator(incidents, settings, { requests++ }, debounceMillis = 1_000)
            .start(backgroundScope)

        runCurrent()
        assertEquals(0, requests)

        incidents.value = listOf(incident())
        advanceTimeBy(1_100)
        runCurrent()

        assertEquals(1, requests)
    }

    @Test
    fun `cambios rapidos se agrupan en una sola peticion`() = runTest {
        val incidents = MutableStateFlow<List<Incident>>(emptyList())
        val settings = MutableStateFlow(consenting)
        var requests = 0
        SyncCoordinator(incidents, settings, { requests++ }, debounceMillis = 1_000)
            .start(backgroundScope)
        runCurrent()

        settings.value = consenting.copy(configDirty = true)
        advanceTimeBy(300)
        settings.value = consenting.copy(configDirty = true, contactsDirty = true)
        advanceTimeBy(1_100)
        runCurrent()

        assertEquals(1, requests)
    }

    @Test
    fun `no repite la peticion si el trabajo pendiente no cambio`() = runTest {
        val first = incident()
        val incidents = MutableStateFlow(listOf(first))
        val settings = MutableStateFlow(consenting)
        var requests = 0
        SyncCoordinator(incidents, settings, { requests++ }, debounceMillis = 1_000)
            .start(backgroundScope)
        advanceTimeBy(1_100)
        runCurrent()

        // Mismo incidente pendiente, otro dato sin relacion (p. ej. un ajuste).
        settings.value = consenting.copy(demoMode = true)
        advanceTimeBy(1_100)
        runCurrent()

        assertEquals(1, requests)
    }

    @Test
    fun `un incidente nuevo vuelve a pedir sincronizacion`() = runTest {
        val incidents = MutableStateFlow(listOf(incident()))
        val settings = MutableStateFlow(consenting)
        var requests = 0
        SyncCoordinator(incidents, settings, { requests++ }, debounceMillis = 1_000)
            .start(backgroundScope)
        advanceTimeBy(1_100)
        runCurrent()

        incidents.value = incidents.value + incident()
        advanceTimeBy(1_100)
        runCurrent()

        assertEquals(2, requests)
    }
}
