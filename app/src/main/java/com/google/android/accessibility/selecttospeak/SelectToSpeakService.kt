package com.google.android.accessibility.selecttospeak

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.bajianfeng.launcher.automation.wechat.manager.StateDetectionManager
import com.bajianfeng.launcher.automation.wechat.manager.TimeoutManager
import com.bajianfeng.launcher.automation.wechat.model.AutomationState
import com.bajianfeng.launcher.automation.wechat.util.AccessibilityUtil
import com.bajianfeng.launcher.common.ui.FloatingStatusView

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

        const val ACTION_START_VIDEO_CALL = "com.bajianfeng.launcher.START_VIDEO_CALL"
        const val EXTRA_CONTACT_NAME = "contact_name"

        private const val TAG = "WeChatAutoService"
        private const val WECHAT_PACKAGE = "com.tencent.mm"

        private const val MAX_HOME_BACK_ATTEMPTS = 6
        private const val MAX_UNKNOWN_HOME_OBSERVE_ATTEMPTS = 2
        private const val MAX_SEARCH_ENTRY_ATTEMPTS = 3
        private const val MAX_SEARCH_OPEN_ATTEMPTS = 3
        private const val MAX_SEARCH_INPUT_ATTEMPTS = 3

        private const val MAX_CONTACT_DETAIL_ATTEMPTS = 4
        private const val MAX_VIDEO_OPTION_ATTEMPTS = 3
        private const val MAX_STEP_RECOVERY_ATTEMPTS = 5
        private const val HOME_ACTION_SETTLE_DELAY_MS = 500L


        // 微信已知 Activity className，来自 WeChatHelper 开源项目
        private const val CLASS_LAUNCHER_UI = "com.tencent.mm.ui.LauncherUI"       // 主页（消息/通讯录/发现/我）
        private const val CLASS_CHATTING_UI = "com.tencent.mm.ui.chatting.ChattingUI" // 聊天页
        private const val CLASS_CONTACT_INFO = "com.tencent.mm.plugin.profile.ui.ContactInfoUI" // 联系人详情
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
    private var timeoutJob: Job? = null
    private var totalTimeoutJob: Job? = null
    private var wechatWaitJob: Job? = null  // 专门用于轮询等待微信前台，与 processJob 独立
    private lateinit var timeoutManager: TimeoutManager
    private val stateDetectionManager = StateDetectionManager()
    private var floatingView: FloatingStatusView? = null
    private var currentSession: VideoCallSession? = null

    private var lastMissingRootLogAt = 0L
    private var lastWeChatClassName: String? = null

    /**
     * 由 onAccessibilityEvent 从 event.source 向上找到的微信 root 节点缓存。
     * rootInActiveWindow 在某些设备上是空壳，用这个替代。
     */
    private var cachedWeChatRoot: AccessibilityNodeInfo? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        timeoutManager = TimeoutManager.getInstance(this)
        floatingView = FloatingStatusView(this)
        consumePendingRequest()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val session = currentSession ?: return
        val pkg = event?.packageName?.toString()
        val className = event?.className?.toString()

        if (pkg != WECHAT_PACKAGE) {
            return
        }
        if (isMeaningfulWeChatClassName(className)) {
            lastWeChatClassName = className
        }

        updateCachedWeChatRoot(event.source)

        // 根据微信页面 className 精准触发，减少无效处理
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
        val session = VideoCallSession(
            requestId = requestId,
            contactName = contactName,
            step = Step.WAITING_HOME,
            stepStartedAt = System.currentTimeMillis(),
            startedAt = System.currentTimeMillis()
        )
        currentSession = session
        session.stateOverride = AutomationState.LAUNCHING_WECHAT

        floatingView?.show("正在打开微信", automationStateLabel(AutomationState.LAUNCHING_WECHAT))
        updateProgress(session, "正在打开微信")


        if (!launchWeChat()) {
            failAndHide("打开微信失败")
            return
        }

        armTotalTimeout(timeoutManager.getTimeout("total"), "微信视频流程整体超时")
        armTimeout(Step.WAITING_HOME, timeoutManager.getTimeout("launch"), "微信启动或返回首页超时")
        startWeChatWaitLoop(session)
    }

    /**

     * 独立的微信等待循环：等待 onAccessibilityEvent 缓存到有效的微信 root。
     * waitLoop 除了确认首页已就绪，也会在“已进微信但不在首页”时主动触发归一化处理。
     */
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
        val cached = cachedWeChatRoot
        if (cached != null && isUsableWeChatRoot(cached)) {
            Log.d(TAG, "getWeChatRoot: 使用缓存 class=${cached.className} childCount=${cached.childCount}")
            return cached
        }
        replaceCachedWeChatRoot(null)
        val root = findWeChatRootInWindows()
        if (root != null) {
            replaceCachedWeChatRoot(AccessibilityNodeInfo.obtain(root))
            Log.d(TAG, "getWeChatRoot: 使用窗口 class=${root.className} childCount=${root.childCount}")
            return root
        }
        Log.d(TAG, "getWeChatRoot: 未找到有效微信窗口")
        return null
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
        val detected = stateDetectionManager.detect(root, currentClass)?.toWeChatPage()
        val page = detected ?: detectWeChatPageLegacy(root, currentClass)
        session?.lastDetectedPage = page
        return page
    }

    private fun StateDetectionManager.DetectedPage.toWeChatPage(): WeChatPage = when (this) {
        StateDetectionManager.DetectedPage.HOME -> WeChatPage.HOME
        StateDetectionManager.DetectedPage.SEARCH -> WeChatPage.SEARCH
        StateDetectionManager.DetectedPage.CHAT -> WeChatPage.CHAT
        StateDetectionManager.DetectedPage.CONTACT_DETAIL -> WeChatPage.CONTACT_DETAIL
        StateDetectionManager.DetectedPage.UNKNOWN -> WeChatPage.UNKNOWN
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
        floatingView?.updateMessage(
            message,
            automationStateLabel(session.toProgressState(success = true, terminal = false))
        )
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
        // 设备档位乘数：LOW 档给微信更多渲染时间，HIGH 档可适当收紧，MID 保持原值
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

        val currentClass = resolveCurrentWeChatClass(root)
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

    private fun tryDismissTransientUi(session: VideoCallSession, root: AccessibilityNodeInfo?): Boolean {
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
        val message = when (action) {
            WeChatDismissAction.SEARCH_CANCEL -> "正在关闭搜索"
            WeChatDismissAction.SHEET_CANCEL -> "正在关闭弹窗"
            WeChatDismissAction.CLOSE_DIALOG -> "正在关闭提示"
            WeChatDismissAction.NONE -> "正在恢复页面"
        }
        updateProgress(session, message)
        scheduleAdaptiveProcess(session, DelayProfile.RECOVER)
        return true
    }

    private fun recoverToHome(
        session: VideoCallSession,
        root: AccessibilityNodeInfo,
        currentClass: String?,
        reason: String
    ) {
        session.stateOverride = AutomationState.RECOVERING
        updateProgress(session, "正在返回微信首页")
        if (tryDismissTransientUi(session, root)) {
            return
        }
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


    // ── 步骤处理 ──────────────────────────────────────────────────────────────

    private fun handleWaitingHome(session: VideoCallSession, root: AccessibilityNodeInfo, currentClass: String?) {
        when (detectWeChatPage(root, currentClass)) {
            WeChatPage.HOME -> {
                Log.d(TAG, "WAITING_HOME -> WAITING_LAUNCHER_UI (class=$currentClass)")
                session.launcherPrepared = false
                session.searchTextApplied = false
                transitionTo(session, Step.WAITING_LAUNCHER_UI, "正在查找联系人")
            }
            WeChatPage.CHAT,
            WeChatPage.CONTACT_DETAIL -> {
                if (isTargetConversationPage(root, currentClass, session.contactName)) {
                    Log.d(TAG, "WAITING_HOME: 已在目标联系人页，直接发起视频")
                    transitionTo(session, Step.WAITING_CONTACT_DETAIL, "已进入目标联系人，正在发起视频")
                    return
                }
                recoverToHome(
                    session = session,
                    root = root,
                    currentClass = currentClass,
                    reason = "WAITING_HOME: 当前处于非目标联系人页"
                )
            }
            WeChatPage.SEARCH -> {
                recoverToHome(
                    session = session,
                    root = root,
                    currentClass = currentClass,
                    reason = "WAITING_HOME: 当前在搜索页"
                )
            }
            WeChatPage.UNKNOWN -> {
                val observeAttempt = incrementActionAttempt(session, "home_observe")
                Log.d(
                    TAG,
                    "WAITING_HOME: 未识别页面 class=$currentClass observeAttempt=$observeAttempt childCount=${root.childCount}"
                )
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
            Log.d(TAG, "WAITING_LAUNCHER_UI: 搜索页已打开，切换到搜索阶段")
            session.launcherPrepared = false
            transitionTo(session, Step.WAITING_SEARCH_FALLBACK, "正在打开搜索")
            return
        }

        if (page != WeChatPage.HOME) {
            Log.d(TAG, "WAITING_LAUNCHER_UI: 当前页面=$page，重新归一化到首页")
            session.launcherPrepared = false
            session.searchTextApplied = false
            rerouteTo(session, Step.WAITING_HOME, "正在返回微信首页")
            return
        }


        if (!session.launcherPrepared) {
            session.launcherPrepared = true
            val tabClicked = clickMessageTab(root)
            Log.d(TAG, "WAITING_LAUNCHER_UI: clickMessageTab=$tabClicked")
            scheduleAdaptiveProcess(
                session,
                if (tabClicked) DelayProfile.TRANSITION else DelayProfile.STABLE
            )
            return
        }


        val contactNode = findNodeByExactText(root, session.contactName, "com.tencent.mm:id/kbq")
            ?: AccessibilityUtil.findBestTextNode(
                root,
                session.contactName,
                exactMatch = true,
                preferBottom = false
            )
        if (contactNode != null) {
            val success = AccessibilityUtil.performClick(this, contactNode)
            Log.d(TAG, "WAITING_LAUNCHER_UI: 消息列表找到联系人=${session.contactName}, node=${AccessibilityUtil.summarizeNode(contactNode)}, click=$success")
            AccessibilityUtil.safeRecycle(contactNode)
            if (success) {
                transitionTo(session, Step.WAITING_CONTACT_DETAIL, "正在打开聊天")
                return
            }
        }


        updateProgress(session, "消息列表未找到，正在打开搜索")
        val searchClicked = clickTopSearchBar(root)
        Log.d(TAG, "WAITING_LAUNCHER_UI: clickTopSearchBar=$searchClicked")
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
                Log.d(TAG, "WAITING_SEARCH_FALLBACK: 搜索页未打开，仍停留在 LauncherUI")
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
                Log.d(TAG, "WAITING_SEARCH_FALLBACK: 当前页面异常 class=$currentClass，重新归一化")
                session.searchTextApplied = false
                session.launcherPrepared = false
                rerouteTo(session, Step.WAITING_HOME, "正在返回微信首页")
                return
            }
        }

        if (!session.searchTextApplied) {
            val filled = fillSearchInput(root, session.contactName)
            Log.d(TAG, "WAITING_SEARCH_FALLBACK: fillSearchInput=$filled, contact=${session.contactName}")
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
                    Log.d(TAG, "WAITING_CONTACT_RESULT: 已进入目标联系人页/聊天页，直接推进")
                    rerouteTo(session, Step.WAITING_CONTACT_DETAIL, "正在打开联系人")
                } else {
                    Log.d(TAG, "WAITING_CONTACT_RESULT: 进入了非目标联系人页，触发步骤恢复")
                    resolveAndRerouteTo(session, session.step, "WAITING_CONTACT_RESULT: nonTargetConversation")
                }

                return
            }
            WeChatPage.HOME -> {

                Log.d(TAG, "WAITING_CONTACT_RESULT: 搜索页已关闭，回到首页重新查找")
                session.searchTextApplied = false
                session.launcherPrepared = true
                rerouteTo(session, Step.WAITING_LAUNCHER_UI, "正在重新打开搜索")
                return
            }
            WeChatPage.UNKNOWN -> {
                Log.d(TAG, "WAITING_CONTACT_RESULT: 页面未知，回首页重新归一化")
                session.searchTextApplied = false
                session.launcherPrepared = false
                rerouteTo(session, Step.WAITING_HOME, "正在返回微信首页")
                return
            }
        }

        val contactClicked = clickContactResult(root, session.contactName)
        Log.d(TAG, "WAITING_CONTACT_RESULT: clickContactResult=$contactClicked")
        if (contactClicked) {
            transitionTo(session, Step.WAITING_CONTACT_DETAIL, "正在打开联系人")
            return
        }
        if (hasNoSearchResult(root)) {
            failAndHide("未找到联系人: ${session.contactName}", root)
            return
        }
        val pollCount = incrementActionAttempt(session, "contact_result_poll")
        scheduleAdaptiveProcess(session, DelayProfile.STABLE, attemptKey = "contact_result_poll", actionSucceeded = pollCount == 1)

    }

    private fun handleContactDetail(session: VideoCallSession, root: AccessibilityNodeInfo) {
        val currentClass = resolveCurrentWeChatClass(root)
        when (detectWeChatPage(root, currentClass)) {

            WeChatPage.CHAT,
            WeChatPage.CONTACT_DETAIL -> {
                if (!isTargetConversationPage(root, currentClass, session.contactName)) {
                    Log.d(TAG, "WAITING_CONTACT_DETAIL: 当前不是目标联系人页，返回首页重来")
                    session.searchTextApplied = false
                    session.launcherPrepared = false
                    rerouteTo(session, Step.WAITING_HOME, "正在返回微信首页")
                    return
                }
            }
            WeChatPage.SEARCH -> {

                Log.d(TAG, "WAITING_CONTACT_DETAIL: 仍停留在搜索页，回到结果阶段")
                rerouteTo(session, Step.WAITING_CONTACT_RESULT, "正在打开联系人")
                return
            }
            WeChatPage.HOME -> {
                Log.d(TAG, "WAITING_CONTACT_DETAIL: 已回到首页，重新查找联系人")
                session.searchTextApplied = false
                session.launcherPrepared = true
                rerouteTo(session, Step.WAITING_LAUNCHER_UI, "正在重新查找联系人")
                return
            }
            WeChatPage.UNKNOWN -> {
                Log.d(TAG, "WAITING_CONTACT_DETAIL: 页面未知，触发步骤恢复")
                resolveAndRerouteTo(session, session.step, "WAITING_CONTACT_DETAIL: unknownPage")
                return
            }

        }

        val directClicked = clickVideoCallEntry(root)
        Log.d(TAG, "WAITING_CONTACT_DETAIL: clickVideoCallEntry(direct)=$directClicked")
        if (directClicked) {
            transitionTo(session, Step.WAITING_VIDEO_OPTIONS, "正在发起视频通话")
            return
        }

        val directOptionClicked = clickVideoCallOption(root)
        Log.d(TAG, "WAITING_CONTACT_DETAIL: clickVideoCallOption(direct)=$directOptionClicked")
        if (directOptionClicked) {
            transitionTo(session, Step.WAITING_VIDEO_OPTIONS, "正在选择视频通话")
            return
        }

        val now = System.currentTimeMillis()
        if (session.moreButtonClickedAt > 0L) {
            val elapsed = now - session.moreButtonClickedAt
            val settleWindow = settleWindow(session, DelayProfile.SHEET, "contact_detail_menu_wait", minWindow = 420L)
            if (elapsed < settleWindow) {
                Log.d(TAG, "WAITING_CONTACT_DETAIL: 等待更多菜单展开 elapsed=${elapsed}ms settleWindow=${settleWindow}ms")
                scheduleAdaptiveProcess(session, DelayProfile.SHEET, attemptKey = "contact_detail_menu_wait")
                return
            }
        }

        val moreClicked = clickMoreButton(root)
        Log.d(TAG, "WAITING_CONTACT_DETAIL: clickMoreButton=$moreClicked")
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
                Log.d(TAG, "WAITING_VIDEO_OPTIONS: 意外回到搜索页，回首页重新查找")
                session.searchTextApplied = false
                session.launcherPrepared = false
                rerouteTo(session, Step.WAITING_HOME, "正在返回微信首页")
                return
            }
            WeChatPage.HOME -> {
                Log.d(TAG, "WAITING_VIDEO_OPTIONS: 意外回到首页，触发步骤恢复")
                resolveAndRerouteTo(session, session.step, "WAITING_VIDEO_OPTIONS: launcherHome")
                return
            }

            else -> Unit
        }

        val elapsed = System.currentTimeMillis() - session.stepStartedAt
        val sheetClicked = clickVideoCallSheetOption(root)
        Log.d(TAG, "WAITING_VIDEO_OPTIONS: clickVideoCallSheetOption=$sheetClicked")
        if (sheetClicked) {
            finishVideoCallStarted(session)
            return
        }
        val settleWindow = settleWindow(session, DelayProfile.SHEET, "video_sheet_wait", minWindow = 500L)
        if (elapsed < settleWindow) {
            Log.d(TAG, "WAITING_VIDEO_OPTIONS: 等待弹窗稳定 elapsed=${elapsed}ms settleWindow=${settleWindow}ms")
            scheduleAdaptiveProcess(session, DelayProfile.SHEET, attemptKey = "video_sheet_wait")
            return
        }
        val clicked = clickVideoCallOption(root)
        Log.d(TAG, "WAITING_VIDEO_OPTIONS: clickVideoCallOption(fallback)=$clicked")
        if (clicked) {
            finishVideoCallStarted(session)
            return
        }
        if (!ensureAttemptBudget(session, "video_option", MAX_VIDEO_OPTION_ATTEMPTS, "发起视频通话失败", root)) {
            return
        }
        scheduleAdaptiveProcess(session, DelayProfile.TRANSITION, attemptKey = "video_option")


    }

    // ── 状态机辅助 ────────────────────────────────────────────────────────────

    private fun transitionTo(session: VideoCallSession, nextStep: Step, message: String) {
        recordStepSuccess(session.step, System.currentTimeMillis() - session.stepStartedAt)
        recordStepHistory(session, nextStep)
        session.step = nextStep
        session.stepStartedAt = System.currentTimeMillis()
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
        floatingView?.updateMessage("视频通话已发起")
        notifyState(session, "视频通话已发起", success = true, terminal = true)
        currentSession = null
        timeoutJob?.cancel()
        totalTimeoutJob?.cancel()
        processJob?.cancel()
        wechatWaitJob?.cancel()
        serviceScope.launch {
            delay(1200)
            floatingView?.hide()
        }
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
                failAndHide(failureMessage, getWeChatRoot())
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
        timeoutJob?.cancel()
        totalTimeoutJob?.cancel()
        wechatWaitJob?.cancel()
        processJob = null
        timeoutJob = null
        totalTimeoutJob = null
        wechatWaitJob = null
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

    // ── 微信操作 ──────────────────────────────────────────────────────────────

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
            "com.tencent.mm:id/d6o"
        )
        if (byId != null) {
            val success = AccessibilityUtil.performClick(this, byId)
            Log.d(TAG, "clickTopSearchBar: by resource-id node=${AccessibilityUtil.summarizeNode(byId)}, click=$success")
            AccessibilityUtil.safeRecycle(byId)
            if (success) return true
        }
        val byText = AccessibilityUtil.findBestTextNode(root, "搜索", exactMatch = false, preferBottom = false, excludeEditable = false)
        if (byText != null) {
            val success = AccessibilityUtil.performClick(this, byText)
            Log.d(TAG, "clickTopSearchBar: by text node=${AccessibilityUtil.summarizeNode(byText)}, click=$success")
            AccessibilityUtil.safeRecycle(byText)
            if (success) return true
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
            "com.tencent.mm:id/more_options"
        )
        if (byId != null) {
            val success = AccessibilityUtil.performClick(this, byId)
            Log.d(TAG, "clickMoreButton: by resource-id, click=$success")
            AccessibilityUtil.safeRecycle(byId)
            if (success) return true
        }
        val byDesc = AccessibilityUtil.findBestTextNode(root, "更多", exactMatch = false, preferBottom = false)
        if (byDesc != null) {
            val success = AccessibilityUtil.performClick(this, byDesc)
            Log.d(TAG, "clickMoreButton: by desc, click=$success")
            AccessibilityUtil.safeRecycle(byDesc)
            return success
        }
        val byPlus = AccessibilityUtil.findBestTextNode(root, "+", exactMatch = true, preferBottom = false)
        if (byPlus != null) {
            val success = AccessibilityUtil.performClick(this, byPlus)
            Log.d(TAG, "clickMoreButton: by text plus, click=$success")
            AccessibilityUtil.safeRecycle(byPlus)
            return success
        }
        return false
    }

    private fun fillSearchInput(root: AccessibilityNodeInfo?, contactName: String): Boolean {
        val byId = findNodeByIds(root, "com.tencent.mm:id/d98")
        if (byId != null && AccessibilityUtil.setText(byId, contactName)) {
            AccessibilityUtil.safeRecycle(byId)
            return true
        }
        AccessibilityUtil.safeRecycle(byId)
        val node = AccessibilityUtil.findFirstEditableNode(root) ?: return false
        return AccessibilityUtil.setText(node, contactName)
    }

    private fun clickContactResult(root: AccessibilityNodeInfo?, contactName: String): Boolean {
        val node = findNodeByExactText(root, contactName, "com.tencent.mm:id/odf", "com.tencent.mm:id/kbq")
            ?: AccessibilityUtil.findBestTextNode(root, contactName, exactMatch = true, preferBottom = false)
        if (node != null) {
            val success = AccessibilityUtil.performClick(this, node)
            Log.d(TAG, "clickContactResult: by text node=${AccessibilityUtil.summarizeNode(node)}, click=$success")
            AccessibilityUtil.safeRecycle(node)
            if (success) {
                return true
            }
        }
        val byId = findNodeByIds(root, "com.tencent.mm:id/odf")
        if (byId != null) {
            val success = AccessibilityUtil.performClick(this, byId)
            Log.d(TAG, "clickContactResult: by resource-id node=${AccessibilityUtil.summarizeNode(byId)}, click=$success")
            AccessibilityUtil.safeRecycle(byId)
            return success
        }
        return false
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
        if (!isVideoCallSheetVisible(root)) {
            return false
        }
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

    private fun hasNoSearchResult(root: AccessibilityNodeInfo?): Boolean {
        return snapshotOf(root)?.let(WeChatUiSnapshotAnalyzer::hasNoSearchResult) ?: false
    }


    // ── 数据类 & 枚举 ─────────────────────────────────────────────────────────

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
        val stepFailCount: MutableMap<Step, Int> = mutableMapOf()
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
