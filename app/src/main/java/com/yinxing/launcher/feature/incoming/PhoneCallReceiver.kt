package com.yinxing.launcher.feature.incoming

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.yinxing.launcher.common.util.CallAudioStrategy
import com.yinxing.launcher.feature.phone.PhoneContactManager
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PhoneCallReceiver : BroadcastReceiver() {
    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val stateToken = AtomicLong(0)
        private val latestEvent = AtomicReference<Pair<Long, String>?>(null)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val token = stateToken.incrementAndGet()
        latestEvent.set(token to state)
        val appContext = context.applicationContext
        if (state != TelephonyManager.EXTRA_STATE_RINGING) {
            IncomingCallForegroundService.stop(appContext)
            return
        }
        @Suppress("DEPRECATION")
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""
        val pendingResult = goAsync()
        scope.launch {
            try {
                runCatching {
                    val matchedContact = IncomingNumberMatcher.findBestMatch(
                        contacts = PhoneContactManager.getInstance(appContext).getContacts(),
                        incomingNumber = incomingNumber
                    )
                    val event = latestEvent.get()
                    if (event == null || event.first != token || event.second != TelephonyManager.EXTRA_STATE_RINGING) {
                        return@runCatching
                    }
                    val callerLabel = matchedContact?.name ?: incomingNumber.ifBlank { null }
                    val autoAnswer = matchedContact?.autoAnswer == true
                    CallAudioStrategy.maximizeIncomingRingVolume(appContext)
                    IncomingCallDiagnostics.recordBroadcastReceived(
                        context = appContext,
                        callerLabel = callerLabel,
                        incomingNumber = incomingNumber,
                        autoAnswer = autoAnswer
                    )
                    IncomingCallForegroundService.start(
                        context = appContext,
                        callerName = callerLabel,
                        autoAnswer = autoAnswer
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
