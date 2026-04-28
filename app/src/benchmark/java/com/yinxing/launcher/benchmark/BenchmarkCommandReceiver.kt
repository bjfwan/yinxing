package com.yinxing.launcher.benchmark

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yinxing.launcher.feature.incoming.IncomingCallForegroundService

class BenchmarkCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SHOW_INCOMING_CALL -> {
                IncomingCallForegroundService.stop(context)
                IncomingCallForegroundService.start(
                    context = context,
                    callerName = intent.getStringExtra(EXTRA_CALLER_NAME),
                    autoAnswer = false
                )
            }

            ACTION_DISMISS_INCOMING_CALL -> {
                IncomingCallForegroundService.stop(context)
            }
        }
    }

    companion object {
        const val ACTION_SHOW_INCOMING_CALL = "com.yinxing.launcher.benchmark.SHOW_INCOMING_CALL"
        const val ACTION_DISMISS_INCOMING_CALL = "com.yinxing.launcher.benchmark.DISMISS_INCOMING_CALL"
        const val EXTRA_CALLER_NAME = "extra_caller_name"
    }
}
