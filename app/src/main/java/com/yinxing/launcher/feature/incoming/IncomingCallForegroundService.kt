package com.yinxing.launcher.feature.incoming

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.yinxing.launcher.R
import com.yinxing.launcher.common.lobster.LobsterClient
import com.yinxing.launcher.common.perf.LauncherTraceNames
import com.yinxing.launcher.common.perf.traceBegin
import com.yinxing.launcher.common.util.DebugLog

class IncomingCallForegroundService : Service() {

    private val platformCompat = IncomingPlatformCompat()

    companion object {
        private const val TAG = "IncomingCallService"

        internal const val ACTION_SHOW_INCOMING_CALL = "com.yinxing.launcher.action.SHOW_INCOMING_CALL"
        internal const val NOTIFICATION_ID = 41001
        internal const val CHANNEL_ID = "incoming_call_alerts"

        internal const val EXTRA_CALLER_NAME = "extra_caller_name"
        internal const val EXTRA_AUTO_ANSWER = "extra_auto_answer"
        internal const val EXTRA_INCOMING_NUMBER = "extra_incoming_number"
        internal const val EXTRA_KNOWN_CONTACT = "extra_known_contact"

        fun start(
            context: Context,
            callerName: String?,
            autoAnswer: Boolean,
            incomingNumber: String? = null,
            knownContact: Boolean = false
        ) {
            val intent = Intent(context, IncomingCallForegroundService::class.java).apply {
                action = ACTION_SHOW_INCOMING_CALL
                putExtra(EXTRA_CALLER_NAME, callerName)
                putExtra(EXTRA_AUTO_ANSWER, autoAnswer)
                putExtra(EXTRA_INCOMING_NUMBER, incomingNumber)
                putExtra(EXTRA_KNOWN_CONTACT, knownContact)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
            context.stopService(Intent(context, IncomingCallForegroundService::class.java))
        }

        fun ensureNotificationChannels(
            context: Context,
            platformCompat: IncomingPlatformCompat = IncomingPlatformCompat()
        ) {
            if (!platformCompat.supportsNotificationChannels) return
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
                autoAnswer = intent.getBooleanExtra(EXTRA_AUTO_ANSWER, false),
                incomingNumber = intent.getStringExtra(EXTRA_INCOMING_NUMBER),
                knownContact = intent.getBooleanExtra(EXTRA_KNOWN_CONTACT, false)
            )
        } else {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (platformCompat.supportsStopForegroundRemoveFlag) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    private fun showIncomingCall(
        callerName: String?,
        autoAnswer: Boolean,
        incomingNumber: String?,
        knownContact: Boolean
    ) {
        DebugLog.banner(
            "INCOMING_SERVICE",
            listOf(
                "[来电服务] 准备显示来电界面",
                "├─ 姓名: $callerName",
                "├─ 号码: $incomingNumber",
                "├─ 自动接听(广播传参): $autoAnswer",
                "└─ 已知联系人: $knownContact"
            )
        )

        LobsterClient.log("[来电服务] 启动: Caller=$callerName, Number=$incomingNumber, Auto=$autoAnswer")
        traceBegin(LauncherTraceNames.INCOMING_CALL_RESPONSE)

        ensureNotificationChannels(this, platformCompat)
        IncomingCallDiagnostics.recordServiceStarted(this, callerName, autoAnswer)
        val callerLabel = callerName?.trim()?.takeIf { it.isNotEmpty() }
            ?: getString(R.string.incoming_call_unknown_caller)

        val openIntent = IncomingCallActivity.buildLaunchIntent(
            context = this,
            callerName = callerName,
            autoAnswer = autoAnswer,
            incomingNumber = incomingNumber,
            knownContact = knownContact
        )
        val acceptIntent = IncomingCallActivity.buildLaunchIntent(
            context = this,
            callerName = callerName,
            autoAnswer = autoAnswer,
            incomingNumber = incomingNumber,
            knownContact = knownContact,
            triggerAction = IncomingCallActivity.TRIGGER_ACTION_ACCEPT
        )
        val declineIntent = IncomingCallActivity.buildLaunchIntent(
            context = this,
            callerName = callerName,
            autoAnswer = autoAnswer,
            incomingNumber = incomingNumber,
            knownContact = knownContact,
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

        val startedInForeground = runCatching {
            startForeground(NOTIFICATION_ID, notification)
            true
        }.getOrElse { error ->
            DebugLog.e(TAG, "startForeground failed, sdk=${platformCompat.sdkInt}", error)
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
            false
        }

        if (startedInForeground) {
            IncomingCallSessionState.foregroundServiceStarted(callerLabel, autoAnswer)
        }
        IncomingCallDiagnostics.recordForegroundServiceStartResult(
            context = this,
            callerLabel = callerLabel,
            started = startedInForeground
        )

        launchIncomingCallUi(openIntent)
        if (!startedInForeground) {
            stopSelf()
        }
    }

    private fun launchIncomingCallUi(intent: Intent) {
        runCatching {
            startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }.onFailure {
            DebugLog.w(TAG, "launchIncomingCallUi failed: ${it.message}")
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
