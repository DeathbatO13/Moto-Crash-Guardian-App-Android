package com.motocrashguardian.emergency

import com.motocrashguardian.ble.protocol.GpsPacket
import com.motocrashguardian.core.model.LocationSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant

/**
 * Ubicación desde el GPS NEO-6M del módulo (característica BLE `GPS`, docs/03 §4.6).
 *
 * La app no pide permiso de ubicación del teléfono: esta es la única fuente. Guarda el
 * último fix válido con su instante real, así sigue disponible aunque el módulo se
 * desconecte tras el choque. [LocationAcquirer] lo usa como "GPS de la moto" mientras
 * es reciente y, si ya es viejo, como "última ubicación conocida".
 */
class ModuleGpsLocationSource(
    gpsPackets: Flow<GpsPacket?>,
    scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC()
) : DeviceGpsSource, LastKnownLocationSource {

    @Volatile
    private var lastFix: LocationFix? = null

    init {
        scope.launch {
            gpsPackets.filterNotNull().collect { packet ->
                packet.toLocationFix(receivedAt = clock.instant())?.let { lastFix = it }
            }
        }
    }

    override fun latest(): LocationFix? = lastFix

    override suspend fun lastKnown(): LocationFix? =
        lastFix?.copy(source = LocationSource.LAST_KNOWN)
}

/**
 * Convierte una trama `GPS` en un fix, o `null` si el módulo no tiene posición
 * (sin fix, nunca tuvo fix o coordenadas en 0,0). El instante del fix descuenta
 * `fixAgeS` al momento de recepción.
 */
fun GpsPacket.toLocationFix(receivedAt: Instant): LocationFix? {
    if (fixType < 2 || fixAgeS == NEVER_FIXED) return null
    if (latE7 == 0 && lonE7 == 0) return null
    return LocationFix(
        latitude = latE7 / 1e7,
        longitude = lonE7 / 1e7,
        accuracyMeters = null,
        source = LocationSource.DEVICE_GPS,
        fixAt = receivedAt.minusSeconds(fixAgeS.toLong())
    )
}

private const val NEVER_FIXED = 65_535
