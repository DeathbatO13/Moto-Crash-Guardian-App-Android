package com.motocrashguardian.core.model

import java.time.Instant
import java.util.UUID

data class Telemetry(
    val protocolVersion: Int,
    val seq: Int,
    val state: DeviceState,
    val flags: Int,
    val pitchCdeg: Int,
    val rollCdeg: Int,
    val accelMagMg: Int,
    val peakAccelMg: Int,
    val peakGyroDps: Int,
    val batteryPercent: Int?,
    val receivedAt: Instant
)

data class DeviceEvent(
    val protocolVersion: Int,
    val type: DeviceEventType,
    val eventId: Int,
    val bootCount: Int,
    val uptimeMs: Long,
    val peakAccelMg: Int,
    val peakGyroDps: Int,
    val pitchCdeg: Int,
    val rollCdeg: Int,
    val flags: Int,
    val faultCode: Int?,
    val receivedAt: Instant
) {
    val deduplicationKey: String
        get() = "$bootCount:$eventId"
}

data class DetectionConfig(
    val impactThresholdMg: Int = 3_500,
    val gyroThresholdDps: Int = 300,
    val tiltThresholdDeg: Int = 65,
    val tiltHoldMs: Int = 2_000,
    val tiltDetectionEnabled: Boolean = true,
    val severeImpactThresholdMg: Int = 8_000,
    val confirmWindowMs: Int = 5_000,
    val stillnessToleranceMg: Int = 200,
    val pitchOffsetCdeg: Int = 0,
    val rollOffsetCdeg: Int = 0
) {
    init {
        require(impactThresholdMg in 2_000..6_000)
        require(gyroThresholdDps in 150..1_000)
        require(tiltThresholdDeg in 50..80)
        require(tiltHoldMs in 500..5_000)
        require(severeImpactThresholdMg in 6_000..16_000)
        require(confirmWindowMs in 3_000..10_000)
        require(stillnessToleranceMg in 100..400)
        require(pitchOffsetCdeg in -9_000..9_000)
        require(rollOffsetCdeg in -9_000..9_000)
    }
}

data class EmergencyContact(
    val role: ContactRole,
    val name: String,
    val phoneE164: String
)

data class DeviceInfo(
    val protocolVersion: Int,
    val firmwareMajor: Int,
    val firmwareMinor: Int,
    val firmwarePatch: Int,
    val hardwareRevision: Int,
    val capabilities: Int,
    val bootCount: Int,
    val deviceId: String
) {
    val firmwareVersion: String
        get() = "$firmwareMajor.$firmwareMinor.$firmwarePatch"
}

data class AppSettings(
    val riderName: String = "",
    val primaryContact: EmergencyContact? = null,
    val secondaryContact: EmergencyContact? = null,
    val detection: DetectionConfig = DetectionConfig(),
    val countdownSeconds: Int = 20,
    val demoMode: Boolean = false,
    val drillPlaceCall: Boolean = false,
    val pairedDeviceAddress: String? = null,
    val pairedDeviceInfo: DeviceInfo? = null,
    val tripActive: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val consentAcceptedAt: Instant? = null,
    val configDirty: Boolean = false,
    val contactsDirty: Boolean = false
) {
    init {
        require(countdownSeconds in 10..60)
    }
}

data class Incident(
    val id: UUID = UUID.randomUUID(),
    val type: IncidentType,
    val triggerType: TriggerType,
    val status: IncidentStatus,
    val deviceEventKey: String? = null,
    val detectedAt: Instant,
    val resolvedAt: Instant? = null,
    val countdownDeadline: Instant? = null,
    val peakAccelMg: Int? = null,
    val peakGyroDps: Int? = null,
    val pitchCdeg: Int? = null,
    val rollCdeg: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracyMeters: Float? = null,
    val locationSource: LocationSource = LocationSource.NONE,
    val locationFixAt: Instant? = null,
    val primarySmsStatus: SmsStatus = SmsStatus.NOT_ATTEMPTED,
    val secondarySmsStatus: SmsStatus = SmsStatus.NOT_ATTEMPTED,
    val callStatus: CallStatus = CallStatus.NOT_ATTEMPTED,
    val firmwareVersion: String? = null,
    val syncState: SyncState = SyncState.PENDING,
    val syncAttempts: Int = 0
) {
    init {
        require(syncAttempts >= 0)
    }
}

data class Trace(
    val incidentId: UUID,
    val sampleRateHz: Int,
    val preTriggerSamples: Int,
    val totalSamples: Int,
    val accelLsbPerG: Int,
    val gyroLsbPerDpsX10: Int,
    val samples: ByteArray,
    val syncState: SyncState = SyncState.PENDING
) {
    init {
        require(sampleRateHz > 0)
        require(preTriggerSamples in 0..totalSamples)
        require(totalSamples >= 0)
        require(accelLsbPerG > 0)
        require(gyroLsbPerDpsX10 > 0)
        require(samples.size.toLong() == totalSamples.toLong() * 12)
    }
}
