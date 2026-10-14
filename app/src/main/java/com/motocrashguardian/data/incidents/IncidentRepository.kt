package com.motocrashguardian.data.incidents

import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.SyncState
import com.motocrashguardian.core.model.Trace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class IncidentRepository @Inject constructor(
    private val database: GuardianDatabase
) {
    val incidents: Flow<List<Incident>> =
        database.incidentDao().observeIncidents().map { rows -> rows.map(IncidentEntity::toDomain) }

    fun observeIncident(id: UUID): Flow<Incident?> =
        database.incidentDao().observeIncident(id.toString()).map { it?.toDomain() }

    suspend fun getIncident(id: UUID): Incident? =
        database.incidentDao().getIncident(id.toString())?.toDomain()

    suspend fun getIncidentByDeviceEventKey(eventKey: String): Incident? =
        database.incidentDao().getIncidentByDeviceEventKey(eventKey)?.toDomain()

    suspend fun getLatestActiveIncident(): Incident? =
        database.incidentDao().getLatestActiveIncident()?.toDomain()

    suspend fun updateIncident(incident: Incident): Boolean =
        database.incidentDao().updateIncidentRow(incident.toEntity()) == 1

    suspend fun getTrace(incidentId: UUID): Trace? =
        database.incidentDao().getTrace(incidentId.toString())?.toDomain()

    suspend fun recordIncident(incident: Incident, trace: Trace? = null): Boolean {
        require(trace == null || trace.incidentId == incident.id)
        return database.incidentDao().insertIncidentWithTraceAndRetention(
            incident = incident.toEntity(),
            trace = trace?.toEntity(),
            retainedCount = MaxRetainedIncidents
        )
    }

    /**
     * Incidentes del modulo (con clave de evento) que aun no tienen caja negra, los mas nuevos
     * primero. Los eventos TEST se excluyen: el firmware los emite sin traza.
     */
    suspend fun incidentsMissingTrace(limit: Int): List<Incident> =
        database.incidentDao().getIncidentsMissingTrace(limit).map(IncidentEntity::toDomain)

    /** Guarda la traza de un incidente existente; `false` si ya tenia una (no se sobrescribe). */
    suspend fun attachTrace(trace: Trace): Boolean =
        database.incidentDao().attachTrace(trace.toEntity())

    suspend fun pendingSyncIncidents(limit: Int): List<Incident> =
        database.incidentDao().getPendingSyncIncidents(limit).map(IncidentEntity::toDomain)

    suspend fun markIncidentSync(id: UUID, state: SyncState, countAttempt: Boolean) {
        database.incidentDao().updateIncidentSync(id.toString(), state.name, if (countAttempt) 1 else 0)
    }

    suspend fun markTraceSync(incidentId: UUID, state: SyncState) {
        database.incidentDao().updateTraceSync(incidentId.toString(), state.name)
    }

    suspend fun resetSyncedToPending() {
        database.incidentDao().resetSyncedToPending()
    }

    suspend fun deleteIncident(id: UUID) {
        database.incidentDao().deleteIncidentAndTrace(id.toString())
    }

    suspend fun deleteTracesOlderThan(cutoff: Instant): Int =
        database.incidentDao().deleteTracesBefore(cutoff.toEpochMilli())

    /** Borra las trazas de mas de 90 dias; los incidentes se conservan (hasta 100). */
    suspend fun purgeExpiredTraces(now: Instant = Instant.now()): Int =
        deleteTracesOlderThan(now.minus(Duration.ofDays(TraceRetentionDays)))

    private companion object {
        const val MaxRetainedIncidents = 100
        const val TraceRetentionDays = 90L
    }
}
