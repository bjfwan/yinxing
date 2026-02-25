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
        
        var score = 0
        
        val packageName = root.packageName?.toString()
        if (packageName == "com.tencent.mm") score++
        
        val searchIcon = AccessibilityUtil.findNodeById(root, "com.tencent.mm:id/f8y")
        if (searchIcon != null) {
            score++
            searchIcon.recycle()
        }
        
        val bottomNav = AccessibilityUtil.findNodeById(root, "com.tencent.mm:id/c6t")
        if (bottomNav != null) {
            score++
            bottomNav.recycle()
        }
        
        return score >= 2
    }
    
    fun isHomePageLoaded(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        
        var score = 0
        
        val searchIcon = AccessibilityUtil.findNodeById(root, "com.tencent.mm:id/f8y")
        if (searchIcon != null) {
            score++
            searchIcon.recycle()
        }
        
        val bottomNav = AccessibilityUtil.findNodeById(root, "com.tencent.mm:id/c6t")
        if (bottomNav != null) {
            score++
            bottomNav.recycle()
        }
        
        val chatList = AccessibilityUtil.findNodeById(root, "com.tencent.mm:id/dxh")
        if (chatList != null && chatList.isScrollable) {
            score++
            chatList.recycle()
        }
        
        return score >= 2
    }
    
    fun isSearchBoxVisible(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        
        var score = 0
        
        val searchBox = AccessibilityUtil.findNodeById(root, "com.tencent.mm:id/cd7")
        if (searchBox != null && searchBox.isEditable) {
            score++
            searchBox.recycle()
        }
        
        val cancelBtn = AccessibilityUtil.findNodeByText(root, "取消")
        if (cancelBtn != null) {
            score++
            cancelBtn.recycle()
        }
        
        return score >= 1
    }
    
    fun isContactFound(root: AccessibilityNodeInfo?, contactName: String): Boolean {
        if (root == null) return false
        
        val resultList = AccessibilityUtil.findNodeById(root, "com.tencent.mm:id/tm")
        if (resultList != null) {
            resultList.recycle()
            return true
        }
        
        val nameNode = AccessibilityUtil.findNodeByText(root, contactName)
        if (nameNode != null) {
            nameNode.recycle()
            return true
        }
        
        return false
    }
    
    fun isChatPageLoaded(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        
        var score = 0
        
        val videoBtn = AccessibilityUtil.findNodeById(root, "com.tencent.mm:id/aop")
        if (videoBtn != null) {
            score++
            videoBtn.recycle()
        }
        
        val inputBox = AccessibilityUtil.findNodeById(root, "com.tencent.mm:id/aou")
        if (inputBox != null) {
            score++
            inputBox.recycle()
        }
        
        return score >= 1
    }
}
