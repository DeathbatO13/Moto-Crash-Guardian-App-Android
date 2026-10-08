package com.motocrashguardian.emergency

import com.motocrashguardian.core.model.CallStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CallDispatcherTest {
    @Test
    fun `coloca la llamada usando el telefono validado`() {
        var requestedNumber: String? = null
        val dispatcher = CallDispatcher(
            transport = CallTransport { requestedNumber = it },
            hasCallPermission = { true }
        )

        val result = dispatcher.placeCall(Destination)

        assertEquals(CallStatus.PLACED, result.status)
        assertNull(result.failure)
        assertEquals(Destination, requestedNumber)
    }

    @Test
    fun `simulacro no llama salvo que se habilite explicitamente`() {
        var calls = 0
        val dispatcher = CallDispatcher(
            transport = CallTransport { calls++ },
            hasCallPermission = { true }
        )

        val result = dispatcher.placeCall(Destination, isDrill = true)

        assertEquals(CallStatus.SKIPPED_DRILL, result.status)
        assertEquals(0, calls)
    }

    @Test
    fun `simulacro habilitado puede iniciar la llamada`() {
        var calls = 0
        val dispatcher = CallDispatcher(
            transport = CallTransport { calls++ },
            hasCallPermission = { true }
        )

        val result = dispatcher.placeCall(
            Destination,
            isDrill = true,
            drillPlaceCall = true
        )

        assertEquals(CallStatus.PLACED, result.status)
        assertEquals(1, calls)
    }

    @Test
    fun `sin permiso no intenta la llamada`() {
        var calls = 0
        val dispatcher = CallDispatcher(
            transport = CallTransport { calls++ },
            hasCallPermission = { false }
        )

        val result = dispatcher.placeCall(Destination)

        assertEquals(CallStatus.NOT_ATTEMPTED, result.status)
        assertEquals(DispatchFailure.PERMISSION_DENIED, result.failure)
        assertEquals(0, calls)
    }

    @Test
    fun `security exception se informa como llamada fallida`() {
        val dispatcher = CallDispatcher(
            transport = CallTransport { throw SecurityException("Permission revoked") },
            hasCallPermission = { true }
        )

        val result = dispatcher.placeCall(Destination)

        assertEquals(CallStatus.FAILED, result.status)
        assertEquals(DispatchFailure.PERMISSION_DENIED, result.failure)
    }

    @Test
    fun `telecom no disponible se informa como fallo`() {
        val dispatcher = CallDispatcher(
            transport = CallTransport { throw IllegalStateException("No telecom") },
            hasCallPermission = { true }
        )

        val result = dispatcher.placeCall(Destination)

        assertEquals(CallStatus.FAILED, result.status)
        assertEquals(DispatchFailure.TRANSPORT_UNAVAILABLE, result.failure)
    }

    private companion object {
        const val Destination = "+573001234567"
    }
}
