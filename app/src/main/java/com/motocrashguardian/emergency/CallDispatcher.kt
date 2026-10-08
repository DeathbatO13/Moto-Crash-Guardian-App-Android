package com.motocrashguardian.emergency

import com.motocrashguardian.core.model.CallStatus

class CallDispatcher(
    private val transport: CallTransport,
    private val hasCallPermission: () -> Boolean
) {
    fun placeCall(
        phoneE164: String,
        isDrill: Boolean = false,
        drillPlaceCall: Boolean = false
    ): CallDispatchResult {
        require(isValidE164PhoneNumber(phoneE164)) { "Destination must be an E.164 phone number." }
        if (isDrill && !drillPlaceCall) {
            return CallDispatchResult(CallStatus.SKIPPED_DRILL)
        }
        if (!hasCallPermission()) {
            return CallDispatchResult(CallStatus.NOT_ATTEMPTED, DispatchFailure.PERMISSION_DENIED)
        }
        return try {
            transport.placeCall(phoneE164)
            CallDispatchResult(CallStatus.PLACED)
        } catch (_: SecurityException) {
            CallDispatchResult(CallStatus.FAILED, DispatchFailure.PERMISSION_DENIED)
        } catch (_: IllegalArgumentException) {
            CallDispatchResult(CallStatus.FAILED, DispatchFailure.INVALID_DESTINATION)
        } catch (_: IllegalStateException) {
            CallDispatchResult(CallStatus.FAILED, DispatchFailure.TRANSPORT_UNAVAILABLE)
        }
    }
}

fun interface CallTransport {
    fun placeCall(phoneE164: String)
}

data class CallDispatchResult(
    val status: CallStatus,
    val failure: DispatchFailure? = null
)
