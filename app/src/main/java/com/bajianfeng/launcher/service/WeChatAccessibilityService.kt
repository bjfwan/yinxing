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
        event ?: return
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(event)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handleContentChanged(event)
            }
        }
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
                Log.d(TAG, "步骤1: 启动微信")
                
                if (!launchWeChat()) {
                    Log.e(TAG, "启动微信失败: Intent为null")
                    floatingView?.hide()
                    notifyState("打开微信失败", false)
                    return@launch
                }
                
                Log.d(TAG, "微信Intent已发送，等待2秒")
                delay(2000)
                
                val currentPackage = rootInActiveWindow?.packageName?.toString()
                Log.d(TAG, "当前前台应用: $currentPackage")
                
                if (currentPackage != "com.tencent.mm") {
                    Log.e(TAG, "微信启动失败: 当前包名=$currentPackage")
                    floatingView?.hide()
                    notifyState("微信启动失败", false)
                    return@launch
                }
                
                Log.d(TAG, "微信启动成功，耗时: ${System.currentTimeMillis() - launchStart}ms")
                timeoutManager.recordSuccess("launch", System.currentTimeMillis() - launchStart)
                
                floatingView?.updateMessage("正在加载首页")
                Log.d(TAG, "步骤2: 等待首页加载")
                delay(2000)
                
                floatingView?.updateMessage("正在查找联系人")
                notifyState("正在查找联系人", true)
                
                val searchStart = System.currentTimeMillis()
                Log.d(TAG, "步骤3: 打开搜索")
                
                if (!openSearch()) {
                    Log.e(TAG, "打开搜索失败")
                    floatingView?.hide()
                    notifyState("打开搜索失败", false)
                    return@launch
                }
                
                Log.d(TAG, "搜索框已打开")
                
                val searchBoxTimeout = timeoutManager.getTimeout("search")
                Log.d(TAG, "步骤4: 等待搜索框出现，超时=${searchBoxTimeout}ms")
                
                val searchBoxVisible = stateDetector.waitForState("搜索框", searchBoxTimeout) {
                    val visible = stateDetector.isSearchBoxVisible(rootInActiveWindow)
                    if (!visible) {
                        Log.d(TAG, "搜索框检测: 未找到")
                    } else {
                        Log.d(TAG, "搜索框检测: 已找到")
                    }
                    visible
                }
                
                if (!searchBoxVisible) {
                    Log.e(TAG, "搜索框未出现，超时")
                    floatingView?.hide()
                    notifyState("搜索框未出现", false)
                    return@launch
                }
                
                Log.d(TAG, "步骤5: 输入联系人名称: $contactName")
                
                if (!searchContact(contactName)) {
                    Log.e(TAG, "输入联系人失败")
                    floatingView?.hide()
                    notifyState("查找联系人失败", false)
                    return@launch
                }
                
                Log.d(TAG, "联系人名称已输入")
                Log.d(TAG, "步骤6: 等待搜索结果")
                
                val contactFound = stateDetector.waitForState("联系人搜索", 10000L) {
                    val found = stateDetector.isContactFound(rootInActiveWindow, contactName)
                    if (!found) {
                        Log.d(TAG, "联系人检测: 未找到 $contactName")
                    } else {
                        Log.d(TAG, "联系人检测: 已找到 $contactName")
                    }
                    found
                }
                
                if (!contactFound) {
                    Log.e(TAG, "未找到联系人: $contactName")
                    floatingView?.hide()
                    notifyState("未找到联系人$contactName", false)
                    return@launch
                }
                
                timeoutManager.recordSuccess("search", System.currentTimeMillis() - searchStart)
                
                floatingView?.updateMessage("正在进入聊天")
                notifyState("正在进入聊天", true)
                
                val chatStart = System.currentTimeMillis()
                Log.d(TAG, "步骤7: 点击联系人进入聊天")
                
                if (!openChat()) {
                    Log.e(TAG, "进入聊天失败")
                    floatingView?.hide()
                    notifyState("进入聊天失败", false)
                    return@launch
                }
                
                Log.d(TAG, "已点击联系人")
                
                val chatTimeout = timeoutManager.getTimeout("chat")
                Log.d(TAG, "步骤8: 等待聊天界面加载，超时=${chatTimeout}ms")
                
                val chatLoaded = stateDetector.waitForState("聊天界面", chatTimeout) {
                    val loaded = stateDetector.isChatPageLoaded(rootInActiveWindow)
                    if (!loaded) {
                        Log.d(TAG, "聊天界面检测: 未加载")
                    } else {
                        Log.d(TAG, "聊天界面检测: 已加载")
                    }
                    loaded
                }
                
                if (!chatLoaded) {
                    Log.e(TAG, "聊天界面加载超时")
                    floatingView?.hide()
                    notifyState("聊天界面加载超时", false)
                    return@launch
                }
                
                timeoutManager.recordSuccess("chat", System.currentTimeMillis() - chatStart)
                
                floatingView?.updateMessage("正在发起视频")
                notifyState("正在发起视频", true)
                
                Log.d(TAG, "步骤9: 点击视频通话按钮")
                
                if (!startVideo()) {
                    Log.e(TAG, "发起视频失败")
                    floatingView?.hide()
                    notifyState("发起视频失败", false)
                    return@launch
                }
                
                Log.d(TAG, "视频通话已发起")
                
                delay(1000)
                floatingView?.updateMessage("操作成功")
                notifyState("操作成功", true)
                
                Log.d(TAG, "========== 视频通话流程完成 ==========")
                
                delay(2000)
                floatingView?.hide()
                
            } catch (e: Exception) {
                Log.e(TAG, "视频通话异常", e)
                floatingView?.hide()
                notifyState("操作异常: ${e.message}", false)
            }
        }
    }
    
    private fun launchWeChat(): Boolean {
        val intent = packageManager.getLaunchIntentForPackage("com.tencent.mm")
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent?.let { startActivity(it) }
        return intent != null
    }
    
    private suspend fun openSearch(): Boolean {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "openSearch: 开始查找搜索按钮")
            repeat(20) { attempt ->
                val root = rootInActiveWindow
                if (root == null) {
                    Log.d(TAG, "openSearch: 尝试$attempt - rootInActiveWindow为null")
                    delay(500)
                    return@repeat
                }
                
                Log.d(TAG, "openSearch: 尝试$attempt - 当前窗口: ${root.packageName}")
                
                if (attempt == 0) {
                    AccessibilityUtil.dumpNodeTree(root, 0)
                }
                
                val searchText = AccessibilityUtil.findNodeByText(root, "搜索")
                if (searchText != null) {
                    Log.d(TAG, "openSearch: 找到'搜索'节点, clickable=${searchText.isClickable}")
                    var clicked = AccessibilityUtil.clickNode(searchText)
                    
                    if (!clicked) {
                        Log.d(TAG, "openSearch: 常规点击失败，尝试坐标点击")
                        clicked = AccessibilityUtil.clickNodeByBounds(this@WeChatAccessibilityService, searchText)
                    }
                    
                    Log.d(TAG, "openSearch: 最终点击结果=$clicked")
                    AccessibilityUtil.recycleNodes(searchText)
                    if (clicked) {
                        delay(1000)
                        return@withContext true
                    }
                } else {
                    Log.d(TAG, "openSearch: 未找到'搜索'节点")
                }
                
                delay(500)
            }
            Log.e(TAG, "openSearch: 20次尝试后仍未找到搜索按钮")
            false
        }
    }
    
    private suspend fun searchContact(name: String): Boolean {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "searchContact: 开始输入联系人名称: $name")
            delay(1000)
            
            repeat(10) { attempt ->
                val root = rootInActiveWindow
                if (root == null) {
                    Log.d(TAG, "searchContact: 尝试$attempt - rootInActiveWindow为null")
                    delay(500)
                    return@repeat
                }
                
                Log.d(TAG, "searchContact: 尝试$attempt - 查找可编辑节点")
                val editableNodes = mutableListOf<AccessibilityNodeInfo>()
                findEditableNodes(root, editableNodes)
                
                Log.d(TAG, "searchContact: 找到${editableNodes.size}个可编辑节点")
                
                if (editableNodes.isNotEmpty()) {
                    val node = editableNodes[0]
                    Log.d(TAG, "searchContact: 节点类型=${node.className}, 可编辑=${node.isEditable}")
                    val success = AccessibilityUtil.setText(node, name)
                    Log.d(TAG, "searchContact: 输入结果=$success")
                    editableNodes.forEach { AccessibilityUtil.safeRecycle(it) }
                    if (success) return@withContext true
                }
                delay(500)
            }
            Log.e(TAG, "searchContact: 10次尝试后仍未能输入")
            false
        }
    }
    
    private fun findEditableNodes(node: AccessibilityNodeInfo?, result: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        
        if (node.isEditable && node.className?.toString()?.contains("EditText") == true) {
            result.add(node)
            return
        }
        
        for (i in 0 until node.childCount) {
            findEditableNodes(node.getChild(i), result)
        }
    }
    
    private suspend fun openChat(): Boolean {
        return withContext(Dispatchers.IO) {
            delay(1000)
            Log.d(TAG, "openChat: 开始查找搜索结果")
            
            repeat(10) { attempt ->
                val root = rootInActiveWindow
                if (root == null) {
                    Log.d(TAG, "openChat: 尝试$attempt - rootInActiveWindow为null")
                    delay(500)
                    return@repeat
                }
                
                val clickableNodes = mutableListOf<AccessibilityNodeInfo>()
                findClickableNodes(root, clickableNodes)
                
                Log.d(TAG, "openChat: 尝试$attempt - 找到${clickableNodes.size}个可点击节点")
                
                if (clickableNodes.isNotEmpty()) {
                    var clicked = AccessibilityUtil.clickNode(clickableNodes[0])
                    if (!clicked) {
                        clicked = AccessibilityUtil.clickNodeByBounds(this@WeChatAccessibilityService, clickableNodes[0])
                    }
                    Log.d(TAG, "openChat: 点击结果=$clicked")
                    clickableNodes.forEach { AccessibilityUtil.safeRecycle(it) }
                    if (clicked) return@withContext true
                }
                
                delay(500)
            }
            Log.e(TAG, "openChat: 10次尝试后仍未能进入聊天")
            false
        }
    }
    
    private fun findClickableNodes(node: AccessibilityNodeInfo?, result: MutableList<AccessibilityNodeInfo>) {
        if (node == null || result.isNotEmpty()) return
        
        if (node.isClickable && node.className?.toString()?.contains("LinearLayout") == true) {
            result.add(node)
            return
        }
        
        for (i in 0 until node.childCount) {
            findClickableNodes(node.getChild(i), result)
        }
    }
    
    private suspend fun startVideo(): Boolean {
        return withContext(Dispatchers.IO) {
            delay(1000)
            Log.d(TAG, "startVideo: 开始查找视频通话按钮")
            
            repeat(10) { attempt ->
                val root = rootInActiveWindow
                if (root == null) {
                    Log.d(TAG, "startVideo: 尝试$attempt - rootInActiveWindow为null")
                    delay(500)
                    return@repeat
                }
                
                val videoButton = AccessibilityUtil.findNodeByText(root, "视频通话")
                
                if (videoButton != null) {
                    Log.d(TAG, "startVideo: 找到'视频通话'按钮")
                    var clicked = AccessibilityUtil.clickNode(videoButton)
                    if (!clicked) {
                        clicked = AccessibilityUtil.clickNodeByBounds(this@WeChatAccessibilityService, videoButton)
                    }
                    Log.d(TAG, "startVideo: 点击结果=$clicked")
                    AccessibilityUtil.safeRecycle(videoButton)
                    if (clicked) {
                        delay(500)
                        clickVideoCallOption()
                        return@withContext true
                    }
                } else {
                    Log.d(TAG, "startVideo: 尝试$attempt - 未找到'视频通话'按钮")
                }
                
                delay(500)
            }
            Log.e(TAG, "startVideo: 10次尝试后仍未找到视频通话按钮")
            false
        }
    }
    
    private suspend fun clickVideoCallOption() {
        delay(500)
        val root = rootInActiveWindow ?: return
        
        val videoOption = AccessibilityUtil.findNodeByText(root, "视频通话")
        if (videoOption != null) {
            var clicked = AccessibilityUtil.clickNode(videoOption)
            if (!clicked) {
                clicked = AccessibilityUtil.clickNodeByBounds(this@WeChatAccessibilityService, videoOption)
            }
            Log.d(TAG, "clickVideoCallOption: 点击结果=$clicked")
            AccessibilityUtil.safeRecycle(videoOption)
        }
    }
    
    private fun handleWindowStateChanged(event: AccessibilityEvent) {
    }
    
    private fun handleContentChanged(event: AccessibilityEvent) {
    }
}
