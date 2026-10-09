package com.motocrashguardian.emergency

import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.core.model.CallStatus
import com.motocrashguardian.core.model.EmergencyContact
import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.IncidentStatus
import com.motocrashguardian.core.model.IncidentType
import com.motocrashguardian.core.model.LocationSource
import com.motocrashguardian.core.model.SmsStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Clock
import java.time.Duration
import java.time.LocalTime

/**
 * Ejecuta el despacho local de una emergencia: SMS a los contactos y luego llamada.
 *
 * No usa red ni el backend. Cada etapa se persiste antes de continuar para que un
 * resultado parcial sobreviva a la muerte del proceso. El estado final del incidente
 * (`DISPATCHED`/`DISPATCH_PARTIAL`/`DISPATCH_FAILED`) lo aplica `GuardianStateMachine`
 * a partir de [DispatchOutcome.status].
 */
class DispatchOrchestrator(
    private val smsDispatcher: SmsDispatcher,
    private val callDispatcher: CallDispatcher,
    private val messageBuilder: GsmMessageBuilder,
    private val loadSettings: suspend () -> AppSettings,
    private val saveIncident: suspend (Incident) -> Boolean,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    suspend fun dispatch(incident: Incident): DispatchOutcome {
        val settings = loadSettings()
        val drill = incident.type == IncidentType.DRILL
        val primary = settings.primaryContact
        val secondary = settings.secondaryContact
        val failures = mutableListOf<DispatchFailure>()

        val message = buildMessage(settings.riderName, incident, drill)

        // Los SMS salen en paralelo: un contacto sin cobertura no debe retrasar al otro.
        val (primarySms, secondarySms) = coroutineScope {
            val first = primary?.let { async { sendSms(it, message) } }
            val second = secondary?.let { async { sendSms(it, message) } }
            first?.await() to second?.await()
        }
        primarySms?.failure?.let(failures::add)
        secondarySms?.failure?.let(failures::add)

        var current = incident.copy(
            primarySmsStatus = primarySms?.status.orNotAttempted(),
            secondarySmsStatus = secondarySms?.status.orNotAttempted()
        )
        persist(current)

        val callTarget = primary ?: secondary
        val call = callTarget?.let { placeCall(it, drill, settings.drillPlaceCall) }
        call?.failure?.let(failures::add)
        current = current.copy(callStatus = call?.status ?: CallStatus.NOT_ATTEMPTED)
        persist(current)

        val status = resolveStatus(
            expected = buildList {
                if (primary != null) add(current.primarySmsStatus.isSuccessful())
                if (secondary != null) add(current.secondarySmsStatus.isSuccessful())
                if (callTarget != null) add(current.callStatus.isSuccessful())
            }
        )
        return DispatchOutcome(current, status, failures)
    }

    private suspend fun sendSms(contact: EmergencyContact, message: String): SmsDispatchResult =
        try {
            smsDispatcher.send(contact.phoneE164, message)
        } catch (_: IllegalArgumentException) {
            SmsDispatchResult(
                status = SmsStatus.FAILED,
                attempts = 0,
                failure = DispatchFailure.INVALID_DESTINATION
            )
        }

    private fun placeCall(
        contact: EmergencyContact,
        drill: Boolean,
        drillPlaceCall: Boolean
    ): CallDispatchResult =
        try {
            callDispatcher.placeCall(contact.phoneE164, drill, drillPlaceCall)
        } catch (_: IllegalArgumentException) {
            CallDispatchResult(CallStatus.FAILED, DispatchFailure.INVALID_DESTINATION)
        }

    /** Un fallo al guardar el progreso nunca debe detener el envio de la alerta. */
    private suspend fun persist(incident: Incident) {
        try {
            saveIncident(incident)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // El estado final lo vuelve a persistir GuardianStateMachine.
        }
    }

    private fun buildMessage(riderName: String, incident: Incident, drill: Boolean): String {
        val name = riderName.trim().take(MaximumRiderNameLength)
        val location = locationFor(incident)
        val unavailable = EmergencyMessageLocation.Unavailable(localTime())
        // Un nombre o una coordenada invalidos no pueden impedir el aviso de emergencia.
        return attempt { messageBuilder.build(name, location, drill) }
            ?: attempt { messageBuilder.build(name, unavailable, drill) }
            ?: messageBuilder.build(FallbackRiderName, unavailable, drill)
    }

    private fun locationFor(incident: Incident): EmergencyMessageLocation {
        val latitude = incident.latitude
        val longitude = incident.longitude
        val time = localTime()
        if (latitude == null || longitude == null) {
            return EmergencyMessageLocation.Unavailable(time)
        }
        return when (incident.locationSource) {
            LocationSource.PHONE_GPS -> EmergencyMessageLocation.PhoneFix(
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = incident.locationAccuracyMeters?.toInt()?.coerceAtLeast(0) ?: 0,
                localTime = time
            )
            LocationSource.DEVICE_GPS -> EmergencyMessageLocation.DeviceGpsFix(
                latitude, longitude, time
            )
            LocationSource.LAST_KNOWN -> EmergencyMessageLocation.LastKnownFix(
                latitude = latitude,
                longitude = longitude,
                ageMinutes = incident.locationFixAt
                    ?.let { Duration.between(it, clock.instant()).toMinutes() }
                    ?.coerceIn(0, Int.MAX_VALUE.toLong())?.toInt()
                    ?: 0,
                localTime = time
            )
            LocationSource.NONE -> EmergencyMessageLocation.Unavailable(time)
        }
    }

    private fun localTime(): LocalTime = LocalTime.now(clock)

    private inline fun attempt(block: () -> String): String? =
        try {
            block()
        } catch (_: IllegalArgumentException) {
            null
        }

    private companion object {
        const val MaximumRiderNameLength = 40
        const val FallbackRiderName = "un motociclista"
    }
}

data class DispatchOutcome(
    /** Incidente con `primarySmsStatus`, `secondarySmsStatus` y `callStatus` ya resueltos. */
    val incident: Incident,
    val status: IncidentStatus,
    val failures: List<DispatchFailure>
)

internal fun resolveStatus(expected: List<Boolean>): IncidentStatus = when {
    expected.isNotEmpty() && expected.all { it } -> IncidentStatus.DISPATCHED
    expected.any { it } -> IncidentStatus.DISPATCH_PARTIAL
    else -> IncidentStatus.DISPATCH_FAILED
}

private fun SmsStatus?.orNotAttempted(): SmsStatus = this ?: SmsStatus.NOT_ATTEMPTED

private fun SmsStatus.isSuccessful(): Boolean =
    this == SmsStatus.SENT || this == SmsStatus.DELIVERED

private fun CallStatus.isSuccessful(): Boolean =
    this == CallStatus.PLACED || this == CallStatus.SKIPPED_DRILL
