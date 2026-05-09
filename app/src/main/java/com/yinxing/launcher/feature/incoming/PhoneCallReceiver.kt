package com.yinxing.launcher.feature.incoming

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import com.yinxing.launcher.common.util.CallAudioStrategy
import com.yinxing.launcher.common.util.DebugLog
import com.yinxing.launcher.common.util.PermissionUtil
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
            DebugLog.w(TAG, "Received PHONE_STATE_CHANGED without EXTRA_STATE; intent=$intent")
            return
        }
        val token = stateToken.incrementAndGet()
        latestEvent.set(token to state)
        if (state != TelephonyManager.EXTRA_STATE_RINGING) {
            runCatching { IncomingCallForegroundService.stop(appContext) }
                .onFailure { DebugLog.w(TAG, "Failed to stop foreground service for state=$state", it) }
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
                    DebugLog.e(TAG, "Failed to load phone contacts for incoming match", it)
                    emptyList()
                }
                val event = latestEvent.get()
                if (event == null || event.first != token || event.second != TelephonyManager.EXTRA_STATE_RINGING) {
                    DebugLog.i(TAG) {
                        "Skip stale ringing token=$token current=${event?.first}/${event?.second}"
                    }
                    return@launch
                }
                val preferences = LauncherPreferences.getInstance(appContext)
                val decision = IncomingAutoAnswerDecisionMaker.decide(
                    contacts = contacts,
                    incomingNumber = incomingNumber,
                    delaySeconds = preferences.getAutoAnswerDelaySeconds(),
                    globalAutoAnswer = preferences.isAutoAnswerEnabled()
                )
                val compatibilityDecision = readCompatibilityDecision(appContext, preferences, decision)
                val finalAutoAnswer = decision.autoAnswer && compatibilityDecision.canAutoAnswer
                callerLabel = decision.callerLabel
                DebugLog.i(TAG) {
                    "compatibility strategy=${compatibilityDecision.strategy}, autoAnswer=$finalAutoAnswer, " +
                        "confidence=${compatibilityDecision.confidence}, blockers=${compatibilityDecision.blockers}"
                }
                runCatching { CallAudioStrategy.maximizeIncomingRingVolume(appContext) }
                    .onFailure { DebugLog.w(TAG, "maximizeIncomingRingVolume failed", it) }
                IncomingCallDiagnostics.recordBroadcastReceived(
                    context = appContext,
                    callerLabel = callerLabel,
                    incomingNumber = incomingNumber,
                    autoAnswer = finalAutoAnswer
                )
                runCatching {
                    IncomingCallForegroundService.start(
                        context = appContext,
                        callerName = callerLabel,
                        autoAnswer = finalAutoAnswer,
                        incomingNumber = incomingNumber,
                        knownContact = decision.matchedContact != null
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
                    .onFailure { DebugLog.w(TAG, "pendingResult.finish failed", it) }
            }
        }
    }

    private fun readCompatibilityDecision(
        context: Context,
        preferences: LauncherPreferences,
        decision: IncomingAutoAnswerDecision
    ): IncomingCallCompatibilityDecision {
        return IncomingCallCompatibilityDecisionEngine.decide(
            IncomingCallCompatibilityInput(
                sdkInt = Build.VERSION.SDK_INT,
                knownContact = decision.matchedContact != null,
                globalAutoAnswerEnabled = preferences.isAutoAnswerEnabled(),
                contactAutoAnswerEnabled = decision.matchedContact?.autoAnswer == true,
                hasPhonePermission = ready { PermissionUtil.hasPhonePermission(context) },
                hasNotificationPermission = ready { PermissionUtil.hasNotificationPermission(context) },
                isDefaultLauncher = ready { PermissionUtil.isDefaultLauncher(context) },
                ignoresBatteryOptimizations = ready { PermissionUtil.isIgnoringBatteryOptimizations(context) },
                autoStartConfirmed = preferences.isAutoStartConfirmed(),
                backgroundStartConfirmed = preferences.isBackgroundStartConfirmed()
            )
        )
    }

    private fun ready(block: () -> Boolean): Boolean = runCatching(block).getOrDefault(false)
}
