package com.motocrashguardian.emergency

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant

sealed interface DemoState {
    data object Off : DemoState

    /** Activo hasta [expiresAt]; pasada esa hora vuelve solo a [Off]. */
    data class On(val expiresAt: Instant) : DemoState
}

/**
 * Modo demo temporal para presentaciones: cuenta regresiva corta, reglas de confirmacion mas
 * sensibles y todo incidente se trata como simulacro (SMS `[PRUEBA]`).
 *
 * Vive solo en memoria a proposito: no se escribe en los ajustes ni en la configuracion del
 * modulo, caduca solo y se apaga al cerrarse el proceso. Un demo olvidado no puede dejar la
 * app en un estado que trate una emergencia real como prueba.
 */
class DemoModeController(
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
    private val duration: Duration = DefaultDuration
) {
    private val mutableState = MutableStateFlow<DemoState>(DemoState.Off)
    val state: StateFlow<DemoState> = mutableState.asStateFlow()

    private var expiry: Job? = null

    /**
     * Se comprueba contra el reloj y no solo contra el estado: aunque la corrutina de caducidad
     * se retrase (p. ej. por el ahorro de energia), una demo vencida nunca cuenta como activa.
     */
    fun isActive(): Boolean {
        val current = mutableState.value
        return current is DemoState.On && clock.instant().isBefore(current.expiresAt)
    }

    /** Activa el demo; si ya estaba activo, reinicia el plazo. */
    @Synchronized
    fun enable() {
        expiry?.cancel()
        val expiresAt = clock.instant().plus(duration)
        mutableState.value = DemoState.On(expiresAt)
        expiry = scope.launch {
            delay(duration.toMillis())
            expireIfStillCurrent(expiresAt)
        }
    }

    @Synchronized
    fun disable() {
        expiry?.cancel()
        expiry = null
        mutableState.value = DemoState.Off
    }

    @Synchronized
    private fun expireIfStillCurrent(expiresAt: Instant) {
        val current = mutableState.value
        if (current is DemoState.On && current.expiresAt == expiresAt) {
            mutableState.value = DemoState.Off
        }
    }

    companion object {
        /** Lo bastante largo para una presentacion; lo bastante corto para no olvidarlo activo. */
        val DefaultDuration: Duration = Duration.ofMinutes(10)
    }
}
