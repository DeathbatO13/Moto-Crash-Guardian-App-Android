package com.motocrashguardian.ui.history

import com.motocrashguardian.core.model.CallStatus
import com.motocrashguardian.core.model.IncidentStatus
import com.motocrashguardian.core.model.IncidentType
import com.motocrashguardian.core.model.LocationSource
import com.motocrashguardian.core.model.SmsStatus
import com.motocrashguardian.core.model.SyncState
import com.motocrashguardian.core.model.TriggerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

class HistoryLabelsTest {

    /** Cada valor del dominio tiene un texto propio: si se agrega un enum nuevo, esto avisa. */
    private fun <T> assertDistinctLabels(values: Collection<T>, label: (T) -> String) {
        val labels = values.map(label)
        assertTrue(labels.all { it.isNotBlank() }, "hay etiquetas vacias: $labels")
        assertEquals(labels.size, labels.toSet().size, "hay etiquetas repetidas: $labels")
    }

    @Test
    fun `todos los estados tienen una etiqueta distinta`() =
        assertDistinctLabels(IncidentStatus.entries) { it.label() }

    @Test
    fun `todos los detonantes tienen una etiqueta distinta`() =
        assertDistinctLabels(TriggerType.entries) { it.label() }

    @Test
    fun `todos los tipos de incidente tienen etiqueta`() =
        assertDistinctLabels(IncidentType.entries) { it.label() }

    @Test
    fun `todos los estados de SMS tienen una etiqueta distinta`() =
        assertDistinctLabels(SmsStatus.entries) { it.label() }

    @Test
    fun `todos los estados de llamada tienen una etiqueta distinta`() =
        assertDistinctLabels(CallStatus.entries) { it.label() }

    @Test
    fun `todas las fuentes de ubicacion tienen una etiqueta distinta`() =
        assertDistinctLabels(LocationSource.entries) { it.label() }

    @Test
    fun `todos los estados de respaldo tienen una etiqueta distinta`() =
        assertDistinctLabels(SyncState.entries) { it.label() }

    @Test
    fun `solo DISPATCHED y DISPATCH_PARTIAL cuentan como aviso enviado`() {
        val dispatched = IncidentStatus.entries.filter { it.isDispatched() }

        assertEquals(setOf(IncidentStatus.DISPATCHED, IncidentStatus.DISPATCH_PARTIAL), dispatched.toSet())
    }

    @Test
    fun `fecha y hora se muestran en espanol y en la zona indicada`() {
        val text = formatDateTime(Instant.parse("2026-10-19T15:30:00Z"), ZoneId.of("America/Bogota"))

        assertTrue(text.contains("10:30"), text) // UTC-5
        assertTrue(text.contains("19/10/2026"), text)
    }

    @Test
    fun `la misma hora en otra zona cambia`() {
        val instant = Instant.parse("2026-10-19T15:30:00Z")

        assertNotEquals(
            formatDateTime(instant, ZoneId.of("America/Bogota")),
            formatDateTime(instant, ZoneId.of("UTC"))
        )
    }

    @Test
    fun `g y grados usan coma decimal`() {
        assertEquals("9,5 g", formatG(9_500))
        assertEquals("0,0 g", formatG(0))
        assertEquals("-45,5°", formatDegrees(-4_550))
        assertFalse(formatG(3_500).contains('.'))
    }
}
