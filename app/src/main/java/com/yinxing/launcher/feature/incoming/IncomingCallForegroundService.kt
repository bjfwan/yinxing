package com.yinxing.launcher.feature.incoming

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
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
        internal const val ACTION_SHOW_ONGOING_CALL = "com.yinxing.launcher.action.SHOW_ONGOING_CALL"
        internal const val NOTIFICATION_ID = IncomingCallNotificationController.NOTIFICATION_ID
        internal const val CHANNEL_ID = IncomingCallNotificationController.CHANNEL_ID

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
            IncomingCallNotificationController.cancel(context)
            context.stopService(Intent(context, IncomingCallForegroundService::class.java))
        }

        fun showOngoing(context: Context, callerName: String?) {
            val intent = Intent(context, IncomingCallForegroundService::class.java).apply {
                action = ACTION_SHOW_ONGOING_CALL
                putExtra(EXTRA_CALLER_NAME, callerName)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun ensureNotificationChannels(
            context: Context,
            platformCompat: IncomingPlatformCompat = IncomingPlatformCompat()
        ) {
            IncomingCallNotificationController.ensureChannel(context, platformCompat)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_INCOMING_CALL -> showIncomingCall(
                callerName = intent.getStringExtra(EXTRA_CALLER_NAME),
                autoAnswer = intent.getBooleanExtra(EXTRA_AUTO_ANSWER, false),
                incomingNumber = intent.getStringExtra(EXTRA_INCOMING_NUMBER),
                knownContact = intent.getBooleanExtra(EXTRA_KNOWN_CONTACT, false)
            )
            ACTION_SHOW_ONGOING_CALL -> showOngoingCall(intent.getStringExtra(EXTRA_CALLER_NAME))
            else -> stopSelf()
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

        val notification = IncomingCallNotificationController.buildIncoming(
            context = this,
            callerName = callerName,
            uiAutoAnswer = autoAnswer,
            incomingNumber = incomingNumber,
            knownContact = knownContact
        )

        val startedInForeground = runCatching {
            startForeground(NOTIFICATION_ID, notification)
            true
        }.getOrElse { error ->
            DebugLog.e(TAG, "startForeground failed, sdk=${platformCompat.sdkInt}", error)
            getSystemService(android.app.NotificationManager::class.java)
                ?.notify(NOTIFICATION_ID, notification)
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

        if (!startedInForeground) {
            stopSelf()
        }
    }

    private fun showOngoingCall(callerName: String?) {
        startForeground(
            NOTIFICATION_ID,
            IncomingCallNotificationController.buildOngoing(this, callerName)
        )
    }
}
