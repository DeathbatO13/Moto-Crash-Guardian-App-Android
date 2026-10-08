package com.motocrashguardian.emergency

import kotlinx.coroutines.delay
import com.motocrashguardian.core.model.SmsStatus

class SmsDispatcher(
    private val transport: SmsTransport,
    private val hasSendPermission: () -> Boolean,
    private val waitBeforeRetry: suspend (Long) -> Unit = { delay(it) }
) {
    suspend fun send(phoneE164: String, message: String): SmsDispatchResult {
        require(isValidE164PhoneNumber(phoneE164)) { "Destination must be an E.164 phone number." }
        require(message.isNotBlank()) { "SMS message cannot be blank." }

        if (!hasSendPermission()) {
            return SmsDispatchResult(
                status = SmsStatus.NOT_ATTEMPTED,
                attempts = 0,
                failure = DispatchFailure.PERMISSION_DENIED
            )
        }

        var attempts = 0
        var lastAttempt: SmsAttemptResult = SmsAttemptResult.Failed(DispatchFailure.UNKNOWN)
        val attemptIds = mutableListOf<String>()
        while (attempts < MaximumAttempts) {
            attempts += 1
            lastAttempt = try {
                transport.send(phoneE164, message)
            } catch (_: SecurityException) {
                SmsAttemptResult.Failed(DispatchFailure.PERMISSION_DENIED)
            } catch (_: IllegalArgumentException) {
                SmsAttemptResult.Failed(DispatchFailure.INVALID_DESTINATION)
            }
            lastAttempt.attemptId?.let(attemptIds::add)
            if (lastAttempt is SmsAttemptResult.Sent) {
                return SmsDispatchResult(
                    status = SmsStatus.SENT,
                    attempts = attempts,
                    failure = null,
                    attemptIds = attemptIds
                )
            }
            val reason = (lastAttempt as SmsAttemptResult.Failed).reason
            if (!reason.isRetryable() || attempts == MaximumAttempts) break
            waitBeforeRetry(RetryDelayMillis)
        }

        return SmsDispatchResult(
            status = SmsStatus.FAILED,
            attempts = attempts,
            failure = (lastAttempt as SmsAttemptResult.Failed).reason,
            attemptIds = attemptIds
        )
    }

    private companion object {
        const val MaximumAttempts = 2
        const val RetryDelayMillis = 5_000L
    }
}

internal fun isValidE164PhoneNumber(value: String): Boolean =
    Regex("^\\+[1-9][0-9]{7,14}$").matches(value)

private fun DispatchFailure.isRetryable(): Boolean =
    this != DispatchFailure.PERMISSION_DENIED &&
        this != DispatchFailure.INVALID_DESTINATION &&
        this != DispatchFailure.NO_SMS_SUBSCRIPTION &&
        this != DispatchFailure.TRANSPORT_UNAVAILABLE

fun interface SmsTransport {
    suspend fun send(destinationE164: String, message: String): SmsAttemptResult
}

sealed interface SmsAttemptResult {
    val attemptId: String?

    data class Sent(override val attemptId: String? = null) : SmsAttemptResult

    data class Failed(
        val reason: DispatchFailure,
        override val attemptId: String? = null
    ) : SmsAttemptResult
}

data class SmsDispatchResult(
    val status: SmsStatus,
    val attempts: Int,
    val failure: DispatchFailure?,
    val attemptIds: List<String> = emptyList()
)

enum class DispatchFailure {
    PERMISSION_DENIED,
    INVALID_DESTINATION,
    NO_SERVICE,
    RADIO_OFF,
    GENERIC_FAILURE,
    NULL_PDU,
    LIMIT_EXCEEDED,
    CALLBACK_TIMEOUT,
    NO_SMS_SUBSCRIPTION,
    TRANSPORT_UNAVAILABLE,
    UNKNOWN
}
