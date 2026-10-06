package com.motocrashguardian.detection

import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.core.model.DeviceEvent
import com.motocrashguardian.core.model.DeviceEventType
import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.IncidentStatus
import com.motocrashguardian.core.model.IncidentType
import com.motocrashguardian.core.model.Telemetry
import com.motocrashguardian.core.model.TriggerType
import com.motocrashguardian.data.incidents.IncidentRepository
import com.motocrashguardian.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CompletableDeferred
import java.time.Clock
import java.time.Duration
import java.time.Instant

class GuardianStateMachine(
    private val incidentRepository: IncidentRepository,
    private val settingsRepository: SettingsRepository,
    private val confirmationEngine: ConfirmationEngine,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC()
) {
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val effectChannel = Channel<GuardianEffect>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow<GuardianState>(GuardianState.Idle)
    private val seenEventKeys = mutableSetOf<String>()
    private var countdownJob: Job? = null
    private var confirmationJob: Job? = null

    val state: StateFlow<GuardianState> = mutableState.asStateFlow()
    val effects: Flow<GuardianEffect> = effectChannel.receiveAsFlow()

    private val actor = scope.launch {
        for (command in commands) {
            try {
                process(command)
                command.completed.complete(Unit)
            } catch (error: CancellationException) {
                command.completed.cancel(error)
                throw error
            } catch (error: Exception) {
                command.completed.completeExceptionally(error)
            }
        }
    }

    suspend fun restore() = send(Command.Restore())

    suspend fun startTrip() = send(Command.StartTrip())

    suspend fun stopTrip() = send(Command.StopTrip())

    suspend fun onCandidate(
        event: DeviceEvent,
        initialTelemetry: List<Telemetry> = emptyList(),
        speed: SpeedSample? = null,
        eventAgeAtReceipt: Duration? = null
    ) = send(
        Command.Candidate(
            event = event,
            telemetry = initialTelemetry,
            speed = speed,
            eventAgeAtReceipt = eventAgeAtReceipt
        )
    )

    suspend fun onTelemetry(sample: Telemetry, speed: SpeedSample? = null) =
        send(Command.NewTelemetry(sample, speed))

    suspend fun cancelCountdown() = send(Command.CancelCountdown())

    suspend fun sendHelpNow() = send(Command.SendHelpNow())

    suspend fun requestHelp() = send(Command.RequestHelp())

    suspend fun onDispatchCompleted(status: IncidentStatus) =
        send(Command.DispatchCompleted(status))

    suspend fun dismissResult() = send(Command.DismissResult())

    suspend fun onConnectionLost() = send(Command.ConnectionLost())

    suspend fun onReconnected() = send(Command.Reconnected())

    fun close() {
        commands.close()
        actor.cancel()
        countdownJob?.cancel()
        confirmationJob?.cancel()
        while (true) {
            val pending = commands.tryReceive().getOrNull() ?: break
            pending.completed.cancel(CancellationException("GuardianStateMachine is closed."))
        }
        effectChannel.close()
    }

    private suspend fun send(command: Command) {
        check(commands.trySend(command).isSuccess) { "GuardianStateMachine is closed." }
        command.completed.await()
    }

    private suspend fun process(command: Command) {
        when (command) {
            is Command.Restore -> restoreState()
            is Command.StartTrip -> startTripInternal()
            is Command.StopTrip -> stopTripInternal()
            is Command.Candidate -> handleCandidate(command)
            is Command.NewTelemetry -> handleTelemetry(command)
            is Command.CancelCountdown -> cancelCountdownInternal()
            is Command.SendHelpNow -> dispatchNow()
            is Command.RequestHelp -> requestHelpInternal()
            is Command.DispatchCompleted -> completeDispatch(command.status)
            is Command.DismissResult -> dismissResultInternal()
            is Command.ConnectionLost -> loseConnection()
            is Command.Reconnected -> reconnect()
            is Command.ConfirmationWindowEnded -> finishConfirmationWindow()
            is Command.CountdownEnded -> finishCountdown(command.deadline)
        }
    }

    private suspend fun restoreState() {
        if (mutableState.value !is GuardianState.Idle) return

        val settings = settingsRepository.settings.first()
        val activeIncident = incidentRepository.getLatestActiveIncident()
        if (activeIncident == null) {
            mutableState.value = if (settings.tripActive) {
                GuardianState.Monitoring
            } else {
                GuardianState.Idle
            }
            return
        }

        val deadline = activeIncident.countdownDeadlineOrFallback(settings)
        if (clock.instant() >= deadline) {
            mutableState.value = GuardianState.Dispatching(activeIncident)
            effectChannel.send(GuardianEffect.DispatchRequested(activeIncident))
        } else {
            beginCountdown(activeIncident, deadline)
        }
    }

    private suspend fun startTripInternal() {
        if (mutableState.value != GuardianState.Idle) return
        settingsRepository.updateSettings { it.copy(tripActive = true) }
        mutableState.value = GuardianState.Monitoring
    }

    private suspend fun stopTripInternal() {
        if (mutableState.value != GuardianState.Monitoring) return
        settingsRepository.updateSettings { it.copy(tripActive = false) }
        mutableState.value = GuardianState.Idle
    }

    private suspend fun handleCandidate(command: Command.Candidate) {
        val key = command.event.deduplicationKey
        when (val current = mutableState.value) {
            is GuardianState.Countdown, is GuardianState.Dispatching -> {
                seenEventKeys.add(key)
                mergeEventIntoActiveIncident(command.event)
                return
            }
            is GuardianState.Confirming -> {
                seenEventKeys.add(key)
                mergeEventIntoConfirmation(current, command.event)
                return
            }
            GuardianState.Monitoring -> Unit
            else -> return
        }

        if (!seenEventKeys.add(key)) return

        val persistedDuplicate = incidentRepository.getIncidentByDeviceEventKey(key)
        if (persistedDuplicate != null) {
            when (val current = mutableState.value) {
                is GuardianState.Countdown, is GuardianState.Dispatching ->
                    mergeEventIntoActiveIncident(command.event)
                GuardianState.Monitoring -> if (persistedDuplicate.status == IncidentStatus.ACTIVE) {
                    val settings = settingsRepository.settings.first()
                    beginCountdown(
                        persistedDuplicate,
                        persistedDuplicate.countdownDeadlineOrFallback(settings)
                    )
                }
                else -> Unit
            }
            return
        }

        val current = mutableState.value
        if (current != GuardianState.Monitoring) return

        val settings = settingsRepository.settings.first()
        val decision = confirmationEngine.evaluate(
            event = command.event,
            telemetry = command.telemetry,
            speed = command.speed,
            config = settings.detection,
            demoMode = settings.demoMode,
            eventAgeAtReceipt = command.eventAgeAtReceipt,
            now = clock.instant()
        )
        handleDecision(
            event = command.event,
            decision = decision,
            telemetry = command.telemetry,
            speed = command.speed,
            settings = settings,
            eventAgeAtReceipt = command.eventAgeAtReceipt
        )
    }

    private suspend fun handleTelemetry(command: Command.NewTelemetry) {
        val confirming = mutableState.value as? GuardianState.Confirming ?: return
        val updatedTelemetry = (confirming.telemetry + command.sample)
            .distinctBy(Telemetry::seq)
            .sortedBy(Telemetry::receivedAt)
        val latestSpeed = command.speed ?: confirming.speed
        val settings = confirming.settings
        val decision = confirmationEngine.evaluate(
            event = confirming.event,
            telemetry = updatedTelemetry,
            speed = latestSpeed,
            config = settings.detection,
            demoMode = confirming.demoMode,
            eventAgeAtReceipt = confirming.eventAgeAtReceipt,
            now = clock.instant()
        )
        handleDecision(
            event = confirming.event,
            decision = decision,
            telemetry = updatedTelemetry,
            speed = latestSpeed,
            settings = settings,
            eventAgeAtReceipt = confirming.eventAgeAtReceipt
        )
    }

    private suspend fun handleDecision(
        event: DeviceEvent,
        decision: ConfirmationDecision,
        telemetry: List<Telemetry>,
        speed: SpeedSample?,
        settings: AppSettings,
        eventAgeAtReceipt: Duration?
    ) {
        when (decision) {
            ConfirmationDecision.Pending -> {
                val windowMillis = if (settings.demoMode) {
                    DemoConfirmationWindowMillis
                } else {
                    settings.detection.confirmWindowMs
                }
                val endsAt = event.receivedAt.plusMillis(windowMillis.toLong())
                mutableState.value = GuardianState.Confirming(
                    event = event,
                    telemetry = telemetry,
                    speed = speed,
                    demoMode = settings.demoMode,
                    eventAgeAtReceipt = eventAgeAtReceipt,
                    settings = settings
                )
                if (confirmationJob == null) {
                    confirmationJob = scope.launch {
                        delay(maxOf(0L, Duration.between(clock.instant(), endsAt).toMillis()))
                        send(Command.ConfirmationWindowEnded())
                    }
                }
            }

            is ConfirmationDecision.Confirmed -> {
                confirmationJob?.cancel()
                confirmationJob = null
                val detectedAt = clock.instant()
                val deadline = detectedAt.plusSeconds(settings.countdownSeconds.toLong())
                val incident = Incident(
                    type = if (event.type == DeviceEventType.TEST || settings.demoMode) {
                        IncidentType.DRILL
                    } else {
                        IncidentType.REAL
                    },
                    triggerType = decision.trigger,
                    status = IncidentStatus.ACTIVE,
                    deviceEventKey = event.deduplicationKey,
                    detectedAt = detectedAt,
                    countdownDeadline = deadline,
                    peakAccelMg = event.peakAccelMg,
                    peakGyroDps = event.peakGyroDps,
                    pitchCdeg = event.pitchCdeg,
                    rollCdeg = event.rollCdeg
                )
                if (!incidentRepository.recordIncident(incident)) {
                    val existing = incidentRepository.getIncidentByDeviceEventKey(event.deduplicationKey)
                    if (existing == null) error("Duplicate incident was not found after insert conflict.")
                    return
                }
                beginCountdown(incident, deadline)
            }

            is ConfirmationDecision.Rejected -> {
                confirmationJob?.cancel()
                confirmationJob = null
                if (event.type == DeviceEventType.FAULT) {
                    mutableState.value = GuardianState.Monitoring
                    effectChannel.send(GuardianEffect.FaultReported(event, decision.reason))
                    return
                }
                val rejected = Incident(
                    type = if (event.type == DeviceEventType.TEST || settings.demoMode) {
                        IncidentType.DRILL
                    } else {
                        IncidentType.REAL
                    },
                    triggerType = event.toTriggerType(),
                    status = IncidentStatus.NOT_CONFIRMED,
                    deviceEventKey = event.deduplicationKey,
                    detectedAt = event.receivedAt,
                    resolvedAt = clock.instant(),
                    peakAccelMg = event.peakAccelMg,
                    peakGyroDps = event.peakGyroDps,
                    pitchCdeg = event.pitchCdeg,
                    rollCdeg = event.rollCdeg
                )
                check(incidentRepository.recordIncident(rejected)) {
                    "Rejected incident was already recorded without a matching active transition."
                }
                mutableState.value = GuardianState.Monitoring
                effectChannel.send(GuardianEffect.IncidentRejected(rejected, decision.reason))
            }
        }
    }

    private suspend fun finishConfirmationWindow() {
        confirmationJob = null
        val confirming = mutableState.value as? GuardianState.Confirming ?: return
        val settings = confirming.settings
        val decision = confirmationEngine.evaluate(
            event = confirming.event,
            telemetry = confirming.telemetry,
            speed = confirming.speed,
            config = settings.detection,
            demoMode = confirming.demoMode,
            eventAgeAtReceipt = confirming.eventAgeAtReceipt,
            now = clock.instant()
        )
        handleDecision(
            confirming.event,
            decision,
            confirming.telemetry,
            confirming.speed,
            settings,
            confirming.eventAgeAtReceipt
        )
    }

    private suspend fun cancelCountdownInternal() {
        val countdown = mutableState.value as? GuardianState.Countdown ?: return
        if (clock.instant() >= countdown.deadline) {
            dispatchNow()
            return
        }
        countdownJob?.cancel()
        countdownJob = null
        val cancelled = countdown.incident.copy(
            status = IncidentStatus.CANCELLED_BY_USER,
            resolvedAt = clock.instant()
        )
        persistUpdate(cancelled)
        mutableState.value = GuardianState.Monitoring
        effectChannel.send(GuardianEffect.CountdownCancelled(cancelled))
    }

    private suspend fun dispatchNow() {
        val countdown = mutableState.value as? GuardianState.Countdown ?: return
        countdownJob?.cancel()
        countdownJob = null
        mutableState.value = GuardianState.Dispatching(countdown.incident)
        effectChannel.send(GuardianEffect.DispatchRequested(countdown.incident))
    }

    private suspend fun requestHelpInternal() {
        if (mutableState.value != GuardianState.ConnectionLost) return
        val settings = settingsRepository.settings.first()
        val detectedAt = clock.instant()
        val deadline = detectedAt.plusSeconds(settings.countdownSeconds.toLong())
        val incident = Incident(
            type = IncidentType.REAL,
            triggerType = TriggerType.CONNECTION_LOST,
            status = IncidentStatus.ACTIVE,
            detectedAt = detectedAt,
            countdownDeadline = deadline
        )
        check(incidentRepository.recordIncident(incident)) {
            "Could not persist the manual help incident."
        }
        beginCountdown(incident, deadline)
    }

    private suspend fun finishCountdown(deadline: Instant) {
        countdownJob = null
        val countdown = mutableState.value as? GuardianState.Countdown ?: return
        if (countdown.deadline != deadline) return
        mutableState.value = GuardianState.Dispatching(countdown.incident)
        effectChannel.send(GuardianEffect.DispatchRequested(countdown.incident))
    }

    private suspend fun completeDispatch(status: IncidentStatus) {
        require(status in DispatchResultStatuses) {
            "Dispatch result must be DISPATCHED, DISPATCH_PARTIAL, or DISPATCH_FAILED."
        }
        val dispatching = mutableState.value as? GuardianState.Dispatching ?: return
        val completed = dispatching.incident.copy(status = status, resolvedAt = clock.instant())
        persistUpdate(completed)
        mutableState.value = GuardianState.Dispatched(completed)
        effectChannel.send(GuardianEffect.DispatchCompleted(completed))
    }

    private suspend fun dismissResultInternal() {
        if (mutableState.value !is GuardianState.Dispatched) return
        mutableState.value = GuardianState.Monitoring
    }

    private suspend fun loseConnection() {
        if (mutableState.value != GuardianState.Monitoring) return
        mutableState.value = GuardianState.ConnectionLost
    }

    private suspend fun reconnect() {
        if (mutableState.value != GuardianState.ConnectionLost) return
        mutableState.value = GuardianState.Monitoring
    }

    private suspend fun beginCountdown(incident: Incident, deadline: Instant) {
        confirmationJob?.cancel()
        confirmationJob = null
        countdownJob?.cancel()
        mutableState.value = GuardianState.Countdown(incident, deadline)
        effectChannel.send(GuardianEffect.CountdownStarted(incident, deadline))
        countdownJob = scope.launch {
            delay(maxOf(0L, Duration.between(clock.instant(), deadline).toMillis()))
            send(Command.CountdownEnded(deadline))
        }
    }

    private suspend fun persistUpdate(incident: Incident) {
        check(incidentRepository.updateIncident(incident)) {
            "Incident ${incident.id} disappeared before its state update."
        }
    }

    private suspend fun mergeEventIntoActiveIncident(event: DeviceEvent) {
        val current = mutableState.value
        val incident = when (current) {
            is GuardianState.Countdown -> current.incident
            is GuardianState.Dispatching -> current.incident
            else -> return
        }
        val updated = incident.withPeakFrom(event)
        if (updated == incident) return
        persistUpdate(updated)
        mutableState.value = when (current) {
            is GuardianState.Countdown -> current.copy(incident = updated)
            is GuardianState.Dispatching -> current.copy(incident = updated)
            else -> error("Unreachable state.")
        }
    }

    private fun mergeEventIntoConfirmation(
        confirming: GuardianState.Confirming,
        event: DeviceEvent
    ) {
        val original = confirming.event
        val updated = original.copy(
            peakAccelMg = maxOf(original.peakAccelMg, event.peakAccelMg),
            peakGyroDps = maxOf(original.peakGyroDps, event.peakGyroDps)
        )
        if (updated != original) mutableState.value = confirming.copy(event = updated)
    }

    private fun Incident.withPeakFrom(event: DeviceEvent): Incident = copy(
        peakAccelMg = maxOf(peakAccelMg ?: 0, event.peakAccelMg),
        peakGyroDps = maxOf(peakGyroDps ?: 0, event.peakGyroDps)
    )

    private fun Incident.countdownDeadlineOrFallback(settings: AppSettings): Instant =
        countdownDeadline ?: detectedAt.plusSeconds(settings.countdownSeconds.toLong())

    private fun DeviceEvent.toTriggerType(): TriggerType = when (type) {
        DeviceEventType.IMPACT -> TriggerType.IMPACT
        DeviceEventType.TILT -> TriggerType.TILT
        DeviceEventType.TEST -> TriggerType.TEST
        DeviceEventType.FAULT -> error("FAULT events are not crash incidents.")
    }

    private sealed class Command(val completed: CompletableDeferred<Unit> = CompletableDeferred()) {
        class Restore : Command()
        class StartTrip : Command()
        class StopTrip : Command()
        class Candidate(
            val event: DeviceEvent,
            val telemetry: List<Telemetry>,
            val speed: SpeedSample?,
            val eventAgeAtReceipt: Duration?
        ) : Command()
        class NewTelemetry(val sample: Telemetry, val speed: SpeedSample?) : Command()
        class CancelCountdown : Command()
        class SendHelpNow : Command()
        class RequestHelp : Command()
        class DispatchCompleted(val status: IncidentStatus) : Command()
        class DismissResult : Command()
        class ConnectionLost : Command()
        class Reconnected : Command()
        class ConfirmationWindowEnded : Command()
        class CountdownEnded(val deadline: Instant) : Command()
    }

    private companion object {
        val DispatchResultStatuses = setOf(
            IncidentStatus.DISPATCHED,
            IncidentStatus.DISPATCH_PARTIAL,
            IncidentStatus.DISPATCH_FAILED
        )
        const val DemoConfirmationWindowMillis = 3_000
    }
}

