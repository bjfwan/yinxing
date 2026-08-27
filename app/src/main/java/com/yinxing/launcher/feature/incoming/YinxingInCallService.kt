package com.yinxing.launcher.feature.incoming

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.OutcomeReceiver
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.InCallService
import android.telecom.VideoProfile
import com.yinxing.launcher.R
import com.yinxing.launcher.common.util.CallAudioStrategy
import com.yinxing.launcher.common.util.DebugLog
import com.yinxing.launcher.common.util.PermissionUtil
import com.yinxing.launcher.data.home.LauncherPreferences
import com.yinxing.launcher.feature.phone.PhoneContactManager
import java.util.IdentityHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 默认电话角色下的系统通话入口。倒计时和自动接听只在此服务执行，并以
 * [Call.STATE_ACTIVE] 回调作为接通确认。
 *
 * Sources:
 * https://developer.android.com/develop/connectivity/telecom/dialer-app#becoming-the-default-phone-app
 * https://developer.android.com/reference/android/telecom/Call#answer(int)
 */
class YinxingInCallService : InCallService() {
    companion object {
        private const val TAG = "YinxingInCallService"
        private const val DETAILS_COALESCE_MS = 200L
        private const val ANSWER_CONFIRMATION_TIMEOUT_MS = 5_000L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val callbacks = IdentityHashMap<Call, Call.Callback>()
    private val presentations = IdentityHashMap<Call, CallPresentation>()
    private val callStates = IdentityHashMap<Call, ManagedTelecomCallState>()
    private val preparationJobs = mutableMapOf<String, Job>()
    private var selectedCall: Call? = null
    private var availableCallEndpoints: List<CallEndpoint> = emptyList()
    private var currentCallEndpoint: CallEndpoint? = null
    private var speakerRoutingCallId: String? = null
    private var speakerRouteRequestInFlight = false

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val presentation = presentationFrom(call.details)
        presentations[call] = presentation
        callStates[call] = callState(call)

        val callback = object : Call.Callback() {
            override fun onStateChanged(changedCall: Call, newState: Int) {
                handleStateChanged(changedCall, mapCallState(newState))
            }

            override fun onDetailsChanged(changedCall: Call, details: Call.Details) {
                handleDetailsChanged(changedCall, details)
            }
        }
        callbacks[call] = callback
        call.registerCallback(callback, mainHandler)
        refreshSelectedCall(preferredCall = call)
    }

    override fun onCallRemoved(call: Call) {
        val removedCallId = callId(call)
        callbacks.remove(call)?.let(call::unregisterCallback)
        presentations.remove(call)
        callStates.remove(call)
        preparationJobs.remove(removedCallId)?.cancel()
        if (selectedCall === call) selectedCall = null
        if (speakerRoutingCallId == removedCallId) clearSpeakerRoutingRequest()
        refreshSelectedCall()
        super.onCallRemoved(call)
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        val snapshot = ActiveTelecomCallSession.snapshot()
        if (!snapshot.hasCall) return
        val intent = IncomingCallActivity.buildLaunchIntent(
            context = this,
            callerName = snapshot.callerName ?: snapshot.incomingNumber,
            autoAnswer = false,
            incomingNumber = snapshot.incomingNumber,
            knownContact = snapshot.callerName != null
        )
        runCatching { startActivity(intent) }
            .onFailure { DebugLog.w(TAG, "Unable to bring in-call UI forward", it) }
    }

