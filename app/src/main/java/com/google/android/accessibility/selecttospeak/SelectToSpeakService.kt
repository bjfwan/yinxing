package com.google.android.accessibility.selecttospeak

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.yinxing.launcher.automation.wechat.WeChatClassNames
import com.yinxing.launcher.automation.wechat.WeChatPackage
import com.yinxing.launcher.automation.wechat.WeChatViewIds
import com.yinxing.launcher.automation.wechat.manager.TimeoutManager
import com.yinxing.launcher.automation.wechat.model.AutomationState
import com.yinxing.launcher.automation.wechat.util.AccessibilityUtil
import com.yinxing.launcher.common.lobster.LobsterClient
import com.yinxing.launcher.common.lobster.LobsterReportStatus
import com.yinxing.launcher.common.perf.LauncherTraceNames
import com.yinxing.launcher.common.util.CallAudioStrategy
import com.yinxing.launcher.common.util.DebugLog
import com.yinxing.launcher.common.ui.FloatingStatusView
import com.yinxing.launcher.data.home.LauncherPreferences
import com.yinxing.launcher.feature.home.MainActivity


import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class SelectToSpeakService : AccessibilityService(), WeChatRequestHost {

    companion object {
        @Volatile
        private var instance: SelectToSpeakService? = null

        fun getInstance(): SelectToSpeakService? = instance

        const val ACTION_START_VIDEO_CALL = "com.yinxing.launcher.START_VIDEO_CALL"
        const val EXTRA_CONTACT_NAME = "contact_name"

        /**
         * 壳服务的完整组件名（package/class 形式），微信会检测该服务是否启用
         * 以决定是否开放节点树。这是该服务存在的唯一原因，因此常量归属于此。
         */
        const val SHELL_SERVICE_COMPONENT =
            "com.yinxing.launcher/com.google.android.accessibility.selecttospeak.SelectToSpeakService"

        private const val TAG = "WeChatAutoService"

        private const val TOTAL_STEPS = 7

        private const val MAX_HOME_BACK_ATTEMPTS = 6
        private const val MAX_UNKNOWN_HOME_OBSERVE_ATTEMPTS = 2
        private const val MAX_SEARCH_ENTRY_ATTEMPTS = 3
        private const val MAX_SEARCH_OPEN_ATTEMPTS = 3
        private const val MAX_SEARCH_INPUT_ATTEMPTS = 3

        private const val MAX_CONTACT_DETAIL_ATTEMPTS = 4
        private const val MAX_VIDEO_OPTION_ATTEMPTS = 3
        private const val MAX_STEP_RECOVERY_ATTEMPTS = 5
        private const val HOME_ACTION_SETTLE_DELAY_MS = 500L


        fun requestVideoCall(contactName: String, listener: (VideoCallProgress) -> Unit): String =
            WeChatRequestQueue.enqueue(contactName, listener, host = instance)

        fun clearRequestListener(requestId: String) =
            WeChatRequestQueue.clearListener(requestId)

        internal fun resetForTesting() = WeChatRequestQueue.resetForTesting()

        private fun deliverProgress(requestId: String, progress: VideoCallProgress) =
            WeChatRequestQueue.deliverProgress(requestId, progress)
    }

    data class VideoCallProgress(
        val message: String,
        val success: Boolean,
        val terminal: Boolean,
        val step: AutomationState = AutomationState.IDLE,
        val page: String? = null
    )


    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val stepClock = VideoCallStepClock(
        scope = serviceScope,
        onProcessTick = {
            if (currentSession != null) processCurrentWindow()
        },
        onTimeoutFailure = { message ->
            failAndHide(message, getWeChatRoot())
        },
        sessionStillActive = { currentSession != null }
    )
    private var wechatWaitJob: Job? = null
    private lateinit var timeoutManager: TimeoutManager
    private var floatingView: FloatingStatusView? = null
    private var currentSession: VideoCallSession? = null
    private lateinit var kioskGuard: KioskLauncherGuard


    private var lastMissingRootLogAt = 0L
    private val rootProvider = WeChatRootProvider(this)
    private val elementLocator = WeChatElementLocator(this)

    private var wechatVersionTagged = false



    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        timeoutManager = TimeoutManager.getInstance(this)
        floatingView = FloatingStatusView(this)
        kioskGuard = KioskLauncherGuard(
            service = this,
            scope = serviceScope,
            launcherActivityClass = MainActivity::class.java,
            activeSession = ::hasActiveSession
        )
        kioskGuard.init()
        consumePendingRequest()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString()
        val className = event?.className?.toString()

        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != null) {
            DebugLog.d(TAG) { "[EVENT] WindowStateChanged: pkg=$pkg, class=$className" }
            if (kioskGuard.onWindowStateChanged(pkg, className)) {
                return
            }
        }

        if (pkg != WeChatPackage.NAME) {
            return
        }
        rootProvider.rememberClassName(className)

        rootProvider.updateFromEvent(event.source)

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
        cancelSession(false)
    }

    override fun onDestroy() {
        instance = null
        if (::kioskGuard.isInitialized) kioskGuard.shutdown()
        cancelSession(false)
        floatingView?.hide()
        floatingView = null
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

    private fun notifyState(
        session: VideoCallSession,
        state: String,
        success: Boolean,
        terminal: Boolean,
        page: WeChatPage? = session.lastDetectedPage
    ) {
        deliverProgress(
            session.requestId,
            VideoCallProgress(
                message = state,
                success = success,
                terminal = terminal,
                step = session.toProgressState(success = success, terminal = terminal),
                page = page?.name
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
        AutomationState.RECOVERING -> "正在恢复"
        AutomationState.COMPLETED -> "已完成"
        AutomationState.FAILED -> "已失败"
    }

    override fun hasActiveSession(): Boolean {
        return currentSession != null
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
        val page = detectWeChatPageLegacy(root, currentClass)
        session?.lastDetectedPage = page
        return page
    }

    private fun detectWeChatPageLegacy(root: AccessibilityNodeInfo, currentClass: String?): WeChatPage {
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
        return containsContactName(root, contactNames)
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

    private fun containsContactName(root: AccessibilityNodeInfo?, contactNames: Collection<String>): Boolean {
        val normalizedNames = contactNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (normalizedNames.isEmpty()) {
            return false
        }
        val snapshot = snapshotOf(root)
        if (snapshot != null && WeChatUiSnapshotAnalyzer.containsAnyContactName(snapshot, normalizedNames)) {
            return true
        }
        normalizedNames.forEach { contactName ->
            val titleNode = elementLocator.findNodeByExactText(
                root,
                contactName,
                WeChatViewIds.CONTACT_TITLE_SECONDARY,
                WeChatViewIds.CONTACT_TITLE_PRIMARY
            )
            if (titleNode != null) {
                AccessibilityUtil.safeRecycle(titleNode)
                return true
            }
            val exactNode = AccessibilityUtil.findBestTextNode(
                root,
                contactName,
                exactMatch = true,
                preferBottom = false,
                excludeEditable = false
            )
            if (exactNode != null) {
                AccessibilityUtil.safeRecycle(exactNode)
                return true
            }
        }
        return false
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
                session.launcherPrepared = false
                session.resolvedContactTitle = null
            }
            Step.WAITING_LAUNCHER_UI,
            Step.WAITING_SEARCH_FALLBACK -> {
                session.searchTextApplied = false
                session.launcherPrepared = false
                session.resolvedContactTitle = null
            }
            Step.WAITING_CONTACT_RESULT -> {
                session.moreButtonClickedAt = 0L
                session.resolvedContactTitle = null
            }
            Step.WAITING_CONTACT_DETAIL,
            Step.WAITING_VIDEO_OPTIONS -> Unit
        }
    }

    private fun resetForStepEntry(session: VideoCallSession, target: Step) {
        when (target) {
            Step.WAITING_HOME,
            Step.WAITING_LAUNCHER_UI,
            Step.WAITING_SEARCH_FALLBACK -> {
                session.searchTextApplied = false
                session.resolvedContactTitle = null
            }
            Step.WAITING_CONTACT_RESULT -> {
                session.resolvedContactTitle = null
            }
            Step.WAITING_CONTACT_DETAIL,
            Step.WAITING_VIDEO_OPTIONS -> Unit
        }
    }

    private fun resolveRecoveryStep(session: VideoCallSession, failedStep: Step, failCount: Int): Step {
        return when (failedStep) {
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
            val fallbackPkg = rootInActiveWindow?.packageName?.toString()
            val now = System.currentTimeMillis()
            if (now - lastMissingRootLogAt >= 2000L) {
                lastMissingRootLogAt = now
                WeChatFailureDiagnostics.logDebugLong(
                    TAG,
                    "processCurrentWindow: 微信窗口未找到，当前前台包名=$fallbackPkg, step=${session.step}, contact=${session.contactName}\nwindows=${WeChatFailureDiagnostics.describeWindows(this)}"
                )
            }
            scheduleAdaptiveProcess(session, DelayProfile.WAIT_LOOP)
            return
        }

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

        when (session.step) {
            Step.WAITING_HOME -> handleWaitingHome(session, root, currentClass)
            Step.WAITING_LAUNCHER_UI -> handleLauncherUI(session, root)
            Step.WAITING_SEARCH_FALLBACK -> handleSearchFallback(session, root)
            Step.WAITING_CONTACT_RESULT -> handleContactResult(session, root)
            Step.WAITING_CONTACT_DETAIL -> handleContactDetail(session, root)
            Step.WAITING_VIDEO_OPTIONS -> handleVideoOptions(session, root)
        }
    }

    private fun snapshotOf(root: AccessibilityNodeInfo?): WeChatUiSnapshot? {
        return WeChatUiSnapshot.fromNode(root)
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
                logStep(session, "detectPage", "SEARCH", "搜索页意外出现，回首页")
                recoverToHome(
                    session = session,
                    root = root,
                    currentClass = currentClass,
                    reason = "WAITING_HOME: 当前在搜索页"
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

        if (!session.launcherPrepared) {
            session.launcherPrepared = true
            val tabClicked = elementLocator.clickMessageTab(root)
            logStep(session, "clickMessageTab", tabClicked)
            scheduleAdaptiveProcess(
                session,
                if (tabClicked) DelayProfile.TRANSITION else DelayProfile.STABLE
            )
            return
        }

        val contactNames = sessionContactNames(session)
        val contactNode = elementLocator.findContactInMessageList(root, contactNames)
        if (contactNode != null) {
            val success = AccessibilityUtil.performClick(this, contactNode)
            logStep(session, "clickContactInList", success, "contacts=$contactNames node=${AccessibilityUtil.summarizeNode(contactNode)}")
            AccessibilityUtil.safeRecycle(contactNode)
            if (success) {
                transitionTo(session, Step.WAITING_CONTACT_DETAIL, "正在打开聊天")
                return
            }
        } else {
            logStep(session, "findContactInList", false, "消息列表未找到 contacts=$contactNames，转搜索路径")
        }

        updateProgress(session, "消息列表未找到，正在打开搜索")
        val searchClicked = elementLocator.clickTopSearchBar(root)
        logStep(session, "clickTopSearchBar", searchClicked)
        if (searchClicked) {
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

        if (session.searchTextApplied && !elementLocator.verifySearchInputFilled(root, session.contactName)) {
            session.searchTextApplied = false
        }

        if (!session.searchTextApplied) {
            val filled = elementLocator.fillSearchInput(root, session.contactName)
            logStep(session, "fillSearchInput", filled, "contact=${session.contactName}")
            if (!filled) {
                if (!ensureAttemptBudget(session, "search_input", MAX_SEARCH_INPUT_ATTEMPTS, "输入搜索名称失败", root)) {
                    return
                }
                scheduleAdaptiveProcess(session, DelayProfile.STABLE, attemptKey = "search_input")
                return

            }
            session.searchTextApplied = true
            transitionTo(session, Step.WAITING_CONTACT_RESULT, "正在查找联系人")
            return
        }

        transitionTo(session, Step.WAITING_CONTACT_RESULT, "正在查找联系人")
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

        val directClicked = elementLocator.clickVideoCallEntry(root)
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

            else -> Unit
        }

        val elapsed = System.currentTimeMillis() - session.stepStartedAt
        val sheetClicked = elementLocator.clickVideoCallSheetOption(root)
        logStep(session, "clickVideoCallSheetOption", sheetClicked, "elapsed=${elapsed}ms")
        if (sheetClicked) {
            finishVideoCallStarted(session)
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
            finishVideoCallStarted(session)
            return
        }
        if (!ensureAttemptBudget(session, "video_option", MAX_VIDEO_OPTION_ATTEMPTS, "发起视频通话失败", root)) {
            return
        }
        scheduleAdaptiveProcess(session, DelayProfile.TRANSITION, attemptKey = "video_option")
    }

    private fun transitionTo(session: VideoCallSession, nextStep: Step, message: String) {
        val oldStep = session.step
        val duration = System.currentTimeMillis() - session.stepStartedAt
        recordStepSuccess(oldStep, duration)
        recordStepHistory(session, nextStep)
        session.step = nextStep
        session.stepStartedAt = System.currentTimeMillis()
        resetForStepEntry(session, nextStep)

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



    private fun finishVideoCallStarted(session: VideoCallSession) {
        recordStepSuccess(session.step, System.currentTimeMillis() - session.stepStartedAt)
        session.moreButtonClickedAt = 0L
        
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
        LobsterClient.report(this, "微信视频", LobsterReportStatus.SUCCESS, "视频通话已发起")
        LobsterClient.reportMetrics(this, listOf(LauncherTraceNames.WECHAT_VIDEO_TOTAL to totalElapsed))

        logStep(session, "COMPLETED", "视频通话已发起", "totalElapsed=${totalElapsed}ms")
        applyWeChatCallAudioStrategy()
        floatingView?.updateMessage("视频通话已发起")
        notifyState(session, "视频通话已发起", success = true, terminal = true)
        currentSession = null
        stepClock.cancelAll()
        wechatWaitJob?.cancel()
        serviceScope.launch {
            delay(1200)
            floatingView?.hide()
        }
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
            Step.WAITING_VIDEO_OPTIONS -> timeoutManager.getTimeout("chat")
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

    private fun cancelSession(notifyFailure: Boolean) {
        val session = currentSession
        stepClock.cancelAll()
        wechatWaitJob?.cancel()
        wechatWaitJob = null


        lastMissingRootLogAt = 0L
        rootProvider.reset()
        currentSession = null
        floatingView?.hide()
        if (notifyFailure && session != null) {
            session.stateOverride = AutomationState.FAILED
            notifyState(session, "操作已取消", success = false, terminal = true)
        }
    }


    private fun failAndHide(message: String, root: AccessibilityNodeInfo? = getWeChatRoot()) {
        val session = currentSession
        if (session != null) {
            logStep(session, "FAILED", message)
        }
        val diagnostics = WeChatFailureDiagnostics.build(
            message = message,
            session = session?.failureSnapshot(),
            root = root,
            service = this
        )
        WeChatFailureDiagnostics.logErrorLong(TAG, diagnostics)
        LobsterClient.log("[微信自动] 失败诊断:\n$diagnostics")
        cancelSession(false)
        if (session != null) {
            notifyState(session, message, success = false, terminal = true)
        }
    }


    private fun Step.stepNumber(): Int = when (this) {
        Step.WAITING_HOME          -> 1
        Step.WAITING_LAUNCHER_UI   -> 2
        Step.WAITING_SEARCH_FALLBACK -> 3
        Step.WAITING_CONTACT_RESULT  -> 4
        Step.WAITING_CONTACT_DETAIL  -> 5
        Step.WAITING_VIDEO_OPTIONS   -> 6
    }

    private fun Step.stepName(): String = when (this) {
        Step.WAITING_HOME            -> "等待微信首页"
        Step.WAITING_LAUNCHER_UI     -> "消息列表找联系人"
        Step.WAITING_SEARCH_FALLBACK -> "搜索联系人"
        Step.WAITING_CONTACT_RESULT  -> "选择搜索结果"
        Step.WAITING_CONTACT_DETAIL  -> "发起视频入口"
        Step.WAITING_VIDEO_OPTIONS   -> "选择视频通话"
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
    }

    private fun recordStepSuccess(step: Step, duration: Long) {
        when (step) {
            Step.WAITING_HOME -> timeoutManager.recordSuccess("launch", duration)
            Step.WAITING_LAUNCHER_UI -> timeoutManager.recordSuccess("home", duration)
            Step.WAITING_SEARCH_FALLBACK,
            Step.WAITING_CONTACT_RESULT -> timeoutManager.recordSuccess("search", duration)
            Step.WAITING_CONTACT_DETAIL,
            Step.WAITING_VIDEO_OPTIONS -> timeoutManager.recordSuccess("chat", duration)
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
            lastAnnouncedMessage = lastAnnouncedMessage
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
        // 弹窗处理：时间戳冷却（自动过期，不依赖手动重置）
        var dismissingUntil: Long = 0L,
        // 弹窗处理：本次 Step 内累计处理次数上限
        var dismissAttempts: Int = 0
    )


    private enum class WeChatPage {
        HOME,
        SEARCH,
        CHAT,
        CONTACT_DETAIL,
        UNKNOWN
    }

    private enum class Step {
        WAITING_HOME,
        WAITING_LAUNCHER_UI,
        WAITING_SEARCH_FALLBACK,
        WAITING_CONTACT_RESULT,
        WAITING_CONTACT_DETAIL,
        WAITING_VIDEO_OPTIONS
    }
}
