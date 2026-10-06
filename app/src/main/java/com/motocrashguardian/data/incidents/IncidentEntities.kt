package com.motocrashguardian.data.incidents

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.motocrashguardian.core.model.CallStatus
import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.IncidentStatus
import com.motocrashguardian.core.model.IncidentType
import com.motocrashguardian.core.model.LocationSource
import com.motocrashguardian.core.model.SmsStatus
import com.motocrashguardian.core.model.SyncState
import com.motocrashguardian.core.model.Trace
import com.motocrashguardian.core.model.TriggerType
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "incidents",
    indices = [
        Index(value = ["device_event_key"], unique = true),
        Index(value = ["detected_at"])
    ]
)
data class IncidentEntity(
    @PrimaryKey
    val id: String,
    val type: String,
    @ColumnInfo(name = "trigger_type")
    val triggerType: String,
    val status: String,
    @ColumnInfo(name = "device_event_key")
    val deviceEventKey: String?,
    @ColumnInfo(name = "detected_at")
    val detectedAtEpochMillis: Long,
    @ColumnInfo(name = "resolved_at")
    val resolvedAtEpochMillis: Long?,
    @ColumnInfo(name = "countdown_deadline")
    val countdownDeadlineEpochMillis: Long?,
    @ColumnInfo(name = "peak_accel_mg")
    val peakAccelMg: Int?,
    @ColumnInfo(name = "peak_gyro_dps")
    val peakGyroDps: Int?,
    @ColumnInfo(name = "pitch_cdeg")
    val pitchCdeg: Int?,
    @ColumnInfo(name = "roll_cdeg")
    val rollCdeg: Int?,
    val lat: Double?,
    val lng: Double?,
    @ColumnInfo(name = "location_accuracy_m")
    val locationAccuracyMeters: Float?,
    @ColumnInfo(name = "location_source")
    val locationSource: String,
    @ColumnInfo(name = "location_fix_at")
    val locationFixAtEpochMillis: Long?,
    @ColumnInfo(name = "sms_primary")
    val primarySmsStatus: String,
    @ColumnInfo(name = "sms_secondary")
    val secondarySmsStatus: String,
    @ColumnInfo(name = "call_status")
    val callStatus: String,
    @ColumnInfo(name = "firmware_version")
    val firmwareVersion: String?,
    @ColumnInfo(name = "sync_state")
    val syncState: String,
    @ColumnInfo(name = "sync_attempts")
    val syncAttempts: Int
)

@Entity(
    tableName = "incident_traces",
    foreignKeys = [
        ForeignKey(
            entity = IncidentEntity::class,
            parentColumns = ["id"],
            childColumns = ["incident_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class IncidentTraceEntity(
    @PrimaryKey
    @ColumnInfo(name = "incident_id")
    val incidentId: String,
    @ColumnInfo(name = "sample_rate_hz")
    val sampleRateHz: Int,
    @ColumnInfo(name = "pre_trigger_samples")
    val preTriggerSamples: Int,
    @ColumnInfo(name = "total_samples")
    val totalSamples: Int,
    @ColumnInfo(name = "accel_lsb_per_g")
    val accelLsbPerG: Int,
    @ColumnInfo(name = "gyro_lsb_per_dps_x10")
    val gyroLsbPerDpsX10: Int,
    val samples: ByteArray,
    @ColumnInfo(name = "sync_state")
    val syncState: String
)

internal fun Incident.toEntity(): IncidentEntity = IncidentEntity(
    id = id.toString(),
    type = type.name,
    triggerType = triggerType.name,
    status = status.name,
    deviceEventKey = deviceEventKey,
    detectedAtEpochMillis = detectedAt.toEpochMilli(),
    resolvedAtEpochMillis = resolvedAt?.toEpochMilli(),
    countdownDeadlineEpochMillis = countdownDeadline?.toEpochMilli(),
    peakAccelMg = peakAccelMg,
    peakGyroDps = peakGyroDps,
    pitchCdeg = pitchCdeg,
    rollCdeg = rollCdeg,
    lat = latitude,
    lng = longitude,
    locationAccuracyMeters = locationAccuracyMeters,
    locationSource = locationSource.name,
    locationFixAtEpochMillis = locationFixAt?.toEpochMilli(),
    primarySmsStatus = primarySmsStatus.name,
    secondarySmsStatus = secondarySmsStatus.name,
    callStatus = callStatus.name,
    firmwareVersion = firmwareVersion,
    syncState = syncState.name,
    syncAttempts = syncAttempts
)

internal fun IncidentEntity.toDomain(): Incident = Incident(
    id = UUID.fromString(id),
    type = IncidentType.valueOf(type),
    triggerType = TriggerType.valueOf(triggerType),
    status = IncidentStatus.valueOf(status),
    deviceEventKey = deviceEventKey,
    detectedAt = Instant.ofEpochMilli(detectedAtEpochMillis),
    resolvedAt = resolvedAtEpochMillis?.let(Instant::ofEpochMilli),
    countdownDeadline = countdownDeadlineEpochMillis?.let(Instant::ofEpochMilli),
    peakAccelMg = peakAccelMg,
    peakGyroDps = peakGyroDps,
    pitchCdeg = pitchCdeg,
    rollCdeg = rollCdeg,
    latitude = lat,
    longitude = lng,
    locationAccuracyMeters = locationAccuracyMeters,
    locationSource = LocationSource.valueOf(locationSource),
    locationFixAt = locationFixAtEpochMillis?.let(Instant::ofEpochMilli),
    primarySmsStatus = SmsStatus.valueOf(primarySmsStatus),
    secondarySmsStatus = SmsStatus.valueOf(secondarySmsStatus),
    callStatus = CallStatus.valueOf(callStatus),
    firmwareVersion = firmwareVersion,
    syncState = SyncState.valueOf(syncState),
    syncAttempts = syncAttempts
)

internal fun Trace.toEntity(): IncidentTraceEntity = IncidentTraceEntity(
    incidentId = incidentId.toString(),
    sampleRateHz = sampleRateHz,
    preTriggerSamples = preTriggerSamples,
    totalSamples = totalSamples,
    accelLsbPerG = accelLsbPerG,
    gyroLsbPerDpsX10 = gyroLsbPerDpsX10,
    samples = samples,
    syncState = syncState.name
)

internal fun IncidentTraceEntity.toDomain(): Trace = Trace(
    incidentId = UUID.fromString(incidentId),
    sampleRateHz = sampleRateHz,
    preTriggerSamples = preTriggerSamples,
    totalSamples = totalSamples,
    accelLsbPerG = accelLsbPerG,
    gyroLsbPerDpsX10 = gyroLsbPerDpsX10,
    samples = samples,
    syncState = SyncState.valueOf(syncState)
)
