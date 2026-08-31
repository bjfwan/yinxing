package com.google.android.accessibility.selecttospeak

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.yinxing.launcher.R
import com.yinxing.launcher.automation.wechat.WeChatClassNames
import com.yinxing.launcher.automation.wechat.WeChatPackage
import com.yinxing.launcher.automation.wechat.WeChatViewIds
import com.yinxing.launcher.automation.wechat.manager.TimeoutManager
import com.yinxing.launcher.automation.wechat.model.AutomationState
import com.yinxing.launcher.automation.wechat.teaching.WeChatLearnedRulePolicy
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingAction
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingAnalyzer
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingBackInference
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingCapturePolicy
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingFingerprint
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingFingerprintFactory
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingGenericAnalyzer
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingGenericFailure
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingGenericResult
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingObservation
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingObservationExtractor
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingObservationKind
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingProfile
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingProgress
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingProgressTracker
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingResult
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingReplayResult
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingRoute
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingRouteUploadFactory
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingStateFingerprint
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingStore
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingUploadFailureReason
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingUploadOutcome
import com.yinxing.launcher.automation.wechat.teaching.learnedProfileOrNull
import com.yinxing.launcher.automation.wechat.util.AccessibilityUtil
import com.yinxing.launcher.common.lobster.LobsterClient
import com.yinxing.launcher.common.lobster.LobsterFailureSampleStore
import com.yinxing.launcher.common.lobster.LobsterReportStatus
import com.yinxing.launcher.common.lobster.LobsterReportDetails
import com.yinxing.launcher.common.lobster.LobsterStepOutcome
import com.yinxing.launcher.common.lobster.LobsterTraceStep
import com.yinxing.launcher.common.perf.LauncherTraceNames
import com.yinxing.launcher.common.util.CallAudioStrategy
import com.yinxing.launcher.common.util.DebugLog
import com.yinxing.launcher.common.util.HomeRedirectPolicy
import com.yinxing.launcher.common.util.HomeRedirectPreferences
import com.yinxing.launcher.common.util.PermissionUtil
import com.yinxing.launcher.common.ui.FloatingStatusView
import com.yinxing.launcher.feature.home.MainActivity
import com.yinxing.launcher.feature.callreturn.CallReturnCoordinator
import com.yinxing.launcher.feature.callreturn.CallReturnOrigin
import com.yinxing.launcher.feature.callreturn.CallReturnWindowAction
import com.yinxing.launcher.feature.callreturn.WeChatCallWindowTracker
import com.yinxing.launcher.feature.callreturn.WeChatCallEndPolicy


import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID


class SelectToSpeakService : AccessibilityService(), WeChatRequestHost {

    companion object {
        @Volatile
        private var instance: SelectToSpeakService? = null

        fun getInstance(): SelectToSpeakService? = instance

        const val ACTION_START_VIDEO_CALL = "com.yinxing.launcher.START_VIDEO_CALL"
        const val EXTRA_CONTACT_NAME = "contact_name"

        private const val TAG = "WeChatAutoService"

        private const val TOTAL_STEPS = 7

        private const val MAX_HOME_BACK_ATTEMPTS = 6
        private const val MAX_UNKNOWN_HOME_OBSERVE_ATTEMPTS = 2
        private const val MAX_SEARCH_ENTRY_ATTEMPTS = 3
        private const val MAX_SEARCH_OPEN_ATTEMPTS = 3

        private const val MAX_CONTACT_DETAIL_ATTEMPTS = 4
        private const val MAX_VIDEO_OPTION_ATTEMPTS = 3
        private const val MAX_STEP_RECOVERY_ATTEMPTS = 5
        private const val HOME_ACTION_SETTLE_DELAY_MS = 500L
        private const val TEACHING_TIMEOUT_MS = 3 * 60 * 1000L
        private const val MAX_TEACHING_OBSERVATIONS = 100


        fun requestVideoCall(contactName: String, listener: (VideoCallProgress) -> Unit): String =
            WeChatRequestQueue.enqueue(contactName, listener, host = instance)

        fun clearRequestListener(requestId: String) =
            WeChatRequestQueue.clearListener(requestId)

        fun prepareWeChatTeaching(): WeChatTeachingPrepareResult =
            instance?.prepareWeChatTeachingInternal()
                ?: WeChatTeachingPrepareResult.SERVICE_NOT_CONNECTED

        internal fun resetForTesting() = WeChatRequestQueue.resetForTesting()

        private fun deliverProgress(requestId: String, progress: VideoCallProgress) =
            WeChatRequestQueue.deliverProgress(requestId, progress)
    }

    data class VideoCallProgress(
        val message: String,
        val success: Boolean,
        val terminal: Boolean,
        val step: AutomationState = AutomationState.IDLE,
        val page: String? = null,
        val reported: Boolean = false
    )


    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val stepClock = VideoCallStepClock(
        scope = serviceScope,
        onProcessTick = {
            if (currentSession != null) processCurrentWindow()
        },
        onTimeoutFailure = { message ->
            val keepWeChatVisible = currentSession?.step == Step.VERIFYING_CALL_STARTED
            failAndHide(
                message = message,
                root = getWeChatRoot(),
                restoreLauncher = !keepWeChatVisible
            )
        },
        sessionStillActive = { currentSession != null }
    )
    private var wechatWaitJob: Job? = null
    private var wechatCallEndJob: Job? = null
    private var lastWeChatWindowClassForReturn: String? = null
    private lateinit var timeoutManager: TimeoutManager
    private var floatingView: FloatingStatusView? = null
    private var currentSession: VideoCallSession? = null
    private var teachingOverlay: WeChatTeachingOverlay? = null
    private var teachingSession: TeachingSession? = null
    private var teachingTimeoutJob: Job? = null
    private val teachingStore by lazy { WeChatTeachingStore(this) }
    private var activeTeachingProfile: WeChatTeachingProfile? = null
    private var lastMissingRootLogAt = 0L
    private val rootProvider = WeChatRootProvider(this)
    private val elementLocator = WeChatElementLocator(
        service = this,
        learnedProfileProvider = { activeTeachingProfile },
        currentWindowClassProvider = { rootProvider.lastObservedClassName }
    )
    private val capabilityTree = WeChatCapabilityBehaviorTree()

