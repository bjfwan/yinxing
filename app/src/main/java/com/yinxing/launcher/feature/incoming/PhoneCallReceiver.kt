package com.yinxing.launcher.feature.incoming

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.yinxing.launcher.common.util.CallAudioStrategy
import com.yinxing.launcher.data.home.LauncherPreferences
import com.yinxing.launcher.feature.phone.PhoneContactManager
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PhoneCallReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "PhoneCallReceiver"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val stateToken = AtomicLong(0)
        private val latestEvent = AtomicReference<Pair<Long, String>?>(null)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val appContext = context.applicationContext
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        if (state == null) {
            Log.w(TAG, "Received PHONE_STATE_CHANGED without EXTRA_STATE; intent=$intent")
            return
        }
        val token = stateToken.incrementAndGet()
        latestEvent.set(token to state)
        if (state != TelephonyManager.EXTRA_STATE_RINGING) {
            runCatching { IncomingCallForegroundService.stop(appContext) }
                .onFailure { Log.w(TAG, "Failed to stop foreground service for state=$state", it) }
            return
        }
        @Suppress("DEPRECATION")
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""
        val pendingResult = goAsync()
        scope.launch {
            var callerLabel: String? = incomingNumber.ifBlank { null }
            try {
                val contacts = runCatching {
                    PhoneContactManager.getInstance(appContext).getContacts()
                }.getOrElse {
                    Log.e(TAG, "Failed to load phone contacts for incoming match", it)
                    emptyList()
                }
                val matchedContact = IncomingNumberMatcher.findBestMatch(
                    contacts = contacts,
                    incomingNumber = incomingNumber
                )
                val event = latestEvent.get()
                if (event == null || event.first != token || event.second != TelephonyManager.EXTRA_STATE_RINGING) {
                    Log.i(
                        TAG,
                        "Skip stale ringing token=$token current=${event?.first}/${event?.second}"
                    )
                    return@launch
                }
                callerLabel = matchedContact?.name ?: callerLabel
                val globalAutoAnswer = LauncherPreferences.getInstance(appContext).isAutoAnswerEnabled()
                val autoAnswer = globalAutoAnswer || matchedContact?.autoAnswer == true
                runCatching { CallAudioStrategy.maximizeIncomingRingVolume(appContext) }
                    .onFailure { Log.w(TAG, "maximizeIncomingRingVolume failed", it) }
                IncomingCallDiagnostics.recordBroadcastReceived(
                    context = appContext,
                    callerLabel = callerLabel,
                    incomingNumber = incomingNumber,
                    autoAnswer = autoAnswer
                )
                runCatching {
                    IncomingCallForegroundService.start(
                        context = appContext,
                        callerName = callerLabel,
                        autoAnswer = autoAnswer,
                        incomingNumber = incomingNumber,
                        knownContact = matchedContact != null
                    )
                }.onFailure { failure ->
                    IncomingCallDiagnostics.recordServiceStartFailure(
                        context = appContext,
                        callerLabel = callerLabel,
                        throwable = failure
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                IncomingCallDiagnostics.recordBroadcastFailure(
                    context = appContext,
                    callerLabel = callerLabel,
                    incomingNumber = incomingNumber,
                    throwable = throwable
                )
            } finally {
                runCatching { pendingResult.finish() }
                    .onFailure { Log.w(TAG, "pendingResult.finish failed", it) }
            }
        }
    }
}
