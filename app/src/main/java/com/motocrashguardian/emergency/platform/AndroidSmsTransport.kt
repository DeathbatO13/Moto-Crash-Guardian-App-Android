package com.motocrashguardian.emergency.platform

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.motocrashguardian.emergency.DispatchFailure
import com.motocrashguardian.emergency.SmsMultipartResultCollector
import com.motocrashguardian.emergency.SmsAttemptResult
import com.motocrashguardian.emergency.SmsTransport
import com.motocrashguardian.emergency.allSmsPartsSucceeded
import com.motocrashguardian.emergency.selectSmsSubscriptionId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AndroidSmsTransport(
    private val context: Context,
    private val callbackTimeoutMillis: Long = 30_000L
) : SmsTransport {
    val deliveryUpdates: SharedFlow<SmsDeliveryUpdate>
        get() = SmsSentResultRegistry.deliveryUpdates

    override suspend fun send(
        destinationE164: String,
        message: String
    ): SmsAttemptResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return SmsAttemptResult.Failed(DispatchFailure.PERMISSION_DENIED)
        }

        val smsManager = selectedSmsManager()
            ?: return SmsAttemptResult.Failed(DispatchFailure.NO_SMS_SUBSCRIPTION)
        val parts = smsManager.divideMessage(message)
        if (parts.isNullOrEmpty()) {
            return SmsAttemptResult.Failed(DispatchFailure.GENERIC_FAILURE)
        }

        val attemptId = UUID.randomUUID().toString()
        val pendingAttempt = SmsSentResultRegistry.register(attemptId, parts.size)
        val sentIntents = ArrayList(parts.indices.map { partIndex ->
            callbackPendingIntent(attemptId, partIndex, CallbackKind.SENT)
        })
        val deliveryIntents = ArrayList(parts.indices.map { partIndex ->
            callbackPendingIntent(attemptId, partIndex, CallbackKind.DELIVERED)
        })

        return try {
            smsManager.sendMultipartTextMessage(
                destinationE164,
                null,
                parts,
                sentIntents,
                deliveryIntents
            )
            val results = withTimeoutOrNull(callbackTimeoutMillis) {
                pendingAttempt.results.await()
            } ?: SmsSentResultRegistry.resultsSoFar(attemptId)
            if (allSmsPartsSucceeded(results, parts.size, Activity.RESULT_OK)) {
                SmsAttemptResult.Sent(attemptId)
            } else {
                SmsAttemptResult.Failed(results.firstOrNull { it != Activity.RESULT_OK }
                    ?.let(::failureForResult) ?: DispatchFailure.CALLBACK_TIMEOUT, attemptId)
            }
        } catch (exception: SecurityException) {
            SmsAttemptResult.Failed(DispatchFailure.PERMISSION_DENIED, attemptId)
        } catch (exception: IllegalArgumentException) {
            SmsAttemptResult.Failed(DispatchFailure.INVALID_DESTINATION, attemptId)
        } finally {
            SmsSentResultRegistry.remove(attemptId)
        }
    }

    private fun selectedSmsManager(): SmsManager? {
        val selectedSubscriptionId = selectSmsSubscriptionId(
            SmsManager.getDefaultSmsSubscriptionId(),
            SubscriptionManager.getDefaultVoiceSubscriptionId(),
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        ) ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
                ?.createForSubscriptionId(selectedSubscriptionId)
        } else {
            SmsManager.getSmsManagerForSubscriptionId(selectedSubscriptionId)
        }
    }

    private fun callbackPendingIntent(
        attemptId: String,
        partIndex: Int,
        kind: CallbackKind
    ): PendingIntent {
        val action = "${context.packageName}.SMS_${kind.name}.$attemptId.$partIndex"
        val intent = Intent(context, SmsSentReceiver::class.java).apply {
            this.action = action
            putExtra(SmsSentReceiver.EXTRA_ATTEMPT_ID, attemptId)
            putExtra(SmsSentReceiver.EXTRA_PART_INDEX, partIndex)
            putExtra(SmsSentReceiver.EXTRA_CALLBACK_KIND, kind.name)
        }
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

class AndroidSmsPermission(private val context: Context) {
    operator fun invoke(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED
}

enum class SmsDeliveryStatus {
    DELIVERED,
    FAILED
}

data class SmsDeliveryUpdate(
    val attemptId: String,
    val partIndex: Int,
    val status: SmsDeliveryStatus
)

class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val attemptId = intent.getStringExtra(EXTRA_ATTEMPT_ID) ?: return
        val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, -1)
        if (partIndex < 0) return
        when (intent.getStringExtra(EXTRA_CALLBACK_KIND)) {
            CallbackKind.SENT.name ->
                SmsSentResultRegistry.complete(attemptId, partIndex, resultCode)
            CallbackKind.DELIVERED.name ->
                SmsSentResultRegistry.reportDelivery(
                    SmsDeliveryUpdate(
                        attemptId,
                        partIndex,
                        if (resultCode == Activity.RESULT_OK) SmsDeliveryStatus.DELIVERED
                        else SmsDeliveryStatus.FAILED
                    )
                )
        }
    }

    companion object {
        const val EXTRA_ATTEMPT_ID = "com.motocrashguardian.extra.SMS_ATTEMPT_ID"
        const val EXTRA_PART_INDEX = "com.motocrashguardian.extra.SMS_PART_INDEX"
        const val EXTRA_CALLBACK_KIND = "com.motocrashguardian.extra.SMS_CALLBACK_KIND"
    }
}

