package com.motocrashguardian.data.sync

import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.core.model.CallStatus
import com.motocrashguardian.core.model.ContactRole
import com.motocrashguardian.core.model.DetectionConfig
import com.motocrashguardian.core.model.EmergencyContact
import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.IncidentStatus
import com.motocrashguardian.core.model.IncidentType
import com.motocrashguardian.core.model.LocationSource
import com.motocrashguardian.core.model.SmsStatus
import com.motocrashguardian.core.model.Trace
import com.motocrashguardian.core.model.TriggerType
import com.motocrashguardian.data.remote.ApiJson
import com.motocrashguardian.data.remote.IncidentUpsertRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Base64
import java.util.UUID

class SyncMappersTest {

    private val detectedAt = Instant.parse("2026-10-19T15:30:00Z")

    private fun contact(role: ContactRole, name: String, phone: String) =
        EmergencyContact(role, name, phone)

    private fun incident(block: Incident.() -> Incident = { this }) = Incident(
        type = IncidentType.REAL,
        triggerType = TriggerType.SEVERE_IMPACT,
        status = IncidentStatus.DISPATCHED,
        detectedAt = detectedAt
    ).block()

    // --- Config ---

    @Test
    fun `config copia ajustes de deteccion y cuenta regresiva`() {
        val settings = AppSettings(
            riderName = "  Carlos  ",
            detection = DetectionConfig(impactThresholdMg = 4_000, tiltDetectionEnabled = false),
            countdownSeconds = 30
        )

        val request = settings.toConfigRequest()!!

        assertEquals("Carlos", request.riderName)
        assertEquals(4_000, request.impactThresholdMg)
        assertEquals(false, request.tiltDetectionEnabled)
        assertEquals(30, request.countdownSeconds)
        assertEquals(8_000, request.severeImpactThresholdMg)
    }

    @Test
    fun `config es null sin nombre y recorta nombres de mas de 60`() {
        assertNull(AppSettings(riderName = "   ").toConfigRequest())
        assertEquals(60, AppSettings(riderName = "A".repeat(90)).toConfigRequest()!!.riderName.length)
    }

    // --- Contactos ---

    @Test
    fun `contactos validos se mapean con su rol`() {
        val request = AppSettings(
            primaryContact = contact(ContactRole.PRIMARY, "Ana", "+573001112233"),
            secondaryContact = contact(ContactRole.SECONDARY, "Luis", "+573004445566")
        ).toContactsRequest()!!

        assertEquals(listOf("PRIMARY", "SECONDARY"), request.contacts.map { it.role })
        assertEquals("+573004445566", request.contacts[1].phoneE164)
    }

    @Test
    fun `sin contactos es una lista vacia valida`() {
        assertEquals(emptyList<Any>(), AppSettings().toContactsRequest()!!.contacts)
    }

    @Test
    fun `contactos que el backend rechazaria no se envian`() {
        val primary = contact(ContactRole.PRIMARY, "Ana", "+573001112233")
        val invalid = listOf(
            AppSettings(primaryContact = contact(ContactRole.PRIMARY, "Ana", "3001112233")),
            AppSettings(primaryContact = contact(ContactRole.PRIMARY, "  ", "+573001112233")),
            AppSettings(primaryContact = contact(ContactRole.PRIMARY, "A".repeat(61), "+573001112233")),
            AppSettings(secondaryContact = contact(ContactRole.SECONDARY, "Luis", "+573004445566")),
            AppSettings(
                primaryContact = primary,
                secondaryContact = contact(ContactRole.SECONDARY, "Luis", "+573001112233")
            )
        )

        invalid.forEach { assertNull(it.toContactsRequest(), it.toString()) }
    }

    // --- Incidentes ---

