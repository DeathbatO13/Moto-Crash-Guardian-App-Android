package com.motocrashguardian.emergency.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import com.motocrashguardian.core.model.LocationSource
import com.motocrashguardian.emergency.DeviceGpsSource
import com.motocrashguardian.emergency.LastKnownLocationSource
import com.motocrashguardian.emergency.LocationFix
import com.motocrashguardian.emergency.PhoneLocationSource
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Instant
import kotlin.coroutines.resume

/** Posicion nueva de alta precision del telefono (Fused Location Provider). */
class FusedPhoneLocationSource(private val context: Context) : PhoneLocationSource {
    override suspend fun currentFix(): LocationFix? {
        if (!context.hasLocationPermission()) return null
        val cancellation = CancellationTokenSource()
        return try {
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                .awaitLocation(onCancel = cancellation::cancel)
                ?.toFix(LocationSource.PHONE_GPS)
        } catch (_: SecurityException) {
            null
        }
    }
}

/** Ultima posicion guardada por el telefono, sin encender el GPS. */
class FusedLastKnownLocationSource(private val context: Context) : LastKnownLocationSource {
    override suspend fun lastKnown(): LocationFix? {
        if (!context.hasLocationPermission()) return null
        return try {
            LocationServices.getFusedLocationProviderClient(context)
                .lastLocation
                .awaitLocation(onCancel = {})
                ?.toFix(LocationSource.LAST_KNOWN)
        } catch (_: SecurityException) {
            null
        }
    }
}

/**
 * Marcador de posicion: el GPS NEO-6M llegara por la caracteristica BLE `GPS`, que se implementa
 * con el protocolo BLE (Fase 2) y `GuardianBleManager` (Fase 3). Mientras tanto no aporta datos.
 */
class NoDeviceGpsSource : DeviceGpsSource {
    override fun latest(): LocationFix? = null
}

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun Location.toFix(source: LocationSource): LocationFix = LocationFix(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = if (hasAccuracy()) accuracy else null,
    source = source,
    fixAt = Instant.ofEpochMilli(time)
)

private suspend fun Task<Location?>.awaitLocation(onCancel: () -> Unit): Location? =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
        addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
        addOnCanceledListener { if (continuation.isActive) continuation.resume(null) }
        continuation.invokeOnCancellation { onCancel() }
    }
