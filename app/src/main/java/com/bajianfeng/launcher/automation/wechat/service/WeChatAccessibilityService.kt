package com.bajianfeng.launcher.automation.wechat.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Resources
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.bajianfeng.launcher.automation.wechat.manager.TimeoutManager
import com.bajianfeng.launcher.automation.wechat.util.AccessibilityUtil
import com.bajianfeng.launcher.common.ui.FloatingStatusView
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

    fun clearStateCallback() {
        stateCallback = null
    }

    private lateinit var timeoutManager: TimeoutManager
    private var floatingView: FloatingStatusView? = null

    private val screenWidth: Int
        get() = Resources.getSystem().displayMetrics.widthPixels
    private val screenHeight: Int
        get() = Resources.getSystem().displayMetrics.heightPixels

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
        currentTask?.cancel()
        floatingView?.hide()
        floatingView = null
        stateCallback = null
        serviceScope.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            if (it.action == ACTION_START_VIDEO_CALL) {
                val contactName = it.getStringExtra(EXTRA_CONTACT_NAME)
                if (contactName != null) {
                    startVideoCall(contactName)
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

                if (!launchWeChat()) {
                    failAndHide("打开微信失败")
                    return@launch
                }

                delay(2000)

                if (!waitForWeChat(10000L)) {
                    failAndHide("微信启动超时")
                    return@launch
                }

                Log.d(TAG, "微信已启动")
                delay(1500)

                floatingView?.updateMessage("正在点击通讯录")
                notifyState("正在点击通讯录", true)

                if (!clickContactsTab()) {
                    failAndHide("点击通讯录失败")
                    return@launch
                }

                Log.d(TAG, "已点击通讯录Tab")
                delay(1500)

                floatingView?.updateMessage("正在查找联系人")
                notifyState("正在查找联系人", true)

                if (!findAndClickContact(contactName)) {
                    failAndHide("未找到联系人: $contactName")
                    return@launch
                }

                Log.d(TAG, "已点击联系人: $contactName")
                delay(2000)

                floatingView?.updateMessage("正在发起视频通话")
                notifyState("正在发起视频通话", true)

                if (!clickVideoCallButton()) {
                    failAndHide("发起视频通话失败")
                    return@launch
                }

                Log.d(TAG, "已点击音视频通话按钮")
                delay(1500)

                if (!selectVideoCall()) {
                    failAndHide("选择视频通话失败")
                    return@launch
                }

                Log.d(TAG, "已选择视频通话")
                floatingView?.updateMessage("视频通话已发起")
                notifyState("视频通话已发起", true)

                delay(3000)
                floatingView?.hide()

            } catch (e: CancellationException) {
                floatingView?.hide()
            } catch (e: Exception) {
                Log.e(TAG, "视频通话异常", e)
                failAndHide("操作异常: ${e.message}")
            }
        }
    }

    private fun failAndHide(message: String) {
        Log.e(TAG, message)
        floatingView?.hide()
        notifyState(message, false)
    }

    private fun launchWeChat(): Boolean {
        val intent = packageManager.getLaunchIntentForPackage("com.tencent.mm")
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (intent != null) {
            startActivity(intent)
            true
        } else false
    }

    private suspend fun waitForWeChat(timeout: Long): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeout) {
            val root = rootInActiveWindow
            if (root?.packageName?.toString() == "com.tencent.mm") {
                return true
            }
            delay(500)
        }
        return false
    }

    private suspend fun clickContactsTab(): Boolean {
        repeat(15) { attempt ->
            Log.d(TAG, "clickContactsTab: 尝试$attempt")
            val root = rootInActiveWindow ?: run {
                delay(500)
                return@repeat
            }

            val contactsNode = AccessibilityUtil.findNodeByText(root, "通讯录")
            if (contactsNode != null) {
                val rect = android.graphics.Rect()
                contactsNode.getBoundsInScreen(rect)
                Log.d(TAG, "clickContactsTab: 找到'通讯录' bounds=$rect clickable=${contactsNode.isClickable} class=${contactsNode.className}")

                if (attempt == 0) {
                    AccessibilityUtil.dumpParentChain(contactsNode)
                }

                if (AccessibilityUtil.clickNode(contactsNode)) {
                    Log.d(TAG, "clickContactsTab: performClick成功")
                    AccessibilityUtil.safeRecycle(contactsNode)
                    delay(1000)
                    return true
                }

                Log.d(TAG, "clickContactsTab: performClick失败，尝试坐标点击 x=${rect.centerX()} y=${rect.centerY()}")
                AccessibilityUtil.clickByCoordinate(
                    this@WeChatAccessibilityService,
                    rect.centerX().toFloat(),
                    rect.centerY().toFloat()
                )
                AccessibilityUtil.safeRecycle(contactsNode)
                delay(1500)

                val verifyRoot = rootInActiveWindow
                val newFriends = AccessibilityUtil.findNodeByText(verifyRoot, "新的朋友")
                if (newFriends != null) {
                    Log.d(TAG, "clickContactsTab: 验证成功，已进入通讯录页面")
                    AccessibilityUtil.safeRecycle(newFriends)
                    return true
                }

                val contactsAgain = AccessibilityUtil.findNodeByText(verifyRoot, "通讯录")
                if (contactsAgain != null) {
                    val newRect = android.graphics.Rect()
                    contactsAgain.getBoundsInScreen(newRect)
                    val allContacts = AccessibilityUtil.findAllByText(verifyRoot, "通讯录")
                    Log.d(TAG, "clickContactsTab: 坐标点击后仍在首页，找到${allContacts.size}个'通讯录'节点")
                    allContacts.forEach { n ->
                        val r = android.graphics.Rect()
                        n.getBoundsInScreen(r)
                        Log.d(TAG, "  node: class=${n.className} bounds=$r clickable=${n.isClickable} id=${n.viewIdResourceName}")
                        AccessibilityUtil.safeRecycle(n)
                    }
                }

                return@repeat
            }

            delay(800)
        }
        return false
    }

    private suspend fun findAndClickContact(contactName: String): Boolean {
        repeat(30) { attempt ->
            Log.d(TAG, "findAndClickContact: 尝试$attempt")
            val root = rootInActiveWindow ?: run {
                delay(500)
                return@repeat
            }

            val contactNode = AccessibilityUtil.findNodeByText(root, contactName)
            if (contactNode != null) {
                Log.d(TAG, "findAndClickContact: 找到联系人'$contactName'")

                if (AccessibilityUtil.clickNode(contactNode)) {
                    Log.d(TAG, "findAndClickContact: performClick成功")
                    AccessibilityUtil.safeRecycle(contactNode)
                    return true
                }

                AccessibilityUtil.clickNodeByBounds(this@WeChatAccessibilityService, contactNode)
                AccessibilityUtil.safeRecycle(contactNode)
                delay(1000)

                val verifyRoot = rootInActiveWindow
                val sendMsg = AccessibilityUtil.findNodeByText(verifyRoot, "发消息")
                if (sendMsg != null) {
                    Log.d(TAG, "findAndClickContact: 验证成功，已进入联系人详情")
                    AccessibilityUtil.safeRecycle(sendMsg)
                    return true
                }

                return@repeat
            }

            val scrollable = AccessibilityUtil.findScrollableNode(root)
            if (scrollable != null) {
                Log.d(TAG, "findAndClickContact: 未找到，向下滚动")
                AccessibilityUtil.scrollNodeDown(scrollable)
            } else {
                AccessibilityUtil.scrollDown(this@WeChatAccessibilityService, screenWidth, screenHeight)
            }

            delay(800)
        }
        return false
    }

    private suspend fun clickVideoCallButton(): Boolean {
        repeat(15) { attempt ->
            Log.d(TAG, "clickVideoCallButton: 尝试$attempt")
            val root = rootInActiveWindow ?: run {
                delay(500)
                return@repeat
            }

            val videoCallNode = AccessibilityUtil.findNodeByText(root, "音视频通话")
            if (videoCallNode != null) {
                Log.d(TAG, "clickVideoCallButton: 找到'音视频通话'")

                if (AccessibilityUtil.clickNode(videoCallNode)) {
                    Log.d(TAG, "clickVideoCallButton: performClick成功")
                    AccessibilityUtil.safeRecycle(videoCallNode)
                    return true
                }

                AccessibilityUtil.clickNodeByBounds(this@WeChatAccessibilityService, videoCallNode)
                AccessibilityUtil.safeRecycle(videoCallNode)
                delay(1000)
                return true
            }

            val sendMsgNode = AccessibilityUtil.findNodeByText(root, "发消息")
            if (sendMsgNode != null) {
                Log.d(TAG, "clickVideoCallButton: 在联系人详情页，向下查找")
                AccessibilityUtil.safeRecycle(sendMsgNode)
                val scrollable = AccessibilityUtil.findScrollableNode(root)
                if (scrollable != null) {
                    AccessibilityUtil.scrollNodeDown(scrollable)
                } else {
                    AccessibilityUtil.scrollDown(this@WeChatAccessibilityService, screenWidth, screenHeight)
                }
            }

            delay(800)
        }
        return false
    }

    private suspend fun selectVideoCall(): Boolean {
        repeat(10) { attempt ->
            Log.d(TAG, "selectVideoCall: 尝试$attempt")
            delay(500)
            val root = rootInActiveWindow ?: run {
                delay(500)
                return@repeat
            }

            val allVideoNodes = AccessibilityUtil.findAllByText(root, "视频通话")
            Log.d(TAG, "selectVideoCall: 找到${allVideoNodes.size}个'视频通话'节点")

            for (node in allVideoNodes) {
                if (AccessibilityUtil.clickNode(node)) {
                    Log.d(TAG, "selectVideoCall: performClick成功")
                    allVideoNodes.forEach { AccessibilityUtil.safeRecycle(it) }
                    return true
                }

                AccessibilityUtil.clickNodeByBounds(this@WeChatAccessibilityService, node)
                allVideoNodes.forEach { AccessibilityUtil.safeRecycle(it) }
                delay(500)
                return true
            }

            delay(500)
        }
        return false
    }
}
