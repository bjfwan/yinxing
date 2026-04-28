package com.google.android.accessibility.selecttospeak

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.yinxing.launcher.automation.wechat.manager.TimeoutManager
import com.yinxing.launcher.automation.wechat.model.AutomationState
import com.yinxing.launcher.automation.wechat.util.AccessibilityUtil
import com.yinxing.launcher.common.util.CallAudioStrategy
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
import java.util.concurrent.atomic.AtomicLong


class SelectToSpeakService : AccessibilityService() {

    companion object {
        @Volatile
        private var instance: SelectToSpeakService? = null

        fun getInstance(): SelectToSpeakService? = instance

        const val ACTION_START_VIDEO_CALL = "com.yinxing.launcher.START_VIDEO_CALL"
        const val EXTRA_CONTACT_NAME = "contact_name"

        private const val TAG = "WeChatAutoService"
        private const val WECHAT_PACKAGE = "com.tencent.mm"

        private const val TOTAL_STEPS = 7

        private const val MAX_HOME_BACK_ATTEMPTS = 6
        private const val MAX_UNKNOWN_HOME_OBSERVE_ATTEMPTS = 2
        private const val MAX_SEARCH_ENTRY_ATTEMPTS = 3
        private const val MAX_SEARCH_OPEN_ATTEMPTS = 3
        private const val MAX_SEARCH_INPUT_ATTEMPTS = 3

        private const val MAX_CONTACT_DETAIL_ATTEMPTS = 4
        private const val MAX_VIDEO_OPTION_ATTEMPTS = 3
        private const val MAX_STEP_RECOVERY_ATTEMPTS = 5
        private const val MAX_AI_ASSIST_REQUESTS = 3
        private const val AI_GUARD_STABLE_DELAY_MS = 250L
        private const val AI_GUARD_RESOLVE_DELAY_MS = 650L
        private const val AI_GUARD_UNKNOWN_STEP_AGE_MS = 450L
        private const val AI_GUARD_RETRY_STEP_AGE_MS = 700L
        private const val AI_GUARD_VIDEO_STEP_AGE_MS = 650L
        private const val AI_GUARD_CONFIDENCE = 0.75f
        private const val AI_RECOVERY_CONFIDENCE = 0.7f
        private const val HOME_ACTION_SETTLE_DELAY_MS = 500L
        private const val LAUNCHER_STATE_SETTLE_DELAY_MS = 450L
        private const val HIBOARD_OVERVIEW_SUPPRESS_WINDOW_MS = 1500L

        private const val CLASS_LAUNCHER_UI = "com.tencent.mm.ui.LauncherUI"
        private const val CLASS_CHATTING_UI = "com.tencent.mm.ui.chatting.ChattingUI"
        private const val CLASS_CONTACT_INFO = "com.tencent.mm.plugin.profile.ui.ContactInfoUI"
        private const val CLASS_SEARCH_UI = "com.tencent.mm.plugin.fts.ui.FTSMainUI"
        private val KNOWN_WECHAT_UI_CLASSES = setOf(
            CLASS_LAUNCHER_UI,
            CLASS_CHATTING_UI,
            CLASS_CONTACT_INFO,
            CLASS_SEARCH_UI
        )


        private val requestCounter = AtomicLong(0)
        private val requestListeners = linkedMapOf<String, (VideoCallProgress) -> Unit>()
        private var pendingRequest: PendingRequest? = null

        fun requestVideoCall(contactName: String, listener: (VideoCallProgress) -> Unit): String {
            val requestId = "wechat-call-${System.currentTimeMillis()}-${requestCounter.incrementAndGet()}"
            var shouldNotifyBusy = false
            var shouldNotifyWaiting = false
            var activeService: SelectToSpeakService? = null
            synchronized(this) {
                requestListeners[requestId] = listener
                val service = instance
                if (pendingRequest != null || service?.hasActiveSession() == true) {
                    shouldNotifyBusy = true
                } else {
                    pendingRequest = PendingRequest(requestId, contactName)
                    activeService = service
                    shouldNotifyWaiting = service == null
                }
            }
            if (shouldNotifyBusy) {
                deliverProgress(
                    requestId,
                    VideoCallProgress(
                        message = "已有进行中的微信视频任务，请稍候",
                        success = false,
                        terminal = true
                    )
                )
                return requestId
            }
            if (shouldNotifyWaiting) {
                deliverProgress(
                    requestId,
                    VideoCallProgress(
                        message = "正在等待无障碍服务连接",
                        success = true,
                        terminal = false
                    )
                )
            }
            activeService?.consumePendingRequest()
            return requestId
        }

        fun clearRequestListener(requestId: String) {
            synchronized(this) {
                requestListeners.remove(requestId)
                if (pendingRequest?.requestId == requestId) {
                    pendingRequest = null
                }
            }
        }

        internal fun resetForTesting() {
            synchronized(this) {
                requestListeners.clear()
                pendingRequest = null
                requestCounter.set(0)
            }
        }

        private fun takePendingRequest(): PendingRequest? {
            return synchronized(this) {
                pendingRequest?.also { pendingRequest = null }
            }
        }

        private fun deliverProgress(requestId: String, progress: VideoCallProgress) {
            val listener = synchronized(this) { requestListeners[requestId] }
            listener?.invoke(progress)
            if (progress.terminal) {
                clearRequestListener(requestId)
            }
        }
    }

    data class VideoCallProgress(
        val message: String,
        val success: Boolean,
        val terminal: Boolean,
        val step: AutomationState = AutomationState.IDLE,
        val page: String? = null
    )


    private data class PendingRequest(
        val requestId: String,
        val contactName: String
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var processJob: Job? = null
    private var assistJob: Job? = null
    private var aiGuardJob: Job? = null
    private var timeoutJob: Job? = null
    private var totalTimeoutJob: Job? = null
    private var wechatWaitJob: Job? = null
    private val weChatStepAssistClient by lazy { WeChatStepAssistClient(this) }

    private lateinit var timeoutManager: TimeoutManager
    private var floatingView: FloatingStatusView? = null
    private var currentSession: VideoCallSession? = null
    private var launcherBringBackConfirmJob: Job? = null


    private var lastMissingRootLogAt = 0L
    private var lastWeChatClassName: String? = null


    private var cachedWeChatRoot: AccessibilityNodeInfo? = null

    private var systemLauncherPackages: Set<String> = emptySet()
    private var defaultLauncherPackage: String? = null
    private var lastLauncherOverviewAt = 0L
    private var wechatVersionTagged = false



    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        timeoutManager = TimeoutManager.getInstance(this)
        floatingView = FloatingStatusView(this)
        systemLauncherPackages = resolveSystemLauncherPackages()
        defaultLauncherPackage = resolveDefaultLauncherPackage()
        consumePendingRequest()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString()
        val className = event?.className?.toString()

        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != null) {
            if (shouldObserveLauncherForeground(pkg)) {
                scheduleLauncherBringBackConfirmation(triggerPkg = pkg, triggerClassName = className)
                return
            }
            launcherBringBackConfirmJob?.cancel()
            launcherBringBackConfirmJob = null
        }

        if (pkg != WECHAT_PACKAGE) {
            return
        }
        if (isMeaningfulWeChatClassName(className)) {
            lastWeChatClassName = className
        }

        updateCachedWeChatRoot(event.source)

