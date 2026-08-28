package com.yinxing.launcher.feature.fall

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.yinxing.launcher.R
import com.yinxing.launcher.feature.settings.SettingsActivity

internal object FallDetectionNotificationController {
    const val MONITORING_NOTIFICATION_ID = 42001
    const val ALERT_NOTIFICATION_ID = 42002
    const val MONITORING_CHANNEL_ID = "fall_detection_monitoring"
    const val ALERT_CHANNEL_ID = "fall_detection_alerts"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(MONITORING_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    MONITORING_CHANNEL_ID,
                    context.getString(R.string.fall_detection_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.fall_detection_channel_description)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                }
            )
        }
        if (manager.getNotificationChannel(ALERT_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    ALERT_CHANNEL_ID,
                    context.getString(R.string.fall_alert_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.fall_alert_channel_description)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0L, 800L, 400L, 800L, 400L, 1_200L)
                }
            )
        }
    }

    fun buildMonitoring(context: Context, sensorAvailable: Boolean = true): Notification {
        ensureChannels(context)
        val openSettings = PendingIntent.getActivity(
            context,
            42010,
            Intent(context, SettingsActivity::class.java)
                .putExtra(SettingsActivity.EXTRA_SECTION, "calls"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, MONITORING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(context.getString(R.string.fall_detection_notification_title))
            .setContentText(
                context.getString(
                    if (sensorAvailable) R.string.fall_detection_notification_text
                    else R.string.fall_detection_sensor_unavailable_notification
                )
            )
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(sensorAvailable)
            .setContentIntent(openSettings)
            .build()
    }

    fun showAlert(context: Context) {
        ensureChannels(context)
        context.getSystemService(NotificationManager::class.java)
            ?.notify(ALERT_NOTIFICATION_ID, buildAlert(context))
    }

    fun buildAlert(context: Context): Notification {
        val openIntent = PendingIntent.getActivity(
            context,
            42020,
            FallAlertActivity.buildLaunchIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelIntent = serviceAction(context, 42021, FallDetectionService.ACTION_FALSE_ALARM)
        val callIntent = serviceAction(context, 42022, FallDetectionService.ACTION_CALL_FAMILY)
        return NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.fall_alert_notification_title))
            .setContentText(context.getString(R.string.fall_alert_notification_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openIntent)
            .setFullScreenIntent(openIntent, true)
            .addAction(0, context.getString(R.string.fall_alert_cancel), cancelIntent)
            .addAction(0, context.getString(R.string.fall_alert_call_now), callIntent)
            .build()
    }

    fun cancelAlert(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ALERT_NOTIFICATION_ID)
    }

    fun cancelMonitoring(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(MONITORING_NOTIFICATION_ID)
    }

    private fun serviceAction(context: Context, requestCode: Int, action: String): PendingIntent {
        return PendingIntent.getService(
            context,
            requestCode,
            Intent(context, FallDetectionService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
