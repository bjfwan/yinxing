package com.bajianfeng.launcher.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.bajianfeng.launcher.manager.StateDetectionManager
import com.bajianfeng.launcher.manager.TimeoutManager
import com.bajianfeng.launcher.ui.FloatingStatusView
import com.bajianfeng.launcher.util.AccessibilityUtil
import kotlinx.coroutines.*

class WeChatAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var instance: WeChatAccessibilityService? = null

        fun getInstance(): WeChatAccessibilityService? = instance

        const val ACTION_START_VIDEO_CALL = "com.bajianfeng.launcher.START_VIDEO_CALL"
        const val EXTRA_CONTACT_NAME = "contact_name"

        private const val TAG = "WeChatAutoService"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentTask: Job? = null
    private var stateCallback: ((String, Boolean) -> Unit)? = null

    private lateinit var timeoutManager: TimeoutManager
    private val stateDetector = StateDetectionManager()
    private var floatingView: FloatingStatusView? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        timeoutManager = TimeoutManager.getInstance(this)
        floatingView = FloatingStatusView(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
        currentTask?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        floatingView?.hide()
        serviceScope.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_START_VIDEO_CALL -> {
                    val contactName = it.getStringExtra(EXTRA_CONTACT_NAME)
                    if (contactName != null) {
                        startVideoCall(contactName)
                    }
                }
            }
        }
        return START_STICKY
    }

    fun setStateCallback(callback: (String, Boolean) -> Unit) {
        this.stateCallback = callback
    }

    private fun notifyState(state: String, success: Boolean) {
        stateCallback?.invoke(state, success)
    }

    private fun startVideoCall(contactName: String) {
        currentTask?.cancel()
        currentTask = serviceScope.launch {
            try {
                Log.d(TAG, "========== 开始视频通话流程 ==========")
                Log.d(TAG, "联系人: $contactName")

                floatingView?.show("正在打开微信")
                notifyState("正在打开微信", true)

                val launchStart = System.currentTimeMillis()

                if (!launchWeChat()) {
                    fail("打开微信失败")
                    return@launch
                }

                Log.d(TAG, "步骤1: 微信Intent已发送，等待启动")

                val launched = stateDetector.waitForState("微信启动", 15000L) {
                    rootInActiveWindow?.packageName?.toString() == "com.tencent.mm"
                }

                if (!launched) {
                    fail("微信启动超时")
                    return@launch
                }

                Log.d(TAG, "微信启动成功，耗时: ${System.currentTimeMillis() - launchStart}ms")
                timeoutManager.recordSuccess("launch", System.currentTimeMillis() - launchStart)
                delay(2000)

                floatingView?.updateMessage("正在打开通讯录")
                notifyState("正在打开通讯录", true)
                Log.d(TAG, "步骤2: 点击通讯录Tab")

                if (!clickContactsTab()) {
                    fail("打开通讯录失败")
                    return@launch
                }

                delay(2000)

                floatingView?.updateMessage("正在查找联系人")
                notifyState("正在查找联系人", true)
                Log.d(TAG, "步骤3: 在通讯录中查找联系人: $contactName")

                if (!findAndClickContact(contactName)) {
                    fail("未找到联系人: $contactName")
                    return@launch
                }

                delay(2000)

                floatingView?.updateMessage("正在发起视频通话")
                notifyState("正在发起视频通话", true)
                Log.d(TAG, "步骤4: 点击音视频通话按钮")

                if (!clickVideoCallButton()) {
                    fail("发起视频通话失败")
                    return@launch
                }

                delay(1000)

                Log.d(TAG, "步骤5: 选择视频通话")

                if (!selectVideoCallOption()) {
                    fail("选择视频通话失败")
                    return@launch
                }

                delay(1000)
                floatingView?.updateMessage("操作成功")
                notifyState("操作成功", true)
                Log.d(TAG, "========== 视频通话流程完成 ==========")

                delay(2000)
                floatingView?.hide()

            } catch (e: Exception) {
                Log.e(TAG, "视频通话异常", e)
                fail("操作异常: ${e.message}")
            }
        }
    }

    private fun fail(message: String) {
        Log.e(TAG, message)
        floatingView?.hide()
        notifyState(message, false)
    }

    private fun launchWeChat(): Boolean {
        val intent = packageManager.getLaunchIntentForPackage("com.tencent.mm")
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent?.let { startActivity(it) }
        return intent != null
    }

    private suspend fun clickContactsTab(): Boolean {
        return withContext(Dispatchers.IO) {
            repeat(15) { attempt ->
                val root = rootInActiveWindow
                if (root == null) {
                    Log.d(TAG, "clickContactsTab: 尝试$attempt - root为null")
                    delay(500)
                    return@repeat
                }

                Log.d(TAG, "clickContactsTab: 尝试$attempt")

                if (attempt == 0) {
                    AccessibilityUtil.dumpNodeTree(root, 0)
                }

                val contactsTab = AccessibilityUtil.findNodeByText(root, "通讯录")
                if (contactsTab != null) {
                    Log.d(TAG, "clickContactsTab: 找到'通讯录', class=${contactsTab.className}, clickable=${contactsTab.isClickable}")
                    var clicked = AccessibilityUtil.clickNode(contactsTab)
                    if (!clicked) {
                        Log.d(TAG, "clickContactsTab: 常规点击失败，尝试坐标点击")
                        clicked = AccessibilityUtil.clickNodeByBounds(this@WeChatAccessibilityService, contactsTab)
                    }
                    Log.d(TAG, "clickContactsTab: 点击结果=$clicked")
                    AccessibilityUtil.safeRecycle(contactsTab)
                    if (clicked) return@withContext true
                } else {
                    Log.d(TAG, "clickContactsTab: 未找到'通讯录'")
                }

                delay(500)
            }
            false
        }
    }

    private suspend fun findAndClickContact(contactName: String): Boolean {
        return withContext(Dispatchers.IO) {
            repeat(20) { attempt ->
                val root = rootInActiveWindow
                if (root == null) {
                    Log.d(TAG, "findAndClickContact: 尝试$attempt - root为null")
                    delay(500)
                    return@repeat
                }

                Log.d(TAG, "findAndClickContact: 尝试$attempt - 查找: $contactName")

                val contactNode = AccessibilityUtil.findNodeByText(root, contactName)
                if (contactNode != null) {
                    Log.d(TAG, "findAndClickContact: 找到联系人节点, class=${contactNode.className}, clickable=${contactNode.isClickable}")
                    var clicked = AccessibilityUtil.clickNode(contactNode)
                    if (!clicked) {
                        clicked = AccessibilityUtil.clickNodeByBounds(this@WeChatAccessibilityService, contactNode)
                    }
                    Log.d(TAG, "findAndClickContact: 点击结果=$clicked")
                    AccessibilityUtil.safeRecycle(contactNode)
                    if (clicked) return@withContext true
                } else {
                    Log.d(TAG, "findAndClickContact: 当前页面未找到，尝试滚动")
                    scrollDown(root)
                }

                delay(1000)
            }
            false
        }
    }

    private fun scrollDown(root: AccessibilityNodeInfo) {
        val scrollableNode = findScrollableNode(root)
        if (scrollableNode != null) {
            Log.d(TAG, "scrollDown: 找到可滚动节点，执行滚动")
            scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            AccessibilityUtil.safeRecycle(scrollableNode)
        } else {
            Log.d(TAG, "scrollDown: 未找到可滚动节点")
        }
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        if (node.isScrollable) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollableNode(child)
            if (found != null) return found
        }

        return null
    }

    private suspend fun clickVideoCallButton(): Boolean {
        return withContext(Dispatchers.IO) {
            repeat(15) { attempt ->
                val root = rootInActiveWindow
                if (root == null) {
                    Log.d(TAG, "clickVideoCallButton: 尝试$attempt - root为null")
                    delay(500)
                    return@repeat
                }

                Log.d(TAG, "clickVideoCallButton: 尝试$attempt")

                if (attempt == 0) {
                    AccessibilityUtil.dumpNodeTree(root, 0)
                }

                var btn = AccessibilityUtil.findNodeByText(root, "音视频通话")
                if (btn == null) {
                    btn = AccessibilityUtil.findNodeByContentDescription(root, "音视频通话")
                }
                if (btn == null) {
                    btn = AccessibilityUtil.findNodeByContentDescription(root, "更多功能按钮")
                }

                if (btn != null) {
                    Log.d(TAG, "clickVideoCallButton: 找到按钮, class=${btn.className}, desc=${btn.contentDescription}, text=${btn.text}")
                    var clicked = AccessibilityUtil.clickNode(btn)
                    if (!clicked) {
                        clicked = AccessibilityUtil.clickNodeByBounds(this@WeChatAccessibilityService, btn)
                    }
                    Log.d(TAG, "clickVideoCallButton: 点击结果=$clicked")
                    AccessibilityUtil.safeRecycle(btn)
                    if (clicked) return@withContext true
                } else {
                    Log.d(TAG, "clickVideoCallButton: 未找到音视频通话按钮")
                }

                delay(500)
            }
            false
        }
    }

    private suspend fun selectVideoCallOption(): Boolean {
        return withContext(Dispatchers.IO) {
            repeat(10) { attempt ->
                val root = rootInActiveWindow
                if (root == null) {
                    delay(500)
                    return@repeat
                }

                Log.d(TAG, "selectVideoCallOption: 尝试$attempt")

                if (attempt == 0) {
                    AccessibilityUtil.dumpNodeTree(root, 0)
                }

                val videoOption = AccessibilityUtil.findNodeByText(root, "视频通话")
                if (videoOption != null) {
                    Log.d(TAG, "selectVideoCallOption: 找到'视频通话'选项")
                    var clicked = AccessibilityUtil.clickNode(videoOption)
                    if (!clicked) {
                        clicked = AccessibilityUtil.clickNodeByBounds(this@WeChatAccessibilityService, videoOption)
                    }
                    Log.d(TAG, "selectVideoCallOption: 点击结果=$clicked")
                    AccessibilityUtil.safeRecycle(videoOption)
                    if (clicked) return@withContext true
                } else {
                    Log.d(TAG, "selectVideoCallOption: 未找到'视频通话'选项")
                }

                delay(500)
            }
            false
        }
    }
}