    @Test
    fun `incidente copia tipo, estado, despacho y versiones`() {
        val request = incident {
            copy(
                deviceEventKey = "57:3",
                primarySmsStatus = SmsStatus.SENT,
                secondarySmsStatus = SmsStatus.FAILED,
                callStatus = CallStatus.PLACED,
                firmwareVersion = "1.2.3",
                peakAccelMg = 9_500
            )
        }.toUpsertRequest("1.0.0")

        assertEquals("REAL", request.type)
        assertEquals("SEVERE_IMPACT", request.triggerType)
        assertEquals("DISPATCHED", request.status)
        assertEquals("57:3", request.deviceEventKey)
        assertEquals("SENT", request.dispatch.smsPrimary)
        assertEquals("FAILED", request.dispatch.smsSecondary)
        assertEquals("PLACED", request.dispatch.call)
        assertEquals("1.2.3", request.firmwareVersion)
        assertEquals("1.0.0", request.appVersion)
        assertEquals(9_500, request.peakAccelMg)
        assertEquals("2026-10-19T15:30:00Z", request.detectedAt)
    }

    @Test
    fun `resolvedAt nunca es anterior a detectedAt`() {
        val request = incident { copy(resolvedAt = detectedAt.minusSeconds(5)) }.toUpsertRequest("1")

        assertEquals(detectedAt.toString(), request.resolvedAt)
    }

    @Test
    fun `ubicacion se redondea a 6 decimales y conserva la fuente`() {
        val request = incident {
            copy(
                latitude = 4.710989123456,
                longitude = -74.0720919999,
                locationAccuracyMeters = 12.5f,
                locationSource = LocationSource.PHONE_GPS,
                locationFixAt = detectedAt
            )
        }.toUpsertRequest("1")

        val location = request.location!!
        assertEquals(4.710989, location.latitude)
        assertEquals(-74.072092, location.longitude)
        assertEquals(12.5f, location.accuracyM)
        assertEquals("PHONE_GPS", location.source)
    }

    @Test
    fun `sin ubicacion no se envia el objeto location`() {
        assertNull(incident().toUpsertRequest("1").location)
        assertNull(
            incident { copy(locationSource = LocationSource.PHONE_GPS) }.toUpsertRequest("1").location
        )
    }

    @Test
    fun `una precision negativa se omite`() {
        val request = incident {
            copy(
                latitude = 1.0,
                longitude = 2.0,
                locationAccuracyMeters = -1f,
                locationSource = LocationSource.LAST_KNOWN
            )
        }.toUpsertRequest("1")

        assertNull(request.location!!.accuracyM)
    }

    @Test
    fun `una clave de evento de mas de 16 caracteres se omite`() {
        assertNull(incident { copy(deviceEventKey = "x".repeat(17)) }.toUpsertRequest("1").deviceEventKey)
        assertNotNull(incident { copy(deviceEventKey = "4294967295:65535") }.toUpsertRequest("1").deviceEventKey)
    }

    // --- Trazas ---

    @Test
    fun `traza se codifica en Base64 y conserva metadatos`() {
        val samples = ByteArray(24) { it.toByte() }
        val request = Trace(
            incidentId = UUID.randomUUID(),
            sampleRateHz = 100,
            preTriggerSamples = 1,
            totalSamples = 2,
            accelLsbPerG = 2048,
            gyroLsbPerDpsX10 = 164,
            samples = samples
        ).toRequest()!!

        assertEquals(100, request.sampleRateHz)
        assertEquals(2, request.totalSamples)
        assertTrue(Base64.getDecoder().decode(request.samplesBase64).contentEquals(samples))
    }

    @Test
    fun `traza demasiado grande para 64 KB devuelve null`() {
        val trace = Trace(
            incidentId = UUID.randomUUID(),
            sampleRateHz = 100,
            preTriggerSamples = 0,
            totalSamples = 5_000,
            accelLsbPerG = 2048,
            gyroLsbPerDpsX10 = 164,
            samples = ByteArray(12 * 5_000)
        )

        assertNull(trace.toRequest())
    }

    // --- Contrato JSON con el backend ---

    @Test
    fun `el JSON del incidente usa los nombres del backend y omite los nulos`() {
        val json = ApiJson.encodeToString(
            IncidentUpsertRequest.serializer(),
            incident { copy(callStatus = CallStatus.PLACED) }.toUpsertRequest("1.0.0")
        )

        assertTrue(json.contains("\"triggerType\":\"SEVERE_IMPACT\""), json)
        assertTrue(json.contains("\"dispatch\":{\"smsPrimary\":\"NOT_ATTEMPTED\""), json)
        assertTrue(json.contains("\"call\":\"PLACED\""), json)
        assertTrue(!json.contains("null"), json)
        assertTrue(!json.contains("location"), json)
    }
}
