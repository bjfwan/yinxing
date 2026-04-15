package com.google.android.accessibility.selecttospeak

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.bajianfeng.launcher.automation.wechat.manager.TimeoutManager
import com.bajianfeng.launcher.automation.wechat.util.AccessibilityUtil
import com.bajianfeng.launcher.common.ui.FloatingStatusView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SelectToSpeakService : AccessibilityService() {

    companion object {
        @Volatile
        private var instance: SelectToSpeakService? = null

        fun getInstance(): SelectToSpeakService? = instance

        const val ACTION_START_VIDEO_CALL = "com.bajianfeng.launcher.START_VIDEO_CALL"
        const val EXTRA_CONTACT_NAME = "contact_name"

        private const val TAG = "WeChatAutoService"
        private const val WECHAT_PACKAGE = "com.tencent.mm"

        // 微信已知 Activity className，来自 WeChatHelper 开源项目
        private const val CLASS_LAUNCHER_UI = "com.tencent.mm.ui.LauncherUI"       // 主页（消息/通讻录/发现/我）
        private const val CLASS_CHATTING_UI = "com.tencent.mm.ui.chatting.ChattingUI" // 聊天页
        private const val CLASS_CONTACT_INFO = "com.tencent.mm.plugin.profile.ui.ContactInfoUI" // 联系人详情
        private const val CLASS_SEARCH_UI = "com.tencent.mm.plugin.fts.ui.FTSMainUI"
        private val KNOWN_WECHAT_UI_CLASSES = setOf(
            CLASS_LAUNCHER_UI,
            CLASS_CHATTING_UI,
            CLASS_CONTACT_INFO,
            CLASS_SEARCH_UI
        )
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var processJob: Job? = null
    private var timeoutJob: Job? = null
    private var wechatWaitJob: Job? = null  // 专门用于轮询等待微信前台，与 processJob 独立
    private var stateCallback: ((String, Boolean) -> Unit)? = null
    private lateinit var timeoutManager: TimeoutManager
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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val session = currentSession ?: return
        val pkg = event?.packageName?.toString()
        val className = event?.className?.toString()

        if (pkg != WECHAT_PACKAGE) {
            return
        }
        if (!className.isNullOrBlank()) {
            lastWeChatClassName = className
        }

        updateCachedWeChatRoot(event.source)

        // 根据微信页面 className 精准触发，减少无效处理
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                Log.d(TAG, "onEvent: STATE_CHANGED className=$className step=${session.step}")
                when (className) {
                    CLASS_LAUNCHER_UI -> {
                        // 微信主页加载完成
                        if (session.step == Step.WAITING_HOME) {
                            Log.d(TAG, "onEvent: LauncherUI -> WAITING_LAUNCHER_UI")
                            wechatWaitJob?.cancel()  // 停止 waitLoop，避免重复推进
                            transitionTo(session, Step.WAITING_LAUNCHER_UI, "正在查找联系人")
                        } else {
                            scheduleProcess(session, 100L)
                        }
                    }
                    CLASS_CHATTING_UI -> {
                        // 聊天页打开
                        if (session.step == Step.WAITING_CONTACT_DETAIL ||
                            session.step == Step.WAITING_LAUNCHER_UI
                        ) {
                            session.step = Step.WAITING_CONTACT_DETAIL
                            session.stepStartedAt = System.currentTimeMillis()
                            armTimeout(Step.WAITING_CONTACT_DETAIL, timeoutManager.getTimeout("chat"), "打开联系人失败")
                            scheduleProcess(session, 300L)
                        }
                    }
                    CLASS_CONTACT_INFO -> {
                        if (session.step == Step.WAITING_CONTACT_DETAIL) {
                            scheduleProcess(session, 200L)
                        }
                    }
                    CLASS_SEARCH_UI -> {
                        if (session.step == Step.WAITING_SEARCH_FALLBACK ||
                            session.step == Step.WAITING_CONTACT_RESULT
                        ) {
                            scheduleProcess(session, 150L)
                        }
                    }
                    else -> scheduleProcess(session, 150L)
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> scheduleProcess(session, 80L)
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
        stateCallback = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_VIDEO_CALL) {
            intent.getStringExtra(EXTRA_CONTACT_NAME)?.let { contactName ->
                startVideoCall(contactName)
            }
        }
        return START_STICKY
    }

    fun setStateCallback(callback: (String, Boolean) -> Unit) {
        stateCallback = callback
    }

    fun clearStateCallback() {
        stateCallback = null
    }

    fun requestVideoCall(contactName: String) {
        startVideoCall(contactName)
    }

    private fun notifyState(state: String, success: Boolean) {
        stateCallback?.invoke(state, success)
    }

    private fun startVideoCall(contactName: String) {
        cancelSession(false)
        lastMissingRootLogAt = 0L
        lastWeChatClassName = null
        val session = VideoCallSession(
            contactName = contactName,
            step = Step.WAITING_HOME,
            stepStartedAt = System.currentTimeMillis()
        )
        currentSession = session

        floatingView?.show("正在打开微信")
        notifyState("正在打开微信", true)

        if (!launchWeChat()) {
            failAndHide("打开微信失败")
            return
        }

        armTimeout(Step.WAITING_HOME, timeoutManager.getTimeout("launch"), "微信启动超时")
        // 启动独立轮询：每 400ms 检查一次微信窗口是否就绪，不受 processJob 重置干扰
        startWeChatWaitLoop(session)
    }

    /**
     * 独立的微信等待循环：等待 onAccessibilityEvent 缓存到有效的微信 root。
     * onAccessibilityEvent 收到微信的 LauncherUI 事件时会直接推进 step，
     * waitLoop 只做保底——每 500ms 检查缓存，如果已有 root 且确认是主页就推进。
     */
    private fun startWeChatWaitLoop(session: VideoCallSession) {
        wechatWaitJob?.cancel()
        wechatWaitJob = serviceScope.launch {
            var attempts = 0
            while (currentSession === session && session.step == Step.WAITING_HOME) {
                delay(500L)
                attempts++
                val root = getWeChatRoot()
                if (root == null) {
                    Log.d(TAG, "waitLoop[$attempts]: 尚未收到微信事件，等待中")
                    continue
                }
                val loaded = isLauncherReady(root, root.className?.toString())
                Log.d(TAG, "waitLoop[$attempts]: root class=${root.className} childCount=${root.childCount} loaded=$loaded")
                if (loaded) {
                    Log.d(TAG, "waitLoop: 主页确认加载完成，推进步骤")
                    wechatWaitJob = null
                    transitionTo(session, Step.WAITING_LAUNCHER_UI, "正在查找联系人")
                    break
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

    private fun isLauncherReady(root: AccessibilityNodeInfo, currentClass: String?): Boolean {
        if (currentClass == CLASS_LAUNCHER_UI && root.childCount > 0) {
            return true
        }
        val tabs = listOf("微信", "通讻录", "发现", "我")
        val matched = tabs.count { root.findAccessibilityNodeInfosByText(it).isNotEmpty() }
        return matched >= 2
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
            scheduleProcess(session, 500L)
            return
        }

        val currentClass = root.className?.toString()
        Log.d(TAG, "processCurrentWindow: step=${session.step} class=$currentClass")

        when (session.step) {
            Step.WAITING_HOME -> handleWaitingHome(session, root, currentClass)
            Step.WAITING_LAUNCHER_UI -> handleLauncherUI(session, root)
            Step.WAITING_SEARCH_FALLBACK -> handleSearchFallback(session, root)
            Step.WAITING_CONTACT_RESULT -> handleContactResult(session, root)
            Step.WAITING_CONTACT_DETAIL -> handleContactDetail(session, root)
            Step.WAITING_VIDEO_OPTIONS -> handleVideoOptions(session, root)
        }
    }

    // ── 步骤处理 ──────────────────────────────────────────────────────────────

    private fun handleWaitingHome(session: VideoCallSession, root: AccessibilityNodeInfo, currentClass: String?) {
        when (currentClass) {
            CLASS_CHATTING_UI -> {
                Log.d(TAG, "WAITING_HOME: 当前是聊天页，返回主页")
                performGlobalAction(GLOBAL_ACTION_BACK)
                scheduleProcess(session, 500L)
            }
            else -> {
                if (isLauncherReady(root, currentClass)) {
                    Log.d(TAG, "WAITING_HOME -> WAITING_LAUNCHER_UI (class=$currentClass)")
                    transitionTo(session, Step.WAITING_LAUNCHER_UI, "正在查找联系人")
                } else {
                    Log.d(TAG, "WAITING_HOME: 主页未就绪 class=$currentClass childCount=${root.childCount}")
                    scheduleProcess(session, 300L)
                }
            }
        }
    }

    private fun handleLauncherUI(session: VideoCallSession, root: AccessibilityNodeInfo) {
        if (!isLauncherReady(root, root.className?.toString())) {
            Log.d(TAG, "WAITING_LAUNCHER_UI: 当前还不是主页，继续等待")
            scheduleProcess(session, 200L)
            return
        }

        if (!session.launcherPrepared) {
            session.launcherPrepared = true
            val tabClicked = clickMessageTab(root)
            Log.d(TAG, "WAITING_LAUNCHER_UI: clickMessageTab=$tabClicked")
            scheduleProcess(session, if (tabClicked) 250L else 150L)
            return
        }

        val contactNode = AccessibilityUtil.findBestTextNode(
            root, session.contactName, exactMatch = true, preferBottom = false
        )
        if (contactNode != null) {
            val success = AccessibilityUtil.performClick(this, contactNode)
            Log.d(TAG, "WAITING_LAUNCHER_UI: 消息列表找到联系人=${session.contactName}, click=$success")
            AccessibilityUtil.safeRecycle(contactNode)
            if (success) {
                transitionTo(session, Step.WAITING_CONTACT_DETAIL, "正在打开聊天")
                return
            }
        }

        Log.d(TAG, "WAITING_LAUNCHER_UI: 消息列表未找到联系人，切换到搜索路径")
        notifyState("消息列表未找到，正在搜索联系人", true)
        floatingView?.updateMessage("正在搜索联系人")

        val searchClicked = clickTopSearchBar(root)
        Log.d(TAG, "WAITING_LAUNCHER_UI: clickTopSearchBar=$searchClicked")
        if (searchClicked) {
            transitionTo(session, Step.WAITING_SEARCH_FALLBACK, "正在搜索联系人")
        } else {
            scheduleProcess(session, 300L)
        }
    }

    private fun handleSearchFallback(session: VideoCallSession, root: AccessibilityNodeInfo) {
        if (!session.searchTextApplied) {
            val filled = fillSearchInput(root, session.contactName)
            Log.d(TAG, "WAITING_SEARCH_FALLBACK: fillSearchInput=$filled, contact=${session.contactName}")
            if (!filled) {
                scheduleProcess(session, 150L)
                return
            }
            session.searchTextApplied = true
            armTimeout(Step.WAITING_SEARCH_FALLBACK, timeoutManager.getTimeout("search"), "查找联系人超时")
            scheduleProcess(session, 300L)
            return
        }

        handleContactResult(session, root)
    }

    private fun handleContactResult(session: VideoCallSession, root: AccessibilityNodeInfo) {
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
        scheduleProcess(session, 250L)
    }

    private fun handleContactDetail(session: VideoCallSession, root: AccessibilityNodeInfo) {
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
            if (elapsed < 700L) {
                Log.d(TAG, "WAITING_CONTACT_DETAIL: 等待更多菜单展开 elapsed=${elapsed}ms")
                scheduleProcess(session, 220L)
                return
            }
        }

        val moreClicked = clickMoreButton(root)
        Log.d(TAG, "WAITING_CONTACT_DETAIL: clickMoreButton=$moreClicked")
        if (moreClicked) {
            session.moreButtonClickedAt = now
            scheduleProcess(session, 420L)
            return
        }
        scheduleProcess(session, 250L)
    }

    private fun handleVideoOptions(session: VideoCallSession, root: AccessibilityNodeInfo) {
        val elapsed = System.currentTimeMillis() - session.stepStartedAt
        val sheetClicked = clickVideoCallSheetOption(root)
        Log.d(TAG, "WAITING_VIDEO_OPTIONS: clickVideoCallSheetOption=$sheetClicked")
        if (sheetClicked) {
            finishVideoCallStarted(session)
            return
        }
        if (elapsed < 900L) {
            Log.d(TAG, "WAITING_VIDEO_OPTIONS: 等待弹窗稳定 elapsed=${elapsed}ms")
            scheduleProcess(session, 200L)
            return
        }
        val clicked = clickVideoCallOption(root)
        Log.d(TAG, "WAITING_VIDEO_OPTIONS: clickVideoCallOption(fallback)=$clicked")
        if (clicked) {
            finishVideoCallStarted(session)
            return
        }
        scheduleProcess(session, 250L)
    }

    // ── 状态机辅助 ────────────────────────────────────────────────────────────

    private fun transitionTo(session: VideoCallSession, nextStep: Step, message: String) {
        recordStepSuccess(session.step, System.currentTimeMillis() - session.stepStartedAt)
        session.step = nextStep
        session.stepStartedAt = System.currentTimeMillis()
        session.moreButtonClickedAt = 0L
        notifyState(message, true)
        floatingView?.updateMessage(message)
        armTimeout(nextStep, timeoutFor(nextStep), failureMessageFor(nextStep, session.contactName))
        scheduleProcess(session, 120L)
    }

    private fun finishVideoCallStarted(session: VideoCallSession) {
        recordStepSuccess(session.step, System.currentTimeMillis() - session.stepStartedAt)
        session.moreButtonClickedAt = 0L
        floatingView?.updateMessage("视频通话已发起")
        notifyState("视频通话已发起", true)
        currentSession = null
        timeoutJob?.cancel()
        processJob?.cancel()
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
            Step.WAITING_HOME -> "微信启动超时"
            Step.WAITING_LAUNCHER_UI -> {
                if (lastWeChatClassName == CLASS_LAUNCHER_UI) {
                    "微信首页未暴露可操作控件"
                } else {
                    "查找联系人入口失败"
                }
            }
            Step.WAITING_SEARCH_FALLBACK,
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

    private fun cancelSession(notifyFailure: Boolean) {
        processJob?.cancel()
        timeoutJob?.cancel()
        wechatWaitJob?.cancel()
        processJob = null
        timeoutJob = null
        wechatWaitJob = null
        lastMissingRootLogAt = 0L
        lastWeChatClassName = null
        cachedWeChatRoot?.recycle()
        cachedWeChatRoot = null
        currentSession = null
        floatingView?.hide()
        if (notifyFailure) {
            notifyState("操作已取消", false)
        }
    }

    private fun failAndHide(message: String, root: AccessibilityNodeInfo? = getWeChatRoot()) {
        logErrorLong(buildFailureDiagnostics(message, root))
        cancelSession(false)
        notifyState(message, false)
    }

    private fun buildFailureDiagnostics(message: String, root: AccessibilityNodeInfo?): String {
        val session = currentSession
        return buildString {
            append("failure=").append(message)
            if (session != null) {
                append("\nstep=").append(session.step)
                append(", contact=").append(session.contactName)
                append(", stepStartedAt=").append(session.stepStartedAt)
                append(", now=").append(System.currentTimeMillis())
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
            "com.tencent.mm:id/f8s",
            "com.tencent.mm:id/d6o"
        )
        if (byId != null) {
            val success = AccessibilityUtil.performClick(this, byId)
            Log.d(TAG, "clickTopSearchBar: by resource-id, click=$success")
            AccessibilityUtil.safeRecycle(byId)
            if (success) return true
        }
        val byText = AccessibilityUtil.findBestTextNode(root, "搜索", exactMatch = false, preferBottom = false)
        if (byText != null) {
            val success = AccessibilityUtil.performClick(this, byText)
            Log.d(TAG, "clickTopSearchBar: by text, click=$success")
            AccessibilityUtil.safeRecycle(byText)
            return success
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
        val node = AccessibilityUtil.findBestTextNode(root, contactName, exactMatch = true, preferBottom = false)
        if (node != null) {
            val success = AccessibilityUtil.performClick(this, node)
            AccessibilityUtil.safeRecycle(node)
            if (success) {
                return true
            }
        }
        val byId = findNodeByIds(root, "com.tencent.mm:id/odf")
        if (byId != null) {
            val success = AccessibilityUtil.performClick(this, byId)
            Log.d(TAG, "clickContactResult: by resource-id, click=$success")
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
        return hasExactText(root, "视频通话") &&
            hasExactText(root, "语音通话") &&
            hasExactText(root, "取消")
    }

    private fun hasExactText(root: AccessibilityNodeInfo?, text: String): Boolean {
        val node = AccessibilityUtil.findBestTextNode(root, text, exactMatch = true, preferBottom = false, excludeEditable = false)
        if (node != null) {
            AccessibilityUtil.safeRecycle(node)
            return true
        }
        return false
    }

    private fun hasNoSearchResult(root: AccessibilityNodeInfo?): Boolean {
        val candidates = listOf("无搜索结果", "没有找到", "无结果")
        for (text in candidates) {
            val node = AccessibilityUtil.findBestTextNode(
                root, text, exactMatch = false, preferBottom = false, excludeEditable = false
            )
            if (node != null) {
                AccessibilityUtil.safeRecycle(node)
                return true
            }
        }
        return false
    }

    // ── 数据类 & 枚举 ─────────────────────────────────────────────────────────

    private data class VideoCallSession(
        val contactName: String,
        var step: Step,
        var stepStartedAt: Long,
        var searchTextApplied: Boolean = false,
        var launcherPrepared: Boolean = false,
        var moreButtonClickedAt: Long = 0L
    )

    private enum class Step {
        WAITING_HOME,
        WAITING_LAUNCHER_UI,
        WAITING_SEARCH_FALLBACK,
        WAITING_CONTACT_RESULT,
        WAITING_CONTACT_DETAIL,
        WAITING_VIDEO_OPTIONS
    }
}