    override fun onAvailableCallEndpointsChanged(availableCallEndpoints: List<CallEndpoint>) {
        this.availableCallEndpoints = availableCallEndpoints.toList()
        routeAnsweredCallToSpeakerIfAppropriate()
    }

    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        currentCallEndpoint = callEndpoint
        routeAnsweredCallToSpeakerIfAppropriate()
    }

    override fun onDestroy() {
        callbacks.forEach { (call, callback) -> runCatching { call.unregisterCallback(callback) } }
        callbacks.clear()
        presentations.clear()
        callStates.clear()
        preparationJobs.values.forEach(Job::cancel)
        preparationJobs.clear()
        selectedCall = null
        clearSpeakerRoutingRequest()
        IncomingCallNotificationController.cancel(this)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleDetailsChanged(call: Call, details: Call.Details) {
        val previous = presentations[call] ?: CallPresentation(null, "")
        val latest = presentationFrom(details, previous)
        if (latest == previous) return
        presentations[call] = latest

        if (selectedCall === call) {
            ActiveTelecomCallSession.updateCaller(
                callId(call),
                latest.callerName,
                latest.incomingNumber
            )
        }
        if (callStates[call] == ManagedTelecomCallState.Ringing) {
            preparationJobs.remove(callId(call))?.cancel()
            refreshSelectedCall(preferredCall = call)
        }
    }

    private fun prepareIncomingCall(call: Call) {
        val callId = callId(call)
        preparationJobs.remove(callId)?.cancel()
        preparationJobs[callId] = serviceScope.launch {
            var incomingNumber = presentations[call]?.incomingNumber.orEmpty()
            try {
                delay(DETAILS_COALESCE_MS)
                if (selectedCall !== call || callStates[call] != ManagedTelecomCallState.Ringing) {
                    return@launch
                }
                incomingNumber = presentations[call]?.incomingNumber.orEmpty()
                val contacts = withContext(Dispatchers.IO) {
                    runCatching { PhoneContactManager.getInstance(this@YinxingInCallService).getContacts() }
                        .getOrElse {
                            DebugLog.w(TAG, "Unable to load contacts for incoming call", it)
                            emptyList()
                        }
                }
                if (selectedCall !== call || callStates[call] != ManagedTelecomCallState.Ringing) {
                    return@launch
                }

                val preferences = LauncherPreferences.getInstance(this@YinxingInCallService)
                val decision = IncomingAutoAnswerDecisionMaker.decide(
                    contacts = contacts,
                    incomingNumber = incomingNumber,
                    delaySeconds = preferences.getAutoAnswerDelaySeconds(),
                    globalAutoAnswer = preferences.isAutoAnswerEnabled()
                )
                val callerLabel = decision.callerLabel
                    ?: presentations[call]?.callerName
                    ?: incomingNumber.trim().takeIf(String::isNotEmpty)

                presentations[call] = CallPresentation(
                    callerLabel,
                    incomingNumber,
                    decision.matchedContact != null
                )
                ActiveTelecomCallSession.updateCaller(callId, callerLabel, incomingNumber)
                IncomingCallNotificationController.notifyIncoming(
                    this@YinxingInCallService,
                    callerLabel,
                    incomingNumber,
                    decision.matchedContact != null
                )

                val autoAnswer = decision.autoAnswer &&
                    PermissionUtil.hasPhonePermission(this@YinxingInCallService) &&
                    PermissionUtil.hasNotificationPermission(this@YinxingInCallService)
                if (!autoAnswer) return@launch

                delay(decision.delaySeconds * 1_000L)
                val beforeAnswer = ActiveTelecomCallSession.snapshot()
                if (selectedCall !== call ||
                    beforeAnswer.callId != callId ||
                    beforeAnswer.state != ManagedTelecomCallState.Ringing
                ) {
                    return@launch
                }
                val result = ActiveTelecomCallSession.answer()
                if (!result.dispatched) {
                    recordAnswerFailure(result.error?.message)
                    return@launch
                }

                delay(ANSWER_CONFIRMATION_TIMEOUT_MS)
                if (ActiveTelecomCallSession.expireAnswerRequest(callId)) {
                    recordAnswerFailure(getString(R.string.incoming_call_status_not_confirmed))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                DebugLog.e(TAG, "Incoming call preparation failed", throwable)
                IncomingCallDiagnostics.recordBroadcastFailure(
                    context = this@YinxingInCallService,
                    callerLabel = presentations[call]?.callerName,
                    incomingNumber = incomingNumber,
                    throwable = throwable
                )
            }
        }
    }

    private fun handleStateChanged(call: Call, state: ManagedTelecomCallState) {
        if (!callStates.containsKey(call)) return
        val callId = callId(call)
        val wasConfirmed = if (selectedCall === call) {
            ActiveTelecomCallSession.snapshot().answerConfirmed
        } else {
            false
        }
        callStates[call] = state
        if (state == ManagedTelecomCallState.Ringing) {
            ActiveTelecomCallSession.expireAnswerRequest(callId)
        } else if (state == ManagedTelecomCallState.Active ||
            state == ManagedTelecomCallState.Disconnected
        ) {
            preparationJobs.remove(callId)?.cancel()
        }

        refreshSelectedCall(preferredCall = call)
        if (selectedCall !== call) return

        val snapshot = ActiveTelecomCallSession.snapshot()
        if (state == ManagedTelecomCallState.Active && snapshot.answerConfirmed) {
            if (!wasConfirmed) {
                IncomingCallSessionState.answered()
                IncomingCallDiagnostics.recordAcceptSuccess(
                    this,
                    getString(R.string.incoming_call_status_accept_confirmed)
                )
            }
            speakerRoutingCallId = callId
            routeAnsweredCallToSpeakerIfAppropriate()
        }
    }

    private fun refreshSelectedCall(preferredCall: Call? = null) {
        val currentCallId = selectedCall?.let(::callId)
        val preferredCallId = preferredCall?.let(::callId)
        val candidate = ManagedCallSelectionPolicy.select(
            candidates = callStates.map { (call, state) ->
                ManagedCallCandidate(callId(call), state)
            },
            currentCallId = currentCallId,
            preferredCallId = preferredCallId
        )
        val nextCall = candidate?.let { selected ->
            callStates.keys.firstOrNull { callId(it) == selected.callId }
        }

        if (nextCall == null) {
            val staleCallId = ActiveTelecomCallSession.snapshot().callId
            selectedCall = null
            staleCallId?.let(ActiveTelecomCallSession::detach)
            IncomingCallNotificationController.cancel(this)
            return
        }

        val changed = selectedCall !== nextCall
        if (changed) {
            selectedCall?.let { preparationJobs.remove(callId(it))?.cancel() }
            selectedCall = nextCall
            val presentation = presentations[nextCall] ?: presentationFrom(nextCall.details)
            presentations[nextCall] = presentation
            ActiveTelecomCallSession.attach(
                call = AndroidManagedTelecomCall(nextCall, callId(nextCall)),
                state = callStates[nextCall] ?: ManagedTelecomCallState.None,
                callerName = presentation.callerName,
                incomingNumber = presentation.incomingNumber
            )
        } else {
            ActiveTelecomCallSession.updateState(
                callId(nextCall),
                callStates[nextCall] ?: ManagedTelecomCallState.None
            )
        }
        renderSelectedCall(nextCall)
    }

    private fun renderSelectedCall(call: Call) {
        val state = callStates[call] ?: return
        val presentation = presentations[call] ?: presentationFrom(call.details)
        when (state) {
            ManagedTelecomCallState.Ringing -> {
                IncomingCallNotificationController.notifyIncoming(
                    this,
                    presentation.callerName,
                    presentation.incomingNumber,
                    knownContact = presentation.knownContact
                )
                if (preparationJobs[callId(call)]?.isActive != true) {
                    prepareIncomingCall(call)
                }
            }
            ManagedTelecomCallState.Active,
            ManagedTelecomCallState.Connecting,
            ManagedTelecomCallState.Held -> IncomingCallNotificationController.notifyOngoing(
                this,
                presentation.callerName ?: presentation.incomingNumber.takeIf(String::isNotBlank)
            )
            ManagedTelecomCallState.None,
            ManagedTelecomCallState.Disconnected -> Unit
        }
    }

    private fun recordAnswerFailure(message: String?) {
        val detail = message?.takeIf(String::isNotBlank)
            ?: getString(R.string.incoming_call_status_not_ringing)
        IncomingCallDiagnostics.recordAcceptFailure(
            this,
            detail,
            IncomingCallFailureReason(IncomingCallFailureCategory.CallAction, detail)
        )
    }

    private fun routeAnsweredCallToSpeakerIfAppropriate() {
        val callId = speakerRoutingCallId ?: return
        val snapshot = ActiveTelecomCallSession.snapshot()
        if (snapshot.callId != callId || snapshot.state != ManagedTelecomCallState.Active) return
        CallAudioStrategy.maximizeSystemCallVolume(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            routeModernCallEndpoint(callId)
        } else {
            routeLegacyCallAudio(callId)
        }
    }

    @Suppress("DEPRECATION")
    private fun routeLegacyCallAudio(callId: String) {
        val route = callAudioState?.route ?: 0
        val privateRoute = route and (
            CallAudioState.ROUTE_BLUETOOTH or CallAudioState.ROUTE_WIRED_HEADSET
        ) != 0
        if (privateRoute) {
            IncomingCallDiagnostics.recordSpeakerKeptPrivate(this)
        } else {
            runCatching { setAudioRoute(CallAudioState.ROUTE_SPEAKER) }
                .onSuccess { IncomingCallDiagnostics.recordSpeakerEnabled(this) }
                .onFailure { DebugLog.w(TAG, "Unable to route call to speaker", it) }
        }
        if (speakerRoutingCallId == callId) clearSpeakerRoutingRequest()
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun routeModernCallEndpoint(callId: String) {
        if (speakerRouteRequestInFlight) return
        val currentType = currentCallEndpoint?.endpointType
        if (currentType == CallEndpoint.TYPE_BLUETOOTH ||
            currentType == CallEndpoint.TYPE_WIRED_HEADSET ||
            currentType == CallEndpoint.TYPE_STREAMING
        ) {
            IncomingCallDiagnostics.recordSpeakerKeptPrivate(this)
            clearSpeakerRoutingRequest()
            return
        }
        val speaker = availableCallEndpoints.firstOrNull {
            it.endpointType == CallEndpoint.TYPE_SPEAKER
        } ?: return
        speakerRouteRequestInFlight = true
        requestCallEndpointChange(
            speaker,
            mainExecutor,
            object : OutcomeReceiver<Void, CallEndpointException> {
                override fun onResult(result: Void?) {
                    IncomingCallDiagnostics.recordSpeakerEnabled(this@YinxingInCallService)
                    if (speakerRoutingCallId == callId) clearSpeakerRoutingRequest()
                }

                override fun onError(error: CallEndpointException) {
                    speakerRouteRequestInFlight = false
                    DebugLog.w(TAG, "Unable to request speaker endpoint", error)
                }
            }
        )
    }

    private fun clearSpeakerRoutingRequest() {
        speakerRoutingCallId = null
        speakerRouteRequestInFlight = false
    }

    private fun presentationFrom(
        details: Call.Details,
        previous: CallPresentation = CallPresentation(null, "")
    ): CallPresentation {
        val latestNumber = details.handle?.schemeSpecificPart?.trim().orEmpty()
        val latestName = details.callerDisplayName?.toString()?.trim().orEmpty()
        return CallPresentation(
            callerName = latestName.takeIf(String::isNotEmpty) ?: previous.callerName,
            incomingNumber = latestNumber.takeIf(String::isNotEmpty) ?: previous.incomingNumber,
            knownContact = previous.knownContact
        )
    }

    @Suppress("DEPRECATION")
    private fun callState(call: Call): ManagedTelecomCallState = mapCallState(call.state)

    private fun callId(call: Call): String = "telecom-${System.identityHashCode(call)}"

    private fun mapCallState(state: Int): ManagedTelecomCallState = when (state) {
        Call.STATE_RINGING, Call.STATE_SIMULATED_RINGING -> ManagedTelecomCallState.Ringing
        Call.STATE_ACTIVE -> ManagedTelecomCallState.Active
        Call.STATE_HOLDING -> ManagedTelecomCallState.Held
        Call.STATE_DISCONNECTED -> ManagedTelecomCallState.Disconnected
        Call.STATE_CONNECTING,
        Call.STATE_DIALING,
        Call.STATE_SELECT_PHONE_ACCOUNT,
        Call.STATE_DISCONNECTING -> ManagedTelecomCallState.Connecting
        else -> ManagedTelecomCallState.None
    }

    private class AndroidManagedTelecomCall(
        private val call: Call,
        override val id: String
    ) : ManagedTelecomCall {
        override fun answerAudioOnly() {
            call.answer(VideoProfile.STATE_AUDIO_ONLY)
        }

        override fun reject() {
            call.reject(false, null)
        }

        override fun disconnect() {
            call.disconnect()
        }
    }

    private data class CallPresentation(
        val callerName: String?,
        val incomingNumber: String,
        val knownContact: Boolean = false
    )
}