        val session = currentSession ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                Log.d(TAG, "onEvent: STATE_CHANGED className=$className step=${session.step}")
                when (className) {
                    CLASS_LAUNCHER_UI -> {
                        if (session.step == Step.WAITING_HOME) {
                            Log.d(TAG, "onEvent: LauncherUI -> WAITING_LAUNCHER_UI")
                            wechatWaitJob?.cancel()
                            session.launcherPrepared = false
                            session.searchTextApplied = false
                            transitionTo(session, Step.WAITING_LAUNCHER_UI, "正在查找联系人")
                        } else {
                            scheduleAdaptiveProcess(session, DelayProfile.STABLE)
                        }
                    }
                    CLASS_CHATTING_UI,
                    CLASS_CONTACT_INFO,
                    CLASS_SEARCH_UI -> scheduleAdaptiveProcess(session, DelayProfile.STABLE)
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
        launcherBringBackConfirmJob?.cancel()
        launcherBringBackConfirmJob = null
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

    private fun shouldObserveLauncherForeground(pkg: String): Boolean {
        if (!LauncherPreferences.getInstance(this).isKioskModeEnabled()) return false
        if (hasActiveSession()) return false
        val defaultHome = defaultLauncherPackage ?: resolveDefaultLauncherPackage().also {
            defaultLauncherPackage = it
        }
        return pkg == defaultHome || (pkg == "com.vivo.hiboard" && defaultHome == "com.bbk.launcher2")
    }

    private fun scheduleLauncherBringBackConfirmation(triggerPkg: String, triggerClassName: String?) {
        launcherBringBackConfirmJob?.cancel()
        launcherBringBackConfirmJob = serviceScope.launch {
            delay(LAUNCHER_STATE_SETTLE_DELAY_MS)
            if (!LauncherPreferences.getInstance(this@SelectToSpeakService).isKioskModeEnabled()) return@launch
            if (hasActiveSession()) return@launch

            val activeRoot = rootInActiveWindow
            val activePkg = activeRoot?.packageName?.toString() ?: triggerPkg
            val activeClassName = activeRoot?.className?.toString()
            AccessibilityUtil.safeRecycle(activeRoot)

            val effectiveClassName = if (activePkg == triggerPkg) {
                triggerClassName ?: activeClassName
            } else {
                activeClassName
            }
            if (shouldBringLauncherBack(activePkg, effectiveClassName)) {
                bringLauncherToFront()
            }
        }
    }

    private fun shouldBringLauncherBack(pkg: String, className: String?): Boolean {
        if (!LauncherPreferences.getInstance(this).isKioskModeEnabled()) return false
        if (hasActiveSession()) return false

        val defaultHome = defaultLauncherPackage ?: resolveDefaultLauncherPackage().also {
            defaultLauncherPackage = it
        }
        val isLauncherOverview = isLauncherOverviewState(pkg, className)
        val now = System.currentTimeMillis()
        if (isLauncherOverview) {
            lastLauncherOverviewAt = now
        }
        val suppressHiboardAfterOverview =
            pkg == "com.vivo.hiboard" &&
                defaultHome == "com.bbk.launcher2" &&
                now - lastLauncherOverviewAt <= HIBOARD_OVERVIEW_SUPPRESS_WINDOW_MS
        val result = when {
            pkg == packageName -> false
            pkg == defaultHome && !isLauncherOverview -> true
            suppressHiboardAfterOverview -> false
            pkg == "com.vivo.hiboard" && defaultHome == "com.bbk.launcher2" -> true
            else -> false
        }
        Log.d(
            TAG,
            "shouldBringLauncherBack pkg=$pkg className=$className result=$result defaultHome=$defaultHome overview=$isLauncherOverview suppressHiboardAfterOverview=$suppressHiboardAfterOverview sinceOverview=${now - lastLauncherOverviewAt} launchers=$systemLauncherPackages"
        )
        return result
    }




    private fun resolveSystemLauncherPackages(): Set<String> {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_HOME)
        }
        return packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .toMutableSet()
            .apply { remove(packageName) }
    }

    private fun resolveDefaultLauncherPackage(): String? {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        return packageManager
            .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
            ?.takeUnless { it == packageName }
    }

    private fun isLauncherOverviewState(pkg: String, className: String?): Boolean {
        if (pkg != "com.bbk.launcher2") return false
        if (className?.contains("Recents", ignoreCase = true) == true) return true
        if (className?.contains("Overview", ignoreCase = true) == true) return true
        return isLauncherOverviewActive(pkg)
    }

    private fun isLauncherOverviewActive(pkg: String): Boolean {
        if (pkg != "com.bbk.launcher2") return false
        val root = rootInActiveWindow ?: return false
        return try {
            val overviewNodes = AccessibilityUtil.findAllById(root, "com.vivo.recents:id/overview_panel2")
            val clearAllNodes = AccessibilityUtil.findAllByText(root, "清除全部")
            val result = overviewNodes.isNotEmpty() || clearAllNodes.isNotEmpty()
            overviewNodes.forEach { AccessibilityUtil.safeRecycle(it) }
            clearAllNodes.forEach { AccessibilityUtil.safeRecycle(it) }
            result
        } finally {
            AccessibilityUtil.safeRecycle(root)
        }
    }

    private fun bringLauncherToFront() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            )
        }

        val startSent = tryStartLauncherActivity(intent, source = "directStart")

        val pendingIntentSent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            trySendLauncherPendingIntent(intent)
        } else {
            false
        }

        if (startSent || pendingIntentSent) {
            serviceScope.launch {
                delay(350)
                val activePackage = rootInActiveWindow?.packageName?.toString()
                if (activePackage != null && activePackage in systemLauncherPackages) {
                    Log.d(TAG, "bringLauncherToFront: retry after settle, activePackage=$activePackage")
                    tryStartLauncherActivity(intent, source = "retryStart")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        trySendLauncherPendingIntent(intent, source = "retryPendingIntent")
                    }
                }
            }
            return
        }

        val homeOk = performGlobalAction(GLOBAL_ACTION_HOME)
        Log.d(TAG, "bringLauncherToFront: fallback globalHome=$homeOk")
    }

    private fun tryStartLauncherActivity(intent: Intent, source: String): Boolean {
        return try {
            startActivity(intent)
            Log.d(TAG, "bringLauncherToFront: startActivity sent source=$source")
            true
        } catch (e: Exception) {
            Log.w(TAG, "bringLauncherToFront: startActivity failed source=$source error=${e.message}")
            false
        }
    }

    private fun trySendLauncherPendingIntent(intent: Intent, source: String = "pendingIntent"): Boolean {
        return try {
            val creatorOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                createPendingIntentCreatorOptions()
            } else {
                null
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                creatorOptions
            )
            val sendOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                createPendingIntentSendOptions()
            } else {
                null
            }
            pendingIntent.send(this, 0, null, null, null, null, sendOptions)
            Log.d(TAG, "bringLauncherToFront: PendingIntent sent source=$source")
            true
        } catch (e: Exception) {
            Log.w(TAG, "bringLauncherToFront: PendingIntent failed source=$source error=${e.message}")
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun createPendingIntentCreatorOptions(): Bundle {
        return ActivityOptions.makeBasic().apply {
            setPendingIntentCreatorBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
        }.toBundle()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun createPendingIntentSendOptions(): Bundle {
        return ActivityOptions.makeBasic().apply {
            setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
        }.toBundle()
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


    private fun hasActiveSession(): Boolean {
        return currentSession != null
    }

    private fun consumePendingRequest() {
        val request = takePendingRequest() ?: return
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
        lastWeChatClassName = null
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
                Log.d(TAG, "用户长按取消了视频通话流程")
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
                    Log.d(TAG, "waitLoop[$attempts]: 尚未收到微信事件，等待中")
                    continue
                }
                val currentClass = resolveCurrentWeChatClass(root)
                val page = detectWeChatPage(root, currentClass)
                scheduleAiGuard(session, root, currentClass, page)

                Log.d(
                    TAG,
                    "waitLoop[$attempts]: root class=$currentClass childCount=${root.childCount} page=$page"
                )
                if (page == WeChatPage.HOME) {
                    Log.d(TAG, "waitLoop: 首页确认加载完成，推进步骤")
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
        processJob?.cancel()
        processJob = serviceScope.launch {
            delay(delayMillis)
            if (currentSession === session) {
                processCurrentWindow()
            }
        }
    }

    private fun updateCachedWeChatRoot(source: AccessibilityNodeInfo?) {
        val root = source?.let { extractRoot(it) } ?: return
        if (root.packageName?.toString() != WECHAT_PACKAGE) {
            AccessibilityUtil.safeRecycle(root)
            return
        }
        val copy = AccessibilityNodeInfo.obtain(root)
        if (!isUsableWeChatRoot(copy)) {
            Log.d(TAG, "onEvent: 丢弃不可用 root ${AccessibilityUtil.summarizeNode(copy)}")
            AccessibilityUtil.safeRecycle(copy)
            return
        }
        replaceCachedWeChatRoot(copy)
        Log.d(TAG, "onEvent: 缓存 root class=${copy.className} childCount=${copy.childCount}")
    }

    private fun extractRoot(source: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var node = source
        var parent = node.parent
        while (parent != null) {
            node = parent
            parent = node.parent
        }
        return node
    }

    private fun replaceCachedWeChatRoot(node: AccessibilityNodeInfo?) {
        if (cachedWeChatRoot === node) {
            return
        }
        AccessibilityUtil.safeRecycle(cachedWeChatRoot)
        cachedWeChatRoot = node
    }

    private fun isUsableWeChatRoot(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) {
            return false
        }
        val pkg = node.packageName?.toString()
        if (pkg != WECHAT_PACKAGE) {
            return false
        }
        val className = node.className?.toString().orEmpty()
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val hasBounds = !bounds.isEmpty
        val hasChildren = node.childCount > 0
        if (!hasChildren && className.isBlank()) {
            return false
        }
        if (!hasBounds && !hasChildren) {
            return false
        }
        val refreshed = runCatching { node.refresh() }.getOrDefault(false)
        if (className in KNOWN_WECHAT_UI_CLASSES) {
            return true
        }
        return hasChildren || (refreshed && hasBounds && className.isNotBlank())
    }

    private fun getWindowRoots(): List<AccessibilityNodeInfo> {
        return windows
            .orEmpty()
            .sortedWith(
                compareByDescending<AccessibilityWindowInfo> { it.isActive }
                    .thenByDescending { it.isFocused }
                    .thenByDescending { it.layer }
            )
            .mapNotNull { it.root }
    }

    private fun findWeChatRootInWindows(): AccessibilityNodeInfo? {
        val activeRoot = rootInActiveWindow
        if (isUsableWeChatRoot(activeRoot)) {
            return activeRoot
        }
        AccessibilityUtil.safeRecycle(activeRoot)
        for (root in getWindowRoots()) {
            if (isUsableWeChatRoot(root)) {
                return root
            }
            AccessibilityUtil.safeRecycle(root)
        }
        return null
    }

    private fun getWeChatRoot(): AccessibilityNodeInfo? {
        val best = findWeChatRootInWindows()
        if (best != null) {
            Log.d(TAG, "getWeChatRoot: 选窗口 class=${best.className} childCount=${best.childCount} nodes=${countNodes(best)}")
            replaceCachedWeChatRoot(best)
            return best
        }
        Log.d(TAG, "getWeChatRoot: 未找到有效微信窗口")
        return null
    }

    private fun countNodes(node: AccessibilityNodeInfo?, depth: Int = 0): Int {
        if (node == null || depth > 35) return 0
        var count = 1
        for (i in 0 until node.childCount) {
            count += countNodes(node.getChild(i), depth + 1)
        }
        return count
    }

    private fun isMeaningfulWeChatClassName(className: String?): Boolean {
        return !className.isNullOrBlank() && className.startsWith("com.tencent.mm")
    }

    private fun resolveCurrentWeChatClass(root: AccessibilityNodeInfo?): String? {
        val rootClass = root?.className?.toString()
        if (rootClass in KNOWN_WECHAT_UI_CLASSES) {
            return rootClass
        }
        if (isMeaningfulWeChatClassName(lastWeChatClassName)) {
            return lastWeChatClassName
        }
        return rootClass
    }

    private fun isLauncherReady(root: AccessibilityNodeInfo, currentClass: String?): Boolean {
        val snapshot = snapshotOf(root)
        if (snapshot != null && WeChatUiSnapshotAnalyzer.isLauncherReady(snapshot)) {
            return true
        }
        return currentClass == CLASS_LAUNCHER_UI && root.childCount > 0
    }

    private fun isChatPage(root: AccessibilityNodeInfo, currentClass: String?): Boolean {
        if (currentClass == CLASS_CHATTING_UI) {
            return true
        }
        val snapshot = snapshotOf(root)
        if (snapshot != null) {
            if (currentClass == CLASS_SEARCH_UI || WeChatUiSnapshotAnalyzer.isSearchPage(snapshot) || WeChatUiSnapshotAnalyzer.isContactInfoPage(snapshot)) {
                return false
            }
            if (WeChatUiSnapshotAnalyzer.isChatPageLike(snapshot)) {
                return true
            }
        }
        if (!hasEditableNode(root)) {
            return false
        }
        return hasConversationChrome(root) || currentClass == CLASS_LAUNCHER_UI
    }


    private fun hasConversationChrome(root: AccessibilityNodeInfo?): Boolean {
        val byId = findNodeByIds(
            root,
            "com.tencent.mm:id/bjz",
            "com.tencent.mm:id/j7s",
            "com.tencent.mm:id/more_options"
        )
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
        if (currentClass == CLASS_SEARCH_UI || isSearchPage(root)) {
            return WeChatPage.SEARCH
        }
        if (currentClass == CLASS_CONTACT_INFO || isContactInfoPage(root)) {
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
        return hasEditableNode(root) && (
            hasExactText(root, "取消") ||
                hasExactText(root, "搜索") ||
                hasExactText(root, "搜索指定内容")
            )
    }

    private fun isContactInfoPage(root: AccessibilityNodeInfo): Boolean {
        val snapshot = snapshotOf(root)
        if (snapshot != null) {
            return WeChatUiSnapshotAnalyzer.isContactInfoPage(snapshot)
        }
        return hasExactText(root, "音视频通话") || hasExactText(root, "发消息")
    }


    private fun isTargetConversationPage(
        root: AccessibilityNodeInfo,
        currentClass: String?,
        contactName: String
    ): Boolean {
        val page = detectWeChatPage(root, currentClass)
        if (page != WeChatPage.CHAT && page != WeChatPage.CONTACT_DETAIL) {
            return false
        }
        return containsContactName(root, contactName)
    }

    private fun containsContactName(root: AccessibilityNodeInfo?, contactName: String): Boolean {
        val snapshot = snapshotOf(root)
        if (snapshot != null && WeChatUiSnapshotAnalyzer.containsContactName(snapshot, contactName)) {
            return true
        }
        val titleNode = findNodeByExactText(root, contactName, "com.tencent.mm:id/kbq", "com.tencent.mm:id/odf")
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
        return false
    }


    private fun findNodeByExactText(root: AccessibilityNodeInfo?, expectedText: String, vararg ids: String): AccessibilityNodeInfo? {
        for (id in ids) {
            val nodes = AccessibilityUtil.findAllById(root, id)
            var matched: AccessibilityNodeInfo? = null
            for (node in nodes) {
                if (matchesNodeText(node, expectedText, exactMatch = true)) {
                    matched = node
                    break
                }
            }
            nodes.forEach { node ->
                if (node !== matched) {
                    AccessibilityUtil.safeRecycle(node)
                }
            }
            if (matched != null) {
                return matched
            }
        }
        return null
    }

    private fun matchesNodeText(node: AccessibilityNodeInfo?, expectedText: String, exactMatch: Boolean): Boolean {
        val text = node?.text?.toString()
        val desc = node?.contentDescription?.toString()
        return listOfNotNull(text, desc).any { value ->
            if (exactMatch) value == expectedText else value.contains(expectedText)
        }
    }


    private fun hasEditableNode(root: AccessibilityNodeInfo?): Boolean {

        if (root == null) {
            return false
        }
        if (root.isEditable || root.className == "android.widget.EditText") {
            return true
        }
        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            val found = hasEditableNode(child)
            AccessibilityUtil.safeRecycle(child)
            if (found) {
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
        Log.d(TAG, "attempt[$key]=$attempt/$maxAttempts step=${session.step}")
        if (attempt > maxAttempts) {
            if (requestAiStepAssist(session, root, "attempt_$key")) {
                return false
            }
            failAndHide(failureMessage, root)
            return false
        }
        return true
    }

    private enum class DelayProfile(
        val minDelay: Long,
        val maxDelay: Long,
        val timeoutDivisor: Long
    ) {
        FAST(80L, 220L, 90L),
        STABLE(120L, 320L, 55L),
        TRANSITION(160L, 480L, 36L),
        RECOVER(220L, 720L, 28L),
        WAIT_LOOP(260L, 760L, 26L),
        SHEET(200L, 650L, 24L)
    }

    private fun adaptiveDelay(
        session: VideoCallSession,
        profile: DelayProfile,
        attemptKey: String? = null,
        actionSucceeded: Boolean? = null
    ): Long {
        val stepTimeout = timeoutFor(session.step)
        val tierMultiplier = when (timeoutManager.getDeviceTier()) {
            TimeoutManager.DeviceTier.LOW  -> 1.40f
            TimeoutManager.DeviceTier.MID  -> 1.00f
            TimeoutManager.DeviceTier.HIGH -> 0.85f
        }
        val rawBase = (stepTimeout / profile.timeoutDivisor * tierMultiplier).toLong()
        val base = rawBase.coerceIn(profile.minDelay, profile.maxDelay)
        val attemptCount = attemptKey?.let { session.actionAttempts[it] ?: 0 } ?: 0
        val attemptBoost = if (attemptCount > 0) {
            (base * 0.28f * attemptCount).toLong()
        } else {
            0L
        }
        val failureBoost = if (actionSucceeded == false) base / 3 else 0L
        return (base + attemptBoost + failureBoost).coerceIn(profile.minDelay, profile.maxDelay)
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
        return adaptiveDelay(session, profile, attemptKey = attemptKey).coerceAtLeast(minWindow)
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
            }
            Step.WAITING_LAUNCHER_UI,
            Step.WAITING_SEARCH_FALLBACK -> {
                session.searchTextApplied = false
                session.launcherPrepared = false
            }
            Step.WAITING_CONTACT_RESULT -> {
                session.moreButtonClickedAt = 0L
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
        Log.d(
            TAG,
            "resolveAndRerouteTo: failed=$failedStep target=$target failCount=$failCount reason=$reason history=${session.stepHistory}"
        )
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
                logDebugLong(
                    "processCurrentWindow: 微信窗口未找到，当前前台包名=$fallbackPkg, step=${session.step}, contact=${session.contactName}\nwindows=${describeWindows()}"
                )
            }
            scheduleAdaptiveProcess(session, DelayProfile.WAIT_LOOP)
            return
        }

        val now = System.currentTimeMillis()
        if (now < session.dismissingUntil) {
            Log.d(TAG, "processCurrentWindow: 弹窗冷却中，剩余${session.dismissingUntil - now}ms，跳过")
            return
        }

        val remaining = session.stepStartedAt + timeoutFor(session.step) - now
        if (remaining > 3000L && session.dismissAttempts < 3) {
            if (tryDismissTransientUi(session, root)) {
                return
            }
        }

        val currentClass = resolveCurrentWeChatClass(root)
        scheduleAiGuard(session, root, currentClass)
        Log.d(TAG, "processCurrentWindow: step=${session.step} class=$currentClass rawClass=${root.className} lastUiClass=$lastWeChatClassName")

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

    private fun scheduleAiGuard(
        session: VideoCallSession,
        root: AccessibilityNodeInfo,
        currentClass: String?,
        page: WeChatPage = detectWeChatPage(root, currentClass)
    ) {
        if (session.aiAssistInFlight) {
            return
        }
        if (!weChatStepAssistClient.isConfigured() || hasSensitiveWechatText(root)) {
            return
        }
        val guardReason = aiGuardReason(session, page) ?: return
        val snapshot = snapshotOf(root) ?: return
        val key = buildAiGuardKey(session, currentClass, page, snapshot)
        val cacheKey = "cache:$key"
        val resolveKey = "resolve:$key"
        if (session.aiGuardPendingKey != null || session.aiAssistKeys.contains(cacheKey) || session.aiAssistKeys.contains(resolveKey)) {
            return
        }
        val stepAtSchedule = session.step
        session.aiGuardPendingKey = key
        Log.d(TAG, "aiGuard schedule step=$stepAtSchedule page=$page class=${currentClass.orEmpty()} reason=$guardReason")
        aiGuardJob = serviceScope.launch {
            delay(AI_GUARD_STABLE_DELAY_MS)
            if (currentSession !== session) {
                return@launch
            }
            if (session.step != stepAtSchedule) {
                session.aiGuardPendingKey = null
                return@launch
            }
            if (session.aiAssistInFlight) {
                session.aiGuardPendingKey = null
                return@launch
            }
            if (!session.aiAssistKeys.add(cacheKey)) {
                session.aiGuardPendingKey = null
                return@launch
            }
            val stepAtRequest = session.step
            val requestRoot = getWeChatRoot()
            if (requestRoot == null || hasSensitiveWechatText(requestRoot)) {
                session.aiGuardPendingKey = null
                return@launch
            }
            val latestClass = resolveCurrentWeChatClass(requestRoot)
            val latestPage = detectWeChatPage(requestRoot, latestClass)
            val latestReason = aiGuardReason(session, latestPage)
            if (latestReason == null) {
                session.aiGuardPendingKey = null
                return@launch
            }
            val latestSnapshot = snapshotOf(requestRoot)
            if (latestSnapshot == null) {
                session.aiGuardPendingKey = null
                return@launch
            }
            Log.d(TAG, "aiGuard cache step=$stepAtRequest page=$latestPage class=${latestClass.orEmpty()} reason=$latestReason")
            val cachedDecision = weChatStepAssistClient.decide(
                step = stepAtRequest.name,
                currentClass = latestClass,
                targetAlias = session.contactName,
                failureReason = latestReason,
                snapshot = latestSnapshot,
                mode = "cache_only"
            )
            if (currentSession !== session) {
                return@launch
            }
            val cachedRoot = getWeChatRoot()
            val cachedApplied = if (
                cachedDecision != null &&
                cachedDecision.confidence >= AI_GUARD_CONFIDENCE &&
                session.step == stepAtRequest &&
                cachedRoot != null &&
                !hasSensitiveWechatText(cachedRoot) &&
                isAiGuardActionAllowed(session.step, cachedDecision.action)
            ) {
                applyAiStepAssistDecision(session, cachedRoot, cachedDecision)
            } else {
                false
            }
            if (cachedDecision != null) {
                logStep(
                    session,
                    "aiGuardCache",
                    if (cachedApplied) cachedDecision?.action?.value else "skip",
                    "confidence=${cachedDecision?.confidence ?: 0f} page=${cachedDecision?.page} msg=${cachedDecision?.reason} stepAtRequest=$stepAtRequest currentStep=${session.step}"
                )
            }
            if (cachedApplied) {
                session.aiGuardPendingKey = null
                return@launch
            }
            if (session.aiAssistRequests >= MAX_AI_ASSIST_REQUESTS) {
                session.aiGuardPendingKey = null
                return@launch
            }
            delay(AI_GUARD_RESOLVE_DELAY_MS)
            if (currentSession !== session || session.step != stepAtRequest) {
                session.aiGuardPendingKey = null
                return@launch
            }
            val resolveRoot = getWeChatRoot()
            if (resolveRoot == null || hasSensitiveWechatText(resolveRoot)) {
                session.aiGuardPendingKey = null
                return@launch
            }
            val resolveClass = resolveCurrentWeChatClass(resolveRoot)
            val resolvePage = detectWeChatPage(resolveRoot, resolveClass)
            val resolveReason = aiGuardReason(session, resolvePage)
            if (resolveReason == null) {
                session.aiGuardPendingKey = null
                return@launch
            }
            val resolveSnapshot = snapshotOf(resolveRoot)
            if (resolveSnapshot == null) {
                session.aiGuardPendingKey = null
                return@launch
            }
            if (session.aiAssistRequests >= MAX_AI_ASSIST_REQUESTS || !session.aiAssistKeys.add(resolveKey)) {
                session.aiGuardPendingKey = null
                return@launch
            }
            session.aiAssistRequests++
            session.aiAssistInFlight = true
            Log.d(TAG, "aiGuard resolve step=$stepAtRequest page=$resolvePage class=${resolveClass.orEmpty()} count=${session.aiAssistRequests} reason=$resolveReason")
            val decision = weChatStepAssistClient.decide(
                step = stepAtRequest.name,
                currentClass = resolveClass,
                targetAlias = session.contactName,
                failureReason = resolveReason,
                snapshot = resolveSnapshot,
                mode = "resolve"
            )
            if (currentSession !== session) {
                return@launch
            }
            session.aiAssistInFlight = false
            session.aiGuardPendingKey = null
            val latestRoot = getWeChatRoot()
            val applied = if (
                decision != null &&
                decision.confidence >= AI_GUARD_CONFIDENCE &&
                session.step == stepAtRequest &&
                latestRoot != null &&
                !hasSensitiveWechatText(latestRoot) &&
                isAiGuardActionAllowed(session.step, decision.action)
            ) {
                applyAiStepAssistDecision(session, latestRoot, decision)
            } else {
                false
            }
            logStep(
                session,
                "aiGuard",
                if (applied) decision?.action?.value else "skip",
                "confidence=${decision?.confidence ?: 0f} page=${decision?.page} msg=${decision?.reason} stepAtRequest=$stepAtRequest currentStep=${session.step}"
            )
        }
    }

    private fun aiGuardReason(session: VideoCallSession, page: WeChatPage): String? {
        val stepAge = System.currentTimeMillis() - session.stepStartedAt
        if (session.step == Step.WAITING_HOME) {
            return null
        }
        val retrying = session.actionAttempts.values.any { it > 1 } || session.stepFailCount.isNotEmpty()
        return when {
            page == WeChatPage.UNKNOWN && stepAge >= AI_GUARD_UNKNOWN_STEP_AGE_MS -> "guard_unknown_${session.step}"
            session.step == Step.WAITING_VIDEO_OPTIONS && stepAge >= AI_GUARD_VIDEO_STEP_AGE_MS -> "guard_video_options"
            retrying && stepAge >= AI_GUARD_RETRY_STEP_AGE_MS -> "guard_retry_${session.step}"
            else -> null
        }
    }

    private fun buildAiGuardKey(
        session: VideoCallSession,
        currentClass: String?,
        page: WeChatPage,
        snapshot: WeChatUiSnapshot
    ): String {
        val signal = snapshot.flatten()
            .filter {
                !it.text.isNullOrBlank() ||
                    !it.contentDescription.isNullOrBlank() ||
                    !it.viewIdResourceName.isNullOrBlank() ||
                    it.editable ||
                    it.clickable
            }
            .take(24)
            .joinToString("|") {
                listOf(
                    it.text.orEmpty().take(20),
                    it.contentDescription.orEmpty().take(20),
                    it.viewIdResourceName.orEmpty().take(40),
                    it.className.orEmpty().take(40),
                    it.clickable.toString(),
                    it.editable.toString()
                ).joinToString("#")
            }
        return "guard:${session.step}:${currentClass.orEmpty()}:$page:${signal.hashCode()}"
    }

    private fun isAiGuardActionAllowed(step: Step, action: WeChatStepAssistAction): Boolean {
        return when (step) {
            Step.WAITING_HOME -> action in setOf(
                WeChatStepAssistAction.Wait,
                WeChatStepAssistAction.TapSearch,
                WeChatStepAssistAction.TapContact,
                WeChatStepAssistAction.TapVideoCall,
                WeChatStepAssistAction.TapVideoOption
            )
            Step.WAITING_LAUNCHER_UI -> action in setOf(
                WeChatStepAssistAction.Wait,
                WeChatStepAssistAction.TapSearch,
                WeChatStepAssistAction.TapContact,
                WeChatStepAssistAction.TapVideoCall,
                WeChatStepAssistAction.TapVideoOption
            )
            Step.WAITING_SEARCH_FALLBACK -> action in setOf(
                WeChatStepAssistAction.Wait,
                WeChatStepAssistAction.InputContact,
                WeChatStepAssistAction.TapContact,
                WeChatStepAssistAction.TapVideoCall,
                WeChatStepAssistAction.TapVideoOption
            )
            Step.WAITING_CONTACT_RESULT -> action in setOf(
                WeChatStepAssistAction.Wait,
                WeChatStepAssistAction.TapContact,
                WeChatStepAssistAction.TapVideoCall,
                WeChatStepAssistAction.TapVideoOption
            )
            Step.WAITING_CONTACT_DETAIL -> action in setOf(
                WeChatStepAssistAction.Wait,
                WeChatStepAssistAction.TapVideoCall,
                WeChatStepAssistAction.TapVideoOption
            )
            Step.WAITING_VIDEO_OPTIONS -> action in setOf(
                WeChatStepAssistAction.Wait,
                WeChatStepAssistAction.TapVideoOption
            )
        }
    }

    private fun requestAiStepAssist(
        session: VideoCallSession,
        root: AccessibilityNodeInfo?,
        reason: String
    ): Boolean {
        if (root == null) {
            Log.d(TAG, "aiStepAssist skip reason=$reason cause=no_root")
            return false
        }
        if (!weChatStepAssistClient.isConfigured()) {
            Log.d(TAG, "aiStepAssist skip reason=$reason cause=not_configured")
            return false
        }
        if (hasSensitiveWechatText(root)) {
            Log.d(TAG, "aiStepAssist skip reason=$reason cause=sensitive_text")
            return false
        }
        if (!session.aiAssistInFlight) {
            aiGuardJob?.cancel()
            session.aiGuardPendingKey = null
        }
        if (session.aiAssistInFlight) {
            Log.d(TAG, "aiStepAssist wait reason=$reason cause=in_flight")
            scheduleAdaptiveProcess(session, DelayProfile.STABLE)
            return true
        }
        if (session.aiAssistRequests >= MAX_AI_ASSIST_REQUESTS) {
            Log.d(TAG, "aiStepAssist skip reason=$reason cause=request_limit count=${session.aiAssistRequests}")
            return false
        }
        val key = "${session.step}:$reason"
        if (!session.aiAssistKeys.add(key)) {
            Log.d(TAG, "aiStepAssist skip reason=$reason cause=duplicate key=$key")
            return false
        }
        val snapshot = snapshotOf(root) ?: run {
            Log.d(TAG, "aiStepAssist skip reason=$reason cause=snapshot_failed")
            return false
        }
        val currentClass = resolveCurrentWeChatClass(root)
        session.aiAssistRequests++
        session.aiAssistInFlight = true
        session.stateOverride = AutomationState.RECOVERING
        updateProgress(session, "正在识别页面")
        val stepAtRequest = session.step
        Log.d(TAG, "aiStepAssist request reason=$reason step=$stepAtRequest class=$currentClass count=${session.aiAssistRequests}")
        assistJob?.cancel()
        assistJob = serviceScope.launch {
            val decision = weChatStepAssistClient.decide(
                step = stepAtRequest.name,
                currentClass = currentClass,
                targetAlias = session.contactName,
                failureReason = reason,
                snapshot = snapshot
            )
            if (currentSession !== session) {
                return@launch
            }
            session.aiAssistInFlight = false
            val latestRoot = getWeChatRoot()
            val applied = if (
                decision != null &&
                decision.confidence >= AI_RECOVERY_CONFIDENCE &&
                session.step == stepAtRequest &&
                isAiGuardActionAllowed(session.step, decision.action) &&
                latestRoot != null &&
                !hasSensitiveWechatText(latestRoot)
            ) {
                applyAiStepAssistDecision(session, latestRoot, decision)
            } else {
                false
            }
            logStep(
                session,
                "aiStepAssist",
                if (applied) decision?.action?.value else "skip",
                "reason=$reason confidence=${decision?.confidence ?: 0f} page=${decision?.page} msg=${decision?.reason}"
            )
            if (!applied && currentSession === session) {
                session.stateOverride = null
                scheduleAdaptiveProcess(session, DelayProfile.RECOVER)
            }
        }
        return true
    }

    private fun applyAiStepAssistDecision(
        session: VideoCallSession,
        root: AccessibilityNodeInfo,
        decision: WeChatStepAssistDecision
    ): Boolean {
        return when (decision.action) {
            WeChatStepAssistAction.Wait -> {
                scheduleAdaptiveProcess(session, DelayProfile.STABLE, attemptKey = "ai_wait", actionSucceeded = true)
                true
            }
            WeChatStepAssistAction.TapSearch -> {
                val clicked = clickTopSearchBar(root)
                if (clicked) {
                    session.searchTextApplied = false
                    transitionTo(session, Step.WAITING_SEARCH_FALLBACK, "正在打开搜索")
                }
                clicked
            }
            WeChatStepAssistAction.InputContact -> {
                val filled = fillSearchInput(root, session.contactName)
                if (filled) {
                    session.searchTextApplied = true
                    transitionTo(session, Step.WAITING_CONTACT_RESULT, "正在查找联系人")
                }
                filled
            }
            WeChatStepAssistAction.TapContact -> {
                val clicked = clickContactResult(root, session.contactName) || clickMessageListContact(root, session.contactName)
                if (clicked) {
                    transitionTo(session, Step.WAITING_CONTACT_DETAIL, "正在打开联系人")
                }
                clicked
            }
            WeChatStepAssistAction.TapVideoCall -> {
                if (!canStartVideoCallOnCurrentPage(session, root)) {
                    return false
                }
                val clicked = clickVideoCallEntry(root) || clickVideoCallOption(root)
                if (clicked) {
                    transitionTo(session, Step.WAITING_VIDEO_OPTIONS, "正在发起视频通话")
                }
                clicked
            }
            WeChatStepAssistAction.TapVideoOption -> {
                val clicked = clickVideoCallSheetOption(root) || clickVideoCallOption(root)
                if (clicked) {
                    finishVideoCallStarted(session)
                }
                clicked
            }
            WeChatStepAssistAction.Fail -> false
        }
    }

    private fun clickMessageListContact(root: AccessibilityNodeInfo?, contactName: String): Boolean {
        val node = findContactInMessageList(root, contactName) ?: return false
        val clicked = AccessibilityUtil.performClick(this, node)
        AccessibilityUtil.safeRecycle(node)
        return clicked
    }

    private fun canStartVideoCallOnCurrentPage(session: VideoCallSession, root: AccessibilityNodeInfo): Boolean {
        val currentClass = resolveCurrentWeChatClass(root)
        return detectWeChatPage(root, currentClass) != WeChatPage.CHAT ||
            isTargetConversationPage(root, currentClass, session.contactName)
    }

    private fun hasSensitiveWechatText(root: AccessibilityNodeInfo?): Boolean {
        return listOf("支付", "转账", "红包", "收款", "付款", "银行卡").any { hasContainingText(root, it) }
    }

    private fun tryDismissTransientUi(session: VideoCallSession, root: AccessibilityNodeInfo?): Boolean {
        // 组合条件：只有当前 Step 期望的关键节点找不到时，才认为是弹窗干扰
        // 避免把正常页面里的「我知道了」等按钮误当弹窗处理
        if (!isCurrentStepBlocked(session, root)) {
            return false
        }
        val action = snapshotOf(root)?.let(WeChatUiSnapshotAnalyzer::suggestDismissAction)
            ?: WeChatDismissAction.NONE
        val dismissed = when (action) {
            WeChatDismissAction.SEARCH_CANCEL -> clickSearchCancel(root)
            WeChatDismissAction.SHEET_CANCEL -> clickVideoCallSheetCancel(root)
            WeChatDismissAction.CLOSE_DIALOG -> clickKnownDialogClose(root)
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
        Log.d(TAG, "tryDismissTransientUi: action=$action attempts=${session.dismissAttempts}")
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
                findNodeByIds(root, "com.tencent.mm:id/d98") == null &&
                    AccessibilityUtil.findFirstEditableNode(root) == null
            }
            Step.WAITING_CONTACT_DETAIL -> {
                // 联系人详情页：找不到「音视频通话」和「发消息」才算被遮挡
                !hasExactText(root, "音视频通话") && !hasExactText(root, "发消息")
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
        updateProgress(session, "正在返回微信首页")
        val backAttempt = incrementActionAttempt(session, "home_back")
        if (backAttempt > MAX_HOME_BACK_ATTEMPTS) {
            Log.d(TAG, "recoverToHome: 超过$MAX_HOME_BACK_ATTEMPTS 次，尝试使用 HOME 退出微信")
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
                if (activePackage == WECHAT_PACKAGE) {
                    Log.d(TAG, "recoverToHome: HOME 后仍停留在微信前台，降级继续 BACK")
                    fallbackBackFromHomeRecovery(session, currentClass, "$reason, stillInWeChat=true")
                    return@launch
                }
                session.actionAttempts.clear()
                session.searchTextApplied = false
                session.launcherPrepared = false
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
        Log.d(TAG, "$reason class=$currentClass, backSuccess=$backSuccess attempt=$backAttempt")
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
        Log.d(TAG, "fallbackBackFromHomeRecovery: reason=$reason class=$currentClass backSuccess=$backSuccess")
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
                if (isTargetConversationPage(root, currentClass, session.contactName)) {
                    logStep(session, "detectPage", "TARGET_CHAT", "已在目标联系人页，直接发起视频")
                    transitionTo(session, Step.WAITING_CONTACT_DETAIL, "已进入目标联系人，正在发起视频")
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
                if (requestAiStepAssist(session, root, "home_unknown")) {
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
            if (page == WeChatPage.UNKNOWN && requestAiStepAssist(session, root, "launcher_unknown")) {
                return
            }
            session.launcherPrepared = false
            session.searchTextApplied = false
            rerouteTo(session, Step.WAITING_HOME, "正在返回微信首页")
            return
        }

        if (!session.launcherPrepared) {
            session.launcherPrepared = true
            val tabClicked = clickMessageTab(root)
            logStep(session, "clickMessageTab", tabClicked)
            scheduleAdaptiveProcess(
                session,
                if (tabClicked) DelayProfile.TRANSITION else DelayProfile.STABLE
            )
            return
        }

        val contactNode = findContactInMessageList(root, session.contactName)
        if (contactNode != null) {
            val success = AccessibilityUtil.performClick(this, contactNode)
            logStep(session, "clickContactInList", success, "contact=${session.contactName} node=${AccessibilityUtil.summarizeNode(contactNode)}")
            AccessibilityUtil.safeRecycle(contactNode)
            if (success) {
                transitionTo(session, Step.WAITING_CONTACT_DETAIL, "正在打开聊天")
                return
            }
        } else {
            logStep(session, "findContactInList", false, "消息列表未找到 contact=${session.contactName}，转搜索路径")
        }

        updateProgress(session, "消息列表未找到，正在打开搜索")
        val searchClicked = clickTopSearchBar(root)
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
                if (requestAiStepAssist(session, root, "search_unexpected")) {
                    return
                }
                session.searchTextApplied = false
                session.launcherPrepared = false
                rerouteTo(session, Step.WAITING_HOME, "正在返回微信首页")
                return
            }
        }

        if (!session.searchTextApplied) {
            val filled = fillSearchInput(root, session.contactName)
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
                if (isTargetConversationPage(root, currentClass, session.contactName)) {
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
                if (requestAiStepAssist(session, root, "contact_result_unknown")) {
                    return
                }
                session.searchTextApplied = false
                session.launcherPrepared = false
                rerouteTo(session, Step.WAITING_HOME, "正在返回微信首页")
                return
            }
        }

        val contactClicked = clickContactResult(root, session.contactName)
        logStep(session, "clickContactResult", contactClicked, "contact=${session.contactName}")
        if (contactClicked) {
            transitionTo(session, Step.WAITING_CONTACT_DETAIL, "正在打开联系人")
            return
        }
        if (hasNoSearchResult(root)) {
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
                if (!isTargetConversationPage(root, currentClass, session.contactName)) {
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
                if (requestAiStepAssist(session, root, "contact_detail_unknown")) {
                    return
                }
                resolveAndRerouteTo(session, session.step, "WAITING_CONTACT_DETAIL: unknownPage")
                return
            }

        }

        val directClicked = clickVideoCallEntry(root)
        logStep(session, "clickVideoCallEntry(direct)", directClicked)
        if (directClicked) {
            transitionTo(session, Step.WAITING_VIDEO_OPTIONS, "正在发起视频通话")
            return
        }

        val directOptionClicked = clickVideoCallOption(root)
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

        val moreClicked = clickMoreButton(root)
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
        val sheetClicked = clickVideoCallSheetOption(root)
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
        val clicked = clickVideoCallOption(root)
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
        recordStepSuccess(session.step, System.currentTimeMillis() - session.stepStartedAt)
        recordStepHistory(session, nextStep)
        session.step = nextStep
        session.stepStartedAt = System.currentTimeMillis()
        session.moreButtonClickedAt = 0L
        session.stateOverride = null
        session.stepFailCount.remove(nextStep)
        if (!session.aiAssistInFlight) {
            aiGuardJob?.cancel()
            session.aiGuardPendingKey = null
        }
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
        logStep(session, "COMPLETED", "视频通话已发起", "totalElapsed=${System.currentTimeMillis() - session.startedAt}ms")
        applyWeChatCallAudioStrategy()
        floatingView?.updateMessage("视频通话已发起")
        notifyState(session, "视频通话已发起", success = true, terminal = true)
        currentSession = null
        timeoutJob?.cancel()
        totalTimeoutJob?.cancel()
        processJob?.cancel()
        assistJob?.cancel()
        aiGuardJob?.cancel()
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
            if (hasContainingText(root, "扬声器已开") || hasContainingText(root, "免提已开")) {
                return
            }
            val toggleNode = findSpeakerToggleNode(root) ?: return
            val clicked = AccessibilityUtil.performClick(this, toggleNode)
            Log.d(TAG, "clickWeChatSpeakerButtonIfNeeded: click=$clicked")
            AccessibilityUtil.safeRecycle(toggleNode)
        } finally {
            AccessibilityUtil.safeRecycle(root)
        }
    }

    private fun obtainSpeakerTargetRoot(): AccessibilityNodeInfo? {
        rootInActiveWindow?.let { return AccessibilityNodeInfo.obtain(it) }
        cachedWeChatRoot?.let { return AccessibilityNodeInfo.obtain(it) }
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
                if (lastWeChatClassName == CLASS_LAUNCHER_UI) {
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
        timeoutJob?.cancel()
        timeoutJob = serviceScope.launch {
            delay(timeoutMillis)
            val session = currentSession
            if (session != null && session.step == step) {
                val root = getWeChatRoot()
                if (!requestAiStepAssist(session, root, "timeout_$step")) {
                    failAndHide(failureMessage, root)
                }
            }
        }
    }

    private fun armTotalTimeout(timeoutMillis: Long, failureMessage: String) {
        totalTimeoutJob?.cancel()
        totalTimeoutJob = serviceScope.launch {
            delay(timeoutMillis)
            val session = currentSession ?: return@launch
            failAndHide(
                "$failureMessage（当前步骤：${session.step}，联系人：${session.contactName}）",
                getWeChatRoot()
            )
        }
    }

    private fun cancelSession(notifyFailure: Boolean) {
        val session = currentSession
        processJob?.cancel()
        assistJob?.cancel()
        aiGuardJob?.cancel()
        timeoutJob?.cancel()
        totalTimeoutJob?.cancel()
        wechatWaitJob?.cancel()
        launcherBringBackConfirmJob?.cancel()
        processJob = null
        assistJob = null
        aiGuardJob = null
        timeoutJob = null
        totalTimeoutJob = null
        wechatWaitJob = null
        launcherBringBackConfirmJob = null


        lastMissingRootLogAt = 0L
        lastWeChatClassName = null
        cachedWeChatRoot?.recycle()
        cachedWeChatRoot = null
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
        logErrorLong(buildFailureDiagnostics(message, root))
        cancelSession(false)
        if (session != null) {
            notifyState(session, message, success = false, terminal = true)
        }
    }


    private fun buildFailureDiagnostics(message: String, root: AccessibilityNodeInfo?): String {
        val session = currentSession
        return buildString {
            append("failure=").append(message)
            if (session != null) {
                append("\nstep=").append(session.step)
                append(", contact=").append(session.contactName)
                append(", startedAt=").append(session.startedAt)
                append(", stepStartedAt=").append(session.stepStartedAt)
                append(", now=").append(System.currentTimeMillis())
                append(", actionAttempts=").append(session.actionAttempts)
                append(", lastAnnouncedMessage=").append(session.lastAnnouncedMessage)
            }
            append("\nroot=").append(AccessibilityUtil.summarizeNode(root))
            append("\nwindows=").append(describeWindows())
            append("\nnodeTree=\n").append(AccessibilityUtil.dumpTree(root))
        }
    }

    private fun describeWindows(): String {
        val summaries = windows.orEmpty().mapIndexed { index, window ->
            val root = window.root
            val summary = buildString {
                append("#").append(index)
                append("(type=").append(window.type)
                append(", active=").append(window.isActive)
                append(", focused=").append(window.isFocused)
                append(", layer=").append(window.layer)
                append(", root=").append(AccessibilityUtil.summarizeNode(root))
                append(")")
            }
            AccessibilityUtil.safeRecycle(root)
            summary
        }
        return if (summaries.isEmpty()) "none" else summaries.joinToString("; ")
    }

    private fun logDebugLong(message: String) {
        message.chunked(3000).forEachIndexed { index, chunk ->
            Log.d(TAG, "[$index] $chunk")
        }
    }

    private fun logErrorLong(message: String) {
        message.chunked(3000).forEachIndexed { index, chunk ->
            Log.e(TAG, "[$index] $chunk")
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
        Log.d(TAG, sb.toString())
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

    private fun findContactInMessageList(root: AccessibilityNodeInfo?, contactName: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val byId = AccessibilityUtil.findAllById(root, "com.tencent.mm:id/kbq")
        val matched = byId.firstOrNull { node ->
            node.text?.toString() == contactName || node.contentDescription?.toString() == contactName
        }
        byId.forEach { if (it !== matched) AccessibilityUtil.safeRecycle(it) }
        if (matched != null) return matched

        val byDesc = AccessibilityUtil.findNodesByContentDescription(root, contactName, exactMatch = true).firstOrNull()
        if (byDesc != null) return byDesc

        return AccessibilityUtil.findBestTextNode(root, contactName, exactMatch = true, preferBottom = false)
    }

    private fun launchWeChat(): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(WECHAT_PACKAGE)
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
            val info = packageManager.getPackageInfo(WECHAT_PACKAGE, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey("wechat_version_name", info.versionName ?: "unknown")
                setCustomKey("wechat_version_code", versionCode)
                setCustomKey("device_brand", Build.BRAND)
                setCustomKey("device_model", Build.MODEL)
            }
        }
    }

    private fun findNodeByIds(root: AccessibilityNodeInfo?, vararg ids: String): AccessibilityNodeInfo? {
        for (id in ids) {
            val node = AccessibilityUtil.findNodeById(root, id)
            if (node != null) {
                return node
            }
        }
        return null
    }

    private fun clickMessageTab(root: AccessibilityNodeInfo?): Boolean {
        val byId = AccessibilityUtil.findAllById(root, "com.tencent.mm:id/icon_tv").firstOrNull()
        if (byId != null) {
            val success = AccessibilityUtil.performClick(this, byId)
            Log.d(TAG, "clickMessageTab: by resource-id, click=$success")
            AccessibilityUtil.safeRecycle(byId)
            if (success) return true
        }
        val byText = AccessibilityUtil.findBestTextNode(root, "微信", exactMatch = true, preferBottom = true)
        if (byText != null) {
            val success = AccessibilityUtil.performClick(this, byText)
            Log.d(TAG, "clickMessageTab: by text, click=$success")
            AccessibilityUtil.safeRecycle(byText)
            return success
        }
        return false
    }

    private fun clickTopSearchBar(root: AccessibilityNodeInfo?): Boolean {
        val byId = findNodeByIds(
            root,
            "com.tencent.mm:id/jha",
            "com.tencent.mm:id/meb",
            "com.tencent.mm:id/f8s",
            "com.tencent.mm:id/d6o",
            "com.tencent.mm:id/e6j",
            "com.tencent.mm:id/hbz",
            "com.tencent.mm:id/ibp"
        )
        if (byId != null) {
            val success = AccessibilityUtil.performClick(this, byId)
            Log.d(TAG, "clickTopSearchBar: by resource-id node=${AccessibilityUtil.summarizeNode(byId)}, click=$success")
            AccessibilityUtil.safeRecycle(byId)
            if (success) return true
        }
        for (hint in listOf("搜索", "Search", "搜索联系人")) {
            val byText = AccessibilityUtil.findBestTextNode(root, hint, exactMatch = false, preferBottom = false, excludeEditable = false)
            if (byText != null) {
                val success = AccessibilityUtil.performClick(this, byText)
                Log.d(TAG, "clickTopSearchBar: by text='$hint' node=${AccessibilityUtil.summarizeNode(byText)}, click=$success")
                AccessibilityUtil.safeRecycle(byText)
                if (success) return true
            }
        }
        val bounds = Rect()
        root?.getBoundsInScreen(bounds)
        if (!bounds.isEmpty) {
            val fallbackX = bounds.left + bounds.width() * 0.82f
            val fallbackY = bounds.top + bounds.height() * 0.075f
            val success = AccessibilityUtil.clickByCoordinate(this, fallbackX, fallbackY)
            Log.d(TAG, "clickTopSearchBar: by coordinate x=$fallbackX y=$fallbackY click=$success")
            if (success) return true
        }
        return false
    }


    private fun clickSearchCancel(root: AccessibilityNodeInfo?): Boolean {
        val node = AccessibilityUtil.findBestTextNode(root, "取消", exactMatch = true, preferBottom = false, excludeEditable = false)
            ?: return false
        val success = AccessibilityUtil.performClick(this, node)
        AccessibilityUtil.safeRecycle(node)
        return success
    }

    private fun clickVideoCallSheetCancel(root: AccessibilityNodeInfo?): Boolean {
        if (!isVideoCallSheetVisible(root)) {
            return false
        }
        val node = AccessibilityUtil.findBestTextNode(root, "取消", exactMatch = true, preferBottom = true, excludeEditable = false)
            ?: return false
        val success = AccessibilityUtil.performClick(this, node)
        AccessibilityUtil.safeRecycle(node)
        return success
    }

    private fun clickKnownDialogClose(root: AccessibilityNodeInfo?): Boolean {
        val closeTexts = listOf("关闭", "我知道了", "稍后再说", "以后再说", "暂不")
        for (text in closeTexts) {
            val node = AccessibilityUtil.findBestTextNode(root, text, exactMatch = true, preferBottom = true, excludeEditable = false)
                ?: continue
            val success = AccessibilityUtil.performClick(this, node)
            AccessibilityUtil.safeRecycle(node)
            if (success) {
                return true
            }
        }
        return false
    }

    private fun clickMoreButton(root: AccessibilityNodeInfo?): Boolean {
        val byId = findNodeByIds(
            root,
            "com.tencent.mm:id/bjz",
            "com.tencent.mm:id/j7s",
            "com.tencent.mm:id/more_options",
            "com.tencent.mm:id/b9s",
            "com.tencent.mm:id/aqy"
        )
        if (byId != null) {
            val success = AccessibilityUtil.performClick(this, byId)
            Log.d(TAG, "clickMoreButton: by resource-id, click=$success")
            AccessibilityUtil.safeRecycle(byId)
            if (success) return true
        }
        for (hint in listOf("更多", "更多功能")) {
            val byDesc = AccessibilityUtil.findBestTextNode(root, hint, exactMatch = false, preferBottom = false)
            if (byDesc != null) {
                val success = AccessibilityUtil.performClick(this, byDesc)
                Log.d(TAG, "clickMoreButton: by desc='$hint', click=$success")
                AccessibilityUtil.safeRecycle(byDesc)
                if (success) return true
            }
        }
        val byPlus = AccessibilityUtil.findBestTextNode(root, "+", exactMatch = true, preferBottom = false)
        if (byPlus != null) {
            val success = AccessibilityUtil.performClick(this, byPlus)
            Log.d(TAG, "clickMoreButton: by text plus, click=$success")
            AccessibilityUtil.safeRecycle(byPlus)
            if (success) return true
        }
        val bounds = Rect()
        root?.getBoundsInScreen(bounds)
        if (!bounds.isEmpty) {
            val fallbackX = bounds.left + bounds.width() * 0.93f
            val fallbackY = bounds.bottom - bounds.height() * 0.045f
            val success = AccessibilityUtil.clickByCoordinate(this, fallbackX, fallbackY)
            Log.d(TAG, "clickMoreButton: by coordinate x=$fallbackX y=$fallbackY click=$success")
            if (success) return true
        }
        return false
    }

    private fun fillSearchInput(root: AccessibilityNodeInfo?, contactName: String): Boolean {
        val byId = findNodeByIds(root, "com.tencent.mm:id/d98")
        if (byId != null) {
            val ok = AccessibilityUtil.setText(byId, contactName)
            AccessibilityUtil.safeRecycle(byId)
            if (ok && verifySearchInputFilled(root, contactName)) return true
        }
        val editableNode = AccessibilityUtil.findFirstEditableNode(root) ?: return false
        val ok = AccessibilityUtil.setText(editableNode, contactName)
        AccessibilityUtil.safeRecycle(editableNode)
        if (!ok) return false
        return verifySearchInputFilled(root, contactName)
    }

    private fun verifySearchInputFilled(root: AccessibilityNodeInfo?, contactName: String): Boolean {
        val editNode = AccessibilityUtil.findFirstEditableNode(root) ?: return true
        val current = editNode.text?.toString().orEmpty()
        AccessibilityUtil.safeRecycle(editNode)
        return current.contains(contactName) || current.isNotEmpty()
    }

    private fun clickContactResult(root: AccessibilityNodeInfo?, contactName: String): Boolean {
        val byText = findNodeByExactText(root, contactName, "com.tencent.mm:id/odf", "com.tencent.mm:id/kbq")
            ?: AccessibilityUtil.findBestTextNode(root, contactName, exactMatch = true, preferBottom = false)
        if (byText != null) {
            val success = AccessibilityUtil.performClick(this, byText)
            Log.d(TAG, "clickContactResult: by text node=${AccessibilityUtil.summarizeNode(byText)}, click=$success")
            AccessibilityUtil.safeRecycle(byText)
            if (success) return true
        }

        val candidates = AccessibilityUtil.findAllById(root, "com.tencent.mm:id/odf")
        val target = candidates.firstOrNull { node ->
            val t = node.text?.toString() ?: ""
            val d = node.contentDescription?.toString() ?: ""
            t == contactName || d == contactName
        }
        candidates.forEach { if (it !== target) AccessibilityUtil.safeRecycle(it) }
        if (target == null) return false

        val success = AccessibilityUtil.performClick(this, target)
        Log.d(TAG, "clickContactResult: by id+verify node=${AccessibilityUtil.summarizeNode(target)}, click=$success")
        AccessibilityUtil.safeRecycle(target)
        return success
    }


    private fun clickVideoCallEntry(root: AccessibilityNodeInfo?): Boolean {
        val node = AccessibilityUtil.findBestTextNode(root, "音视频通话", exactMatch = true, preferBottom = false)
            ?: return false
        val success = AccessibilityUtil.performClick(this, node)
        AccessibilityUtil.safeRecycle(node)
        return success
    }

    private fun clickVideoCallOption(root: AccessibilityNodeInfo?): Boolean {
        val node = AccessibilityUtil.findBestTextNode(root, "视频通话", exactMatch = true, preferBottom = true)
            ?: return false
        val success = AccessibilityUtil.performClick(this, node)
        AccessibilityUtil.safeRecycle(node)
        return success
    }

    private fun clickVideoCallSheetOption(root: AccessibilityNodeInfo?): Boolean {
        if (!isVideoCallSheetVisible(root)) return false
        val node = AccessibilityUtil.findBestTextNode(root, "视频通话", exactMatch = true, preferBottom = false)
            ?: return false
        val success = AccessibilityUtil.performClick(this, node)
        AccessibilityUtil.safeRecycle(node)
        return success
    }

    private fun isVideoCallSheetVisible(root: AccessibilityNodeInfo?): Boolean {
        val snapshot = snapshotOf(root)
        if (snapshot != null) {
            return WeChatUiSnapshotAnalyzer.isVideoCallSheetVisible(snapshot)
        }
        return hasExactText(root, "视频通话") &&
            hasExactText(root, "语音通话") &&
            hasExactText(root, "取消")
    }


    private fun hasExactText(root: AccessibilityNodeInfo?, text: String): Boolean {
        val node = AccessibilityUtil.findBestTextNode(
            root,
            text,
            exactMatch = true,
            preferBottom = false,
            excludeEditable = false
        )
        if (node != null) {
            AccessibilityUtil.safeRecycle(node)
            return true
        }
        return false
    }

    private fun hasContainingText(root: AccessibilityNodeInfo?, text: String): Boolean {
        val node = AccessibilityUtil.findBestTextNode(
            root,
            text,
            exactMatch = false,
            preferBottom = false,
            excludeEditable = false
        )
        if (node != null) {
            AccessibilityUtil.safeRecycle(node)
            return true
        }
        return false
    }

    private fun hasNoSearchResult(root: AccessibilityNodeInfo?): Boolean {
        return snapshotOf(root)?.let(WeChatUiSnapshotAnalyzer::hasNoSearchResult) ?: false
    }


    private data class VideoCallSession(
        val requestId: String,
        val contactName: String,
        var step: Step,
        var stepStartedAt: Long,
        val startedAt: Long,
        var searchTextApplied: Boolean = false,
        var launcherPrepared: Boolean = false,
        var moreButtonClickedAt: Long = 0L,
        var lastAnnouncedMessage: String? = null,
        var lastDetectedPage: WeChatPage? = null,
        var stateOverride: AutomationState? = null,
        val actionAttempts: MutableMap<String, Int> = mutableMapOf(),
        val stepHistory: ArrayDeque<Step> = ArrayDeque(),
        val stepFailCount: MutableMap<Step, Int> = mutableMapOf(),
        val aiAssistKeys: MutableSet<String> = mutableSetOf(),
        var aiAssistRequests: Int = 0,
        var aiAssistInFlight: Boolean = false,
        var aiGuardPendingKey: String? = null,
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
