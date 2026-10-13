package com.motocrashguardian.emergency

import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.detection.GuardianState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

sealed interface DrillOutcome {
    /**
     * La cuenta regresiva del simulacro ya empezo. [viaDevice] indica que la origino el modulo
     * con `TRIGGER_TEST_EVENT`; [fellBack] que se intento por el modulo y hubo que hacerlo local.
     */
    data class Started(val viaDevice: Boolean, val fellBack: Boolean) : DrillOutcome

    /** No hay contactos a quienes avisar: el simulacro no probaria nada. */
    data object NoContacts : DrillOutcome

    /** La proteccion esta apagada (sin viaje activo la maquina no acepta eventos). */
    data object ProtectionOff : DrillOutcome

    /** Ya hay una alerta o un evento en curso. */
    data object AlertInProgress : DrillOutcome
}

/**
 * Inicia un simulacro de alerta. Con el modulo conectado se pide un evento de prueba por BLE para
 * ejercitar toda la ruta (modulo -> app); si no hay modulo, o no responde, se hace un simulacro
 * local. Desde ahi es el mismo flujo que una alerta real, con dos diferencias: el SMS lleva
 * `[PRUEBA]` y la llamada solo se hace si el usuario la habilito.
 */
class DrillStarter(
    private val guardianState: StateFlow<GuardianState>,
    private val deviceReady: () -> Boolean,
    /** Pide el evento de prueba al modulo; `true` si el modulo respondio OK. */
    private val triggerDeviceTest: suspend () -> Boolean,
    private val startLocalDrill: suspend () -> Unit,
    private val deviceEventTimeoutMillis: Long = DefaultDeviceEventTimeoutMillis
) {
    suspend fun start(settings: AppSettings): DrillOutcome {
        if (settings.primaryContact == null && settings.secondaryContact == null) {
            return DrillOutcome.NoContacts
        }
        when (guardianState.value) {
            GuardianState.Monitoring, GuardianState.ConnectionLost -> Unit
            GuardianState.Idle -> return DrillOutcome.ProtectionOff
            else -> return DrillOutcome.AlertInProgress
        }

        var triedDevice = false
        if (deviceReady()) {
            triedDevice = true
            if (requestDeviceTest() && countdownStartedSoon()) {
                return DrillOutcome.Started(viaDevice = true, fellBack = false)
            }
        }
        startLocalDrill()
        return DrillOutcome.Started(viaDevice = false, fellBack = triedDevice)
    }

    private suspend fun requestDeviceTest(): Boolean =
        try {
            triggerDeviceTest()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }

    private suspend fun countdownStartedSoon(): Boolean =
        withTimeoutOrNull(deviceEventTimeoutMillis) {
            guardianState.first { it is GuardianState.Countdown }
        } != null

    companion object {
        const val DefaultDeviceEventTimeoutMillis = 5_000L
    }
}
