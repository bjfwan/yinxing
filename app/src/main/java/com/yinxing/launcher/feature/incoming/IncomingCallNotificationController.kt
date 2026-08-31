package com.yinxing.launcher.feature.incoming

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.yinxing.launcher.R

/** Shared call notification builder used by both Telecom and the legacy receiver path. */
internal object IncomingCallNotificationController {
    const val NOTIFICATION_ID = 41001
    const val CHANNEL_ID = "incoming_call_alerts"

    fun ensureChannel(
        context: Context,
        platformCompat: IncomingPlatformCompat = IncomingPlatformCompat()
    ) {
        if (!platformCompat.supportsNotificationChannels) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.incoming_call_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.incoming_call_notification_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    fun buildIncoming(
        context: Context,
        callerName: String?,
        uiAutoAnswer: Boolean,
        incomingNumber: String?,
        knownContact: Boolean
    ): Notification {
        ensureChannel(context)
        val callerLabel = callerName?.trim()?.takeIf(String::isNotEmpty)
            ?: context.getString(R.string.incoming_call_unknown_caller)
        val openIntent = IncomingCallActivity.buildLaunchIntent(
            context,
            callerName,
            uiAutoAnswer,
            incomingNumber,
            knownContact
        )
        val acceptIntent = IncomingCallActivity.buildLaunchIntent(
            context,
            callerName,
            uiAutoAnswer,
            incomingNumber,
            knownContact,
            IncomingCallActivity.TRIGGER_ACTION_ACCEPT
        )
        val declineIntent = IncomingCallActivity.buildLaunchIntent(
            context,
            callerName,
            uiAutoAnswer,
            incomingNumber,
            knownContact,
            IncomingCallActivity.TRIGGER_ACTION_DECLINE
        )
        val openPendingIntent = activityPendingIntent(context, 100, openIntent)
        val acceptPendingIntent = activityPendingIntent(context, 102, acceptIntent)
        val declinePendingIntent = activityPendingIntent(context, 103, declineIntent)
        val person = Person.Builder().setName(callerLabel).setImportant(true).build()

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(context.getString(R.string.incoming_call_notification_title))
            .setContentText(callerLabel)
            .setSubText(IncomingCallDiagnostics.getNotificationStatusText(context))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openPendingIntent)
            .setFullScreenIntent(openPendingIntent, true)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    person,
                    declinePendingIntent,
                    acceptPendingIntent
                )
            )
            .build()
    }

    fun buildOngoing(context: Context, callerName: String?): Notification {
        ensureChannel(context)
        val callerLabel = callerName?.trim()?.takeIf(String::isNotEmpty)
            ?: context.getString(R.string.incoming_call_unknown_caller)
        val openIntent = IncomingCallActivity.buildLaunchIntent(
            context = context,
            callerName = callerName,
            autoAnswer = false
        )
        val endIntent = IncomingCallActivity.buildLaunchIntent(
            context = context,
            callerName = callerName,
            autoAnswer = false,
            triggerAction = IncomingCallActivity.TRIGGER_ACTION_DECLINE
        )
        val person = Person.Builder().setName(callerLabel).setImportant(true).build()
        val endPendingIntent = activityPendingIntent(context, 111, endIntent)
        val openPendingIntent = activityPendingIntent(context, 110, openIntent)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(context.getString(R.string.incoming_call_ongoing_notification_title))
            .setContentText(callerLabel)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .setFullScreenIntent(openPendingIntent, true)
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(person, endPendingIntent))
            .build()
    }

    fun notifyIncoming(
        context: Context,
        callerName: String?,
        incomingNumber: String?,
        knownContact: Boolean
    ) {
        val notification = buildIncoming(
            context = context,
            callerName = callerName,
            uiAutoAnswer = false,
            incomingNumber = incomingNumber,
            knownContact = knownContact
        )
        context.getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, notification)
    }

    fun notifyOngoing(context: Context, callerName: String?) {
        context.getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, buildOngoing(context, callerName))
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    private fun activityPendingIntent(
        context: Context,
        requestCode: Int,
        intent: android.content.Intent
    ): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
