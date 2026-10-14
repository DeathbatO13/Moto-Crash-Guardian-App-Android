package com.motocrashguardian.ble

import com.motocrashguardian.core.model.DeviceState
import com.motocrashguardian.detection.GuardianState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Lo que [ModuleArmer] decide enviarle al modulo. */
enum class ArmingAction { ARM, DISARM, NONE }

internal data class ArmingInput(
    val connected: Boolean,
    val tripActive: Boolean,
    /** Estado que el modulo reporta en su telemetria; `null` si aun no ha llegado ninguna. */
    val deviceState: DeviceState?
)

/**
 * Decide si hay que armar o desarmar el modulo.
 *
 * El firmware arranca `DISARMED` y solo emite eventos de caida estando `ARMED`, asi que sin
 * `ARM` la app nunca recibiria una caida real. Se arma cuando hay un viaje activo y el modulo
 * esta conectado pero no armado (cubre el primer viaje, una reconexion y el regreso a
 * `DISARMED` tras recuperarse de un fallo). Se desarma una vez al terminar el viaje.
 */
internal fun decideArming(input: ArmingInput, pendingDisarm: Boolean): ArmingAction = when {
    !input.connected -> ArmingAction.NONE
    input.tripActive ->
        if (input.deviceState == null || input.deviceState == DeviceState.DISARMED) {
            ArmingAction.ARM
        } else {
            ArmingAction.NONE
        }
    pendingDisarm && input.deviceState != DeviceState.DISARMED -> ArmingAction.DISARM
    else -> ArmingAction.NONE
}

/**
 * Mantiene el modulo armado mientras dura el viaje. Cada orden se reintenta unas veces porque el
 * modulo puede responder `BUSY`; si no se logra, queda en el registro de la app y se vuelve a
 * intentar cuando cambie la conexion, el viaje o el estado que reporta el modulo.
 */
class ModuleArmer(
    private val connected: Flow<Boolean>,
    private val guardianState: Flow<GuardianState>,
    private val deviceState: Flow<DeviceState?>,
    /** Envia ARM; `true` si el modulo respondio OK. */
    private val arm: suspend () -> Boolean,
    /** Envia DISARM; `true` si el modulo respondio OK. */
    private val disarm: suspend () -> Boolean,
    private val maxAttempts: Int = DefaultMaxAttempts,
    private val retryDelayMillis: Long = DefaultRetryDelayMillis
) {
    private var wasTripActive: Boolean? = null
    private var pendingDisarm = false

    fun start(scope: CoroutineScope): Job = scope.launch {
        combine(connected, guardianState, deviceState) { isConnected, state, device ->
            ArmingInput(
                connected = isConnected,
                // Cualquier estado distinto de Idle significa que hay un viaje en curso.
                tripActive = state != GuardianState.Idle,
                deviceState = device
            )
        }
            .distinctUntilChanged()
            .collectLatest { input ->
                // Al terminar un viaje queda un DISARM pendiente, aunque en ese momento no haya
                // conexion. En el arranque (sin viaje previo) no se desarma nada.
                if (wasTripActive == true && !input.tripActive) pendingDisarm = true
                if (input.tripActive) pendingDisarm = false
                wasTripActive = input.tripActive

                when (decideArming(input, pendingDisarm)) {
                    ArmingAction.ARM -> send(arm)
                    ArmingAction.DISARM -> if (send(disarm)) pendingDisarm = false
                    ArmingAction.NONE -> Unit
                }
            }
    }

    private suspend fun send(command: suspend () -> Boolean): Boolean {
        repeat(maxAttempts) { attempt ->
            val ok = try {
                command()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false
            }
            if (ok) return true
            if (attempt < maxAttempts - 1) delay(retryDelayMillis)
        }
        return false
    }

    companion object {
        const val DefaultMaxAttempts = 5
        const val DefaultRetryDelayMillis = 2_000L
    }
}
