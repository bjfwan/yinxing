package com.bajianfeng.launcher.feature.incoming

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import com.bajianfeng.launcher.feature.phone.PhoneContactManager

class PhoneCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        if (state != TelephonyManager.EXTRA_STATE_RINGING) return

        @Suppress("DEPRECATION")
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""

        val manager = PhoneContactManager.getInstance(context)
        val matchedContact = if (incomingNumber.isNotBlank()) {
            manager.getContacts().firstOrNull { contact ->
                val stored = contact.phoneNumber?.filter { it.isDigit() } ?: return@firstOrNull false
                val incoming = incomingNumber.filter { it.isDigit() }
                stored.isNotEmpty() && (stored == incoming ||
                    stored.endsWith(incoming.takeLast(7.coerceAtMost(incoming.length))) ||
                    incoming.endsWith(stored.takeLast(7.coerceAtMost(stored.length))))
            }
        } else null

        val launchIntent = IncomingCallActivity.buildLaunchIntent(
            context = context,
            callerName = matchedContact?.name ?: incomingNumber.ifBlank { null },
            autoAnswer = matchedContact?.autoAnswer ?: false
        )
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
    }
}

