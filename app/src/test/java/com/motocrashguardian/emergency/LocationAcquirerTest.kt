package com.motocrashguardian.emergency

import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.IncidentStatus
import com.motocrashguardian.core.model.IncidentType
import com.motocrashguardian.core.model.LocationSource
import com.motocrashguardian.core.model.TriggerType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class LocationAcquirerTest {
    private val now = Instant.parse("2026-10-19T15:30:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun fix(source: LocationSource, latitude: Double, age: Long = 0) = LocationFix(
        latitude = latitude,
        longitude = -74.0,
        accuracyMeters = 10f,
        source = source,
        fixAt = now.minusSeconds(age)
    )

    private val phone = fix(LocationSource.PHONE_GPS, 1.0)
    private val device = fix(LocationSource.DEVICE_GPS, 2.0, age = 5)
    private val lastKnown = fix(LocationSource.LAST_KNOWN, 3.0, age = 600)

    private fun TestScope.acquirer(
        phoneSource: PhoneLocationSource = PhoneLocationSource { phone },
        lastKnownSource: LastKnownLocationSource = LastKnownLocationSource { lastKnown },
        deviceGps: DeviceGpsSource = DeviceGpsSource { device }
    ) = LocationAcquirer(
        scope = backgroundScope,
        phoneSource = phoneSource,
        lastKnownSource = lastKnownSource,
        deviceGps = deviceGps,
        clock = clock
    )

    @Test
    fun `prefiere el GPS del telefono sobre la moto y la ultima conocida`() = runTest {
        val acquirer = acquirer()
        acquirer.begin()
        runCurrent()

        assertEquals(phone, acquirer.finish())
    }

    @Test
    fun `si el telefono aun no responde usa el GPS reciente de la moto sin esperar`() = runTest {
        val acquirer = acquirer(phoneSource = { awaitCancellation() })
        acquirer.begin()
        runCurrent()

        assertEquals(device, acquirer.finish())
        assertEquals(0, currentTime)
    }

    @Test
    fun `ignora un GPS de la moto desactualizado`() = runTest {
        val acquirer = acquirer(
            phoneSource = { null },
            deviceGps = { device.copy(fixAt = now.minusSeconds(60)) }
        )
        acquirer.begin()
        runCurrent()

        assertEquals(lastKnown, acquirer.finish())
    }

    @Test
    fun `espera un margen corto al telefono cuando no hay nada mejor`() = runTest {
        val acquirer = acquirer(
            phoneSource = {
                delay(1_500)
                phone
            },
            deviceGps = { null }
        )
        acquirer.begin()

        assertEquals(phone, acquirer.finish())
        assertEquals(1_500, currentTime)
    }

    @Test
    fun `si el telefono tarda mas que el margen cae a la ultima conocida`() = runTest {
        val acquirer = acquirer(phoneSource = { awaitCancellation() }, deviceGps = { null })
        acquirer.begin()

        assertEquals(lastKnown, acquirer.finish())
        assertEquals(2_000, currentTime)
    }

    @Test
    fun `el GPS del telefono vence a los diez segundos`() = runTest {
        val acquirer = acquirer(
            phoneSource = {
                delay(20_000)
                phone
            },
            deviceGps = { null }
        )
        acquirer.begin()
        advanceTimeBy(10_001)
        runCurrent()

        assertEquals(lastKnown, acquirer.finish())
        assertEquals(10_001, currentTime)
    }

    @Test
    fun `descarta una ultima ubicacion de mas de 24 horas`() = runTest {
        val acquirer = acquirer(
            phoneSource = { null },
            deviceGps = { null },
            lastKnownSource = { lastKnown.copy(fixAt = now.minusSeconds(25 * 3_600)) }
        )
        acquirer.begin()
        runCurrent()

        assertNull(acquirer.finish())
    }

    @Test
    fun `conserva una ultima ubicacion de menos de 24 horas`() = runTest {
        val old = lastKnown.copy(fixAt = now.minusSeconds(23 * 3_600))
        val acquirer = acquirer(
            phoneSource = { null },
            deviceGps = { null },
            lastKnownSource = { old }
        )
        acquirer.begin()
        runCurrent()

        assertEquals(old, acquirer.finish())
    }

    @Test
    fun `sin ninguna fuente devuelve null`() = runTest {
        val acquirer = acquirer(phoneSource = { null }, lastKnownSource = { null }, deviceGps = { null })
        acquirer.begin()
        runCurrent()

        assertNull(acquirer.finish())
    }

    @Test
    fun `una fuente que lanza excepcion no rompe la busqueda`() = runTest {
        val acquirer = acquirer(
            phoneSource = { error("sin servicios de Google") },
            lastKnownSource = { throw SecurityException("sin permiso") },
            deviceGps = { error("BLE caido") }
        )
        acquirer.begin()
        runCurrent()

        assertNull(acquirer.finish())
    }

    @Test
    fun `finish sin begin inicia la busqueda por su cuenta`() = runTest {
        assertEquals(phone, acquirer(deviceGps = { null }).finish())
    }

    @Test
    fun `finish sin begin usa de inmediato un GPS reciente de la moto`() = runTest {
        assertEquals(device, acquirer().finish())
        assertEquals(0, currentTime)
    }

    @Test
    fun `cancelar interrumpe la busqueda pendiente`() = runTest {
        var cancelled = false
        val acquirer = acquirer(phoneSource = {
            try {
                awaitCancellation()
            } catch (error: CancellationException) {
                cancelled = true
                throw error
            }
        })
        acquirer.begin()
        runCurrent()

        acquirer.cancel()
        runCurrent()

        assertTrue(cancelled)
    }

    @Test
    fun `begin reinicia la busqueda anterior`() = runTest {
        var calls = 0
        val acquirer = acquirer(phoneSource = {
            calls++
            phone
        })
        acquirer.begin()
        runCurrent()
        acquirer.begin()
        runCurrent()

        assertEquals(2, calls)
        assertEquals(phone, acquirer.finish())
    }

    @Test
    fun `withFix copia la ubicacion al incidente y null lo deja igual`() {
        val incident = Incident(
            type = IncidentType.REAL,
            triggerType = TriggerType.IMPACT,
            status = IncidentStatus.ACTIVE,
            detectedAt = now
        )

        val located = incident.withFix(phone)

        assertEquals(1.0, located.latitude)
        assertEquals(-74.0, located.longitude)
        assertEquals(10f, located.locationAccuracyMeters)
        assertEquals(LocationSource.PHONE_GPS, located.locationSource)
        assertEquals(phone.fixAt, located.locationFixAt)
        assertEquals(incident, incident.withFix(null))
    }
}
