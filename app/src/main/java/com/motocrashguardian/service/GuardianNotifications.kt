package com.motocrashguardian.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.motocrashguardian.R

/**
 * Notificación persistente del servicio de conexión BLE (docs/05 §3.1):
 * canal de baja importancia + notification ongoing con el estado de la
 * conexión.
 */
class GuardianNotifications(private val context: Context) {

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.service_channel_description)
        }
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    /** Notification ongoing con el texto de estado dado. */
    fun build(statusTextRes: Int): Notification {
        val openApp = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?: Intent()
        val contentIntent = PendingIntent.getActivity(
            context, 0, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.service_notification_title))
            .setContentText(context.getString(statusTextRes))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "ble_connection"
    }
}
