package com.bajianfeng.launcher.automation.wechat.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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

class WeChatAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var instance: WeChatAccessibilityService? = null

        fun getInstance(): WeChatAccessibilityService? = instance

        const val ACTION_START_VIDEO_CALL = "com.bajianfeng.launcher.START_VIDEO_CALL"
        const val EXTRA_CONTACT_NAME = "contact_name"

        private const val TAG = "WeChatAutoService"
        private const val WECHAT_PACKAGE = "com.tencent.mm"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var processJob: Job? = null
    private var timeoutJob: Job? = null
    private var stateCallback: ((String, Boolean) -> Unit)? = null
    private lateinit var timeoutManager: TimeoutManager
    private var floatingView: FloatingStatusView? = null
    private var currentSession: VideoCallSession? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        timeoutManager = TimeoutManager.getInstance(this)
        floatingView = FloatingStatusView(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val session = currentSession ?: return
        if (event?.packageName?.toString() != WECHAT_PACKAGE) {
            return
        }
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
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
        scheduleProcess(session, 300L)
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

    private fun processCurrentWindow() {
        val session = currentSession ?: return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != WECHAT_PACKAGE) {
            return
        }

        when (session.step) {
            Step.WAITING_HOME -> {
                if (clickContactsTab(root)) {
                    transitionTo(
                        session = session,
                        nextStep = Step.WAITING_SEARCH_ENTRY,
                        message = "正在打开通讯录"
                    )
                }
            }

            Step.WAITING_SEARCH_ENTRY -> {
                if (isSearchInputVisible(root) || clickSearchEntry(root)) {
                    transitionTo(
                        session = session,
                        nextStep = Step.WAITING_CONTACT_RESULT,
                        message = "正在查找联系人"
                    )
                }
            }

            Step.WAITING_CONTACT_RESULT -> {
                if (!session.searchTextApplied) {
                    if (!fillSearchInput(root, session.contactName)) {
                        if (clickSearchEntry(root)) {
                            scheduleProcess(session, 120L)
                        }
                        return
                    }
                    session.searchTextApplied = true
                    armTimeout(Step.WAITING_CONTACT_RESULT, timeoutManager.getTimeout("search"), "查找联系人超时")
                    scheduleProcess(session, 200L)
                    return
                }

                if (clickContactResult(root, session.contactName)) {
                    transitionTo(
                        session = session,
                        nextStep = Step.WAITING_CONTACT_DETAIL,
                        message = "正在打开联系人"
                    )
                    return
                }

                if (hasNoSearchResult(root)) {
                    failAndHide("未找到联系人: ${session.contactName}")
                }
            }

            Step.WAITING_CONTACT_DETAIL -> {
                if (clickVideoCallEntry(root)) {
                    transitionTo(
                        session = session,
                        nextStep = Step.WAITING_VIDEO_OPTIONS,
                        message = "正在发起视频通话"
                    )
                }
            }

            Step.WAITING_VIDEO_OPTIONS -> {
                if (clickVideoCallOption(root)) {
                    recordStepSuccess(session.step, System.currentTimeMillis() - session.stepStartedAt)
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
            }
        }
    }

    private fun transitionTo(session: VideoCallSession, nextStep: Step, message: String) {
        recordStepSuccess(session.step, System.currentTimeMillis() - session.stepStartedAt)
        session.step = nextStep
        session.stepStartedAt = System.currentTimeMillis()
        notifyState(message, true)
        floatingView?.updateMessage(message)
        armTimeout(nextStep, timeoutFor(nextStep), failureMessageFor(nextStep, session.contactName))
        scheduleProcess(session, 120L)
    }

    private fun timeoutFor(step: Step): Long {
        return when (step) {
            Step.WAITING_HOME -> timeoutManager.getTimeout("launch")
            Step.WAITING_SEARCH_ENTRY -> timeoutManager.getTimeout("home")
            Step.WAITING_CONTACT_RESULT -> timeoutManager.getTimeout("search")
            Step.WAITING_CONTACT_DETAIL,
            Step.WAITING_VIDEO_OPTIONS -> timeoutManager.getTimeout("chat")
        }
    }

    private fun failureMessageFor(step: Step, contactName: String): String {
        return when (step) {
            Step.WAITING_HOME -> "微信启动超时"
            Step.WAITING_SEARCH_ENTRY -> "打开通讯录失败"
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
                failAndHide(failureMessage)
            }
        }
    }

    private fun cancelSession(notifyFailure: Boolean) {
        processJob?.cancel()
        timeoutJob?.cancel()
        processJob = null
        timeoutJob = null
        currentSession = null
        floatingView?.hide()
        if (notifyFailure) {
            notifyState("操作已取消", false)
        }
    }

    private fun failAndHide(message: String) {
        Log.e(TAG, message)
        cancelSession(false)
        notifyState(message, false)
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

    private fun clickContactsTab(root: AccessibilityNodeInfo?): Boolean {
        val node = AccessibilityUtil.findBestTextNode(root, "通讯录", preferBottom = true) ?: return false
        val success = AccessibilityUtil.performClick(this, node)
        AccessibilityUtil.safeRecycle(node)
        return success
    }

    private fun clickSearchEntry(root: AccessibilityNodeInfo?): Boolean {
        val node = AccessibilityUtil.findBestTextNode(root, "搜索", exactMatch = false, preferBottom = false) ?: return false
        val success = AccessibilityUtil.performClick(this, node)
        AccessibilityUtil.safeRecycle(node)
        return success
    }

    private fun isSearchInputVisible(root: AccessibilityNodeInfo?): Boolean {
        return AccessibilityUtil.findFirstEditableNode(root) != null
    }

    private fun fillSearchInput(root: AccessibilityNodeInfo?, contactName: String): Boolean {
        val node = AccessibilityUtil.findFirstEditableNode(root) ?: return false
        return AccessibilityUtil.setText(node, contactName)
    }

    private fun clickContactResult(root: AccessibilityNodeInfo?, contactName: String): Boolean {
        val node = AccessibilityUtil.findBestTextNode(root, contactName, exactMatch = true, preferBottom = false) ?: return false
        val success = AccessibilityUtil.performClick(this, node)
        AccessibilityUtil.safeRecycle(node)
        return success
    }

    private fun clickVideoCallEntry(root: AccessibilityNodeInfo?): Boolean {
        val node = AccessibilityUtil.findBestTextNode(root, "音视频通话", exactMatch = true, preferBottom = false) ?: return false
        val success = AccessibilityUtil.performClick(this, node)
        AccessibilityUtil.safeRecycle(node)
        return success
    }

    private fun clickVideoCallOption(root: AccessibilityNodeInfo?): Boolean {
        val node = AccessibilityUtil.findBestTextNode(root, "视频通话", exactMatch = true, preferBottom = true) ?: return false
        val success = AccessibilityUtil.performClick(this, node)
        AccessibilityUtil.safeRecycle(node)
        return success
    }

    private fun hasNoSearchResult(root: AccessibilityNodeInfo?): Boolean {
        val candidates = listOf("无搜索结果", "没有找到", "无结果")
        for (text in candidates) {
            val node = AccessibilityUtil.findBestTextNode(root, text, exactMatch = false, preferBottom = false, excludeEditable = false)
            if (node != null) {
                AccessibilityUtil.safeRecycle(node)
                return true
            }
        }
        return false
    }

    private fun recordStepSuccess(step: Step, duration: Long) {
        when (step) {
            Step.WAITING_HOME -> timeoutManager.recordSuccess("launch", duration)
            Step.WAITING_SEARCH_ENTRY -> timeoutManager.recordSuccess("home", duration)
            Step.WAITING_CONTACT_RESULT -> timeoutManager.recordSuccess("search", duration)
            Step.WAITING_CONTACT_DETAIL,
            Step.WAITING_VIDEO_OPTIONS -> timeoutManager.recordSuccess("chat", duration)
        }
    }

    private data class VideoCallSession(
        val contactName: String,
        var step: Step,
        var stepStartedAt: Long,
        var searchTextApplied: Boolean = false
    )

    private enum class Step {
        WAITING_HOME,
        WAITING_SEARCH_ENTRY,
        WAITING_CONTACT_RESULT,
        WAITING_CONTACT_DETAIL,
        WAITING_VIDEO_OPTIONS
    }
}
