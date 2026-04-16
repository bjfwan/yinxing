package com.bajianfeng.launcher.automation.wechat.manager

import android.view.accessibility.AccessibilityNodeInfo
import com.bajianfeng.launcher.automation.wechat.util.AccessibilityUtil
import com.google.android.accessibility.selecttospeak.WeChatUiSnapshot
import com.google.android.accessibility.selecttospeak.WeChatUiSnapshotAnalyzer
import kotlinx.coroutines.delay

class StateDetectionManager {

    enum class DetectedPage {
        HOME,
        SEARCH,
        CHAT,
        CONTACT_DETAIL,
        UNKNOWN
    }

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

    /**
     * 主流程统一页面识别入口。
     *
     * 优先使用可回放的 `WeChatUiSnapshotAnalyzer` 识别，必要时再回退到节点树兜底。
     */
    fun detect(root: AccessibilityNodeInfo?, currentClass: String?): DetectedPage? {
        val packageName = root?.packageName?.toString()
        if (packageName != WECHAT_PACKAGE) return null
        return detect(WeChatUiSnapshot.fromNode(root), currentClass, packageName)
    }

    internal fun detect(
        snapshot: WeChatUiSnapshot?,
        currentClass: String?,
        packageName: String? = WECHAT_PACKAGE
    ): DetectedPage? {
        if (packageName != WECHAT_PACKAGE) return null
        if (snapshot == null) return DetectedPage.UNKNOWN

        return when {
            currentClass == CLASS_SEARCH_UI || WeChatUiSnapshotAnalyzer.isSearchPage(snapshot) -> DetectedPage.SEARCH
            currentClass == CLASS_CONTACT_INFO || WeChatUiSnapshotAnalyzer.isContactInfoPage(snapshot) -> DetectedPage.CONTACT_DETAIL
            currentClass == CLASS_CHATTING_UI || WeChatUiSnapshotAnalyzer.isChatPageLike(snapshot) -> DetectedPage.CHAT
            currentClass == CLASS_LAUNCHER_UI || WeChatUiSnapshotAnalyzer.isLauncherReady(snapshot) -> DetectedPage.HOME
            else -> DetectedPage.UNKNOWN
        }
    }

    fun isWeChatLaunched(root: AccessibilityNodeInfo?): Boolean {
        return root?.packageName?.toString() == WECHAT_PACKAGE
    }

    fun isHomePageLoaded(root: AccessibilityNodeInfo?): Boolean {
        return detect(root, currentClass = null) == DetectedPage.HOME
    }

    fun isSearchBoxVisible(root: AccessibilityNodeInfo?): Boolean {
        return detect(root, currentClass = null) == DetectedPage.SEARCH
    }

    fun isContactFound(root: AccessibilityNodeInfo?, contactName: String): Boolean {
        val snapshot = WeChatUiSnapshot.fromNode(root) ?: return false
        return WeChatUiSnapshotAnalyzer.containsContactName(snapshot, contactName)
    }

    fun isChatPageLoaded(root: AccessibilityNodeInfo?): Boolean {
        val snapshot = WeChatUiSnapshot.fromNode(root) ?: return false
        return WeChatUiSnapshotAnalyzer.isChatPageLike(snapshot) ||
            WeChatUiSnapshotAnalyzer.isContactInfoPage(snapshot) ||
            AccessibilityUtil.findNodeByText(root, "视频通话") != null ||
            AccessibilityUtil.findNodeByText(root, "语音通话") != null
    }

    companion object {
        private const val WECHAT_PACKAGE = "com.tencent.mm"
        private const val CLASS_LAUNCHER_UI = "com.tencent.mm.ui.LauncherUI"
        private const val CLASS_CHATTING_UI = "com.tencent.mm.ui.chatting.ChattingUI"
        private const val CLASS_CONTACT_INFO = "com.tencent.mm.plugin.profile.ui.ContactInfoUI"
        private const val CLASS_SEARCH_UI = "com.tencent.mm.plugin.fts.ui.FTSMainUI"
    }
}
