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
