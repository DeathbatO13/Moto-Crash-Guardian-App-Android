package com.motocrashguardian.data.sync

import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.SyncState
import com.motocrashguardian.core.model.Trace
import com.motocrashguardian.data.incidents.IncidentRepository
import com.motocrashguardian.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject

/** [SyncLocalStore] sobre Room (incidentes y trazas) y DataStore (ajustes). */
class RoomSyncLocalStore @Inject constructor(
    private val incidents: IncidentRepository,
    private val settings: SettingsRepository
) : SyncLocalStore {

    override suspend fun settings(): AppSettings = settings.settings.first()

    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) =
        settings.updateSettings(transform)

    override suspend fun pendingIncidents(limit: Int): List<Incident> =
        incidents.pendingSyncIncidents(limit)

    override suspend fun trace(incidentId: UUID): Trace? = incidents.getTrace(incidentId)

    override suspend fun markIncident(id: UUID, state: SyncState, countAttempt: Boolean) =
        incidents.markIncidentSync(id, state, countAttempt)

    override suspend fun markTrace(incidentId: UUID, state: SyncState) =
        incidents.markTraceSync(incidentId, state)

    override suspend fun resetSyncedToPending() = incidents.resetSyncedToPending()
}