sealed interface GuardianState {
    data object Idle : GuardianState
    data object Monitoring : GuardianState
    data class Confirming(
        val event: DeviceEvent,
        val telemetry: List<Telemetry>,
        val speed: SpeedSample?,
        val demoMode: Boolean,
        val eventAgeAtReceipt: Duration?,
        val settings: AppSettings
    ) : GuardianState
    data class Countdown(val incident: Incident, val deadline: Instant) : GuardianState {
        fun remainingSeconds(now: Instant): Int {
            val remainingMillis = Duration.between(now, deadline).toMillis()
            return if (remainingMillis <= 0) 0 else ((remainingMillis + 999) / 1_000).toInt()
        }
    }
    data class Dispatching(val incident: Incident) : GuardianState
    data class Dispatched(val incident: Incident) : GuardianState
    data object ConnectionLost : GuardianState
}

sealed interface GuardianEffect {
    data class CountdownStarted(val incident: Incident, val deadline: Instant) : GuardianEffect
    data class CountdownCancelled(val incident: Incident) : GuardianEffect
    data class DispatchRequested(val incident: Incident) : GuardianEffect
    data class DispatchCompleted(val incident: Incident) : GuardianEffect
    data class IncidentRejected(val incident: Incident, val reason: RejectionReason) : GuardianEffect
    data class FaultReported(val event: DeviceEvent, val reason: RejectionReason) : GuardianEffect
}
