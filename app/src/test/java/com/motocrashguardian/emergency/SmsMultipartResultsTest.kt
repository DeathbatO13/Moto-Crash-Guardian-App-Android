package com.motocrashguardian.emergency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsMultipartResultsTest {
    @Test
    fun `requiere resultado exitoso para cada parte`() {
        assertTrue(allSmsPartsSucceeded(listOf(1, 1), 2, resultOk = 1))
        assertFalse(allSmsPartsSucceeded(listOf(1, 2), 2, resultOk = 1))
        assertFalse(allSmsPartsSucceeded(listOf(1), 2, resultOk = 1))
    }

    @Test
    fun `acumula resultados aunque lleguen fuera de orden e ignora duplicados`() {
        val collector = SmsMultipartResultCollector(expectedParts = 3)

        assertNull(collector.record(partIndex = 2, resultCode = 1))
        assertNull(collector.record(partIndex = 2, resultCode = 2))
        assertNull(collector.record(partIndex = 0, resultCode = 1))
        assertEquals(listOf(1, 1, 1), collector.record(partIndex = 1, resultCode = 1))
    }

    @Test
    fun `ignora indices invalidos y mantiene resultados parciales`() {
        val collector = SmsMultipartResultCollector(expectedParts = 2)

        assertNull(collector.record(partIndex = -1, resultCode = 1))
        assertNull(collector.record(partIndex = 2, resultCode = 1))
        assertNull(collector.record(partIndex = 0, resultCode = 1))
        assertEquals(listOf(1), collector.snapshot())
    }

    @Test
    fun `usa SIM SMS por defecto y recurre a la SIM de voz solo si falta`() {
        assertEquals(
            3,
            selectSmsSubscriptionId(
                defaultSmsSubscriptionId = 3,
                defaultVoiceSubscriptionId = 4,
                invalidSubscriptionId = -1
            )
        )
        assertEquals(
            4,
            selectSmsSubscriptionId(
                defaultSmsSubscriptionId = -1,
                defaultVoiceSubscriptionId = 4,
                invalidSubscriptionId = -1
            )
        )
        assertNull(
            selectSmsSubscriptionId(
                defaultSmsSubscriptionId = -1,
                defaultVoiceSubscriptionId = -1,
                invalidSubscriptionId = -1
            )
        )
    }
}
