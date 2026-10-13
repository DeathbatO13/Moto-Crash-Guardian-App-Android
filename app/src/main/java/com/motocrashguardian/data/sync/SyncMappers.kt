package com.motocrashguardian.data.sync

import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.core.model.EmergencyContact
import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.LocationSource
import com.motocrashguardian.core.model.Trace
import com.motocrashguardian.data.remote.EmergencyContactDto
import com.motocrashguardian.data.remote.IncidentDispatchDto
import com.motocrashguardian.data.remote.IncidentLocationDto
import com.motocrashguardian.data.remote.IncidentTraceRequest
import com.motocrashguardian.data.remote.IncidentUpsertRequest
import com.motocrashguardian.data.remote.UpdateContactsRequest
import com.motocrashguardian.data.remote.UserConfigRequest
import java.util.Base64
import kotlin.math.roundToLong

// Limites del contrato del backend (docs/04-data-model.md, validaciones del API).
private const val MaxRiderNameLength = 60
private const val MaxContactNameLength = 60
private const val MaxDeviceEventKeyLength = 16
private const val MaxTraceBodyBytes = 64 * 1024
private const val TraceBodyOverheadBytes = 512
private val E164 = Regex("^\\+[1-9][0-9]{7,14}$")

/** `null` mientras el perfil no tenga nombre: el backend exige `riderName` no vacio. */
fun AppSettings.toConfigRequest(): UserConfigRequest? {
    val name = riderName.trim().take(MaxRiderNameLength)
    if (name.isEmpty()) return null
    return UserConfigRequest(
        riderName = name,
        impactThresholdMg = detection.impactThresholdMg,
        severeImpactThresholdMg = detection.severeImpactThresholdMg,
        gyroThresholdDps = detection.gyroThresholdDps,
        tiltThresholdDeg = detection.tiltThresholdDeg,
        tiltHoldMs = detection.tiltHoldMs,
        tiltDetectionEnabled = detection.tiltDetectionEnabled,
        confirmWindowMs = detection.confirmWindowMs,
        stillnessToleranceMg = detection.stillnessToleranceMg,
        countdownSeconds = countdownSeconds,
        pitchOffsetCdeg = detection.pitchOffsetCdeg,
        rollOffsetCdeg = detection.rollOffsetCdeg
    )
}

/**
 * `null` si los contactos locales no cumplen las reglas del backend (telefono E.164, nombre,
 * secundario solo con principal, telefonos distintos): enviarlos solo produciria un 400.
 */
fun AppSettings.toContactsRequest(): UpdateContactsRequest? {
    val contacts = listOfNotNull(primaryContact, secondaryContact)
    if (secondaryContact != null && primaryContact == null) return null
    if (contacts.any { !it.isSyncable() }) return null
    if (contacts.map { it.phoneE164 }.distinct().size != contacts.size) return null
    return UpdateContactsRequest(
        contacts.map { EmergencyContactDto(it.role.name, it.name.trim(), it.phoneE164) }
    )
}

private fun EmergencyContact.isSyncable(): Boolean =
    name.isNotBlank() && name.trim().length <= MaxContactNameLength && E164.matches(phoneE164)

fun Incident.toUpsertRequest(appVersion: String): IncidentUpsertRequest {
    val located = locationSource != LocationSource.NONE && latitude != null && longitude != null
    return IncidentUpsertRequest(
        type = type.name,
        triggerType = triggerType.name,
        status = status.name,
        deviceEventKey = deviceEventKey?.takeIf { it.length <= MaxDeviceEventKeyLength },
        detectedAt = detectedAt.toString(),
        // El backend rechaza resolvedAt anterior a detectedAt.
        resolvedAt = resolvedAt?.let { maxOf(it, detectedAt) }?.toString(),
        peakAccelMg = peakAccelMg,
        peakGyroDps = peakGyroDps,
        pitchCdeg = pitchCdeg,
        rollCdeg = rollCdeg,
        location = if (located) {
            IncidentLocationDto(
                latitude = latitude?.roundTo6(),
                longitude = longitude?.roundTo6(),
                accuracyM = locationAccuracyMeters?.takeIf { it >= 0f },
                source = locationSource.name,
                fixAt = locationFixAt?.toString()
            )
        } else {
            null
        },
        dispatch = IncidentDispatchDto(
            smsPrimary = primarySmsStatus.name,
            smsSecondary = secondarySmsStatus.name,
            call = callStatus.name
        ),
        firmwareVersion = firmwareVersion?.take(16),
        appVersion = appVersion.take(32)
    )
}

/** `null` si la traza no cabe en el limite de 64 KB del backend. */
fun Trace.toRequest(): IncidentTraceRequest? {
    val encoded = Base64.getEncoder().encodeToString(samples)
    if (encoded.length > MaxTraceBodyBytes - TraceBodyOverheadBytes) return null
    return IncidentTraceRequest(
        sampleRateHz = sampleRateHz,
        preTriggerSamples = preTriggerSamples,
        totalSamples = totalSamples,
        accelLsbPerG = accelLsbPerG,
        gyroLsbPerDpsX10 = gyroLsbPerDpsX10,
        samplesBase64 = encoded
    )
}

/** El backend guarda NUMERIC(9,6); enviar mas decimales solo agrega ruido. */
private fun Double.roundTo6(): Double = (this * 1_000_000.0).roundToLong() / 1_000_000.0

