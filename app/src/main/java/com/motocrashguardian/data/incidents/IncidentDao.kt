package com.motocrashguardian.data.incidents

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class IncidentDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertIncidentRow(incident: IncidentEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertTraceRow(trace: IncidentTraceEntity)

    @Query("SELECT * FROM incidents WHERE id = :id")
    abstract fun observeIncident(id: String): Flow<IncidentEntity?>

    @Query("SELECT * FROM incidents ORDER BY detected_at DESC, id DESC")
    abstract fun observeIncidents(): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE id = :id")
    abstract suspend fun getIncident(id: String): IncidentEntity?

    @Query("SELECT * FROM incidents WHERE device_event_key = :eventKey")
    abstract suspend fun getIncidentByDeviceEventKey(eventKey: String): IncidentEntity?

    @Query(
        "SELECT * FROM incidents WHERE status = 'ACTIVE' ORDER BY detected_at DESC, id DESC LIMIT 1"
    )
    abstract suspend fun getLatestActiveIncident(): IncidentEntity?


    @Query(
        """
        SELECT * FROM incidents
        WHERE sync_state = 'PENDING' AND status != 'ACTIVE'
        ORDER BY detected_at ASC, id ASC
        LIMIT :limit
        """
    )
    abstract suspend fun getPendingSyncIncidents(limit: Int): List<IncidentEntity>

    @Query(
        """
        UPDATE incidents
        SET sync_state = :state, sync_attempts = sync_attempts + :attemptDelta
        WHERE id = :id
        """
    )
    abstract suspend fun updateIncidentSync(id: String, state: String, attemptDelta: Int)

    @Query("UPDATE incident_traces SET sync_state = :state WHERE incident_id = :incidentId")
    abstract suspend fun updateTraceSync(incidentId: String, state: String)

    @Query("UPDATE incidents SET sync_state = 'PENDING' WHERE sync_state = 'SYNCED'")
    protected abstract suspend fun resetSyncedIncidents()

    @Query("UPDATE incident_traces SET sync_state = 'PENDING' WHERE sync_state = 'SYNCED'")
    protected abstract suspend fun resetSyncedTraces()

    @Transaction
    open suspend fun resetSyncedToPending() {
        resetSyncedIncidents()
        resetSyncedTraces()
    }

    @Query(
        """
        SELECT i.* FROM incidents i
        LEFT JOIN incident_traces t ON t.incident_id = i.id
        WHERE t.incident_id IS NULL
          AND i.device_event_key IS NOT NULL
          AND i.status != 'ACTIVE'
          AND i.trigger_type != 'TEST'
        ORDER BY i.detected_at DESC, i.id DESC
        LIMIT :limit
        """
    )
    abstract suspend fun getIncidentsMissingTrace(limit: Int): List<IncidentEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertTraceIfAbsent(trace: IncidentTraceEntity): Long

    @Query("UPDATE incidents SET sync_state = 'PENDING' WHERE id = :id AND sync_state = 'SYNCED'")
    protected abstract suspend fun reopenSyncedIncident(id: String)

    /**
     * Agrega la traza a un incidente ya guardado. Si el incidente ya se habia respaldado sin
     * ella, vuelve a pendiente para que la sincronizacion suba tambien la traza.
     * Devuelve `false` si el incidente ya tenia traza (no se sobrescribe).
     */
    @Transaction
    open suspend fun attachTrace(trace: IncidentTraceEntity): Boolean {
        if (insertTraceIfAbsent(trace) == -1L) return false
        reopenSyncedIncident(trace.incidentId)
        return true
    }
    @Update
    abstract suspend fun updateIncidentRow(incident: IncidentEntity): Int

    @Query("SELECT * FROM incident_traces WHERE incident_id = :incidentId")
    abstract suspend fun getTrace(incidentId: String): IncidentTraceEntity?

    @Query("DELETE FROM incidents WHERE id IN (:ids)")
    protected abstract suspend fun deleteIncidents(ids: List<String>)

    @Query(
        """
        SELECT id FROM incidents
        ORDER BY detected_at DESC, id DESC
        LIMIT -1 OFFSET :retainedCount
        """
    )
    protected abstract suspend fun findIncidentsBeyondRetention(retainedCount: Int): List<String>

    @Query(
        """
        DELETE FROM incident_traces
        WHERE incident_id IN (
            SELECT id FROM incidents WHERE detected_at < :cutoffEpochMillis
        )
        """
    )
    abstract suspend fun deleteTracesBefore(cutoffEpochMillis: Long): Int

    @Query("SELECT COUNT(*) FROM incidents")
    abstract suspend fun countIncidents(): Int

    @Query("SELECT COUNT(*) FROM incident_traces")
    abstract suspend fun countTraces(): Int

    @Transaction
    open suspend fun insertIncidentWithTraceAndRetention(
        incident: IncidentEntity,
        trace: IncidentTraceEntity?,
        retainedCount: Int
    ): Boolean {
        require(retainedCount > 0)
        val rowId = insertIncidentRow(incident)
        if (rowId == -1L) return false

        if (trace != null) insertTraceRow(trace)
        val expiredIncidentIds = findIncidentsBeyondRetention(retainedCount)
        if (expiredIncidentIds.isNotEmpty()) deleteIncidents(expiredIncidentIds)
        return true
    }

    @Transaction
    open suspend fun deleteIncidentAndTrace(incidentId: String) {
        deleteIncidents(listOf(incidentId))
    }
}
