package com.bajianfeng.launcher.manager

import android.view.accessibility.AccessibilityNodeInfo
import com.bajianfeng.launcher.util.AccessibilityUtil
import kotlinx.coroutines.delay

class StateDetectionManager {

    suspend fun waitForState(
        stateName: String,
        timeout: Long,
        checkInterval: Long = 500L,
        validator: () -> Boolean
    ): Boolean {
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeout) {
            if (validator()) return true
            delay(checkInterval)
        }

        return false
    }

    fun isWeChatLaunched(root: AccessibilityNodeInfo?): Boolean {
        return root?.packageName?.toString() == "com.tencent.mm"
    }

    fun isHomePageLoaded(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        if (root.packageName?.toString() != "com.tencent.mm") return false

        val searchText = AccessibilityUtil.findNodeByText(root, "搜索")
        if (searchText != null) {
            AccessibilityUtil.safeRecycle(searchText)
            return true
        }

        val wechatText = AccessibilityUtil.findNodeByText(root, "微信")
        if (wechatText != null) {
            AccessibilityUtil.safeRecycle(wechatText)
            return true
        }

        return true
    }

    fun isSearchBoxVisible(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false

        val cancelBtn = AccessibilityUtil.findNodeByText(root, "取消")
        if (cancelBtn != null) {
            AccessibilityUtil.safeRecycle(cancelBtn)
            return true
        }

        val searchHint = AccessibilityUtil.findNodeByText(root, "搜索")
        if (searchHint != null && searchHint.isEditable) {
            AccessibilityUtil.safeRecycle(searchHint)
            return true
        }

        return false
    }

    fun isContactFound(root: AccessibilityNodeInfo?, contactName: String): Boolean {
        if (root == null) return false

        val nameNode = AccessibilityUtil.findNodeByText(root, contactName)
        if (nameNode != null) {
            AccessibilityUtil.safeRecycle(nameNode)
            return true
        }

        return false
    }

    fun isChatPageLoaded(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false

        val videoText = AccessibilityUtil.findNodeByText(root, "视频通话")
        if (videoText != null) {
            AccessibilityUtil.safeRecycle(videoText)
            return true
        }

        val voiceText = AccessibilityUtil.findNodeByText(root, "语音通话")
        if (voiceText != null) {
            AccessibilityUtil.safeRecycle(voiceText)
            return true
        }

        return false
    }
}
