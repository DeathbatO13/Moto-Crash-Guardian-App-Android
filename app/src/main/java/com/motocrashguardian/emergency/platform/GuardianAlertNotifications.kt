package com.motocrashguardian.emergency.platform

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.motocrashguardian.R
import com.motocrashguardian.ui.alert.AlertActivity

class GuardianAlertNotifications(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)
        ?: throw IllegalStateException("Notification service is unavailable.")

    fun showCountdown(remainingSeconds: Int): AlertNotificationVisibility {
        require(remainingSeconds >= 0)
        if (!canPostNotifications()) return AlertNotificationVisibility.NOTIFICATIONS_DISABLED

        createChannel()
        val fullScreenAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            notificationManager.canUseFullScreenIntent()
        val alertPendingIntent = alertPendingIntent(REQUEST_ALERT_CONTENT)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(appContext.getString(R.string.alert_notification_title))
            .setContentText(
                appContext.getString(R.string.alert_notification_countdown, remainingSeconds)
            )
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSound(null)
            .setVibrate(null)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(alertPendingIntent)
            .setFullScreenIntent(alertPendingIntent, fullScreenAllowed)
            .addAction(
                android.R.drawable.ic_menu_view,
                appContext.getString(R.string.alert_notification_open),
                alertPendingIntent(REQUEST_ALERT_ACTION)
            )
            .build()
        try {
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            return AlertNotificationVisibility.NOTIFICATIONS_DISABLED
        }
        return if (fullScreenAllowed) {
            AlertNotificationVisibility.FULL_SCREEN_INTENT
        } else {
            AlertNotificationVisibility.HEADS_UP
        }
    }

    fun cancelCountdown() {
        NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID)
    }

    private fun canPostNotifications(): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(appContext).areNotificationsEnabled()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.alert_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = appContext.getString(R.string.alert_notification_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    private fun alertPendingIntent(requestCode: Int): PendingIntent {
        val intent = Intent(appContext, AlertActivity::class.java).apply {
            action = ACTION_OPEN_ALERT
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        const val CHANNEL_ID = "guardian_alert"
        const val ACTION_OPEN_ALERT = "com.motocrashguardian.action.OPEN_ALERT"
        const val NOTIFICATION_ID = 2_001
        const val REQUEST_ALERT_CONTENT = 2_002
        const val REQUEST_ALERT_ACTION = 2_003
    }
}

enum class AlertNotificationVisibility {
    FULL_SCREEN_INTENT,
    HEADS_UP,
    NOTIFICATIONS_DISABLED
}
