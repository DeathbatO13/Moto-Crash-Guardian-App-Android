package com.motocrashguardian.emergency

import com.motocrashguardian.core.model.SmsStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsDispatcherTest {
    @Test
    fun `envia sin reintento cuando todos los segmentos salen bien`() = runTest {
        var sends = 0
        var waitMillis: Long? = null
        val dispatcher = SmsDispatcher(
            transport = SmsTransport { _, _ ->
                sends++
                SmsAttemptResult.Sent("attempt-1")
            },
            hasSendPermission = { true },
            waitBeforeRetry = { waitMillis = it }
        )

        val result = dispatcher.send(Destination, "Alerta")

        assertEquals(SmsStatus.SENT, result.status)
        assertEquals(1, result.attempts)
        assertNull(result.failure)
        assertEquals(1, sends)
        assertNull(waitMillis)
        assertEquals(listOf("attempt-1"), result.attemptIds)
    }

    @Test
    fun `reintenta una sola vez luego de cinco segundos`() = runTest {
        var sends = 0
        val waits = mutableListOf<Long>()
        val dispatcher = SmsDispatcher(
            transport = SmsTransport { _, _ ->
                sends++
                if (sends == 1) {
                    SmsAttemptResult.Failed(DispatchFailure.NO_SERVICE, "attempt-$sends")
                }
                else SmsAttemptResult.Sent("attempt-$sends")
            },
            hasSendPermission = { true },
            waitBeforeRetry = { waits += it }
        )

        val result = dispatcher.send(Destination, "Alerta")

        assertEquals(SmsStatus.SENT, result.status)
        assertEquals(2, result.attempts)
        assertEquals(2, sends)
        assertEquals(listOf(5_000L), waits)
        assertEquals(listOf("attempt-1", "attempt-2"), result.attemptIds)
    }

    @Test
    fun `termina fallido despues de exactamente dos intentos`() = runTest {
        var sends = 0
        val dispatcher = SmsDispatcher(
            transport = SmsTransport { _, _ ->
                sends++
                SmsAttemptResult.Failed(DispatchFailure.RADIO_OFF)
            },
            hasSendPermission = { true },
            waitBeforeRetry = { }
        )

        val result = dispatcher.send(Destination, "Alerta")

        assertEquals(SmsStatus.FAILED, result.status)
        assertEquals(2, result.attempts)
        assertEquals(DispatchFailure.RADIO_OFF, result.failure)
        assertEquals(2, sends)
    }

    @Test
    fun `sin permiso no llama al transporte ni reintenta`() = runTest {
        var sends = 0
        val dispatcher = SmsDispatcher(
            transport = SmsTransport { _, _ ->
                sends++
                SmsAttemptResult.Sent()
            },
            hasSendPermission = { false },
            waitBeforeRetry = { error("No debe reintentar") }
        )

        val result = dispatcher.send(Destination, "Alerta")

        assertEquals(SmsStatus.NOT_ATTEMPTED, result.status)
        assertEquals(0, result.attempts)
        assertEquals(DispatchFailure.PERMISSION_DENIED, result.failure)
        assertEquals(0, sends)
    }

    @Test
    fun `permiso revocado durante el envio se informa sin reintento`() = runTest {
        var sends = 0
        val dispatcher = SmsDispatcher(
            transport = SmsTransport { _, _ ->
                sends++
                throw SecurityException("Permission revoked")
            },
            hasSendPermission = { true },
            waitBeforeRetry = { error("No debe reintentar") }
        )

        val result = dispatcher.send(Destination, "Alerta")

        assertEquals(SmsStatus.FAILED, result.status)
        assertEquals(1, result.attempts)
        assertEquals(DispatchFailure.PERMISSION_DENIED, result.failure)
        assertEquals(1, sends)
    }

    @Test
    fun `sin suscripcion disponible termina sin reintento`() = runTest {
        var sends = 0
        val dispatcher = SmsDispatcher(
            transport = SmsTransport { _, _ ->
                sends++
                SmsAttemptResult.Failed(DispatchFailure.NO_SMS_SUBSCRIPTION)
            },
            hasSendPermission = { true },
            waitBeforeRetry = { error("No debe reintentar") }
        )

        val result = dispatcher.send(Destination, "Alerta")

        assertEquals(SmsStatus.FAILED, result.status)
        assertEquals(1, result.attempts)
        assertEquals(DispatchFailure.NO_SMS_SUBSCRIPTION, result.failure)
        assertEquals(1, sends)
    }

    @Test
    fun `rechaza destino no E164 antes de invocar el transporte`() = runTest {
        var sends = 0
        val dispatcher = SmsDispatcher(
            transport = SmsTransport { _, _ ->
                sends++
                SmsAttemptResult.Sent()
            },
            hasSendPermission = { true }
        )

        val exception = org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException::class.java
        ) {
            kotlinx.coroutines.runBlocking {
                dispatcher.send("3001234567", "Alerta")
            }
        }

        assertTrue(exception.message.orEmpty().contains("E.164"))
        assertEquals(0, sends)
    }

    private companion object {
        const val Destination = "+573001234567"
    }
}
