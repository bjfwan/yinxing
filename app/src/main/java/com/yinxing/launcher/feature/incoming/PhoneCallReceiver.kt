package com.bajianfeng.launcher.feature.incoming

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.bajianfeng.launcher.common.util.CallAudioStrategy
import com.bajianfeng.launcher.feature.phone.PhoneContactManager

class PhoneCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return

        if (state != TelephonyManager.EXTRA_STATE_RINGING) {
            IncomingCallForegroundService.stop(context)
            return
        }

        @Suppress("DEPRECATION")
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""
        val matchedContact = IncomingNumberMatcher.findBestMatch(
            contacts = PhoneContactManager.getInstance(context).getContacts(),
            incomingNumber = incomingNumber
        )
        val callerLabel = matchedContact?.name ?: incomingNumber.ifBlank { null }
        val autoAnswer = matchedContact?.autoAnswer == true
        CallAudioStrategy.maximizeIncomingRingVolume(context)

        IncomingCallDiagnostics.recordBroadcastReceived(
            context = context,
            callerLabel = callerLabel,
            incomingNumber = incomingNumber,
            autoAnswer = autoAnswer
        )

        IncomingCallForegroundService.start(
            context = context,
            callerName = callerLabel,
            autoAnswer = autoAnswer
        )
    }
}
