package com.motocrashguardian.emergency

import com.motocrashguardian.ble.protocol.GpsPacket
import com.motocrashguardian.core.model.LocationSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ModuleGpsLocationSourceTest {
    private val now = Instant.parse("2026-10-17T18:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun packet(
        fixType: Int = 3,
        latE7: Int = 46_123_456,
        lonE7: Int = -740_654_321,
        fixAgeS: Int = 2
    ) = GpsPacket(
        fixType = fixType,
        satellites = 8,
        hdopX10 = 10,
        latE7 = latE7,
        lonE7 = lonE7,
        speedKmhX100 = 0,
        fixAgeS = fixAgeS
    )

    @Test
    fun `una trama con fix se convierte en GPS de la moto`() {
        val fix = packet().toLocationFix(now)!!

        assertEquals(4.6123456, fix.latitude, 1e-9)
        assertEquals(-74.0654321, fix.longitude, 1e-9)
        assertEquals(LocationSource.DEVICE_GPS, fix.source)
        assertEquals(now.minusSeconds(2), fix.fixAt)
    }

    @Test
    fun `sin fix, nunca fijado o en 0,0 no hay ubicacion`() {
        assertNull(packet(fixType = 0).toLocationFix(now))
        assertNull(packet(fixAgeS = 65_535).toLocationFix(now))
        assertNull(packet(latE7 = 0, lonE7 = 0).toLocationFix(now))
    }

    @Test
    fun `conserva el ultimo fix valido aunque luego llegue una trama sin fix`() = runTest {
        val packets = MutableStateFlow<GpsPacket?>(null)
        val source = ModuleGpsLocationSource(
            packets,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            clock
        )
        assertNull(source.latest())

        packets.value = packet()
        packets.value = packet(fixType = 0)

        assertEquals(LocationSource.DEVICE_GPS, source.latest()?.source)
        assertEquals(LocationSource.LAST_KNOWN, source.lastKnown()?.source)
    }
}
