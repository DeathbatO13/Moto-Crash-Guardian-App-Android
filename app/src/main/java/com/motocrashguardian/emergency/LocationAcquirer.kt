package com.motocrashguardian.emergency

import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.LocationSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.time.Duration
import java.time.Instant

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val source: LocationSource,
    val fixAt: Instant
)

/** Pide al GPS del telefono una posicion nueva; `null` si no hay permiso, senal o servicios. */
fun interface PhoneLocationSource {
    suspend fun currentFix(): LocationFix?
}

/** Ultima posicion que el telefono conoce, posiblemente antigua. */
fun interface LastKnownLocationSource {
    suspend fun lastKnown(): LocationFix?
}

/** Ultima posicion recibida del GPS NEO-6M de la moto por BLE. */
fun interface DeviceGpsSource {
    fun latest(): LocationFix?
}

/**
 * Reune la mejor ubicacion posible durante la cuenta regresiva.
 *
 * Prioridad: GPS del telefono > GPS de la moto (reciente) > ultima ubicacion conocida (no
 * demasiado vieja) > ninguna. [begin] arranca la busqueda al comenzar la cuenta y [finish] entrega
 * el resultado al despachar, esperando solo un margen corto si todavia no hay nada mejor.
 *
 * Los umbrales por defecto son supuestos razonables: el documento de requisitos con los valores
 * definitivos de frescura y timeouts no esta en este repositorio.
 */
class LocationAcquirer(
    private val scope: CoroutineScope,
    private val phoneSource: PhoneLocationSource,
    private val lastKnownSource: LastKnownLocationSource,
    private val deviceGps: DeviceGpsSource,
    private val clock: Clock = Clock.systemUTC(),
    private val phoneTimeoutMillis: Long = 10_000L,
    private val firstFixGraceMillis: Long = 2_000L,
    private val deviceFixMaxAge: Duration = Duration.ofSeconds(30),
    private val lastKnownMaxAge: Duration = Duration.ofHours(24)
) {
    private class Cycle(
        val phone: Deferred<LocationFix?>,
        val lastKnown: Deferred<LocationFix?>
    )

    private var cycle: Cycle? = null

    /** Inicia (o reinicia) la busqueda. Seguro de llamar varias veces. */
    @Synchronized
    fun begin() {
        cancelCurrent()
        cycle = Cycle(
            phone = scope.async {
                withTimeoutOrNull(phoneTimeoutMillis) { safely { phoneSource.currentFix() } }
            },
            lastKnown = scope.async { safely { lastKnownSource.lastKnown() } }
        )
    }

    @Synchronized
    fun cancel() = cancelCurrent()

    /** Devuelve la mejor ubicacion disponible y termina la busqueda. */
    suspend fun finish(): LocationFix? {
        val current = synchronized(this) { cycle } ?: run {
            begin()
            synchronized(this) { cycle }!!
        }
        try {
            return resolve(current)
        } finally {
            cancel()
        }
    }

    private suspend fun resolve(current: Cycle): LocationFix? {
        readyPhoneFix(current)?.let { return it }
        freshDeviceFix()?.let { return it }
        // Aun no hay nada mejor: dar un margen corto al GPS del telefono antes de rendirse.
        withTimeoutOrNull(firstFixGraceMillis) { current.phone.await() }?.let { return it }
        freshDeviceFix()?.let { return it }
        val lastKnown = withTimeoutOrNull(firstFixGraceMillis) { current.lastKnown.await() }
        return lastKnown?.takeIf { Duration.between(it.fixAt, clock.instant()) <= lastKnownMaxAge }
    }

    private suspend fun readyPhoneFix(current: Cycle): LocationFix? =
        if (current.phone.isCompleted) current.phone.await() else null

    private fun freshDeviceFix(): LocationFix? =
        safely { deviceGps.latest() }
            ?.takeIf { Duration.between(it.fixAt, clock.instant()) <= deviceFixMaxAge }

    private fun cancelCurrent() {
        cycle?.phone?.cancel()
        cycle?.lastKnown?.cancel()
        cycle = null
    }

    private inline fun <T> safely(block: () -> T?): T? =
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }

}

/** Copia los datos de ubicacion al incidente; sin ubicacion nueva conserva lo que ya tenia. */
fun Incident.withFix(fix: LocationFix?): Incident = if (fix == null) {
    this
} else {
    copy(
        latitude = fix.latitude,
        longitude = fix.longitude,
        locationAccuracyMeters = fix.accuracyMeters,
        locationSource = fix.source,
        locationFixAt = fix.fixAt
    )
}
