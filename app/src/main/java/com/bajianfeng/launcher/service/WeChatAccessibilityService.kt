package com.bajianfeng.launcher.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
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
                floatingView?.show("正在打开微信")
                notifyState("正在打开微信", true)
                
                val launchStart = System.currentTimeMillis()
                if (!launchWeChat()) {
                    floatingView?.hide()
                    notifyState("打开微信失败", false)
                    return@launch
                }
                
                val launchTimeout = timeoutManager.getTimeout("launch")
                val launchSuccess = stateDetector.waitForState("微信启动", launchTimeout) {
                    stateDetector.isWeChatLaunched(rootInActiveWindow)
                }
                
                if (!launchSuccess) {
                    floatingView?.hide()
                    notifyState("微信启动超时", false)
                    return@launch
                }
                
                timeoutManager.recordSuccess("launch", System.currentTimeMillis() - launchStart)
                
                floatingView?.updateMessage("正在加载首页")
                val homeTimeout = timeoutManager.getTimeout("home")
                val homeSuccess = stateDetector.waitForState("首页加载", homeTimeout) {
                    stateDetector.isHomePageLoaded(rootInActiveWindow)
                }
                
                if (!homeSuccess) {
                    floatingView?.hide()
                    notifyState("首页加载超时", false)
                    return@launch
                }
                
                floatingView?.updateMessage("正在查找联系人")
                notifyState("正在查找联系人", true)
                
                val searchStart = System.currentTimeMillis()
                if (!openSearch()) {
                    floatingView?.hide()
                    notifyState("打开搜索失败", false)
                    return@launch
                }
                
                val searchBoxTimeout = timeoutManager.getTimeout("search")
                val searchBoxVisible = stateDetector.waitForState("搜索框", searchBoxTimeout) {
                    stateDetector.isSearchBoxVisible(rootInActiveWindow)
                }
                
                if (!searchBoxVisible) {
                    floatingView?.hide()
                    notifyState("搜索框未出现", false)
                    return@launch
                }
                
                if (!searchContact(contactName)) {
                    floatingView?.hide()
                    notifyState("查找联系人失败", false)
                    return@launch
                }
                
                val contactFound = stateDetector.waitForState("联系人搜索", 10000L) {
                    stateDetector.isContactFound(rootInActiveWindow, contactName)
                }
                
                if (!contactFound) {
                    floatingView?.hide()
                    notifyState("未找到联系人$contactName", false)
                    return@launch
                }
                
                timeoutManager.recordSuccess("search", System.currentTimeMillis() - searchStart)
                
                floatingView?.updateMessage("正在进入聊天")
                notifyState("正在进入聊天", true)
                
                val chatStart = System.currentTimeMillis()
                if (!openChat()) {
                    floatingView?.hide()
                    notifyState("进入聊天失败", false)
                    return@launch
                }
                
                val chatTimeout = timeoutManager.getTimeout("chat")
                val chatLoaded = stateDetector.waitForState("聊天界面", chatTimeout) {
                    stateDetector.isChatPageLoaded(rootInActiveWindow)
                }
                
                if (!chatLoaded) {
                    floatingView?.hide()
                    notifyState("聊天界面加载超时", false)
                    return@launch
                }
                
                timeoutManager.recordSuccess("chat", System.currentTimeMillis() - chatStart)
                
                floatingView?.updateMessage("正在发起视频")
                notifyState("正在发起视频", true)
                
                if (!startVideo()) {
                    floatingView?.hide()
                    notifyState("发起视频失败", false)
                    return@launch
                }
                
                delay(1000)
                floatingView?.updateMessage("操作成功")
                notifyState("操作成功", true)
                
                delay(2000)
                floatingView?.hide()
                
            } catch (e: Exception) {
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
            repeat(20) {
                val root = rootInActiveWindow ?: return@withContext false
                
                val searchIcon = AccessibilityUtil.findNodeById(root, "com.tencent.mm:id/f8y") 
                    ?: AccessibilityUtil.findNodeByText(root, "搜索")
                
                if (searchIcon != null) {
                    val clicked = AccessibilityUtil.clickNode(searchIcon)
                    AccessibilityUtil.recycleNodes(searchIcon, root)
                    if (clicked) return@withContext true
                }
                
                AccessibilityUtil.recycleNodes(root)
                delay(500)
            }
            false
        }
    }
    
    private suspend fun searchContact(name: String): Boolean {
        return withContext(Dispatchers.IO) {
            delay(1000)
            
            repeat(10) {
                val root = rootInActiveWindow ?: return@withContext false
                
                val searchBox = AccessibilityUtil.findNodeById(root, "com.tencent.mm:id/cd7")
                    ?: AccessibilityUtil.findNodeByText(root, "搜索")
                
                if (searchBox != null) {
                    val success = AccessibilityUtil.setText(searchBox, name)
                    AccessibilityUtil.recycleNodes(searchBox, root)
                    if (success) return@withContext true
                }
                
                AccessibilityUtil.recycleNodes(root)
                delay(500)
            }
            false
        }
    }
    
    private suspend fun openChat(): Boolean {
        return withContext(Dispatchers.IO) {
            delay(1000)
            
            repeat(10) {
                val root = rootInActiveWindow ?: return@withContext false
                
                val nodes = root.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/tm")
                if (nodes.isNotEmpty()) {
                    val clicked = AccessibilityUtil.clickNode(nodes[0])
                    nodes.forEach { it.recycle() }
                    AccessibilityUtil.recycleNodes(root)
                    if (clicked) return@withContext true
                }
                
                AccessibilityUtil.recycleNodes(root)
                delay(500)
            }
            false
        }
    }
    
    private suspend fun startVideo(): Boolean {
        return withContext(Dispatchers.IO) {
            delay(1000)
            
            repeat(10) {
                val root = rootInActiveWindow ?: return@withContext false
                
                val videoButton = AccessibilityUtil.findNodeById(root, "com.tencent.mm:id/aop")
                    ?: AccessibilityUtil.findNodeByText(root, "视频通话")
                
                if (videoButton != null) {
                    val clicked = AccessibilityUtil.clickNode(videoButton)
                    AccessibilityUtil.recycleNodes(videoButton, root)
                    if (clicked) {
                        delay(500)
                        clickVideoCallOption()
                        return@withContext true
                    }
                }
                
                AccessibilityUtil.recycleNodes(root)
                delay(500)
            }
            false
        }
    }
    
    private suspend fun clickVideoCallOption() {
        delay(500)
        val root = rootInActiveWindow ?: return
        
        val videoOption = AccessibilityUtil.findNodeByText(root, "视频通话")
        if (videoOption != null) {
            AccessibilityUtil.clickNode(videoOption)
            AccessibilityUtil.recycleNodes(videoOption)
        }
        
        AccessibilityUtil.recycleNodes(root)
    }
    
    private fun handleWindowStateChanged(event: AccessibilityEvent) {
    }
    
    private fun handleContentChanged(event: AccessibilityEvent) {
    }
}