private enum class CallbackKind {
    SENT,
    DELIVERED
}

private data class PendingSmsAttempt(
    val results: CompletableDeferred<List<Int>>,
    val collector: SmsMultipartResultCollector
)

private object SmsSentResultRegistry {
    private val attempts = ConcurrentHashMap<String, PendingSmsAttempt>()
    private val mutableDeliveryUpdates = MutableSharedFlow<SmsDeliveryUpdate>(
        replay = 32,
        extraBufferCapacity = 32
    )
    val deliveryUpdates: SharedFlow<SmsDeliveryUpdate> = mutableDeliveryUpdates.asSharedFlow()

    fun register(attemptId: String, partCount: Int): PendingSmsAttempt {
        val pending = PendingSmsAttempt(
            results = CompletableDeferred(),
            collector = SmsMultipartResultCollector(partCount)
        )
        check(attempts.putIfAbsent(attemptId, pending) == null) {
            "SMS attempt identifier collision."
        }
        return pending
    }

    fun complete(attemptId: String, partIndex: Int, resultCode: Int) {
        val pending = attempts[attemptId] ?: return
        pending.collector.record(partIndex, resultCode)?.let(pending.results::complete)
    }

    fun resultsSoFar(attemptId: String): List<Int> {
        val pending = attempts[attemptId] ?: return emptyList()
        return pending.collector.snapshot()
    }

    fun reportDelivery(update: SmsDeliveryUpdate) {
        if (!mutableDeliveryUpdates.tryEmit(update)) {
            Log.w(TAG, "Dropping SMS delivery update because the observer is behind.")
        }
    }

    fun remove(attemptId: String) {
        attempts.remove(attemptId)
    }

    private const val TAG = "SmsSentResultRegistry"
}

private fun failureForResult(resultCode: Int): DispatchFailure = when (resultCode) {
    SmsManager.RESULT_ERROR_NO_SERVICE -> DispatchFailure.NO_SERVICE
    SmsManager.RESULT_ERROR_RADIO_OFF -> DispatchFailure.RADIO_OFF
    SmsManager.RESULT_ERROR_NULL_PDU -> DispatchFailure.NULL_PDU
    SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> DispatchFailure.LIMIT_EXCEEDED
    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> DispatchFailure.GENERIC_FAILURE
    else -> DispatchFailure.GENERIC_FAILURE
}
