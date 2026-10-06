package com.motocrashguardian.data.incidents

import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.Trace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    suspend fun deleteIncident(id: UUID) {
        database.incidentDao().deleteIncidentAndTrace(id.toString())
    }

    suspend fun deleteTracesOlderThan(cutoff: Instant): Int =
        database.incidentDao().deleteTracesBefore(cutoff.toEpochMilli())

    private companion object {
        const val MaxRetainedIncidents = 100
    }
}
