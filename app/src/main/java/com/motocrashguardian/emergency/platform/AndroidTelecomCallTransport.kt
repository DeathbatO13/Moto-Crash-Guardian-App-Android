package com.motocrashguardian.emergency.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import com.motocrashguardian.emergency.CallTransport

class AndroidTelecomCallTransport(
    private val context: Context
) : CallTransport {
    override fun placeCall(phoneE164: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("CALL_PHONE permission is not granted.")
        }
        val telecomManager = context.getSystemService(TelecomManager::class.java)
            ?: throw IllegalStateException("Telecom service is unavailable.")
        telecomManager.placeCall(Uri.fromParts("tel", phoneE164, null), Bundle())
    }
}

class AndroidCallPermission(private val context: Context) {
    operator fun invoke(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
}
