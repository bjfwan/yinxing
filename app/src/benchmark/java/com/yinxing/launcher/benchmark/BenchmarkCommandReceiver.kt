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
                    autoAnswer = intent.getBooleanExtra(EXTRA_AUTO_ANSWER, true),
                    incomingNumber = intent.getStringExtra(EXTRA_INCOMING_NUMBER),
                    knownContact = intent.getBooleanExtra(EXTRA_KNOWN_CONTACT, false)
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
        const val EXTRA_AUTO_ANSWER = "extra_auto_answer"
        const val EXTRA_INCOMING_NUMBER = "extra_incoming_number"
        const val EXTRA_KNOWN_CONTACT = "extra_known_contact"
    }
}
