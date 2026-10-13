package com.motocrashguardian.data.remote

import kotlinx.serialization.Serializable

/**
 * Modelos del contrato REST del backend (`/api/v1`). Las fechas viajan como ISO-8601 en texto.
 * Los campos opcionales con valor `null` se omiten al serializar (ver `RemoteModule`).
 */

@Serializable
data class RegisterInstallationRequest(val appVersion: String, val consentAcceptedAt: String)

@Serializable
data class RegisterInstallationResponse(val installationId: String, val token: String)

@Serializable
data class UserConfigRequest(
    val riderName: String,
    val impactThresholdMg: Int,
    val severeImpactThresholdMg: Int,
    val gyroThresholdDps: Int,
    val tiltThresholdDeg: Int,
    val tiltHoldMs: Int,
    val tiltDetectionEnabled: Boolean,
    val confirmWindowMs: Int,
    val stillnessToleranceMg: Int,
    val countdownSeconds: Int,
    val pitchOffsetCdeg: Int,
    val rollOffsetCdeg: Int
)

@Serializable
data class EmergencyContactDto(val role: String, val name: String, val phoneE164: String)

@Serializable
data class UpdateContactsRequest(val contacts: List<EmergencyContactDto>)

@Serializable
data class IncidentLocationDto(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyM: Float? = null,
    val source: String,
    val fixAt: String? = null
)

@Serializable
data class IncidentDispatchDto(
    val smsPrimary: String,
    val smsSecondary: String,
    val call: String
)

@Serializable
data class IncidentUpsertRequest(
    val type: String,
    val triggerType: String,
    val status: String,
    val deviceEventKey: String? = null,
    val detectedAt: String,
    val resolvedAt: String? = null,
    val peakAccelMg: Int? = null,
    val peakGyroDps: Int? = null,
    val pitchCdeg: Int? = null,
    val rollCdeg: Int? = null,
    val location: IncidentLocationDto? = null,
    val dispatch: IncidentDispatchDto,
    val firmwareVersion: String? = null,
    val appVersion: String? = null
)

@Serializable
data class IncidentTraceRequest(
    val sampleRateHz: Int,
    val preTriggerSamples: Int,
    val totalSamples: Int,
    val accelLsbPerG: Int,
    val gyroLsbPerDpsX10: Int,
    val samplesBase64: String
)