    private var wechatVersionTagged = false
    private val homeRedirectPreferences by lazy { HomeRedirectPreferences(this) }
    private var lastHomeRedirectAtMs = 0L



    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        timeoutManager = TimeoutManager.getInstance(this)
        floatingView = FloatingStatusView(this)
        teachingOverlay = WeChatTeachingOverlay(this)
        consumePendingRequest()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString()
        val className = event?.className?.toString()

        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != null) {
            DebugLog.d(TAG) { "[EVENT] WindowStateChanged: pkg=$pkg, class=$className" }
            lastWeChatWindowClassForReturn = WeChatCallWindowTracker.remember(
                previousClass = lastWeChatWindowClassForReturn,
                packageName = pkg,
                observedClass = className
            )
            when (CallReturnCoordinator.observeWindow(this, pkg, className)) {
                CallReturnWindowAction.CHECK_WECHAT_ENDED -> scheduleWeChatCallEndCheck()
                CallReturnWindowAction.USER_ESCAPED -> wechatCallEndJob?.cancel()
                CallReturnWindowAction.IGNORE -> Unit
            }
            if (redirectSystemLauncher(pkg, className)) return
        }

        if (pkg != WeChatPackage.NAME) {
            return
        }
        rootProvider.rememberClassName(className)

        rootProvider.updateFromEvent(event.source)

        if (
            CallReturnCoordinator.hasConfirmedSession(CallReturnOrigin.WECHAT_VIDEO) &&
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            scheduleWeChatCallEndCheck()
        }

        recordTeachingEvent(event, className)

        val session = currentSession ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                DebugLog.d(TAG) { "onEvent: STATE_CHANGED className=$className step=${session.step}" }
                when (className) {
                    WeChatClassNames.LAUNCHER_UI -> {
                        if (session.step == Step.WAITING_HOME) {
                            DebugLog.i(TAG) { "[STEP] LauncherUI detected -> Moving to WAITING_LAUNCHER_UI" }
                            wechatWaitJob?.cancel()
                            session.launcherPrepared = false
                            session.searchTextApplied = false
                            transitionTo(session, Step.WAITING_LAUNCHER_UI, "正在查找联系人")
                        } else {
                            scheduleAdaptiveProcess(session, DelayProfile.STABLE)
                        }
                    }
                    WeChatClassNames.CHATTING_UI,
                    WeChatClassNames.CONTACT_INFO,
                    WeChatClassNames.SEARCH_UI -> {
                        DebugLog.d(TAG) { "[EVENT] Meaningful class detected: $className" }
                        scheduleAdaptiveProcess(session, DelayProfile.STABLE)
                    }
                    else -> scheduleAdaptiveProcess(session, DelayProfile.TRANSITION)
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> scheduleAdaptiveProcess(session, DelayProfile.FAST)
            else -> Unit
        }
    }

    override fun onInterrupt() {
        cancelSession(true, "无障碍服务已中断，请重新开启后再试")
        CallReturnCoordinator.cancel(CallReturnOrigin.WECHAT_VIDEO)
        wechatCallEndJob?.cancel()
        closeTeachingSession()
    }

    private fun redirectSystemLauncher(packageName: String, className: String?): Boolean {
        val nowMs = SystemClock.elapsedRealtime()
        if (!HomeRedirectPolicy.shouldRedirect(
                userEnabled = homeRedirectPreferences.isEnabled(),
                nativeHomeActive = PermissionUtil.isDefaultLauncher(this),
                manufacturer = Build.MANUFACTURER,
                packageName = packageName,
                className = className,
                nowMs = nowMs,
                lastRedirectAtMs = lastHomeRedirectAtMs
            )
        ) {
            return false
        }

        lastHomeRedirectAtMs = nowMs
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            )
        }
        return runCatching {
            startActivity(intent)
            DebugLog.i(TAG) { "[HOME_REDIRECT] system launcher -> Yinxing; manufacturer=${Build.MANUFACTURER}" }
            true
        }.getOrElse { error ->
            DebugLog.w(TAG, "[HOME_REDIRECT] failed: ${error.message}")
            false
        }
    }

    override fun onDestroy() {
        instance = null
        cancelSession(true, "无障碍服务已关闭，请重新开启后再试")
        closeTeachingSession()
        floatingView?.hide()
        floatingView = null
        teachingOverlay = null
        CallReturnCoordinator.cancel(CallReturnOrigin.WECHAT_VIDEO)
        wechatCallEndJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_VIDEO_CALL) {
            intent.getStringExtra(EXTRA_CONTACT_NAME)?.let { contactName ->
                requestVideoCall(contactName) { }
            }
        }
        return START_STICKY
    }

    private fun prepareWeChatTeachingInternal(): WeChatTeachingPrepareResult {
        if (currentSession != null || teachingSession != null) {
            return WeChatTeachingPrepareResult.BUSY
        }
        val fingerprint = WeChatTeachingFingerprintFactory.capture(this)
            ?: return WeChatTeachingPrepareResult.WECHAT_UNAVAILABLE
        val session = TeachingSession(fingerprint = fingerprint)
        teachingSession = session
        val shown = showTeachingPrepared(session, getString(R.string.settings_wechat_teaching_prepared_message))
        if (!shown) {
            teachingSession = null
            return WeChatTeachingPrepareResult.OVERLAY_UNAVAILABLE
        }
        return WeChatTeachingPrepareResult.READY
    }

    private fun showTeachingPrepared(
        session: TeachingSession,
        message: String,
        isError: Boolean = false,
        isSuccess: Boolean = false
    ): Boolean {
        session.state = TeachingState.PREPARED
        val overlay = teachingOverlay ?: WeChatTeachingOverlay(this).also {
            teachingOverlay = it
            DebugLog.w(TAG, "[微信示教] 悬浮层未初始化，已重新创建")
        }
        return overlay.show(
            message = message,
            primaryText = getString(R.string.settings_wechat_teaching_start),
            primaryBackgroundRes = R.drawable.bg_wechat_teaching_start,
            secondaryText = null,
            onPrimary = { startTeachingRecording(session) },
            onSecondary = null,
            uploadChecked = session.uploadAnonymousData,
            onUploadCheckedChange = { checked -> session.uploadAnonymousData = checked },
            messageColorRes = when {
                isError -> R.color.wechat_teaching_status_error
                isSuccess -> R.color.wechat_teaching_status_success
                else -> R.color.launcher_text_primary
            }
        ) == true
    }

    private fun startTeachingRecording(session: TeachingSession) {
        if (teachingSession !== session || session.state != TeachingState.PREPARED) return
        if (!isWeChatForeground()) {
            showTeachingPrepared(
                session,
                getString(R.string.settings_wechat_teaching_not_in_wechat),
                isError = true
            )
            return
        }
        session.state = TeachingState.RECORDING
        session.startedAt = System.currentTimeMillis()
        val initialRoot = getWeChatRoot()
        val initialState = try {
            initialRoot?.let {
                WeChatTeachingStateSnapshotFactory.create(
                    windowClass = resolveCurrentWeChatClass(it),
                    snapshot = teachingSnapshotOf(it)
                ).copy(resourceIds = emptySet())
            }
        } finally {
            AccessibilityUtil.safeRecycle(initialRoot)
        }
        session.initialState = initialState
        session.currentWindowClass = initialState?.windowClass ?: rootProvider.lastObservedClassName
        session.observations.clear()
        session.currentWindowClass?.let { windowClass ->
            session.observations += WeChatTeachingObservation(
                kind = WeChatTeachingObservationKind.WINDOW,
                windowClass = windowClass,
                selector = null,
                elapsedMs = 0L
            )
        }
        session.visibleCaptureTracker.reset()
        session.progress = WeChatTeachingProgress.WECHAT_OPENED
        session.selectedCallLabel = null
        session.lastCallMode = WeChatTeachingCallMode.UNKNOWN
        session.videoCallConfirmed = false
        showTeachingRecording(session, session.progress)
        teachingTimeoutJob?.cancel()
        teachingTimeoutJob = serviceScope.launch {
            delay(TEACHING_TIMEOUT_MS)
            if (teachingSession === session && session.state == TeachingState.RECORDING) {
                showTeachingIncomplete(session, getString(R.string.settings_wechat_teaching_timeout))
            }
        }
        DebugLog.i(TAG) { "[微信示教] 开始记录安全控件特征" }
    }

    private fun showTeachingRecording(
        session: TeachingSession,
        progress: WeChatTeachingProgress
    ) {
        teachingOverlay?.show(
            message = getString(progress.messageRes()),
            primaryText = getString(R.string.settings_wechat_teaching_finish),
            primaryBackgroundRes = R.drawable.bg_wechat_teaching_finish,
            secondaryText = null,
            onPrimary = { finishTeachingRecording(session) },
            onSecondary = null,
            messageColorRes = R.color.wechat_teaching_status_success
        )
    }

    private fun recordTeachingEvent(event: AccessibilityEvent, eventClassName: String?) {
        val session = teachingSession ?: return
        if (session.state == TeachingState.PREPARED) {
            if (!session.weChatEntered) {
                session.weChatEntered = true
                showTeachingPrepared(
                    session,
                    getString(R.string.settings_wechat_teaching_progress_wechat),
                    isSuccess = true
                )
            }
            return
        }
        if (session.state != TeachingState.RECORDING) return
        val previousWindowClass = session.currentWindowClass
        val captureDecision = WeChatTeachingCapturePolicy.decide(
            videoCallConfirmed = session.videoCallConfirmed,
            currentWindowClass = session.currentWindowClass,
            observedWindowClass = eventClassName.takeIf {
                event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            }
        )
        session.currentWindowClass = captureDecision.currentWindowClass
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (eventClassName == WeChatClassNames.CHATTING_UI) {
                session.selectedCallLabel = null
            }
        }
        if (!captureDecision.shouldCapture) return
        val metrics = resources.displayMetrics
        val teachingRoot = rootInActiveWindow
        val snapshot = try {
            teachingSnapshotOf(teachingRoot)
        } finally {
            AccessibilityUtil.safeRecycle(teachingRoot)
        }
        val canonicalWindowClass = WeChatTeachingVisibleControlCollector.canonicalWindowClass(
            snapshot,
            session.currentWindowClass
        )
        session.currentWindowClass = canonicalWindowClass
        session.visibleCaptureTracker.observeWindow(canonicalWindowClass)
        val extracted = WeChatTeachingObservationExtractor.extract(
            event = event,
            activeWindowClass = canonicalWindowClass,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            elapsedMs = System.currentTimeMillis() - session.startedAt
        )
        val observation = extracted?.let {
            if (it.kind == WeChatTeachingObservationKind.WINDOW) {
                it.copy(windowClass = canonicalWindowClass ?: it.windowClass)
            } else {
                it
            }
        }
        if (
            observation?.kind == WeChatTeachingObservationKind.WINDOW &&
            WeChatTeachingBackInference.shouldInfer(
                history = session.observations,
                previousWindowClass = previousWindowClass,
                currentWindowClass = observation.windowClass
            ) &&
            session.observations.size < MAX_TEACHING_OBSERVATIONS - 1
        ) {
            session.observations += WeChatTeachingObservation(
                kind = WeChatTeachingObservationKind.BACK,
                windowClass = previousWindowClass,
                selector = null,
                elapsedMs = observation.elapsedMs
            )
        }
        if (observation != null) {
            if (
                observation.kind == WeChatTeachingObservationKind.CLICK &&
                observation.windowClass.orEmpty().startsWith("com.tencent.mm.ui.widget.dialog.") &&
                observation.selector?.semanticLabel in setOf(
                    com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingSemanticLabel.VIDEO_CALL,
                    com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingSemanticLabel.VOICE_CALL
                )
            ) {
                session.selectedCallLabel = observation.selector?.semanticLabel
            }
            if (session.observations.size < MAX_TEACHING_OBSERVATIONS) {
                session.observations += observation
            }
        }
        if (
            canonicalWindowClass != null &&
            canonicalWindowClass != previousWindowClass &&
            observation?.kind != WeChatTeachingObservationKind.WINDOW &&
            session.observations.size < MAX_TEACHING_OBSERVATIONS
        ) {
            session.observations += WeChatTeachingObservation(
                kind = WeChatTeachingObservationKind.WINDOW,
                windowClass = canonicalWindowClass,
                selector = null,
                elapsedMs = System.currentTimeMillis() - session.startedAt
            )
        }
        WeChatTeachingVisibleControlCollector.collect(
            snapshot = snapshot,
            activeWindowClass = canonicalWindowClass,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            elapsedMs = System.currentTimeMillis() - session.startedAt
        ).forEach { visible ->
            if (
                session.visibleCaptureTracker.shouldCapture(visible.action) &&
                session.observations.size < MAX_TEACHING_OBSERVATIONS
            ) {
                session.observations += visible.observation
                DebugLog.i(TAG) { "[微信示教] 已采集候选步骤 action=${visible.action}" }
            }
        }

        updateTeachingCallMode(session)
        val progress = WeChatTeachingProgressTracker.latest(
            observations = session.observations,
            videoCallConfirmed = session.videoCallConfirmed
        )
        if (progress != session.progress) {
            session.progress = progress
            showTeachingRecording(session, progress)
        }
    }

    private fun updateTeachingCallMode(session: TeachingSession) {
        val audioManager = getSystemService(AudioManager::class.java)
        val isVoipWindow = session.currentWindowClass.orEmpty().contains(".plugin.voip.")
        if (!isVoipWindow && audioManager.mode != AudioManager.MODE_IN_COMMUNICATION) return

        val root = rootInActiveWindow
        val snapshot = try {
            teachingSnapshotOf(root)
        } finally {
            AccessibilityUtil.safeRecycle(root)
        }
        val mode = WeChatTeachingCallModeDetector.detect(
            snapshot = snapshot,
            selectedLabel = session.selectedCallLabel,
            audioRoute = currentTeachingAudioRoute(audioManager)
        )
        if (session.videoCallConfirmed && mode == WeChatTeachingCallMode.VOICE) return
        if (mode == WeChatTeachingCallMode.UNKNOWN || mode == session.lastCallMode) return

        session.lastCallMode = mode
        when (mode) {
            WeChatTeachingCallMode.VIDEO -> {
                session.videoCallConfirmed = true
                WeChatTeachingConfirmedCallRecorder.appendIfMissing(
                    observations = session.observations,
                    elapsedMs = System.currentTimeMillis() - session.startedAt,
                    maxSize = MAX_TEACHING_OBSERVATIONS
                )
                DebugLog.i(TAG) { "[微信示教] 已确认视频通话" }
            }
            WeChatTeachingCallMode.VOICE -> {
                showTeachingVoiceDetected(session)
                DebugLog.i(TAG) { "[微信示教] 检测到语音通话，不计为视频示教" }
            }
            WeChatTeachingCallMode.UNKNOWN -> Unit
        }
    }

    private fun currentTeachingAudioRoute(audioManager: AudioManager): WeChatTeachingAudioRoute {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return when (audioManager.communicationDevice?.type) {
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> WeChatTeachingAudioRoute.EARPIECE
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> WeChatTeachingAudioRoute.SPEAKER
                null -> WeChatTeachingAudioRoute.UNKNOWN
                else -> WeChatTeachingAudioRoute.OTHER
            }
        }
        @Suppress("DEPRECATION")
        return if (audioManager.isSpeakerphoneOn) {
            WeChatTeachingAudioRoute.SPEAKER
        } else {
            WeChatTeachingAudioRoute.UNKNOWN
        }
    }

    private fun showTeachingVoiceDetected(session: TeachingSession) {
        teachingOverlay?.show(
            message = getString(R.string.settings_wechat_teaching_voice_detected),
            primaryText = getString(R.string.settings_wechat_teaching_finish),
            primaryBackgroundRes = R.drawable.bg_wechat_teaching_finish,
            secondaryText = null,
            onPrimary = { finishTeachingRecording(session) },
            onSecondary = null,
            messageColorRes = R.color.wechat_teaching_status_error
        )
    }

    private fun finishTeachingRecording(session: TeachingSession) {
        if (teachingSession !== session || session.state != TeachingState.RECORDING) return
        teachingTimeoutJob?.cancel()
        val finishedAt = System.currentTimeMillis()
        val result = WeChatTeachingAnalyzer.analyze(
            observations = session.observations.toList(),
            fingerprint = session.fingerprint,
            createdAtEpochMs = finishedAt
        )
        val genericResult = WeChatTeachingGenericAnalyzer.analyze(
            observations = session.observations.toList(),
            fingerprint = session.fingerprint,
            videoCallConfirmed = session.videoCallConfirmed,
            createdAtEpochMs = finishedAt,
            initialState = session.initialState
        )
        val candidateRoute = when (genericResult) {
            is WeChatTeachingGenericResult.Complete -> genericResult.route
            is WeChatTeachingGenericResult.Incomplete -> genericResult.route
        }
        val calibrationCount = result.learnedProfileOrNull()?.steps.orEmpty().size
        val finishDecision = WeChatTeachingFinishPolicy.decide(
            callMode = if (session.videoCallConfirmed) {
                WeChatTeachingCallMode.VIDEO
            } else {
                session.lastCallMode
            },
            learnedRuleCount = calibrationCount
        )
        when (finishDecision) {
            WeChatTeachingFinishDecision.SAVE_RULE -> {
                val record = teachingStore.saveVideoOutcome(
                    result = result,
                    fingerprint = session.fingerprint,
                    createdAtEpochMs = finishedAt
                )
                val profile = requireNotNull(teachingStore.loadCompatible(session.fingerprint))
                session.state = TeachingState.COMPLETE
                reportTeachingCapture(
                    session = session,
                    route = candidateRoute,
                    outcome = WeChatTeachingUploadOutcome.SUCCEEDED,
                    failureReason = genericFailureReason(genericResult)
                )
                showTeachingCalibrationSaved(
                    session = session,
                    calibratedActionCount = record.learnedActions.size,
                    deviceRuleCount = record.addedActions.size
                )
                DebugLog.i(TAG) {
                    "[微信示教] 语义校准已保存 builtIn=${record.verifiedActions.size} " +
                        "device=${record.addedActions.size} active=${record.learnedActions.size} " +
                        "reliability=${profile.reliabilityScore}"
                }
            }
            WeChatTeachingFinishDecision.ACCEPT_WITHOUT_RULE -> {
                val record = teachingStore.saveVideoOutcome(
                    result = result,
                    fingerprint = session.fingerprint,
                    createdAtEpochMs = finishedAt
                )
                val missing = (result as? WeChatTeachingResult.Incomplete)?.missing.orEmpty()
                DebugLog.i(TAG) {
                    "[微信示教] 视频已打通，未新增差异规则 builtIn=${record.verifiedActions.size} missing=$missing"
                }
                reportTeachingCapture(
                    session = session,
                    route = candidateRoute,
                    outcome = WeChatTeachingUploadOutcome.SUCCEEDED,
                    failureReason = genericFailureReason(genericResult)
                )
                showTeachingAcceptedWithoutRule(session, record.verifiedActions.size)
            }
            WeChatTeachingFinishDecision.FAIL -> {
                val missing = (result as? WeChatTeachingResult.Incomplete)?.missing.orEmpty()
                val pendingActions = teachingStore.savePendingCandidates(
                    result = result,
                    fingerprint = session.fingerprint,
                    createdAtEpochMs = finishedAt
                )
                DebugLog.i(TAG) {
                    "[微信示教] 演示未完成 mode=${session.lastCallMode} missing=$missing " +
                        "pending=${pendingActions.size}"
                }
                reportTeachingCapture(
                    session = session,
                    route = candidateRoute,
                    outcome = WeChatTeachingUploadOutcome.FAILED,
                    failureReason = genericFailureReason(genericResult)
                )
                showTeachingIncomplete(
                    session,
                    if (pendingActions.isEmpty()) {
                        getString(R.string.settings_wechat_teaching_incomplete)
                    } else {
                        getString(
                            R.string.settings_wechat_teaching_candidates_saved,
                            pendingActions.size
                        )
                    }
                )
            }
        }
    }

    private fun genericFailureReason(
        result: WeChatTeachingGenericResult
    ): WeChatTeachingUploadFailureReason = when ((result as? WeChatTeachingGenericResult.Incomplete)?.reason) {
        WeChatTeachingGenericFailure.VIDEO_NOT_CONFIRMED ->
            WeChatTeachingUploadFailureReason.VIDEO_NOT_CONFIRMED
        WeChatTeachingGenericFailure.LOW_RELIABILITY ->
            WeChatTeachingUploadFailureReason.LOW_RELIABILITY
        WeChatTeachingGenericFailure.NO_REUSABLE_STEPS ->
            WeChatTeachingUploadFailureReason.UNKNOWN
        null -> WeChatTeachingUploadFailureReason.NONE
    }

    private fun reportTeachingCapture(
        session: TeachingSession,
        route: WeChatTeachingRoute?,
        outcome: WeChatTeachingUploadOutcome,
        failureReason: WeChatTeachingUploadFailureReason,
        replayResult: WeChatTeachingReplayResult = WeChatTeachingReplayResult.NOT_RUN
    ) {
        session.captureReported = true
        if (!session.uploadAnonymousData) return
        val missingEventCount = session.observations.count {
            it.source == com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingObservationSource.VISIBLE_CONTROL
        }
        val event = if (route != null) {
            WeChatTeachingRouteUploadFactory.create(
                route = route,
                sessionId = session.sessionId,
                outcome = outcome,
                replayResult = replayResult,
                failureReason = failureReason,
                missingEventCount = missingEventCount
            )
        } else {
            WeChatTeachingRouteUploadFactory.createUnknown(
                fingerprint = session.fingerprint,
                sessionId = session.sessionId,
                outcome = outcome,
                replayResult = replayResult,
                failureReason = failureReason,
                missingEventCount = missingEventCount,
                createdAtEpochMs = System.currentTimeMillis()
            )
        }
        LobsterClient.reportUsage(this, event)
    }

    private fun showTeachingAcceptedWithoutRule(
        session: TeachingSession,
        verifiedBuiltInCount: Int
    ) {
        teachingTimeoutJob?.cancel()
        session.state = TeachingState.COMPLETE
        teachingOverlay?.show(
            message = if (verifiedBuiltInCount > 0) {
                getString(
                    R.string.settings_wechat_teaching_verified_no_new,
                    verifiedBuiltInCount
                )
            } else {
                getString(R.string.settings_wechat_teaching_video_confirmed_no_rules)
            },
            primaryText = getString(R.string.settings_wechat_teaching_exit),
            primaryBackgroundRes = R.drawable.bg_wechat_teaching_start,
            secondaryText = null,
            onPrimary = ::closeTeachingSession,
            onSecondary = null,
            messageColorRes = R.color.wechat_teaching_status_success
        )
    }

    private fun showTeachingCalibrationSaved(
        session: TeachingSession,
        calibratedActionCount: Int,
        deviceRuleCount: Int
    ) {
        teachingOverlay?.show(
            message = getString(
                R.string.settings_wechat_teaching_calibration_saved,
                calibratedActionCount,
                deviceRuleCount
            ),
            primaryText = getString(R.string.settings_wechat_teaching_exit),
            primaryBackgroundRes = R.drawable.bg_settings_primary_capsule,
            secondaryText = null,
            onPrimary = ::closeTeachingSession,
            onSecondary = null
        )
    }

    private fun showTeachingIncomplete(session: TeachingSession, message: String) {
        teachingTimeoutJob?.cancel()
        var pendingActionCount = 0
        if (!session.captureReported && session.startedAt > 0L) {
            val createdAt = System.currentTimeMillis()
            val semanticResult = WeChatTeachingAnalyzer.analyze(
                observations = session.observations.toList(),
                fingerprint = session.fingerprint,
                createdAtEpochMs = createdAt
            )
            pendingActionCount = teachingStore.savePendingCandidates(
                result = semanticResult,
                fingerprint = session.fingerprint,
                createdAtEpochMs = createdAt
            ).size
            val genericResult = WeChatTeachingGenericAnalyzer.analyze(
                observations = session.observations.toList(),
                fingerprint = session.fingerprint,
                videoCallConfirmed = session.videoCallConfirmed,
                createdAtEpochMs = createdAt,
                initialState = session.initialState
            )
            val route = when (genericResult) {
                is WeChatTeachingGenericResult.Complete -> genericResult.route
                is WeChatTeachingGenericResult.Incomplete -> genericResult.route
            }
            reportTeachingCapture(
                session = session,
                route = route,
                outcome = WeChatTeachingUploadOutcome.FAILED,
                failureReason = genericFailureReason(genericResult)
            )
        }
        session.state = TeachingState.INCOMPLETE
        teachingOverlay?.show(
            message = if (pendingActionCount > 0) {
                getString(
                    R.string.settings_wechat_teaching_candidates_saved,
                    pendingActionCount
                )
            } else {
                message
            },
            primaryText = getString(R.string.settings_wechat_teaching_retry),
            primaryBackgroundRes = R.drawable.bg_settings_primary_capsule,
            secondaryText = getString(R.string.settings_wechat_teaching_exit),
            onPrimary = {
                if (teachingSession === session) {
                    session.observations.clear()
                    session.visibleCaptureTracker.reset()
                    session.weChatEntered = false
                    session.selectedCallLabel = null
                    session.lastCallMode = WeChatTeachingCallMode.UNKNOWN
                    session.videoCallConfirmed = false
                    session.captureReported = false
                    session.initialState = null
                    session.sessionId = UUID.randomUUID().toString()
                    showTeachingPrepared(session, getString(R.string.settings_wechat_teaching_prepared_message))
                }
            },
            onSecondary = ::closeTeachingSession,
            messageColorRes = R.color.wechat_teaching_status_error
        )
    }

    private fun WeChatTeachingProgress.messageRes(): Int = when (this) {
        WeChatTeachingProgress.WECHAT_OPENED -> R.string.settings_wechat_teaching_progress_wechat
        WeChatTeachingProgress.CONTACT_OPENED -> R.string.settings_wechat_teaching_progress_contact
        WeChatTeachingProgress.VIDEO_OPENED -> R.string.settings_wechat_teaching_progress_video
        WeChatTeachingProgress.CALL_STARTED -> R.string.settings_wechat_teaching_progress_call
    }

    private fun closeTeachingSession() {
        teachingTimeoutJob?.cancel()
        teachingTimeoutJob = null
        teachingSession = null
        teachingOverlay?.hide()
        DebugLog.i(TAG) { "[微信示教] 已结束" }
    }

    private fun isWeChatForeground(): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            root.packageName?.toString() == WeChatPackage.NAME
        } finally {
            AccessibilityUtil.safeRecycle(root)
        }
    }

    private fun notifyState(
        session: VideoCallSession,
        state: String,
        success: Boolean,
        terminal: Boolean,
        page: WeChatPage? = session.lastDetectedPage,
        reported: Boolean = false
    ) {
        session.lastProgressAt = System.currentTimeMillis()
        deliverProgress(
            session.requestId,
            VideoCallProgress(
                message = state,
                success = success,
                terminal = terminal,
                step = session.toProgressState(success = success, terminal = terminal),
                page = page?.name,
                reported = reported
            )
        )
    }

    private fun VideoCallSession.toProgressState(success: Boolean, terminal: Boolean): AutomationState {
        return when {
            terminal && success -> AutomationState.COMPLETED
            terminal -> AutomationState.FAILED
            stateOverride != null -> stateOverride ?: AutomationState.IDLE
            else -> step.toAutomationState()
        }
    }

    private fun Step.toAutomationState(): AutomationState = when (this) {
        Step.WAITING_HOME -> AutomationState.WAITING_HOME
        Step.WAITING_LAUNCHER_UI -> AutomationState.WAITING_LAUNCHER_UI
        Step.WAITING_SEARCH_FALLBACK -> AutomationState.WAITING_SEARCH
        Step.WAITING_CONTACT_RESULT -> AutomationState.WAITING_CONTACT_RESULT
        Step.WAITING_CONTACT_DETAIL -> AutomationState.WAITING_CONTACT_DETAIL
        Step.WAITING_VIDEO_OPTIONS -> AutomationState.WAITING_VIDEO_OPTIONS
        Step.VERIFYING_CALL_STARTED -> AutomationState.VERIFYING_CALL_STARTED
    }

    private fun automationStateLabel(state: AutomationState): String = when (state) {
        AutomationState.IDLE -> "空闲"
        AutomationState.LAUNCHING_WECHAT -> "启动微信"
        AutomationState.WAITING_HOME -> "等待首页"
        AutomationState.WAITING_LAUNCHER_UI -> "查找联系人"
        AutomationState.WAITING_SEARCH -> "打开搜索"
        AutomationState.WAITING_CONTACT_RESULT -> "搜索结果"
        AutomationState.WAITING_CONTACT_DETAIL -> "联系人详情"
        AutomationState.WAITING_VIDEO_OPTIONS -> "视频通话"
        AutomationState.VERIFYING_CALL_STARTED -> "确认呼叫状态"
        AutomationState.RECOVERING -> "正在恢复"
        AutomationState.COMPLETED -> "已完成"
        AutomationState.FAILED -> "已失败"
    }

    override fun hasActiveSession(): Boolean {
        return currentSession != null || teachingSession != null
    }

    override fun consumePendingRequest() {
        val request = WeChatRequestQueue.takeNext() ?: return
        if (hasActiveSession()) {
            deliverProgress(
                request.requestId,
                VideoCallProgress(
                    message = "已有进行中的微信视频任务，请稍候",
                    success = false,
                    terminal = true
                )
            )
            return
        }
        startVideoCall(request.requestId, request.contactName)
    }

    private fun startVideoCall(requestId: String, contactName: String) {
        DebugLog.banner(
            TAG,
            listOf(
                "[微信自动] 开始请求",
                "├─ 联系人: $contactName",
                "└─ 任务ID: $requestId"
            )
        )

        LobsterClient.log("[微信自动] 请求开始: 联系人=$contactName, 任务ID=$requestId")

        if (hasActiveSession()) {
            deliverProgress(
                requestId,
                VideoCallProgress(
                    message = "已有进行中的微信视频任务，请稍候",
                    success = false,
                    terminal = true
                )
            )
            return
        }
        cancelSession(false)
        lastMissingRootLogAt = 0L
        tagWeChatVersionOnce()
        val currentTeachingFingerprint = WeChatTeachingFingerprintFactory.capture(this)
        activeTeachingProfile = WeChatLearnedRulePolicy.compatibleProfile(
            profile = teachingStore.load(),
            currentFingerprint = currentTeachingFingerprint
        )
        if (activeTeachingProfile != null) {
            DebugLog.i(TAG) { "[微信自动] 当前设备学习规则已启用为兜底" }
        }
        val session = VideoCallSession(
            requestId = requestId,
            contactName = contactName,
            step = Step.WAITING_HOME,
            stepStartedAt = System.currentTimeMillis(),
            startedAt = System.currentTimeMillis()
        )
        currentSession = session
        session.stateOverride = AutomationState.LAUNCHING_WECHAT

        floatingView?.setOnCancelListener {
            if (currentSession === session) {
                DebugLog.d(TAG) { "用户长按取消了视频通话流程" }
                cancelSession(true)
            }
        }
        floatingView?.show("正在打开微信", session.stepLabel())
        updateProgress(session, "正在打开微信")


        if (!launchWeChat()) {
            logStep(session, "launchWeChat", false, "无法找到微信启动 Intent")
            failAndHide("打开微信失败")
            return
        }
        logStep(session, "launchWeChat", true)

        armTotalTimeout(timeoutManager.getTimeout("total"), "微信视频流程整体超时")
        armTimeout(Step.WAITING_HOME, timeoutManager.getTimeout("launch"), "微信启动或返回首页超时")
        startWeChatWaitLoop(session)
    }

    private fun startWeChatWaitLoop(session: VideoCallSession) {
        wechatWaitJob?.cancel()
        wechatWaitJob = serviceScope.launch {
            var attempts = 0
            while (currentSession === session && session.step == Step.WAITING_HOME) {
                session.actionAttempts["home_wait_loop"] = attempts
                delay(adaptiveDelay(session, DelayProfile.WAIT_LOOP, attemptKey = "home_wait_loop"))
                attempts++
                val root = getWeChatRoot()
                if (root == null) {
                    DebugLog.d(TAG) { "waitLoop[$attempts]: 尚未收到微信事件，等待中" }
                    continue
                }
                val currentClass = resolveCurrentWeChatClass(root)
                val page = detectWeChatPage(root, currentClass)
                DebugLog.d(TAG) {
                    "waitLoop[$attempts]: root class=$currentClass childCount=${root.childCount} page=$page"
                }
                if (page == WeChatPage.HOME) {
                    DebugLog.d(TAG) { "waitLoop: 首页确认加载完成，推进步骤" }
                    wechatWaitJob = null
                    session.launcherPrepared = false
                    session.searchTextApplied = false
                    transitionTo(session, Step.WAITING_LAUNCHER_UI, "正在查找联系人")
                    break
                }
                if (page != WeChatPage.UNKNOWN || attempts <= MAX_UNKNOWN_HOME_OBSERVE_ATTEMPTS) {
                    scheduleAdaptiveProcess(
                        session,
                        if (page == WeChatPage.UNKNOWN) DelayProfile.WAIT_LOOP else DelayProfile.TRANSITION,
                        attemptKey = "home_wait_loop"
                    )
                }
            }
        }
    }


    private fun scheduleProcess(session: VideoCallSession, delayMillis: Long) {
        if (currentSession !== session) return
        stepClock.scheduleProcess(delayMillis)
    }

    private fun getWeChatRoot(): AccessibilityNodeInfo? = rootProvider.acquireBestRoot()

    private fun resolveCurrentWeChatClass(root: AccessibilityNodeInfo?): String? =
        rootProvider.resolveCurrentWeChatClass(root)

    private fun isLauncherReady(root: AccessibilityNodeInfo, currentClass: String?): Boolean {
        val snapshot = snapshotOf(root)
        if (snapshot != null && WeChatUiSnapshotAnalyzer.isLauncherReady(snapshot)) {
            return true
        }
        return currentClass == WeChatClassNames.LAUNCHER_UI && root.childCount > 0
    }

    private fun isChatPage(root: AccessibilityNodeInfo, currentClass: String?): Boolean {
        if (currentClass == WeChatClassNames.CHATTING_UI) {
            return true
        }
        val snapshot = snapshotOf(root)
        if (snapshot != null) {
            if (currentClass == WeChatClassNames.SEARCH_UI || WeChatUiSnapshotAnalyzer.isSearchPage(snapshot) || WeChatUiSnapshotAnalyzer.isContactInfoPage(snapshot)) {
                return false
            }
            if (WeChatUiSnapshotAnalyzer.isChatPageLike(snapshot)) {
                return true
            }
        }
        if (!elementLocator.hasEditableNode(root)) {
            return false
        }
        return hasConversationChrome(root) || currentClass == WeChatClassNames.LAUNCHER_UI
    }


    private fun hasConversationChrome(root: AccessibilityNodeInfo?): Boolean {
        val byId = elementLocator.findNodeByIds(root, WeChatViewIds.MORE_BUTTON_BASE_IDS)
        if (byId != null) {
            AccessibilityUtil.safeRecycle(byId)
            return true
        }
        val byDesc = AccessibilityUtil.findBestTextNode(root, "更多", exactMatch = false, preferBottom = false)
        if (byDesc != null) {
            AccessibilityUtil.safeRecycle(byDesc)
            return true
        }
        val byPlus = AccessibilityUtil.findBestTextNode(root, "+", exactMatch = true, preferBottom = false)
        if (byPlus != null) {
            AccessibilityUtil.safeRecycle(byPlus)
            return true
        }
        return false
    }

    private fun detectWeChatPage(
        root: AccessibilityNodeInfo,
        currentClass: String?,
        session: VideoCallSession? = currentSession
    ): WeChatPage {
        val semantic = snapshotOf(root)?.let(WeChatSemanticPageRecognizer::recognize)
        val legacy = detectWeChatPageLegacy(root, currentClass)
        val page = if (semantic != null && semantic.reliable) {
            semantic.toWeChatPage().takeIf { it != WeChatPage.UNKNOWN } ?: legacy
        } else {
            legacy
        }
        session?.lastSemanticPage = semantic
        session?.lastDetectedPage = page
        return page
    }

    private fun detectWeChatPageLegacy(root: AccessibilityNodeInfo, currentClass: String?): WeChatPage {
        if (elementLocator.isVideoCallSheetVisible(root)) {
            return WeChatPage.VIDEO_SHEET
        }
        if (currentClass == WeChatClassNames.SEARCH_UI || isSearchPage(root)) {
            return WeChatPage.SEARCH
        }
        if (currentClass == WeChatClassNames.CONTACT_INFO || currentClass == WeChatClassNames.SOS_WEBVIEW || isContactInfoPage(root)) {
            return WeChatPage.CONTACT_DETAIL
        }
        if (isChatPage(root, currentClass)) {
            return WeChatPage.CHAT
        }
        if (isLauncherReady(root, currentClass)) {
            return WeChatPage.HOME
        }
        return WeChatPage.UNKNOWN
    }

    private fun WeChatSemanticPageResult.toWeChatPage(): WeChatPage {
        return when (page) {
            WeChatSemanticPage.HOME -> WeChatPage.HOME
            WeChatSemanticPage.SEARCH -> WeChatPage.SEARCH
            WeChatSemanticPage.CONTACT_DETAIL -> WeChatPage.CONTACT_DETAIL
            WeChatSemanticPage.CHAT -> WeChatPage.CHAT
            WeChatSemanticPage.VIDEO_SHEET -> WeChatPage.VIDEO_SHEET
            WeChatSemanticPage.NO_RESULT -> WeChatPage.SEARCH
            WeChatSemanticPage.UNKNOWN -> WeChatPage.UNKNOWN
        }
    }



    private fun isSearchPage(root: AccessibilityNodeInfo): Boolean {
        val snapshot = snapshotOf(root)
        if (snapshot != null) {
            return WeChatUiSnapshotAnalyzer.isSearchPage(snapshot)
        }
        return elementLocator.hasEditableNode(root) && (
            elementLocator.hasExactText(root, "取消") ||
                elementLocator.hasExactText(root, "搜索") ||
                elementLocator.hasExactText(root, "搜索指定内容")
            )
    }

    private fun isContactInfoPage(root: AccessibilityNodeInfo): Boolean {
        val snapshot = snapshotOf(root)
        if (snapshot != null) {
            return WeChatUiSnapshotAnalyzer.isContactInfoPage(snapshot)
        }
        return elementLocator.hasExactText(root, "音视频通话") || elementLocator.hasExactText(root, "发消息")
    }


    private fun isTargetConversationPage(
        root: AccessibilityNodeInfo,
        currentClass: String?,
        contactNames: Collection<String>
    ): Boolean {
        val page = detectWeChatPage(root, currentClass)
        if (page != WeChatPage.CHAT && page != WeChatPage.CONTACT_DETAIL) {
            return false
        }
        val snapshot = snapshotOf(root)
        if (
            snapshot != null &&
            WeChatUiSnapshotAnalyzer.isVerifiedTargetConversation(snapshot, contactNames)
        ) {
            return true
        }
        val normalizedNames = contactNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val matchedKnownTitleId = normalizedNames.any { contactName ->
            val titleNode = elementLocator.findNodeByExactText(
                root,
                contactName,
                WeChatViewIds.CONTACT_TITLE_SECONDARY,
                WeChatViewIds.CONTACT_TITLE_PRIMARY
            )
            if (titleNode == null) {
                false
            } else {
                AccessibilityUtil.safeRecycle(titleNode)
                true
            }
        }
        if (matchedKnownTitleId) {
            return true
        }
        return hasLiveTargetConversationTitle(root, normalizedNames)
    }

    private fun hasLiveTargetConversationTitle(
        root: AccessibilityNodeInfo,
        contactNames: Collection<String>
    ): Boolean {
        val rootRect = Rect()
        root.getBoundsInScreen(rootRect)
        if (rootRect.isEmpty) return false
        val rootBounds = WeChatUiBounds(rootRect.left, rootRect.top, rootRect.right, rootRect.bottom)

        return contactNames.any { contactName ->
            val textNodes = AccessibilityUtil.findAllByText(root, contactName)
            val descriptionNodes = AccessibilityUtil.findNodesByContentDescription(
                root,
                contactName,
                exactMatch = true
            )
            val candidates = (textNodes + descriptionNodes).distinct()
            val matched = candidates.any { node ->
                val exactMatch = node.text?.toString() == contactName ||
                    node.contentDescription?.toString() == contactName
                if (!exactMatch) {
                    false
                } else {
                    val nodeRect = Rect()
                    node.getBoundsInScreen(nodeRect)
                    WeChatConversationTitlePolicy.isInsideTitleBand(
                        rootBounds,
                        if (nodeRect.isEmpty) null else WeChatUiBounds(
                            nodeRect.left,
                            nodeRect.top,
                            nodeRect.right,
                            nodeRect.bottom
                        )
                    )
                }
            }
            candidates.forEach(AccessibilityUtil::safeRecycle)
            matched
        }
    }

    private fun sessionContactNames(session: VideoCallSession): List<String> {
        return buildList {
            session.contactName.trim().takeIf { it.isNotEmpty() }?.let(::add)
            session.resolvedContactTitle
                ?.trim()
                ?.takeIf { it.isNotEmpty() && it != session.contactName.trim() }
                ?.let(::add)
        }
    }

    private fun updateProgress(
        session: VideoCallSession,
        message: String,
        page: WeChatPage? = session.lastDetectedPage
    ) {
        if (session.lastAnnouncedMessage != message) {
            session.lastAnnouncedMessage = message
            notifyState(session, message, success = true, terminal = false, page = page)
        }
        floatingView?.updateMessage(message, session.stepLabel())
    }



    private fun incrementActionAttempt(session: VideoCallSession, key: String): Int {
        val next = (session.actionAttempts[key] ?: 0) + 1
        session.actionAttempts[key] = next
        return next
    }

    private fun ensureAttemptBudget(
        session: VideoCallSession,
        key: String,
        maxAttempts: Int,
        failureMessage: String,
        root: AccessibilityNodeInfo? = getWeChatRoot()
    ): Boolean {
        val attempt = incrementActionAttempt(session, key)
        DebugLog.d(TAG) { "attempt[$key]=$attempt/$maxAttempts step=${session.step}" }
        if (attempt > maxAttempts) {
            failAndHide(failureMessage, root)
            return false
        }
        return true
    }

    private fun adaptiveDelay(
        session: VideoCallSession,
        profile: DelayProfile,
        attemptKey: String? = null,
        actionSucceeded: Boolean? = null
    ): Long {
        val attemptCount = attemptKey?.let { session.actionAttempts[it] ?: 0 } ?: 0
        return AdaptiveDelayCalculator.delayFor(
            stepTimeoutMs = timeoutFor(session.step),
            deviceTier = timeoutManager.getDeviceTier(),
            profile = profile,
            attemptCount = attemptCount,
            actionSucceeded = actionSucceeded
        )
    }

    private fun scheduleAdaptiveProcess(
        session: VideoCallSession,
        profile: DelayProfile,
        attemptKey: String? = null,
        actionSucceeded: Boolean? = null
    ) {
        scheduleProcess(session, adaptiveDelay(session, profile, attemptKey, actionSucceeded))
    }

    private fun settleWindow(
        session: VideoCallSession,
        profile: DelayProfile,
        attemptKey: String,
        minWindow: Long
    ): Long {
        val attemptCount = session.actionAttempts[attemptKey] ?: 0
        return AdaptiveDelayCalculator.settleWindow(
            stepTimeoutMs = timeoutFor(session.step),
            deviceTier = timeoutManager.getDeviceTier(),
            profile = profile,
            attemptCount = attemptCount,
            minWindow = minWindow
        )
    }

    private fun rerouteTo(
        session: VideoCallSession,
        nextStep: Step,
        message: String,
        recovering: Boolean = false,
        launching: Boolean = false
    ) {
        recordStepHistory(session, nextStep)
        session.step = nextStep
        session.stepStartedAt = System.currentTimeMillis()
        resetForStepEntry(session, nextStep)
        session.moreButtonClickedAt = 0L
        session.actionAttempts.clear()
        session.lastDetectedPage = null
        session.dismissAttempts = 0  // Step 切换，弹窗计数重置
        session.stateOverride = when {
            launching -> AutomationState.LAUNCHING_WECHAT
            recovering -> AutomationState.RECOVERING
            else -> null
        }
        updateProgress(session, message)
        armTimeout(nextStep, timeoutFor(nextStep), failureMessageFor(nextStep, session.contactName))
        if (nextStep == Step.WAITING_HOME) {
            startWeChatWaitLoop(session)
        } else {
            wechatWaitJob?.cancel()
            wechatWaitJob = null
        }
        scheduleAdaptiveProcess(session, DelayProfile.TRANSITION)
    }

    private fun recordStepHistory(session: VideoCallSession, nextStep: Step) {
        if (session.step == nextStep) {
            return
        }
        if (session.stepHistory.size >= 5) {
            session.stepHistory.removeFirst()
        }
        session.stepHistory.addLast(session.step)
    }

    private fun prepareRecoveryState(session: VideoCallSession, target: Step) {
        when (target) {
            Step.WAITING_HOME -> {
                session.searchTextApplied = false
                session.searchInputSubmittedAt = 0L
                session.launcherPrepared = false
                session.resolvedContactTitle = null
            }
            Step.WAITING_LAUNCHER_UI,
            Step.WAITING_SEARCH_FALLBACK -> {
                session.searchTextApplied = false
                session.searchInputSubmittedAt = 0L
                session.launcherPrepared = false
                session.resolvedContactTitle = null
            }
            Step.WAITING_CONTACT_RESULT -> {
                session.moreButtonClickedAt = 0L
                session.resolvedContactTitle = null
            }
            Step.WAITING_CONTACT_DETAIL,
            Step.WAITING_VIDEO_OPTIONS,
            Step.VERIFYING_CALL_STARTED -> Unit
        }
    }

    private fun resetForStepEntry(session: VideoCallSession, target: Step) {
        when (target) {
            Step.WAITING_HOME,
            Step.WAITING_LAUNCHER_UI,
            Step.WAITING_SEARCH_FALLBACK -> {
                session.searchTextApplied = false
                session.searchInputSubmittedAt = 0L
                session.resolvedContactTitle = null
            }
            Step.WAITING_CONTACT_RESULT -> {
                session.resolvedContactTitle = null
            }
            Step.WAITING_CONTACT_DETAIL,
            Step.WAITING_VIDEO_OPTIONS -> Unit
            Step.VERIFYING_CALL_STARTED -> {
                session.callVerificationState = WeChatCallVerificationState()
                session.callVerificationPollCount = 0
                session.lastCallVerificationLogKey = null
            }
        }
    }

    private fun resolveRecoveryStep(session: VideoCallSession, failedStep: Step, failCount: Int): Step {
        return when (failedStep) {
            Step.VERIFYING_CALL_STARTED -> failedStep
            Step.WAITING_VIDEO_OPTIONS -> if (failCount <= 2) failedStep else Step.WAITING_CONTACT_DETAIL
            Step.WAITING_CONTACT_DETAIL -> if (failCount <= 2) failedStep else Step.WAITING_CONTACT_RESULT
            Step.WAITING_CONTACT_RESULT -> if (failCount <= 2) Step.WAITING_SEARCH_FALLBACK else Step.WAITING_LAUNCHER_UI
            Step.WAITING_SEARCH_FALLBACK -> Step.WAITING_LAUNCHER_UI
            Step.WAITING_LAUNCHER_UI -> session.stepHistory.lastOrNull() ?: Step.WAITING_HOME
            Step.WAITING_HOME -> Step.WAITING_HOME
        }
    }

    private fun resolveAndRerouteTo(session: VideoCallSession, failedStep: Step, reason: String) {
        val failCount = (session.stepFailCount[failedStep] ?: 0) + 1
        session.stepFailCount[failedStep] = failCount
        if (failCount > MAX_STEP_RECOVERY_ATTEMPTS) {
            failAndHide("页面恢复失败，请重试", getWeChatRoot())
            return
        }
        val target = resolveRecoveryStep(session, failedStep, failCount)
        prepareRecoveryState(session, target)
        DebugLog.d(TAG) {
            "resolveAndRerouteTo: failed=$failedStep target=$target failCount=$failCount reason=$reason history=${session.stepHistory}"
        }
        rerouteTo(session, target, "页面有变化，正在恢复", recovering = true)
    }


    private fun processCurrentWindow() {
        val session = currentSession ?: return
        val root = getWeChatRoot()

        if (root == null) {
            if (session.step == Step.VERIFYING_CALL_STARTED) {
                handleVerifyingCallStarted(
                    session = session,
                    root = null,
                    currentClass = rootProvider.lastObservedClassName
                )
                return
            }
            val fallbackPkg = rootInActiveWindow?.packageName?.toString()
            val now = System.currentTimeMillis()
            if (session.missingRootSince == 0L) session.missingRootSince = now
            if (now - lastMissingRootLogAt >= 2000L) {
                lastMissingRootLogAt = now
                WeChatFailureDiagnostics.logDebugLong(
                    TAG,
                    "processCurrentWindow: 微信窗口未找到，当前前台包名=$fallbackPkg, step=${session.step}, contact=${session.contactName}\nwindows=${WeChatFailureDiagnostics.describeWindows(this)}"
                )
            }
            when (WeChatForegroundRecoveryPolicy.decide(
                activePackage = fallbackPkg,
                missingRootMs = now - session.missingRootSince,
                recoveryAttempts = session.foregroundRecoveryAttempts
            )) {
                ForegroundRecoveryDecision.WAIT -> Unit
                ForegroundRecoveryDecision.RELAUNCH -> {
                    session.foregroundRecoveryAttempts++
                    session.missingRootSince = now
                    logStep(session, "recoverWechatForeground", true, "attempt=${session.foregroundRecoveryAttempts}")
                    rootProvider.reset()
                    prepareRecoveryState(session, Step.WAITING_HOME)
                    rerouteTo(session, Step.WAITING_HOME, "微信已离开前台，正在重新打开", launching = true)
                    if (!launchWeChat()) failAndHide("重新打开微信失败")
                    return
                }
                ForegroundRecoveryDecision.FAIL -> {
                    logStep(session, "recoverWechatForeground", false, "package=$fallbackPkg")
                    failAndHide("微信窗口恢复失败")
                    return
                }
            }
            scheduleAdaptiveProcess(session, DelayProfile.WAIT_LOOP)
            return
        }
        session.missingRootSince = 0L

        val now = System.currentTimeMillis()
        if (now < session.dismissingUntil) {
            DebugLog.d(TAG) { "processCurrentWindow: 弹窗冷却中，剩余${session.dismissingUntil - now}ms，跳过" }
            return
        }

        val remaining = session.stepStartedAt + timeoutFor(session.step) - now
        if (remaining > 3000L && session.dismissAttempts < 3) {
            if (tryDismissTransientUi(session, root)) {
                return
            }
        }

        val currentClass = resolveCurrentWeChatClass(root)
        DebugLog.d(TAG) {
            "processCurrentWindow: step=${session.step} class=$currentClass rawClass=${root.className} lastUiClass=${rootProvider.lastObservedClassName}"
        }

        if (session.step != Step.VERIFYING_CALL_STARTED && applyCapabilityDecision(session, root, currentClass)) {
            return
        }

        when (session.step) {
            Step.WAITING_HOME -> handleWaitingHome(session, root, currentClass)
            Step.WAITING_LAUNCHER_UI -> handleLauncherUI(session, root)
            Step.WAITING_SEARCH_FALLBACK -> handleSearchFallback(session, root)
            Step.WAITING_CONTACT_RESULT -> handleContactResult(session, root)
            Step.WAITING_CONTACT_DETAIL -> handleContactDetail(session, root)
            Step.WAITING_VIDEO_OPTIONS -> handleVideoOptions(session, root)
            Step.VERIFYING_CALL_STARTED -> handleVerifyingCallStarted(session, root, currentClass)
        }
    }

    private fun snapshotOf(root: AccessibilityNodeInfo?): WeChatUiSnapshot? {
        return WeChatUiSnapshot.fromNode(root)
    }

    private fun teachingSnapshotOf(root: AccessibilityNodeInfo?): WeChatUiSnapshot? {
        return WeChatUiSnapshot.fromNode(
            root = root,
            maxDepth = 32,
            maxNodes = 420
        )
    }

    private fun applyCapabilityDecision(
        session: VideoCallSession,
        root: AccessibilityNodeInfo,
        currentClass: String?
    ): Boolean {
        val snapshot = snapshotOf(root)
        val semantic = WeChatSemanticPageRecognizer.recognize(
            snapshot = snapshot,
            currentClass = currentClass,
            expectingVideoSheet = session.step == Step.WAITING_VIDEO_OPTIONS
        )
        val contactScore = if (semantic.page == WeChatSemanticPage.SEARCH) {
            WeChatUiSnapshot.fromNode(root, maxDepth = 32, maxNodes = 420)?.let { searchSnapshot ->
                WeChatUiSnapshotAnalyzer.scoreContactSearchResult(searchSnapshot, session.contactName)
            }
        } else {
            null
        }
        val contactNames = sessionContactNames(session)
        val semanticTitleVerified = snapshot != null &&
            WeChatUiSnapshotAnalyzer.isVerifiedTargetConversation(snapshot, contactNames)
        val legacyExactTitleVerified =
            (semantic.page == WeChatSemanticPage.CHAT || semantic.page == WeChatSemanticPage.CONTACT_DETAIL) &&
                isTargetConversationPage(root, currentClass, contactNames)
        val targetConversationVerified = WeChatConversationVerificationPolicy.isVerified(
            page = semantic.page,
            semanticTitleVerified = semanticTitleVerified,
            legacyExactTitleVerified = legacyExactTitleVerified
        )
        val searchQueryVerified = semantic.page == WeChatSemanticPage.SEARCH &&
            session.searchTextApplied &&
            elementLocator.verifySearchInputFilled(root, session.contactName)
        val observation = WeChatCapabilityObservation(
            page = semantic,
            targetConversationVerified = targetConversationVerified,
            searchQueryVerified = searchQueryVerified,
            contactAccepted = contactScore?.accepted == true
        )
        val plannedDecision = capabilityTree.decide(
            state = session.behaviorState,
            observation = observation
        )
        val observedResult = capabilityTree.capability(plannedDecision.capabilityId)
            .observeResult(observation)
        val decision = if (plannedDecision.status == WeChatCapabilityStatus.READY) {
            plannedDecision.copy(
                status = observedResult.status,
                failure = plannedDecision.failure ?: observedResult.failure
            )
        } else {
            plannedDecision
        }
        session.behaviorState = decision.nextState
        session.lastSemanticPage = semantic
        session.lastCapabilityId = decision.capabilityId
        session.lastCapabilityReason = decision.reason
        session.lastCapabilityFailure = decision.failure
        logCapabilityDecision(session, semantic, decision, contactScore)
        if (decision.failure == WeChatCapabilityFailure.PRECONDITION_NOT_MET) {
            recoverToHome(session, root, currentClass, "capabilityTree: precondition_not_met")
            return true
        }
        return when (decision.capabilityId) {
            WeChatCapabilityId.RECOVER_HOME -> {
                recoverToHome(session, root, currentClass, "capabilityTree: ${decision.reason}")
                true
            }
            WeChatCapabilityId.OPEN_RECENT_CONVERSATION,
            WeChatCapabilityId.OPEN_SEARCH -> {
                if (session.step == Step.WAITING_HOME) {
                    session.launcherPrepared = false
                    session.searchTextApplied = false
                    transitionTo(session, Step.WAITING_LAUNCHER_UI, "正在查找联系人")
                    true
                } else {
                    false
                }
            }
            WeChatCapabilityId.TYPE_CONTACT -> when (session.step) {
                Step.WAITING_HOME -> {
                    session.launcherPrepared = false
                    session.searchTextApplied = false
                    transitionTo(session, Step.WAITING_SEARCH_FALLBACK, "正在搜索联系人")
                    true
                }
                Step.WAITING_CONTACT_RESULT -> {
                    rerouteTo(session, Step.WAITING_SEARCH_FALLBACK, "正在重新输入联系人")
                    true
                }
                else -> false
            }
            WeChatCapabilityId.OPEN_VIDEO_ENTRY -> {
                if (session.step != Step.WAITING_CONTACT_DETAIL && targetConversationVerified) {
                    transitionTo(session, Step.WAITING_CONTACT_DETAIL, "已确认目标联系人")
                    true
                } else {
                    false
                }
            }
            WeChatCapabilityId.SELECT_VIDEO -> {
                if (session.step != Step.WAITING_VIDEO_OPTIONS) {
                    transitionTo(session, Step.WAITING_VIDEO_OPTIONS, "正在选择视频通话")
                    true
                } else {
                    false
                }
            }
            WeChatCapabilityId.OPEN_SEARCH_RESULT,
            WeChatCapabilityId.CONFIRM_CALL_STARTED,
            WeChatCapabilityId.VERIFY_TARGET_CONVERSATION,
            WeChatCapabilityId.LAUNCH_WECHAT -> false
            WeChatCapabilityId.WAIT -> {
                if (decision.reason == "waiting_search_result") {
                    return when (
                        WeChatSearchResultWaitPolicy.decide(
                            queryVerified = searchQueryVerified,
                            inSearchInputStep = session.step == Step.WAITING_SEARCH_FALLBACK,
                            inSearchResultStep = session.step == Step.WAITING_CONTACT_RESULT
                        )
                    ) {
                        SearchResultWaitDecision.ADVANCE_TO_RESULTS -> {
                            markCapabilitySucceeded(session, WeChatCapabilityId.TYPE_CONTACT)
                            transitionTo(session, Step.WAITING_CONTACT_RESULT, "正在查找联系人")
                            true
                        }
                        SearchResultWaitDecision.DEFER_TO_RESULT_HANDLER -> false
                        SearchResultWaitDecision.KEEP_WAITING -> {
                            scheduleAdaptiveProcess(session, DelayProfile.STABLE)
                            true
                        }
                    }
                }
                when (decision.failure) {
                    WeChatCapabilityFailure.SEARCH_RESULT_NOT_FOUND ->
                        failAndHide("未找到联系人: ${session.contactName}", root)
                    WeChatCapabilityFailure.LOW_PAGE_CONFIDENCE,
                    WeChatCapabilityFailure.TARGET_NOT_VERIFIED -> {
                        val attempt = incrementActionAttempt(session, "capability_page_observe")
                        when (
                            WeChatLowConfidenceRecoveryPolicy.decide(
                                currentClass = currentClass,
                                observeAttempt = attempt,
                                conversationRecoveries = session.conversationPageRecoveries
                            )
                        ) {
                            WeChatLowConfidenceAction.WAIT -> scheduleAdaptiveProcess(
                                session,
                                DelayProfile.STABLE,
                                attemptKey = "capability_page_observe"
                            )
                            WeChatLowConfidenceAction.RECOVER_HOME -> {
                                if (WeChatLowConfidenceRecoveryPolicy.isConversationClass(currentClass)) {
                                    session.conversationPageRecoveries += 1
                                    if (session.behaviorState.selectedRoute == WeChatRouteId.RECENT_MESSAGES) {
                                        markCapabilityFailed(
                                            session,
                                            WeChatCapabilityId.OPEN_RECENT_CONVERSATION,
                                            WeChatCapabilityFailure.ACTION_FAILED
                                        )
                                    }
                                }
                                recoverToHome(
                                    session,
                                    root,
                                    currentClass,
                                    "capabilityTree: ${decision.reason}"
                                )
                            }
                            WeChatLowConfidenceAction.FAIL_SAFE -> failAndHide(
                                "微信聊天页面暂时无法识别，请重试",
                                root
                            )
                        }
                    }
                    else -> scheduleAdaptiveProcess(session, DelayProfile.STABLE)
                }
                true
            }
        }
    }

    private fun logCapabilityDecision(
        session: VideoCallSession,
        page: WeChatSemanticPageResult,
        decision: WeChatBehaviorDecision,
        contactScore: WeChatTargetScore?
    ) {
        val logKey = listOf(
            decision.capabilityId.name,
            decision.status.name,
            decision.failure?.name.orEmpty(),
            decision.nextState.selectedRoute?.name.orEmpty(),
            page.page.name,
            decision.reason
        ).joinToString(":")
        if (session.lastCapabilityLogKey == logKey) return
        session.lastCapabilityLogKey = logKey
        val details = buildString {
            append("page=").append(page.page.name)
            append(" confidence=").append(page.confidence)
            append(" route=").append(decision.nextState.selectedRoute?.name ?: "NONE")
            append(" failure=").append(decision.failure?.name ?: "NONE")
            contactScore?.score?.let { append(" targetScore=").append(it) }
            append(" reason=").append(decision.reason)
        }
        DebugLog.i(TAG) {
            "[微信能力] capability=${decision.capabilityId.name} result=${decision.status.name} $details"
        }
        LobsterClient.log(
            "[微信能力] capability=${decision.capabilityId.name} result=${decision.status.name} $details"
        )
        logStep(
            session = session,
            action = "capability.${decision.capabilityId.name.lowercase()}",
            result = decision.status.name,
            extra = details
        )
    }

    private fun markCapabilityFailed(
        session: VideoCallSession,
        capabilityId: WeChatCapabilityId,
        failure: WeChatCapabilityFailure
    ) {
        session.behaviorState = session.behaviorState.markFailed(capabilityId, failure)
        session.lastCapabilityId = capabilityId
        session.lastCapabilityFailure = failure
    }

    private fun markCapabilitySucceeded(
        session: VideoCallSession,
        capabilityId: WeChatCapabilityId
    ) {
        session.behaviorState = session.behaviorState.markSucceeded(capabilityId)
        session.lastCapabilityId = capabilityId
        session.lastCapabilityFailure = null
    }

    private fun tryDismissTransientUi(session: VideoCallSession, root: AccessibilityNodeInfo?): Boolean {
        // 组合条件：只有当前 Step 期望的关键节点找不到时，才认为是弹窗干扰
        // 避免把正常页面里的“我知道了”等按钮误当弹窗处理
        if (!isCurrentStepBlocked(session, root)) {
            return false
        }
        val action = snapshotOf(root)?.let(WeChatUiSnapshotAnalyzer::suggestDismissAction)
            ?: WeChatDismissAction.NONE
        val dismissed = when (action) {
            WeChatDismissAction.SEARCH_CANCEL -> elementLocator.clickSearchCancel(root)
            WeChatDismissAction.SHEET_CANCEL -> elementLocator.clickVideoCallSheetCancel(root)
            WeChatDismissAction.CLOSE_DIALOG -> elementLocator.clickKnownDialogClose(root)
            WeChatDismissAction.NONE -> false
        }
        if (!dismissed) {
            return false
        }
        // dismiss 成功：设置冷却时间戳，计数+1
        session.dismissAttempts++
        session.dismissingUntil = System.currentTimeMillis() + 1500L
        val message = when (action) {
            WeChatDismissAction.SEARCH_CANCEL -> "正在关闭搜索"
            WeChatDismissAction.SHEET_CANCEL -> "正在关闭弹窗"
            WeChatDismissAction.CLOSE_DIALOG -> "正在关闭提示"
            WeChatDismissAction.NONE -> "正在恢复页面"
        }
        DebugLog.d(TAG) { "tryDismissTransientUi: action=$action attempts=${session.dismissAttempts}" }
        updateProgress(session, message)
        scheduleAdaptiveProcess(session, DelayProfile.RECOVER)
        return true
    }

    /**
     * 判断当前 Step 期望的核心节点是否被遮挡（找不到），
     * 只有被遮挡时才值得尝试清除弹窗。
     */
    private fun isCurrentStepBlocked(session: VideoCallSession, root: AccessibilityNodeInfo?): Boolean {
        return when (session.step) {
            Step.WAITING_SEARCH_FALLBACK -> {
                // 搜索页：找不到输入框才算被遮挡
                elementLocator.findNodeByIds(root, WeChatViewIds.SEARCH_INPUT) == null &&
                    AccessibilityUtil.findFirstEditableNode(root) == null
            }
            Step.WAITING_CONTACT_DETAIL -> {
                // 联系人详情页：找不到「音视频通话」和「发消息」才算被遮挡
                !elementLocator.hasExactText(root, "音视频通话") &&
                    !elementLocator.hasExactText(root, "发消息")
            }
            Step.WAITING_VIDEO_OPTIONS -> {
                // 视频选项：弹窗本身找不到才算被遮挡（弹窗未出现时不属于"被遮挡"）
                // 这个 Step 不需要弹窗清理，由 settle window 机制处理
                false
            }
            else -> false  // 其他 Step 不做弹窗清理
        }
    }

    private fun recoverToHome(
        session: VideoCallSession,
        root: AccessibilityNodeInfo,
        currentClass: String?,
        reason: String
    ) {
        session.stateOverride = AutomationState.RECOVERING
        session.resolvedContactTitle = null
        updateProgress(session, "正在返回微信首页")
        val backAttempt = incrementActionAttempt(session, "home_back")
        if (backAttempt > MAX_HOME_BACK_ATTEMPTS) {
            DebugLog.d(TAG) { "recoverToHome: 超过$MAX_HOME_BACK_ATTEMPTS 次，尝试使用 HOME 退出微信" }
            val homeSuccess = performGlobalAction(GLOBAL_ACTION_HOME)
            if (!homeSuccess) {
                fallbackBackFromHomeRecovery(session, currentClass, "$reason, homeAction=false")
                return
            }
            serviceScope.launch {
                delay(HOME_ACTION_SETTLE_DELAY_MS)
                if (currentSession !== session) {
                    return@launch
                }
                val activePackage = getWeChatRoot()?.packageName?.toString()
                    ?: rootInActiveWindow?.packageName?.toString()
                if (activePackage == WeChatPackage.NAME) {
                    DebugLog.d(TAG) { "recoverToHome: HOME 后仍停留在微信前台，降级继续 BACK" }
                    fallbackBackFromHomeRecovery(session, currentClass, "$reason, stillInWeChat=true")
                    return@launch
                }
                session.actionAttempts.clear()
                session.searchTextApplied = false
                session.launcherPrepared = false
                session.resolvedContactTitle = null
                session.moreButtonClickedAt = 0L
                rerouteTo(
                    session,
                    Step.WAITING_HOME,
                    "已回到桌面，正在重新启动微信",
                    launching = true
                )
                if (!launchWeChat()) {
                    failAndHide("返回桌面后重新打开微信失败")
                }
            }
            return
        }
        val backSuccess = performGlobalAction(GLOBAL_ACTION_BACK)
        DebugLog.d(TAG) { "$reason class=$currentClass, backSuccess=$backSuccess attempt=$backAttempt" }
        scheduleAdaptiveProcess(
            session,
            DelayProfile.RECOVER,
            attemptKey = "home_back",
            actionSucceeded = backSuccess
        )
    }

    private fun fallbackBackFromHomeRecovery(
        session: VideoCallSession,
        currentClass: String?,
        reason: String
    ) {
        val backSuccess = performGlobalAction(GLOBAL_ACTION_BACK)
        DebugLog.d(TAG) { "fallbackBackFromHomeRecovery: reason=$reason class=$currentClass backSuccess=$backSuccess" }
        scheduleAdaptiveProcess(
            session,
            DelayProfile.RECOVER,
            attemptKey = "home_back",
            actionSucceeded = backSuccess
        )
    }


    private fun handleWaitingHome(session: VideoCallSession, root: AccessibilityNodeInfo, currentClass: String?) {
        when (detectWeChatPage(root, currentClass)) {
            WeChatPage.HOME -> {
                logStep(session, "detectPage", "HOME", "class=$currentClass → 转消息列表")
                session.launcherPrepared = false
                session.searchTextApplied = false
                transitionTo(session, Step.WAITING_LAUNCHER_UI, "正在查找联系人")
            }
            WeChatPage.CHAT,
            WeChatPage.CONTACT_DETAIL -> {
                if (isTargetConversationPage(root, currentClass, sessionContactNames(session))) {
                    logStep(session, "detectPage", "TARGET_CHAT", "已在目标联系人页，直接发起视频")
                    transitionTo(session, Step.WAITING_CONTACT_DETAIL, "正在打开聊天")
                    return
                }
                logStep(session, "detectPage", "OTHER_CHAT", "非目标联系人页 class=$currentClass，回首页")
                recoverToHome(
                    session = session,
                    root = root,
                    currentClass = currentClass,
                    reason = "WAITING_HOME: 当前处于非目标联系人页"
                )
            }
            WeChatPage.SEARCH -> {
                logStep(session, "detectPage", "SEARCH", "已在搜索页，直接接续")
                session.launcherPrepared = false
                session.searchTextApplied = false
                transitionTo(session, Step.WAITING_SEARCH_FALLBACK, "正在搜索联系人")
            }
            WeChatPage.VIDEO_SHEET -> {
                logStep(session, "detectPage", "VIDEO_SHEET", "视频弹窗意外出现，回首页")
                recoverToHome(
                    session = session,
                    root = root,
                    currentClass = currentClass,
                    reason = "WAITING_HOME: 当前在视频弹窗"
                )
            }
            WeChatPage.UNKNOWN -> {
                val observeAttempt = incrementActionAttempt(session, "home_observe")
                logStep(session, "detectPage", "UNKNOWN", "class=$currentClass observeAttempt=$observeAttempt childCount=${root.childCount}")
                if (observeAttempt <= MAX_UNKNOWN_HOME_OBSERVE_ATTEMPTS) {
                    scheduleAdaptiveProcess(session, DelayProfile.STABLE, attemptKey = "home_observe")
                    return
                }
                recoverToHome(
                    session = session,
                    root = root,
                    currentClass = currentClass,
                    reason = "WAITING_HOME: 未识别页面，尝试返回首页"
                )
            }
        }
    }


    private fun handleLauncherUI(session: VideoCallSession, root: AccessibilityNodeInfo) {
        val currentClass = resolveCurrentWeChatClass(root)
        val page = detectWeChatPage(root, currentClass)

        if (page == WeChatPage.SEARCH) {
            logStep(session, "detectPage", "SEARCH", "搜索页已打开，直接进搜索阶段")
            markCapabilitySucceeded(session, WeChatCapabilityId.OPEN_SEARCH)
            session.launcherPrepared = false
            transitionTo(session, Step.WAITING_SEARCH_FALLBACK, "正在打开搜索")
            return
        }

        if (page != WeChatPage.HOME) {
            logStep(session, "detectPage", page, "非首页，重新归一化")
            session.launcherPrepared = false
            session.searchTextApplied = false
            rerouteTo(session, Step.WAITING_HOME, "正在返回微信首页")
            return
        }

        val searchRouteSelected = session.behaviorState.selectedRoute == WeChatRouteId.SEARCH
        if (!searchRouteSelected && !session.launcherPrepared) {
            session.launcherPrepared = true
            val tabClicked = elementLocator.clickMessageTab(root)
            logStep(session, "clickMessageTab", tabClicked)
            scheduleAdaptiveProcess(
                session,
                if (tabClicked) DelayProfile.TRANSITION else DelayProfile.STABLE
            )
            return
        }

        if (!searchRouteSelected) {
            val contactNames = sessionContactNames(session)
            val contactNode = elementLocator.findContactInMessageList(root, contactNames)
            if (contactNode != null) {
                val success = AccessibilityUtil.performClick(this, contactNode)
                logStep(session, "clickContactInList", success, "contacts=$contactNames node=${AccessibilityUtil.summarizeNode(contactNode)}")
                AccessibilityUtil.safeRecycle(contactNode)
                if (success) {
                    markCapabilitySucceeded(session, WeChatCapabilityId.OPEN_RECENT_CONVERSATION)
                    transitionTo(session, Step.WAITING_CONTACT_DETAIL, "正在打开聊天")
                    return
                }
            } else {
                logStep(session, "findContactInList", false, "消息列表未找到 contacts=$contactNames，转搜索路径")
            }
            markCapabilityFailed(
                session,
                WeChatCapabilityId.OPEN_RECENT_CONVERSATION,
                WeChatCapabilityFailure.RECENT_TARGET_NOT_FOUND
            )
        }

        updateProgress(session, "消息列表未找到，正在打开搜索")
        val searchClicked = elementLocator.clickTopSearchBar(root)
        logStep(session, "clickTopSearchBar", searchClicked)
        if (searchClicked) {
            session.behaviorState = session.behaviorState.copy(selectedRoute = WeChatRouteId.SEARCH)
            session.searchTextApplied = false
            transitionTo(session, Step.WAITING_SEARCH_FALLBACK, "正在打开搜索")
            return
        }
        if (!ensureAttemptBudget(session, "search_entry", MAX_SEARCH_ENTRY_ATTEMPTS, "查找联系人入口失败", root)) {
            return
        }
        session.launcherPrepared = false
        scheduleAdaptiveProcess(session, DelayProfile.TRANSITION, attemptKey = "search_entry")
    }

    private fun handleSearchFallback(session: VideoCallSession, root: AccessibilityNodeInfo) {
        val currentClass = resolveCurrentWeChatClass(root)
        when (detectWeChatPage(root, currentClass)) {

            WeChatPage.HOME -> {
                logStep(session, "detectPage", "HOME", "搜索页未打开，仍在首页，重新点搜索")
                if (!ensureAttemptBudget(session, "search_open", MAX_SEARCH_OPEN_ATTEMPTS, "打开搜索失败", root)) {
                    return
                }
                session.searchTextApplied = false
                session.launcherPrepared = false
                rerouteTo(session, Step.WAITING_LAUNCHER_UI, "正在重新打开搜索")
                return
            }

            WeChatPage.SEARCH -> Unit
            else -> {
                logStep(session, "detectPage", "UNEXPECTED:$currentClass", "异常页面，归一化回首页")
                session.searchTextApplied = false
                session.launcherPrepared = false
                rerouteTo(session, Step.WAITING_HOME, "正在返回微信首页")
                return
            }
        }

        val now = System.currentTimeMillis()
        val verified = session.searchTextApplied && elementLocator.verifySearchInputFilled(root, session.contactName)
        when (WeChatSearchInputPolicy.decide(
            submitted = session.searchTextApplied,
            verified = verified,
            elapsedMs = now - session.searchInputSubmittedAt,
            failedAttempts = session.actionAttempts["search_input"] ?: 0
        )) {
            SearchInputDecision.COMPLETE -> {
                markCapabilitySucceeded(session, WeChatCapabilityId.TYPE_CONTACT)
                transitionTo(session, Step.WAITING_CONTACT_RESULT, "正在查找联系人")
            }
            SearchInputDecision.WAIT_FOR_VERIFICATION -> scheduleAdaptiveProcess(session, DelayProfile.STABLE)
            SearchInputDecision.SUBMIT -> {
                val submitted = elementLocator.fillSearchInput(root, session.contactName)
                logStep(session, "submitSearchInput", submitted)
                if (submitted) {
                    session.searchTextApplied = true
                    session.searchInputSubmittedAt = now
                    scheduleAdaptiveProcess(session, DelayProfile.STABLE)
                } else {
                    if (!ensureAttemptBudget(session, "search_input", WeChatSearchInputPolicy.MAX_FAILED_ATTEMPTS, "输入搜索名称失败", root)) return
                    scheduleAdaptiveProcess(session, DelayProfile.STABLE, attemptKey = "search_input", actionSucceeded = false)
                }
            }
            SearchInputDecision.RETRY -> {
                session.searchTextApplied = false
                session.searchInputSubmittedAt = 0L
                if (!ensureAttemptBudget(session, "search_input", WeChatSearchInputPolicy.MAX_FAILED_ATTEMPTS, "输入搜索名称失败", root)) return
                scheduleAdaptiveProcess(session, DelayProfile.STABLE, attemptKey = "search_input", actionSucceeded = false)
            }
            SearchInputDecision.FAIL -> failAndHide("输入搜索名称失败", root)
        }
    }

    private fun handleContactResult(session: VideoCallSession, root: AccessibilityNodeInfo) {
        val currentClass = resolveCurrentWeChatClass(root)
        when (detectWeChatPage(root, currentClass)) {

            WeChatPage.SEARCH -> Unit
            WeChatPage.CHAT,
            WeChatPage.CONTACT_DETAIL -> {
                if (isTargetConversationPage(root, currentClass, sessionContactNames(session))) {
                    logStep(session, "detectPage", "TARGET_CHAT", "已进入目标联系人页，直接推进")
                    rerouteTo(session, Step.WAITING_CONTACT_DETAIL, "正在打开联系人")
                } else {
                    logStep(session, "detectPage", "OTHER_CHAT", "进入了非目标联系人页，触发步骤恢复")
                    resolveAndRerouteTo(session, session.step, "WAITING_CONTACT_RESULT: nonTargetConversation")
                }
                return
            }
            WeChatPage.VIDEO_SHEET -> {
                logStep(session, "detectPage", "VIDEO_SHEET", "已出现视频选项，直接推进")
                rerouteTo(session, Step.WAITING_VIDEO_OPTIONS, "正在选择视频通话")
                return
            }
            WeChatPage.HOME -> {
                logStep(session, "detectPage", "HOME", "搜索页已关闭回到首页，重新查找")
                session.searchTextApplied = false
                session.launcherPrepared = true
                rerouteTo(session, Step.WAITING_LAUNCHER_UI, "正在重新打开搜索")
                return
            }
            WeChatPage.UNKNOWN -> {
                logStep(session, "detectPage", "UNKNOWN", "页面未知，回首页归一化")
                session.searchTextApplied = false
                session.launcherPrepared = false
                rerouteTo(session, Step.WAITING_HOME, "正在返回微信首页")
                return
            }
        }

        val contactClicked = clickContactResult(root, session)
        logStep(session, "clickContactResult", contactClicked, "contact=${session.contactName}, resolved=${session.resolvedContactTitle}")
        if (contactClicked) {
            markCapabilitySucceeded(session, WeChatCapabilityId.OPEN_SEARCH_RESULT)
            transitionTo(session, Step.WAITING_CONTACT_DETAIL, "正在打开联系人")
            return
        }
        if (elementLocator.hasNoSearchResult(root)) {
            logStep(session, "hasNoSearchResult", true, "contact=${session.contactName}，直接失败")
            failAndHide("未找到联系人: ${session.contactName}", root)
            return
        }
        val pollCount = incrementActionAttempt(session, "contact_result_poll")
        logStep(session, "pollSearchResult", "wait#$pollCount")
        scheduleAdaptiveProcess(session, DelayProfile.STABLE, attemptKey = "contact_result_poll", actionSucceeded = pollCount == 1)
    }

    private fun handleContactDetail(session: VideoCallSession, root: AccessibilityNodeInfo) {
        val currentClass = resolveCurrentWeChatClass(root)
        when (detectWeChatPage(root, currentClass)) {

            WeChatPage.CONTACT_DETAIL -> {
                logStep(session, "detectPage", "CONTACT_DETAIL", "直接找音视频通话按钮")
            }
            WeChatPage.CHAT -> {
                if (!isTargetConversationPage(root, currentClass, sessionContactNames(session))) {
                    logStep(session, "detectPage", "OTHER_CHAT", "非目标联系人页，重新恢复")
                    resolveAndRerouteTo(session, session.step, "WAITING_CONTACT_DETAIL: nonTargetConversation")
                    return
                }
                logStep(session, "detectPage", "CHAT", "聊天页，点+展开菜单发起视频通话")
            }
            WeChatPage.VIDEO_SHEET -> {
                logStep(session, "detectPage", "VIDEO_SHEET", "已出现视频选项，直接选择")
                transitionTo(session, Step.WAITING_VIDEO_OPTIONS, "正在选择视频通话")
                return
            }
            WeChatPage.SEARCH -> {
                logStep(session, "detectPage", "SEARCH", "仍停留在搜索页，回到结果阶段")
                rerouteTo(session, Step.WAITING_CONTACT_RESULT, "正在打开联系人")
                return
            }
            WeChatPage.HOME -> {
                logStep(session, "detectPage", "HOME", "已回到首页，重新查找联系人")
                session.searchTextApplied = false
                session.launcherPrepared = true
                rerouteTo(session, Step.WAITING_LAUNCHER_UI, "正在重新查找联系人")
                return
            }
            WeChatPage.UNKNOWN -> {
                logStep(session, "detectPage", "UNKNOWN", "页面未知，触发步骤恢复")
                resolveAndRerouteTo(session, session.step, "WAITING_CONTACT_DETAIL: unknownPage")
                return
            }

        }

        val directClicked = elementLocator.clickVideoCallEntry(
            root,
            allowLearnedFallback = session.moreButtonClickedAt > 0L
        )
        logStep(session, "clickVideoCallEntry(direct)", directClicked)
        if (directClicked) {
            transitionTo(session, Step.WAITING_VIDEO_OPTIONS, "正在发起视频通话")
            return
        }

        val directOptionClicked = elementLocator.clickVideoCallOption(root)
        logStep(session, "clickVideoCallOption(direct)", directOptionClicked)
        if (directOptionClicked) {
            transitionTo(session, Step.WAITING_VIDEO_OPTIONS, "正在选择视频通话")
            return
        }

        val now = System.currentTimeMillis()
        if (session.moreButtonClickedAt > 0L) {
            val elapsed = now - session.moreButtonClickedAt
            val settleWindow = settleWindow(session, DelayProfile.SHEET, "contact_detail_menu_wait", minWindow = 420L)
            if (elapsed < settleWindow) {
                logStep(session, "waitMoreMenuSettle", "${elapsed}ms/<${settleWindow}ms")
                scheduleAdaptiveProcess(session, DelayProfile.SHEET, attemptKey = "contact_detail_menu_wait")
                return
            }
        }

        val moreClicked = elementLocator.clickMoreButton(root)
        logStep(session, "clickMoreButton", moreClicked)
        if (moreClicked) {
            session.moreButtonClickedAt = now
            scheduleAdaptiveProcess(session, DelayProfile.SHEET, attemptKey = "contact_detail_menu_click", actionSucceeded = true)
            return
        }
        if (!ensureAttemptBudget(session, "contact_detail", MAX_CONTACT_DETAIL_ATTEMPTS, "打开联系人失败", root)) {
            return
        }
        scheduleAdaptiveProcess(session, DelayProfile.STABLE, attemptKey = "contact_detail")

    }

    private fun handleVideoOptions(session: VideoCallSession, root: AccessibilityNodeInfo) {
        val currentClass = resolveCurrentWeChatClass(root)
        when (detectWeChatPage(root, currentClass)) {

            WeChatPage.SEARCH -> {
                logStep(session, "detectPage", "SEARCH", "意外回到搜索页，回首页重新查找")
                session.searchTextApplied = false
                session.launcherPrepared = false
                rerouteTo(session, Step.WAITING_HOME, "正在返回微信首页")
                return
            }
            WeChatPage.HOME -> {
                logStep(session, "detectPage", "HOME", "意外回到首页，触发步骤恢复")
                resolveAndRerouteTo(session, session.step, "WAITING_VIDEO_OPTIONS: launcherHome")
                return
            }
            WeChatPage.VIDEO_SHEET -> Unit

            else -> Unit
        }

        val elapsed = System.currentTimeMillis() - session.stepStartedAt
        val learnedFinalSelector = WeChatLearnedRulePolicy.selectorForWindowFallback(
            profile = activeTeachingProfile,
            action = WeChatTeachingAction.START_VIDEO_CALL,
            currentWindowClass = currentClass
        )
        val sheetClicked = elementLocator.clickVideoCallSheetOption(
            root,
            allowLearnedFallback = learnedFinalSelector != null
        )
        logStep(session, "clickVideoCallSheetOption", sheetClicked, "elapsed=${elapsed}ms")
        if (sheetClicked) {
            transitionTo(session, Step.VERIFYING_CALL_STARTED, "正在确认视频通话")
            return
        }
        val settleWindow = settleWindow(session, DelayProfile.SHEET, "video_sheet_wait", minWindow = 500L)
        if (elapsed < settleWindow) {
            logStep(session, "waitSheetSettle", "${elapsed}ms/<${settleWindow}ms")
            scheduleAdaptiveProcess(session, DelayProfile.SHEET, attemptKey = "video_sheet_wait")
            return
        }
        val clicked = elementLocator.clickVideoCallOption(root)
        logStep(session, "clickVideoCallOption(fallback)", clicked)
        if (clicked) {
            transitionTo(session, Step.VERIFYING_CALL_STARTED, "正在确认视频通话")
            return
        }
        if (!ensureAttemptBudget(session, "video_option", MAX_VIDEO_OPTION_ATTEMPTS, "发起视频通话失败", root)) {
            return
        }
        scheduleAdaptiveProcess(session, DelayProfile.TRANSITION, attemptKey = "video_option")
    }

    private fun handleVerifyingCallStarted(
        session: VideoCallSession,
        root: AccessibilityNodeInfo?,
        currentClass: String?
    ) {
        val assessment = WeChatCallStartVerifier.assess(
            snapshot = snapshotOf(root),
            className = currentClass
        )
        val decision = WeChatCallVerificationPolicy.decide(
            state = session.callVerificationState,
            assessment = assessment
        )
        session.callVerificationState = decision.nextState
        session.callVerificationPollCount++
        val logKey = "${assessment.status}:${assessment.reasons}:${decision.nextState.consecutiveConfirmations}"
        val shouldLog = assessment.status != WeChatCallStartStatus.PENDING ||
            session.lastCallVerificationLogKey != logKey ||
            session.callVerificationPollCount % 10 == 0
        if (shouldLog) {
            logStep(
                session,
                "verifyCallStarted",
                assessment.status,
                "class=$currentClass reasons=${assessment.reasons} confirmations=${decision.nextState.consecutiveConfirmations}"
            )
        }
        session.lastCallVerificationLogKey = logKey

        when (decision.action) {
            WeChatCallVerificationAction.COMPLETE -> finishVideoCallStarted(session)
            WeChatCallVerificationAction.FAIL -> failAndHide(
                message = assessment.userMessage ?: "微信未能发起视频通话",
                root = root
            )
            WeChatCallVerificationAction.WAIT -> {
                updateProgress(session, "正在确认视频通话")
                scheduleAdaptiveProcess(session, DelayProfile.STABLE)
            }
        }
    }

    private fun transitionTo(session: VideoCallSession, nextStep: Step, message: String) {
        val oldStep = session.step
        val duration = System.currentTimeMillis() - session.stepStartedAt
        session.stepDurations[oldStep.name.lowercase()] = duration
        recordStepSuccess(oldStep, duration)
        recordStepHistory(session, nextStep)
        session.step = nextStep
        session.stepStartedAt = System.currentTimeMillis()
        resetForStepEntry(session, nextStep)
        session.actionAttempts.clear()

        DebugLog.i(TAG) { "[微信自动] 状态流转: $oldStep -> $nextStep | 消息: $message" }

        LobsterClient.log("[微信自动] 流转: $oldStep -> $nextStep | 消息: $message")

        session.moreButtonClickedAt = 0L
        session.stateOverride = null
        session.stepFailCount.remove(nextStep)
        if (nextStep != Step.WAITING_HOME) {
            wechatWaitJob?.cancel()
            wechatWaitJob = null
        }
        updateProgress(session, message)
        armTimeout(nextStep, timeoutFor(nextStep), failureMessageFor(nextStep, session.contactName))
        scheduleAdaptiveProcess(session, DelayProfile.TRANSITION)
    }

    private fun scheduleWeChatCallEndCheck() {
        if (!CallReturnCoordinator.hasConfirmedSession(CallReturnOrigin.WECHAT_VIDEO)) return
        wechatCallEndJob?.cancel()
        wechatCallEndJob = serviceScope.launch {
            delay(1_200L)
            repeat(4) { attempt ->
                if (!CallReturnCoordinator.hasConfirmedSession(CallReturnOrigin.WECHAT_VIDEO)) {
                    return@launch
                }
                val root = rootInActiveWindow
                val packageName = root?.packageName?.toString()
                AccessibilityUtil.safeRecycle(root)
                val className = lastWeChatWindowClassForReturn
                val audioManager = getSystemService(AUDIO_SERVICE) as? AudioManager
                val audioMode = audioManager?.mode ?: AudioManager.MODE_NORMAL
                val ended = WeChatCallEndPolicy.shouldComplete(
                    packageName = packageName,
                    className = className,
                    videoActivityVisible = className == "com.tencent.mm.plugin.voip.ui.VideoActivity",
                    audioMode = audioMode
                )
                DebugLog.d(TAG) {
                    "[通话返回] 微信结束检查 attempt=$attempt class=$className audioMode=$audioMode ended=$ended"
                }
                if (ended) {
                    CallReturnCoordinator.complete(this@SelectToSpeakService, CallReturnOrigin.WECHAT_VIDEO)
                    return@launch
                }
                delay(500L)
            }
        }
    }



    private fun finishVideoCallStarted(session: VideoCallSession) {
        val stepElapsed = System.currentTimeMillis() - session.stepStartedAt
        session.stepDurations[session.step.name.lowercase()] = stepElapsed
        recordStepSuccess(session.step, stepElapsed)
        session.moreButtonClickedAt = 0L
        markCapabilitySucceeded(session, WeChatCapabilityId.SELECT_VIDEO)
        markCapabilitySucceeded(session, WeChatCapabilityId.CONFIRM_CALL_STARTED)
        session.lastCapabilityReason = "call_started_confirmed"
        
        val totalElapsed = System.currentTimeMillis() - session.startedAt
        DebugLog.banner(
            TAG,
            listOf(
                "[微信自动] 流程成功完成 ✅",
                "└─ 总耗时: ${totalElapsed}ms"
            )
        )
        LobsterClient.log("[微信自动] 流程成功 ✅")
        LobsterClient.log("[微信自动] 流程终点: 成功发起视频通话 | 耗时=${totalElapsed}ms")
        logStep(session, "COMPLETED", "视频通话已发起", "totalElapsed=${totalElapsed}ms")
        LobsterClient.report(
            this,
            "微信视频",
            LobsterReportStatus.SUCCESS,
            "视频通话已发起",
            LobsterReportDetails(
                traceId = session.requestId,
                steps = session.structuredSteps,
                sensitiveValues = listOf(session.contactName)
            )
        )
        reportTerminalMetrics(session, success = true)
        applyWeChatCallAudioStrategy()
        CallReturnCoordinator.confirm(CallReturnOrigin.WECHAT_VIDEO)
        floatingView?.updateMessage("视频通话已发起")
        notifyState(session, "视频通话已发起", success = true, terminal = true)
        currentSession = null
        activeTeachingProfile = null
        stepClock.cancelAll()
        wechatWaitJob?.cancel()
        serviceScope.launch {
            delay(1200)
            floatingView?.hide()
        }
    }

    private fun reportTerminalMetrics(session: VideoCallSession, success: Boolean) {
        val totalName = if (success) {
            LauncherTraceNames.WECHAT_VIDEO_TOTAL
        } else {
            LauncherTraceNames.WECHAT_VIDEO_FAILURE_TOTAL
        }
        val metrics = mutableListOf(totalName to (System.currentTimeMillis() - session.startedAt))
        session.stepDurations.forEach { (step, duration) ->
            metrics += "oldlauncher.wechat.video.step.$step" to duration
        }
        LobsterClient.reportMetrics(this, metrics, traceId = session.requestId)
    }

    private fun applyWeChatCallAudioStrategy() {
        val result = CallAudioStrategy.prepareVoipCall(this)
        if (result.keptPrivateOutput) {
            return
        }
        serviceScope.launch {
            clickWeChatSpeakerButtonIfNeeded(delayMillis = 400L)
            clickWeChatSpeakerButtonIfNeeded(delayMillis = 1200L)
        }
    }

    private suspend fun clickWeChatSpeakerButtonIfNeeded(delayMillis: Long) {
        delay(delayMillis)
        val root = obtainSpeakerTargetRoot() ?: return
        try {
            if (elementLocator.hasContainingText(root, "扩声器已开") ||
                elementLocator.hasContainingText(root, "免提已开")
            ) {
                return
            }
            val toggleNode = findSpeakerToggleNode(root) ?: return
            val clicked = AccessibilityUtil.performClick(this, toggleNode)
            DebugLog.d(TAG) { "clickWeChatSpeakerButtonIfNeeded: click=$clicked" }
            AccessibilityUtil.safeRecycle(toggleNode)
        } finally {
            AccessibilityUtil.safeRecycle(root)
        }
    }

    @Suppress("DEPRECATION")
    private fun obtainSpeakerTargetRoot(): AccessibilityNodeInfo? {
        rootInActiveWindow?.let { return AccessibilityNodeInfo.obtain(it) }
        rootProvider.peekCachedRoot()?.let { return AccessibilityNodeInfo.obtain(it) }
        return null
    }

    private fun findSpeakerToggleNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val texts = listOf("扬声器已关", "免提已关", "扬声器已关闭", "免提已关闭")
        texts.forEach { text ->
            val node = AccessibilityUtil.findBestTextNode(
                root,
                text,
                exactMatch = false,
                preferBottom = true,
                excludeEditable = false
            )
            if (node != null) {
                return node
            }
        }
        return null
    }


    private fun timeoutFor(step: Step): Long {
        return when (step) {
            Step.WAITING_HOME -> timeoutManager.getTimeout("launch")
            Step.WAITING_LAUNCHER_UI -> timeoutManager.getTimeout("home")
            Step.WAITING_SEARCH_FALLBACK,
            Step.WAITING_CONTACT_RESULT -> timeoutManager.getTimeout("search")
            Step.WAITING_CONTACT_DETAIL,
            Step.WAITING_VIDEO_OPTIONS,
            Step.VERIFYING_CALL_STARTED -> timeoutManager.getTimeout("chat")
        }
    }

    private fun failureMessageFor(step: Step, contactName: String): String {
        return when (step) {
            Step.WAITING_HOME -> "微信启动或返回首页超时"
            Step.WAITING_LAUNCHER_UI -> {
                if (rootProvider.lastObservedClassName == WeChatClassNames.LAUNCHER_UI) {
                    "微信首页未暴露可操作控件"
                } else {
                    "查找联系人入口失败"
                }
            }
            Step.WAITING_SEARCH_FALLBACK -> "打开搜索或输入搜索名称失败"
            Step.WAITING_CONTACT_RESULT -> "未找到联系人: $contactName"
            Step.WAITING_CONTACT_DETAIL -> "打开联系人失败"
            Step.WAITING_VIDEO_OPTIONS -> "发起视频通话失败"
            Step.VERIFYING_CALL_STARTED -> "视频通话状态确认超时，请查看微信"
        }
    }

    private fun armTimeout(step: Step, timeoutMillis: Long, failureMessage: String) {
        stepClock.armStepTimeout(timeoutMillis, failureMessage) {
            currentSession?.step == step
        }
    }

    private fun armTotalTimeout(timeoutMillis: Long, failureMessage: String) {
        stepClock.armTotalTimeout(timeoutMillis) {
            val session = currentSession
            if (session != null) {
                "$failureMessage（当前步骤：${session.step}，联系人：${session.contactName}）"
            } else {
                failureMessage
            }
        }
    }

    private fun cancelSession(
        notifyFailure: Boolean,
        message: String = "操作已取消",
        restoreLauncher: Boolean = notifyFailure
    ) {
        val session = currentSession
        stepClock.cancelAll()
        wechatWaitJob?.cancel()
        wechatWaitJob = null


        lastMissingRootLogAt = 0L
        rootProvider.reset()
        currentSession = null
        activeTeachingProfile = null
        floatingView?.hide()
        if (notifyFailure && session != null) {
            session.stateOverride = AutomationState.FAILED
            session.stepDurations["${session.step.name.lowercase()}.cancelled"] =
                System.currentTimeMillis() - session.stepStartedAt
            reportTerminalMetrics(session, success = false)
            notifyState(session, message, success = false, terminal = true, reported = false)
        }
        if (restoreLauncher && session != null) {
            bringLauncherBackToForeground()
        }
    }


    private fun failAndHide(
        message: String,
        root: AccessibilityNodeInfo? = getWeChatRoot(),
        restoreLauncher: Boolean = true
    ) {
        val session = currentSession
        if (session != null) {
            session.stepDurations["${session.step.name.lowercase()}.failed"] =
                System.currentTimeMillis() - session.stepStartedAt
            logStep(session, "FAILED", message)
        }
        val sessionSnapshot = session?.failureSnapshot()
        val rootSnapshot = snapshotOf(root)
        val suffix = if (message.contains("超时")) "TIMEOUT" else "FAILED"
        val errorCode = session?.let { "WECHAT_${it.step.name}_$suffix" }
        val failureFingerprint = activeTeachingProfile?.fingerprint
            ?: WeChatTeachingFingerprintFactory.capture(this)
        val failureSample = if (sessionSnapshot != null && errorCode != null) {
            WeChatFailureSampleFactory.create(
                errorCode = errorCode,
                session = sessionSnapshot,
                route = session.behaviorState.selectedRoute?.name,
                root = rootSnapshot,
                windowClass = rootProvider.lastObservedClassName,
                targetVersionName = failureFingerprint?.weChatVersionName,
                targetVersionCode = failureFingerprint?.weChatVersionCode,
            )
        } else {
            null
        }
        val diagnostics = WeChatFailureDiagnostics.build(
            message = message,
            session = sessionSnapshot,
            root = root,
            service = this
        )
        WeChatFailureDiagnostics.logErrorLong(TAG, diagnostics)
        serviceScope.launch(Dispatchers.IO) {
            WeChatFailureDiagnostics.saveReplay(
                context = this@SelectToSpeakService,
                message = message,
                session = sessionSnapshot,
                root = rootSnapshot
            )
            if (session != null && failureSample != null) {
                LobsterFailureSampleStore.save(
                    context = this@SelectToSpeakService,
                    sample = failureSample,
                    traceId = session.requestId,
                )
            }
        }
        if (failureSample != null) {
            LobsterClient.log(
                "[微信自动] 失败样本已生成 fingerprint=${failureSample.fingerprint} " +
                    "error=${failureSample.failureCode}"
            )
        }
        if (session != null && failureSample != null) {
            reportTerminalMetrics(session, success = false)
            LobsterClient.reportUsage(
                this,
                WeChatFailureReportFactory.create(
                    sample = failureSample,
                    traceId = session.requestId,
                    contactName = session.contactName,
                    steps = session.structuredSteps,
                )
            )
        }
        cancelSession(false)
        if (session != null) {
            notifyState(session, message, success = false, terminal = true, reported = true)
            if (restoreLauncher) {
                bringLauncherBackToForeground()
            }
        }
    }

    private fun bringLauncherBackToForeground() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            )
        }
        val started = try {
            startActivity(intent)
            true
        } catch (e: Exception) {
            DebugLog.w(TAG, "bringLauncherBackToForeground failed: ${e.message}")
            false
        }
        if (!started) {
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }
    }


    private fun Step.stepNumber(): Int = when (this) {
        Step.WAITING_HOME          -> 1
        Step.WAITING_LAUNCHER_UI   -> 2
        Step.WAITING_SEARCH_FALLBACK -> 3
        Step.WAITING_CONTACT_RESULT  -> 4
        Step.WAITING_CONTACT_DETAIL  -> 5
        Step.WAITING_VIDEO_OPTIONS   -> 6
        Step.VERIFYING_CALL_STARTED  -> 7
    }

    private fun Step.stepName(): String = when (this) {
        Step.WAITING_HOME            -> "等待微信首页"
        Step.WAITING_LAUNCHER_UI     -> "消息列表找联系人"
        Step.WAITING_SEARCH_FALLBACK -> "搜索联系人"
        Step.WAITING_CONTACT_RESULT  -> "选择搜索结果"
        Step.WAITING_CONTACT_DETAIL  -> "发起视频入口"
        Step.WAITING_VIDEO_OPTIONS   -> "选择视频通话"
        Step.VERIFYING_CALL_STARTED  -> "确认呼叫状态"
    }

    private fun VideoCallSession.stepLabel(): String {
        return when (stateOverride) {
            AutomationState.LAUNCHING_WECHAT -> "第0步/共${TOTAL_STEPS}步  启动微信"
            AutomationState.RECOVERING       -> "恢复中  ${step.stepName()}"
            else -> "第${step.stepNumber()}步/共${TOTAL_STEPS}步  ${step.stepName()}"
        }
    }

    private fun logStep(
        session: VideoCallSession,
        action: String,
        result: Any?,
        extra: String? = null
    ) {
        val stepNo = when (session.stateOverride) {
            AutomationState.LAUNCHING_WECHAT -> 0
            else -> session.step.stepNumber()
        }
        val stepName = when (session.stateOverride) {
            AutomationState.LAUNCHING_WECHAT -> "启动微信"
            AutomationState.RECOVERING       -> "恢复[${session.step.stepName()}]"
            else -> session.step.stepName()
        }
        val sb = StringBuilder()
            .append("[步骤$stepNo/$TOTAL_STEPS][").append(stepName).append("] ")
            .append("action=").append(action)
            .append(" result=").append(result)
        if (!extra.isNullOrBlank()) sb.append(" | ").append(extra)
        DebugLog.d(TAG) { sb.toString() }
        session.structuredSteps += LobsterTraceStep(
            stepCode = session.step.name.lowercase(),
            stepName = stepName,
            action = action,
            outcome = when (result) {
                false -> LobsterStepOutcome.ERROR
                true -> LobsterStepOutcome.SUCCESS
                else -> if (action == "FAILED") LobsterStepOutcome.ERROR else LobsterStepOutcome.REPORTED
            },
            detail = listOfNotNull(result?.toString(), extra).joinToString(" · ").takeIf { it.isNotBlank() },
            durationMs = (System.currentTimeMillis() - session.stepStartedAt).coerceAtLeast(0),
            occurredAt = currentTraceTimestamp()
        )
    }

    private fun recordStepSuccess(step: Step, duration: Long) {
        when (step) {
            Step.WAITING_HOME -> timeoutManager.recordSuccess("launch", duration)
            Step.WAITING_LAUNCHER_UI -> timeoutManager.recordSuccess("home", duration)
            Step.WAITING_SEARCH_FALLBACK,
            Step.WAITING_CONTACT_RESULT -> timeoutManager.recordSuccess("search", duration)
            Step.WAITING_CONTACT_DETAIL,
            Step.WAITING_VIDEO_OPTIONS,
            Step.VERIFYING_CALL_STARTED -> timeoutManager.recordSuccess("chat", duration)
        }
    }

    private fun launchWeChat(): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(WeChatPackage.NAME)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (intent != null) {
            startActivity(intent)
            true
        } else {
            false
        }
    }

    private fun tagWeChatVersionOnce() {
        if (wechatVersionTagged) return
        wechatVersionTagged = true
        runCatching {
            val info = packageManager.getPackageInfo(WeChatPackage.NAME, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            LobsterClient.log("[微信自动] 版本: name=${info.versionName ?: "unknown"}, code=$versionCode, brand=${Build.BRAND}, model=${Build.MODEL}")
        }
    }

    private fun clickContactResult(root: AccessibilityNodeInfo?, session: VideoCallSession): Boolean {
        val target = elementLocator.findContactResultTarget(root, session.contactName) ?: return false
        val success = AccessibilityUtil.performClick(this, target.node)
        DebugLog.d(TAG) {
            "clickContactResult: displayName=${target.displayName} node=${AccessibilityUtil.summarizeNode(target.node)}, click=$success"
        }
        if (success) {
            session.resolvedContactTitle = target.displayName
        }
        AccessibilityUtil.safeRecycle(target.node)
        return success
    }


    private fun VideoCallSession.failureSnapshot(): WeChatFailureSnapshot {
        return WeChatFailureSnapshot(
            step = step.toString(),
            contactName = contactName,
            startedAt = startedAt,
            stepStartedAt = stepStartedAt,
            actionAttempts = actionAttempts.toMap(),
            stepHistory = stepHistory.map { it.name },
            stepDurations = stepDurations.toMap(),
            lastDetectedPage = lastDetectedPage?.name,
            lastProgressAt = lastProgressAt,
            lastAnnouncedMessage = lastAnnouncedMessage,
            lastSemanticPage = lastSemanticPage?.page?.name,
            taskStep = step.name,
            taskReason = lastCapabilityReason,
            capabilityId = lastCapabilityId?.name,
            capabilityFailure = lastCapabilityFailure?.name
        )
    }


    private data class VideoCallSession(
        val requestId: String,
        val contactName: String,
        var step: Step,
        var stepStartedAt: Long,
        val startedAt: Long,
        var searchTextApplied: Boolean = false,
        var launcherPrepared: Boolean = false,
        var resolvedContactTitle: String? = null,
        var moreButtonClickedAt: Long = 0L,
        var lastAnnouncedMessage: String? = null,
        var lastDetectedPage: WeChatPage? = null,
        var stateOverride: AutomationState? = null,
        val actionAttempts: MutableMap<String, Int> = mutableMapOf(),
        val stepHistory: ArrayDeque<Step> = ArrayDeque(),
        val stepFailCount: MutableMap<Step, Int> = mutableMapOf(),
        val stepDurations: MutableMap<String, Long> = mutableMapOf(),
        val structuredSteps: MutableList<LobsterTraceStep> = mutableListOf(),
        var searchInputSubmittedAt: Long = 0L,
        var missingRootSince: Long = 0L,
        var foregroundRecoveryAttempts: Int = 0,
        var lastProgressAt: Long = System.currentTimeMillis(),
        var dismissingUntil: Long = 0L,
        var dismissAttempts: Int = 0,
        var lastSemanticPage: WeChatSemanticPageResult? = null,
        var behaviorState: WeChatBehaviorTreeState = WeChatBehaviorTreeState(),
        var lastCapabilityId: WeChatCapabilityId? = null,
        var lastCapabilityReason: String? = null,
        var lastCapabilityFailure: WeChatCapabilityFailure? = null,
        var lastCapabilityLogKey: String? = null,
        var conversationPageRecoveries: Int = 0,
        var callVerificationState: WeChatCallVerificationState = WeChatCallVerificationState(),
        var callVerificationPollCount: Int = 0,
        var lastCallVerificationLogKey: String? = null
    )

    private data class TeachingSession(
        val fingerprint: WeChatTeachingFingerprint,
        var sessionId: String = UUID.randomUUID().toString(),
        var state: TeachingState = TeachingState.PREPARED,
        var startedAt: Long = 0L,
        var initialState: WeChatTeachingStateFingerprint? = null,
        var currentWindowClass: String? = null,
        val observations: MutableList<WeChatTeachingObservation> = mutableListOf(),
        val visibleCaptureTracker: WeChatTeachingVisibleCaptureTracker =
            WeChatTeachingVisibleCaptureTracker(),
        var progress: WeChatTeachingProgress = WeChatTeachingProgress.WECHAT_OPENED,
        var weChatEntered: Boolean = false,
        var selectedCallLabel: com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingSemanticLabel? = null,
        var lastCallMode: WeChatTeachingCallMode = WeChatTeachingCallMode.UNKNOWN,
        var videoCallConfirmed: Boolean = false,
        var uploadAnonymousData: Boolean = true,
        var captureReported: Boolean = false
    )

    private enum class TeachingState {
        PREPARED,
        RECORDING,
        COMPLETE,
        INCOMPLETE
    }

    private fun currentTraceTimestamp(): String = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        Locale.US
    ).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())


    private enum class WeChatPage {
        HOME,
        SEARCH,
        CHAT,
        CONTACT_DETAIL,
        VIDEO_SHEET,
        UNKNOWN
    }

    private enum class Step {
        WAITING_HOME,
        WAITING_LAUNCHER_UI,
        WAITING_SEARCH_FALLBACK,
        WAITING_CONTACT_RESULT,
        WAITING_CONTACT_DETAIL,
        WAITING_VIDEO_OPTIONS,
        VERIFYING_CALL_STARTED
    }
}
