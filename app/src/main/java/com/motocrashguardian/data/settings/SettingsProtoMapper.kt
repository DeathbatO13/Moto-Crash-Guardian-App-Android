package com.motocrashguardian.data.settings

import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.core.model.ContactRole
import com.motocrashguardian.core.model.DetectionConfig
import com.motocrashguardian.core.model.DeviceInfo
import com.motocrashguardian.core.model.EmergencyContact
import com.motocrashguardian.data.settings.proto.AppSettingsProto
import com.motocrashguardian.data.settings.proto.ContactRoleProto
import com.motocrashguardian.data.settings.proto.DetectionConfigProto
import com.motocrashguardian.data.settings.proto.DeviceInfoProto
import com.motocrashguardian.data.settings.proto.EmergencyContactProto
import java.time.Instant

internal fun AppSettingsProto.toDomain(): AppSettings = AppSettings(
    riderName = riderName,
    primaryContact = if (hasPrimaryContact()) primaryContact.toDomain() else null,
    secondaryContact = if (hasSecondaryContact()) secondaryContact.toDomain() else null,
    detection = if (hasDetection()) detection.toDomain() else DetectionConfig(),
    countdownSeconds = if (hasCountdownSeconds()) countdownSeconds else AppSettings().countdownSeconds,
    demoMode = demoMode,
    drillPlaceCall = drillPlaceCall,
    pairedDeviceAddress = if (hasPairedDeviceAddress()) pairedDeviceAddress else null,
    pairedDeviceInfo = if (hasPairedDeviceInfo()) pairedDeviceInfo.toDomain() else null,
    tripActive = tripActive,
    onboardingCompleted = onboardingCompleted,
    consentAcceptedAt = if (consentAcceptedAtPresent) {
        Instant.ofEpochMilli(consentAcceptedAtEpochMillis)
    } else {
        null
    },
    configDirty = configDirty,
    contactsDirty = contactsDirty
)

internal fun AppSettings.toProto(): AppSettingsProto {
    val builder = AppSettingsProto.newBuilder()
        .setRiderName(riderName)
        .setDetection(detection.toProto())
        .setCountdownSeconds(countdownSeconds)
        .setDemoMode(demoMode)
        .setDrillPlaceCall(drillPlaceCall)
        .setTripActive(tripActive)
        .setOnboardingCompleted(onboardingCompleted)
        .setConfigDirty(configDirty)
        .setContactsDirty(contactsDirty)

    primaryContact?.let { builder.primaryContact = it.toProto() }
    secondaryContact?.let { builder.secondaryContact = it.toProto() }
    pairedDeviceAddress?.let(builder::setPairedDeviceAddress)
    pairedDeviceInfo?.let { builder.pairedDeviceInfo = it.toProto() }
    consentAcceptedAt?.let {
        builder
            .setConsentAcceptedAtPresent(true)
            .setConsentAcceptedAtEpochMillis(it.toEpochMilli())
    }
    return builder.build()
}

private fun EmergencyContactProto.toDomain(): EmergencyContact = EmergencyContact(
    role = when (role) {
        ContactRoleProto.CONTACT_ROLE_PRIMARY -> ContactRole.PRIMARY
        ContactRoleProto.CONTACT_ROLE_SECONDARY -> ContactRole.SECONDARY
        ContactRoleProto.CONTACT_ROLE_UNSPECIFIED,
        ContactRoleProto.UNRECOGNIZED -> throw IllegalStateException(
            "Stored emergency contact has an unsupported role."
        )
    },
    name = name,
    phoneE164 = phoneE164
)

private fun EmergencyContact.toProto(): EmergencyContactProto =
    EmergencyContactProto.newBuilder()
        .setRole(
            when (role) {
                ContactRole.PRIMARY -> ContactRoleProto.CONTACT_ROLE_PRIMARY
                ContactRole.SECONDARY -> ContactRoleProto.CONTACT_ROLE_SECONDARY
            }
        )
        .setName(name)
        .setPhoneE164(phoneE164)
        .build()

private fun DetectionConfigProto.toDomain(): DetectionConfig = DetectionConfig(
    impactThresholdMg = impactThresholdMg,
    gyroThresholdDps = gyroThresholdDps,
    tiltThresholdDeg = tiltThresholdDeg,
    tiltHoldMs = tiltHoldMs,
    tiltDetectionEnabled = tiltDetectionEnabled,
    severeImpactThresholdMg = severeImpactThresholdMg,
    confirmWindowMs = confirmWindowMs,
    stillnessToleranceMg = stillnessToleranceMg,
    pitchOffsetCdeg = pitchOffsetCdeg,
    rollOffsetCdeg = rollOffsetCdeg
)

private fun DetectionConfig.toProto(): DetectionConfigProto =
    DetectionConfigProto.newBuilder()
        .setImpactThresholdMg(impactThresholdMg)
        .setGyroThresholdDps(gyroThresholdDps)
        .setTiltThresholdDeg(tiltThresholdDeg)
        .setTiltHoldMs(tiltHoldMs)
        .setTiltDetectionEnabled(tiltDetectionEnabled)
        .setSevereImpactThresholdMg(severeImpactThresholdMg)
        .setConfirmWindowMs(confirmWindowMs)
        .setStillnessToleranceMg(stillnessToleranceMg)
        .setPitchOffsetCdeg(pitchOffsetCdeg)
        .setRollOffsetCdeg(rollOffsetCdeg)
        .build()

private fun DeviceInfoProto.toDomain(): DeviceInfo = DeviceInfo(
    protocolVersion = protocolVersion,
    firmwareMajor = firmwareMajor,
    firmwareMinor = firmwareMinor,
    firmwarePatch = firmwarePatch,
    hardwareRevision = hardwareRevision,
    capabilities = capabilities,
    bootCount = bootCount,
    deviceId = deviceId
)

private fun DeviceInfo.toProto(): DeviceInfoProto =
    DeviceInfoProto.newBuilder()
        .setProtocolVersion(protocolVersion)
        .setFirmwareMajor(firmwareMajor)
        .setFirmwareMinor(firmwareMinor)
        .setFirmwarePatch(firmwarePatch)
        .setHardwareRevision(hardwareRevision)
        .setCapabilities(capabilities)
        .setBootCount(bootCount)
        .setDeviceId(deviceId)
        .build()
