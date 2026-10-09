package com.motocrashguardian.emergency

import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.core.model.CallStatus
import com.motocrashguardian.core.model.ContactRole
import com.motocrashguardian.core.model.EmergencyContact
import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.IncidentStatus
import com.motocrashguardian.core.model.IncidentType
import com.motocrashguardian.core.model.LocationSource
import com.motocrashguardian.core.model.SmsStatus
import com.motocrashguardian.core.model.TriggerType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DispatchOrchestratorTest {
    private val now = Instant.parse("2026-10-19T15:30:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val primary = EmergencyContact(ContactRole.PRIMARY, "Ana", "+573001112233")
    private val secondary = EmergencyContact(ContactRole.SECONDARY, "Luis", "+573004445566")

    private val sentMessages = mutableListOf<Pair<String, String>>()
    private val placedCalls = mutableListOf<String>()
    private val saved = mutableListOf<Incident>()
    private val events = mutableListOf<String>()

    private var smsResults: Map<String, SmsAttemptResult> = emptyMap()
    private var callFailure: RuntimeException? = null
    private var smsPermission = true
    private var callPermission = true
    private var saveFails = false

    @Test
    fun `todo exitoso marca DISPATCHED y guarda los estados`() = runTest {
        val outcome = orchestrator(settings()).dispatch(realIncident())

        assertEquals(IncidentStatus.DISPATCHED, outcome.status)
        assertEquals(SmsStatus.SENT, outcome.incident.primarySmsStatus)
        assertEquals(SmsStatus.SENT, outcome.incident.secondarySmsStatus)
        assertEquals(CallStatus.PLACED, outcome.incident.callStatus)
        assertTrue(outcome.failures.isEmpty())
        assertEquals(listOf(primary.phoneE164), placedCalls)
        assertEquals(
            setOf(primary.phoneE164, secondary.phoneE164),
            sentMessages.map { it.first }.toSet()
        )
    }

    @Test
    fun `el SMS sale antes que la llamada`() = runTest {
        orchestrator(settings()).dispatch(realIncident())

        assertTrue(events.indexOf("sms") < events.indexOf("call"))
    }

    @Test
    fun `falla el secundario pero el principal y la llamada salen - parcial`() = runTest {
        smsResults = mapOf(
            secondary.phoneE164 to SmsAttemptResult.Failed(DispatchFailure.NO_SERVICE, "a2")
        )

        val outcome = orchestrator(settings()).dispatch(realIncident())

        assertEquals(IncidentStatus.DISPATCH_PARTIAL, outcome.status)
        assertEquals(SmsStatus.SENT, outcome.incident.primarySmsStatus)
        assertEquals(SmsStatus.FAILED, outcome.incident.secondarySmsStatus)
        assertEquals(CallStatus.PLACED, outcome.incident.callStatus)
        assertEquals(listOf(DispatchFailure.NO_SERVICE), outcome.failures)
    }

    @Test
    fun `si fallan ambos SMS igual intenta la llamada`() = runTest {
        smsResults = mapOf(
            primary.phoneE164 to SmsAttemptResult.Failed(DispatchFailure.NO_SERVICE),
            secondary.phoneE164 to SmsAttemptResult.Failed(DispatchFailure.NO_SERVICE)
        )

        val outcome = orchestrator(settings()).dispatch(realIncident())

        assertEquals(IncidentStatus.DISPATCH_PARTIAL, outcome.status)
        assertEquals(CallStatus.PLACED, outcome.incident.callStatus)
    }

    @Test
    fun `todo falla sin permisos - DISPATCH_FAILED y nada intentado`() = runTest {
        smsPermission = false
        callPermission = false

        val outcome = orchestrator(settings()).dispatch(realIncident())

        assertEquals(IncidentStatus.DISPATCH_FAILED, outcome.status)
        assertEquals(SmsStatus.NOT_ATTEMPTED, outcome.incident.primarySmsStatus)
        assertEquals(SmsStatus.NOT_ATTEMPTED, outcome.incident.secondarySmsStatus)
        assertEquals(CallStatus.NOT_ATTEMPTED, outcome.incident.callStatus)
        assertTrue(sentMessages.isEmpty())
        assertTrue(placedCalls.isEmpty())
        assertTrue(outcome.failures.all { it == DispatchFailure.PERMISSION_DENIED })
    }

    @Test
    fun `sin contactos configurados falla sin intentar nada`() = runTest {
        val outcome = orchestrator(AppSettings(riderName = "Carlos")).dispatch(realIncident())

        assertEquals(IncidentStatus.DISPATCH_FAILED, outcome.status)
        assertTrue(sentMessages.isEmpty())
        assertTrue(placedCalls.isEmpty())
    }

    @Test
    fun `solo contacto secundario - recibe SMS y llamada`() = runTest {
        val outcome = orchestrator(settings(primary = null)).dispatch(realIncident())

        assertEquals(IncidentStatus.DISPATCHED, outcome.status)
        assertEquals(SmsStatus.NOT_ATTEMPTED, outcome.incident.primarySmsStatus)
        assertEquals(SmsStatus.SENT, outcome.incident.secondarySmsStatus)
        assertEquals(listOf(secondary.phoneE164), placedCalls)
    }

    @Test
    fun `solo contacto principal sin secundario no penaliza el resultado`() = runTest {
        val outcome = orchestrator(settings(secondary = null)).dispatch(realIncident())

        assertEquals(IncidentStatus.DISPATCHED, outcome.status)
        assertEquals(SmsStatus.NOT_ATTEMPTED, outcome.incident.secondarySmsStatus)
    }

    @Test
    fun `telefono invalido en un contacto se marca fallido sin abortar el resto`() = runTest {
        val broken = secondary.copy(phoneE164 = "3004445566")

        val outcome = orchestrator(settings(secondary = broken)).dispatch(realIncident())

        assertEquals(IncidentStatus.DISPATCH_PARTIAL, outcome.status)
        assertEquals(SmsStatus.SENT, outcome.incident.primarySmsStatus)
        assertEquals(SmsStatus.FAILED, outcome.incident.secondarySmsStatus)
        assertTrue(DispatchFailure.INVALID_DESTINATION in outcome.failures)
        assertEquals(CallStatus.PLACED, outcome.incident.callStatus)
    }

    @Test
    fun `la llamada fallida deja el resultado parcial`() = runTest {
        callFailure = IllegalStateException("sin telecom")

        val outcome = orchestrator(settings()).dispatch(realIncident())

        assertEquals(IncidentStatus.DISPATCH_PARTIAL, outcome.status)
        assertEquals(CallStatus.FAILED, outcome.incident.callStatus)
        assertEquals(listOf(DispatchFailure.TRANSPORT_UNAVAILABLE), outcome.failures)
    }

    @Test
    fun `simulacro envia SMS con PRUEBA y omite la llamada por defecto`() = runTest {
        val outcome = orchestrator(settings()).dispatch(realIncident(type = IncidentType.DRILL))

        assertEquals(IncidentStatus.DISPATCHED, outcome.status)
        assertEquals(CallStatus.SKIPPED_DRILL, outcome.incident.callStatus)
        assertTrue(placedCalls.isEmpty())
        assertTrue(sentMessages.all { it.second.startsWith("[PRUEBA] ") })
    }

    @Test
    fun `simulacro con llamada habilitada la realiza`() = runTest {
        val outcome = orchestrator(settings(drillPlaceCall = true))
            .dispatch(realIncident(type = IncidentType.DRILL))

        assertEquals(CallStatus.PLACED, outcome.incident.callStatus)
        assertEquals(listOf(primary.phoneE164), placedCalls)
    }

    @Test
    fun `un incidente real nunca lleva el prefijo PRUEBA`() = runTest {
        orchestrator(settings()).dispatch(realIncident())

        assertTrue(sentMessages.none { it.second.contains("PRUEBA") })
    }

    @Test
    fun `el mensaje incluye la ubicacion GPS del telefono`() = runTest {
        val incident = realIncident().copy(
            latitude = 4.710989,
            longitude = -74.072092,
            locationAccuracyMeters = 12.4f,
            locationSource = LocationSource.PHONE_GPS
        )

        orchestrator(settings()).dispatch(incident)

        val message = sentMessages.first().second
        assertTrue(message.contains("https://maps.google.com/?q=4.710989,-74.072092"))
        assertTrue(message.contains("+/-12m"))
    }

    @Test
    fun `ultima ubicacion conocida informa su antiguedad`() = runTest {
        val incident = realIncident().copy(
            latitude = 4.7,
            longitude = -74.0,
            locationSource = LocationSource.LAST_KNOWN,
            locationFixAt = now.minusSeconds(7 * 60)
        )

        orchestrator(settings()).dispatch(incident)

        assertTrue(sentMessages.first().second.contains("hace 7 min"))
    }

    @Test
    fun `sin ubicacion el mensaje lo indica y se envia igual`() = runTest {
        orchestrator(settings()).dispatch(realIncident())

        assertTrue(sentMessages.first().second.contains("Ubicacion no disponible"))
    }

    @Test
    fun `coordenadas invalidas no impiden el aviso`() = runTest {
        val incident = realIncident().copy(
            latitude = 999.0,
            longitude = -74.0,
            locationSource = LocationSource.PHONE_GPS
        )

        val outcome = orchestrator(settings()).dispatch(incident)

        assertEquals(IncidentStatus.DISPATCHED, outcome.status)
        assertTrue(sentMessages.first().second.contains("Ubicacion no disponible"))
    }

    @Test
    fun `nombre vacio o con emojis usa un nombre generico y envia`() = runTest {
        orchestrator(settings(riderName = "")).dispatch(realIncident())
        orchestrator(settings(riderName = "Carlos 🏍")).dispatch(realIncident())

        assertEquals(4, sentMessages.size)
        assertTrue(sentMessages.all { it.second.contains("un motociclista") })
    }

    @Test
    fun `nombre largo se recorta a 40 caracteres`() = runTest {
        orchestrator(settings(riderName = "A".repeat(80))).dispatch(realIncident())

        assertTrue(sentMessages.first().second.contains("accidente de ${"A".repeat(40)}."))
    }

    @Test
    fun `persiste el progreso tras los SMS y tras la llamada`() = runTest {
        orchestrator(settings()).dispatch(realIncident())

        assertEquals(2, saved.size)
        assertEquals(CallStatus.NOT_ATTEMPTED, saved[0].callStatus)
        assertEquals(SmsStatus.SENT, saved[0].primarySmsStatus)
        assertEquals(CallStatus.PLACED, saved[1].callStatus)
    }

    @Test
    fun `si no se puede guardar el progreso el despacho continua`() = runTest {
        saveFails = true

        val outcome = orchestrator(settings()).dispatch(realIncident())

        assertEquals(IncidentStatus.DISPATCHED, outcome.status)
        assertEquals(listOf(primary.phoneE164), placedCalls)
    }

    @Test
    fun `resolveStatus cubre todos y ninguno`() {
        assertEquals(IncidentStatus.DISPATCHED, resolveStatus(listOf(true, true)))
        assertEquals(IncidentStatus.DISPATCH_PARTIAL, resolveStatus(listOf(true, false)))
        assertEquals(IncidentStatus.DISPATCH_FAILED, resolveStatus(listOf(false, false)))
        assertEquals(IncidentStatus.DISPATCH_FAILED, resolveStatus(emptyList()))
    }

    private fun orchestrator(settings: AppSettings) = DispatchOrchestrator(
        smsDispatcher = SmsDispatcher(
            transport = SmsTransport { destination, message ->
                events += "sms"
                sentMessages += destination to message
                smsResults[destination] ?: SmsAttemptResult.Sent("sms-$destination")
            },
            hasSendPermission = { smsPermission },
            waitBeforeRetry = {}
        ),
        callDispatcher = CallDispatcher(
            transport = CallTransport { phone ->
                events += "call"
                callFailure?.let { throw it }
                placedCalls += phone
            },
            hasCallPermission = { callPermission }
        ),
        messageBuilder = GsmMessageBuilder(),
        loadSettings = { settings },
        saveIncident = {
            if (saveFails) error("disco lleno")
            saved += it
            true
        },
        clock = clock
    )

    private fun settings(
        riderName: String = "Carlos",
        primary: EmergencyContact? = this.primary,
        secondary: EmergencyContact? = this.secondary,
        drillPlaceCall: Boolean = false
    ) = AppSettings(
        riderName = riderName,
        primaryContact = primary,
        secondaryContact = secondary,
        drillPlaceCall = drillPlaceCall
    )

    private fun realIncident(type: IncidentType = IncidentType.REAL) = Incident(
        type = type,
        triggerType = if (type == IncidentType.DRILL) TriggerType.TEST else TriggerType.IMPACT,
        status = IncidentStatus.ACTIVE,
        detectedAt = now.minusSeconds(20)
    )
}
