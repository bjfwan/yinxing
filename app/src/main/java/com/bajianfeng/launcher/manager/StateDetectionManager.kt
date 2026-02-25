package com.bajianfeng.launcher.manager

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.bajianfeng.launcher.util.AccessibilityUtil
import kotlinx.coroutines.delay

class StateDetectionManager {
    
    companion object {
        private const val TAG = "StateDetector"
    }
    
    suspend fun waitForState(
        stateName: String,
        timeout: Long,
        checkInterval: Long = 300L,
        validator: () -> Boolean
    ): Boolean {
        Log.d(TAG, "waitForState: 开始等待状态[$stateName], 超时=${timeout}ms")
        val startTime = System.currentTimeMillis()
        var attempts = 0
        
        while (System.currentTimeMillis() - startTime < timeout) {
            attempts++
            if (validator()) {
                Log.d(TAG, "waitForState: 状态[$stateName]检测成功, 尝试次数=$attempts, 耗时=${System.currentTimeMillis() - startTime}ms")
                return true
            }
            delay(checkInterval)
        }
        
        Log.e(TAG, "waitForState: 状态[$stateName]检测超时, 尝试次数=$attempts, 耗时=${System.currentTimeMillis() - startTime}ms")
        return false
    }
    
    fun isWeChatLaunched(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) {
            Log.d(TAG, "isWeChatLaunched: root为null")
            return false
        }
        
        val packageName = root.packageName?.toString()
        Log.d(TAG, "isWeChatLaunched: 当前包名=$packageName")
        return packageName == "com.tencent.mm"
    }
    
    fun isHomePageLoaded(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        
        val packageName = root.packageName?.toString()
        if (packageName != "com.tencent.mm") return false
        
        val searchText = AccessibilityUtil.findNodeByText(root, "搜索")
        if (searchText != null) {
            searchText.recycle()
            return true
        }
        
        val wechatText = AccessibilityUtil.findNodeByText(root, "微信")
        if (wechatText != null) {
            wechatText.recycle()
            return true
        }
        
        return true
    }
    
    fun isSearchBoxVisible(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) {
            Log.d(TAG, "isSearchBoxVisible: root为null")
            return false
        }
        
        val cancelBtn = AccessibilityUtil.findNodeByText(root, "取消")
        if (cancelBtn != null) {
            Log.d(TAG, "isSearchBoxVisible: 找到'取消'按钮")
            cancelBtn.recycle()
            return true
        }
        
        val searchHint = AccessibilityUtil.findNodeByText(root, "搜索")
        if (searchHint != null && searchHint.isEditable) {
            Log.d(TAG, "isSearchBoxVisible: 找到可编辑的'搜索'框")
            searchHint.recycle()
            return true
        }
        
        Log.d(TAG, "isSearchBoxVisible: 未找到搜索框")
        return false
    }
    
    fun isContactFound(root: AccessibilityNodeInfo?, contactName: String): Boolean {
        if (root == null) return false
        
        val nameNode = AccessibilityUtil.findNodeByText(root, contactName)
        if (nameNode != null) {
            nameNode.recycle()
            return true
        }
        
        return false
    }
    
    fun isChatPageLoaded(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        
        val videoText = AccessibilityUtil.findNodeByText(root, "视频通话")
        if (videoText != null) {
            videoText.recycle()
            return true
        }
        
        val voiceText = AccessibilityUtil.findNodeByText(root, "语音通话")
        if (voiceText != null) {
            voiceText.recycle()
            return true
        }
        
        return false
    }
}
