package com.bajianfeng.launcher.feature.incoming

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.common.util.PermissionUtil

class IncomingCallForegroundService : Service() {

    companion object {
        internal const val ACTION_SHOW_INCOMING_CALL = "com.bajianfeng.launcher.action.SHOW_INCOMING_CALL"
        internal const val NOTIFICATION_ID = 41001
        internal const val CHANNEL_ID = "incoming_call_alerts"

        internal const val EXTRA_CALLER_NAME = "extra_caller_name"
        internal const val EXTRA_AUTO_ANSWER = "extra_auto_answer"

        fun start(context: Context, callerName: String?, autoAnswer: Boolean) {
            val intent = Intent(context, IncomingCallForegroundService::class.java).apply {
                action = ACTION_SHOW_INCOMING_CALL
                putExtra(EXTRA_CALLER_NAME, callerName)
                putExtra(EXTRA_AUTO_ANSWER, autoAnswer)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, IncomingCallForegroundService::class.java))
        }

        fun ensureNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.incoming_call_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.incoming_call_notification_channel_description)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHOW_INCOMING_CALL) {
            showIncomingCall(
                callerName = intent.getStringExtra(EXTRA_CALLER_NAME),
                autoAnswer = intent.getBooleanExtra(EXTRA_AUTO_ANSWER, false)
            )
        } else {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    private fun showIncomingCall(callerName: String?, autoAnswer: Boolean) {
        ensureNotificationChannels(this)
        IncomingCallDiagnostics.recordServiceStarted(this, callerName, autoAnswer)
        val callerLabel = callerName?.trim()?.takeIf { it.isNotEmpty() }
            ?: getString(R.string.incoming_call_unknown_caller)

        val openIntent = IncomingCallActivity.buildLaunchIntent(
            context = this,
            callerName = callerName,
            autoAnswer = autoAnswer
        )
        val acceptIntent = IncomingCallActivity.buildLaunchIntent(
            context = this,
            callerName = callerName,
            autoAnswer = autoAnswer,
            triggerAction = IncomingCallActivity.TRIGGER_ACTION_ACCEPT
        )
        val declineIntent = IncomingCallActivity.buildLaunchIntent(
            context = this,
            callerName = callerName,
            autoAnswer = autoAnswer,
            triggerAction = IncomingCallActivity.TRIGGER_ACTION_DECLINE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(getString(R.string.incoming_call_notification_title))
            .setContentText(callerLabel)
            .setSubText(IncomingCallDiagnostics.getNotificationStatusText(this))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(createActivityPendingIntent(100, openIntent))
            .setFullScreenIntent(createActivityPendingIntent(101, openIntent), true)
            .addAction(
                android.R.drawable.sym_action_call,
                getString(R.string.incoming_call_accept),
                createActivityPendingIntent(102, acceptIntent)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.incoming_call_decline),
                createActivityPendingIntent(103, declineIntent)
            )
            .build()

        startForeground(NOTIFICATION_ID, notification)

        if (!PermissionUtil.hasNotificationPermission(this)) {
            runCatching { startActivity(openIntent) }
        }
    }

    private fun createActivityPendingIntent(requestCode: Int, intent: Intent): PendingIntent {
        return PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
