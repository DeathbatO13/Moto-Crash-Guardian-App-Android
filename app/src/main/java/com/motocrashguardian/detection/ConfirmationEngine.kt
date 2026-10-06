package com.motocrashguardian.detection

import com.motocrashguardian.core.model.DetectionConfig
import com.motocrashguardian.core.model.DeviceEvent
import com.motocrashguardian.core.model.DeviceEventType
import com.motocrashguardian.core.model.Telemetry
import com.motocrashguardian.core.model.TriggerType
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

class ConfirmationEngine(
    private val clock: Clock = Clock.systemUTC()
) {
    fun evaluate(
        event: DeviceEvent,
        telemetry: List<Telemetry>,
        speed: SpeedSample?,
        config: DetectionConfig,
        demoMode: Boolean = false,
        eventAgeAtReceipt: Duration? = null,
        now: Instant = clock.instant()
    ): ConfirmationDecision {
        require(eventAgeAtReceipt == null || !eventAgeAtReceipt.isNegative) {
            "Event age cannot be negative."
        }

        if (event.type == DeviceEventType.TEST) {
            return ConfirmationDecision.Confirmed(TriggerType.TEST, event.receivedAt)
        }
        if (
            event.type == DeviceEventType.IMPACT &&
            event.peakAccelMg >= config.severeImpactThresholdMg
        ) {
            return ConfirmationDecision.Confirmed(TriggerType.SEVERE_IMPACT, event.receivedAt)
        }
        if (eventAgeAtReceipt != null && eventAgeAtReceipt > StaleEventThreshold) {
            return ConfirmationDecision.Rejected(RejectionReason.STALE)
        }
        if (event.type == DeviceEventType.FAULT) {
            return ConfirmationDecision.Rejected(RejectionReason.UNSUPPORTED_EVENT)
        }

        val effectiveConfig = if (demoMode) {
            config.copy(confirmWindowMs = DemoConfirmWindowMillis, tiltHoldMs = DemoTiltHoldMillis)
        } else {
            config
        }
        val windowEndsAt = event.receivedAt.plusMillis(effectiveConfig.confirmWindowMs.toLong())
        val effectiveNow = minOf(now, windowEndsAt)
        val inWindow = telemetry
            .asSequence()
            .filter { it.receivedAt >= event.receivedAt && it.receivedAt <= effectiveNow }
            .sortedBy(Telemetry::receivedAt)
            .distinctBy(Telemetry::seq)
            .toList()

        val holdMillis = if (event.type == DeviceEventType.TILT) {
            TiltEventHoldMillis
        } else {
            effectiveConfig.tiltHoldMs.toLong()
        }
        val qualifyingRun = findQualifyingRun(inWindow, effectiveConfig, holdMillis)
        if (qualifyingRun != null && qualifyingRun.durationMillis >= holdMillis) {
            val trigger = event.type.toTriggerType()
            if (!demoMode && speed?.isReliablyAboveThreshold(qualifyingRun.endedAt) == true) {
                return ConfirmationDecision.Rejected(RejectionReason.PHONE_MOVING)
            }
            return ConfirmationDecision.Confirmed(trigger, qualifyingRun.endedAt)
        }

        if (now < windowEndsAt) return ConfirmationDecision.Pending

        val expectedSamples = (effectiveConfig.confirmWindowMs + TelemetryPeriodMillis - 1) /
            TelemetryPeriodMillis
        if (inWindow.size.toDouble() / expectedSamples < LowTelemetryCoverageThreshold) {
            return ConfirmationDecision.Confirmed(
                trigger = event.type.toTriggerType(),
                confirmedAt = windowEndsAt,
                lowTelemetry = true
            )
        }

        val everTilted = inWindow.any { it.maxTiltCdeg() >= effectiveConfig.tiltThresholdDeg * 100L }
        return ConfirmationDecision.Rejected(
            if (everTilted) RejectionReason.NOT_STILL else RejectionReason.NOT_TILTED
        )
    }

    private fun findQualifyingRun(
        samples: List<Telemetry>,
        config: DetectionConfig,
        holdMillis: Long
    ): QualifyingRun? {
        var runStart: Instant? = null
        var previousAt: Instant? = null

        for (sample in samples) {
            val tilted = sample.maxTiltCdeg() >= config.tiltThresholdDeg * 100L
            val still = abs(sample.accelMagMg.toLong() - GravityMagnitudeMg) <=
                config.stillnessToleranceMg
            val timestamp = sample.receivedAt
            val gapMillis = previousAt?.let { Duration.between(it, timestamp).toMillis() }

            if (!tilted || !still || (gapMillis != null && gapMillis > MaxContinuousSampleGapMillis)) {
                runStart = if (tilted && still) timestamp else null
            } else if (runStart == null) {
                runStart = timestamp
            }

            if (tilted && still) {
                val start = requireNotNull(runStart)
                if (Duration.between(start, timestamp).toMillis() >= holdMillis) {
                    return QualifyingRun(startedAt = start, endedAt = timestamp)
                }
            }
            previousAt = timestamp
        }
        return null
    }

    private fun Telemetry.maxTiltCdeg(): Long =
        maxOf(abs(pitchCdeg.toLong()), abs(rollCdeg.toLong()))

    private fun DeviceEventType.toTriggerType(): TriggerType = when (this) {
        DeviceEventType.IMPACT -> TriggerType.IMPACT
        DeviceEventType.TILT -> TriggerType.TILT
        DeviceEventType.TEST -> TriggerType.TEST
        DeviceEventType.FAULT -> error("FAULT events do not map to a crash trigger.")
    }

    private data class QualifyingRun(
        val startedAt: Instant,
        val endedAt: Instant
    ) {
        val durationMillis: Long
            get() = Duration.between(startedAt, endedAt).toMillis()
    }

    private companion object {
        val StaleEventThreshold: Duration = Duration.ofMinutes(5)
        const val TelemetryPeriodMillis = 200
        const val MaxContinuousSampleGapMillis = 400L
        const val LowTelemetryCoverageThreshold = 0.5
        const val GravityMagnitudeMg = 1_000L
        const val TiltEventHoldMillis = 1_000L
        const val DemoConfirmWindowMillis = 3_000
        const val DemoTiltHoldMillis = 1_000
    }
}

data class SpeedSample(
    val speedKmh: Double,
    val accuracyMeters: Float,
    val measuredAt: Instant
) {
    fun isReliablyAboveThreshold(at: Instant): Boolean {
        val age = Duration.between(measuredAt, at)
        return speedKmh.isFinite() &&
            speedKmh > MovingSpeedThresholdKmh &&
            accuracyMeters.isFinite() &&
            accuracyMeters in 0f..MaximumReliableAccuracyMeters &&
            !age.isNegative &&
            age <= MaximumSpeedSampleAge
    }

    private companion object {
        const val MovingSpeedThresholdKmh = 15.0
        const val MaximumReliableAccuracyMeters = 30f
        val MaximumSpeedSampleAge: Duration = Duration.ofSeconds(5)
    }
}

sealed interface ConfirmationDecision {
    data class Confirmed(
        val trigger: TriggerType,
        val confirmedAt: Instant,
        val lowTelemetry: Boolean = false
    ) : ConfirmationDecision

    data class Rejected(val reason: RejectionReason) : ConfirmationDecision

    data object Pending : ConfirmationDecision
}

enum class RejectionReason {
    STALE,
    PHONE_MOVING,
    NOT_TILTED,
    NOT_STILL,
    UNSUPPORTED_EVENT
}
