package com.motocrashguardian.emergency

import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.IncidentStatus
import com.motocrashguardian.detection.GuardianEffect
import com.motocrashguardian.detection.GuardianStateMachine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Conecta los efectos de [GuardianStateMachine] con el mundo real: alarma, notificacion y despacho.
 *
 * Es el unico consumidor de `machine.effects` (el canal entrega cada efecto una sola vez).
 * Ningun fallo de alarma o notificacion puede impedir el despacho, y un despacho que falle de
 * forma inesperada se reporta como `DISPATCH_FAILED` para que la maquina no quede atascada.
 */
class EmergencyFlowController(
    private val machine: GuardianStateMachine,
    private val orchestrator: DispatchOrchestrator,
    private val alarm: AlarmPlayer,
    private val showCountdown: (remainingSeconds: Int) -> Unit,
    private val cancelCountdownNotification: () -> Unit,
    private val locationAcquirer: LocationAcquirer,
    private val clock: Clock = Clock.systemUTC()
) {
    fun start(scope: CoroutineScope): Job = scope.launch {
        var ticker: Job? = null
        machine.effects.collect { effect ->
            when (effect) {
                is GuardianEffect.CountdownStarted -> {
                    ticker?.cancel()
                    guarded { alarm.start() }
                    guarded { locationAcquirer.begin() }
                    ticker = launch { tickNotification(effect.deadline) }
                }
                is GuardianEffect.CountdownCancelled -> {
                    ticker?.cancel()
                    guarded { locationAcquirer.cancel() }
                    endAlert()
                }
                is GuardianEffect.DispatchRequested -> {
                    ticker?.cancel()
                    endAlert()
                    dispatch(effect.incident.withFix(acquireLocation()))
                }
                is GuardianEffect.DispatchCompleted,
                is GuardianEffect.IncidentRejected,
                is GuardianEffect.FaultReported -> Unit
            }
        }
    }

    private suspend fun acquireLocation(): LocationFix? =
        try {
            locationAcquirer.finish()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }

    private suspend fun dispatch(incident: Incident) {
        val status = try {
            orchestrator.dispatch(incident).status
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            IncidentStatus.DISPATCH_FAILED
        }
        guarded { machine.onDispatchCompleted(status) }
    }

    private suspend fun tickNotification(deadline: Instant) {
        while (true) {
            val remainingMillis = Duration.between(clock.instant(), deadline).toMillis()
            if (remainingMillis <= 0) return
            guarded { showCountdown(((remainingMillis + 999) / 1_000).toInt()) }
            // Dormir hasta que el numero visible cambie, sin acumular deriva.
            delay((remainingMillis % 1_000).takeIf { it > 0 } ?: 1_000L)
        }
    }

    private fun endAlert() {
        guarded { alarm.stop() }
        guarded { cancelCountdownNotification() }
    }

    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Alarma o notificacion no disponibles: la emergencia debe continuar.
        }
    }
}
