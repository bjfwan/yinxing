package com.bajianfeng.launcher.manager

import android.view.accessibility.AccessibilityNodeInfo
import com.bajianfeng.launcher.util.AccessibilityUtil
import kotlinx.coroutines.delay

class StateDetectionManager {
    
    suspend fun waitForState(
        stateName: String,
        timeout: Long,
        checkInterval: Long = 300L,
        validator: () -> Boolean
    ): Boolean {
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeout) {
            if (validator()) {
                return true
            }
            delay(checkInterval)
        }
        
        return false
    }
    
    fun isWeChatLaunched(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        
        val packageName = root.packageName?.toString()
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
        if (root == null) return false
        
        val cancelBtn = AccessibilityUtil.findNodeByText(root, "取消")
        if (cancelBtn != null) {
            cancelBtn.recycle()
            return true
        }
        
        val searchHint = AccessibilityUtil.findNodeByText(root, "搜索")
        if (searchHint != null && searchHint.isEditable) {
            searchHint.recycle()
            return true
        }
        
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
