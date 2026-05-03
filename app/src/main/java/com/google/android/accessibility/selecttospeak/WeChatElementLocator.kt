package com.google.android.accessibility.selecttospeak

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.yinxing.launcher.automation.wechat.WeChatViewIds
import com.yinxing.launcher.automation.wechat.util.AccessibilityUtil
import com.yinxing.launcher.common.util.DebugLog

internal data class ContactResultTarget(
    val node: AccessibilityNodeInfo,
    val displayName: String
)

internal data class SearchResultSections(
    val contactHeaderCenterY: Int?,
    val groupHeaderCenterY: Int?,
    val networkHeaderCenterY: Int?
)

internal class WeChatElementLocator(private val service: AccessibilityService) {

    private companion object {
        const val TAG = "WeChatElementLocator"
        const val MATCH_PARENT_LOOKUP_DEPTH = 4
        val DIALOG_CLOSE_TEXTS = listOf("关闭", "我知道了", "稍后再说", "以后再说", "暂不")
        val SEARCH_ENTRY_HINT_TEXTS = listOf("搜索", "Search", "搜索联系人")
        val MORE_BUTTON_HINT_TEXTS = listOf("更多", "更多功能")
        const val TOP_SEARCH_BAR_X_RATIO = 0.82f
        const val TOP_SEARCH_BAR_Y_RATIO = 0.075f
        const val MORE_BUTTON_X_RATIO = 0.93f
        const val MORE_BUTTON_Y_RATIO = 0.045f
    }

    fun clickMessageTab(root: AccessibilityNodeInfo?): Boolean {
        val byId = AccessibilityUtil.findAllById(root, WeChatViewIds.MESSAGE_TAB_ICON).firstOrNull()
        if (byId != null) {
            val success = AccessibilityUtil.performClick(service, byId)
            DebugLog.d(TAG) { "clickMessageTab: by resource-id, click=$success" }
            AccessibilityUtil.safeRecycle(byId)
            if (success) return true
        }
        val byText = AccessibilityUtil.findBestTextNode(root, "微信", exactMatch = true, preferBottom = true)
        if (byText != null) {
            val success = AccessibilityUtil.performClick(service, byText)
            DebugLog.d(TAG) { "clickMessageTab: by text, click=$success" }
            AccessibilityUtil.safeRecycle(byText)
            return success
        }
        return false
    }

    fun clickTopSearchBar(root: AccessibilityNodeInfo?): Boolean {
        val byId = findNodeByIds(root, WeChatViewIds.TOP_SEARCH_BAR_IDS)
        if (byId != null) {
            val success = AccessibilityUtil.performClick(service, byId)
            DebugLog.d(TAG) {
                "clickTopSearchBar: by resource-id node=${AccessibilityUtil.summarizeNode(byId)}, click=$success"
            }
            AccessibilityUtil.safeRecycle(byId)
            if (success) return true
        }
        for (hint in SEARCH_ENTRY_HINT_TEXTS) {
            val byText = AccessibilityUtil.findBestTextNode(
                root,
                hint,
                exactMatch = false,
                preferBottom = false,
                excludeEditable = false
            )
            if (byText != null) {
                val success = AccessibilityUtil.performClick(service, byText)
                DebugLog.d(TAG) {
                    "clickTopSearchBar: by text='$hint' node=${AccessibilityUtil.summarizeNode(byText)}, click=$success"
                }
                AccessibilityUtil.safeRecycle(byText)
                if (success) return true
            }
        }
        val bounds = Rect()
        root?.getBoundsInScreen(bounds)
        if (!bounds.isEmpty) {
            val fallbackX = bounds.left + bounds.width() * TOP_SEARCH_BAR_X_RATIO
            val fallbackY = bounds.top + bounds.height() * TOP_SEARCH_BAR_Y_RATIO
            val success = AccessibilityUtil.clickByCoordinate(service, fallbackX, fallbackY)
            DebugLog.d(TAG) { "clickTopSearchBar: by coordinate x=$fallbackX y=$fallbackY click=$success" }
            if (success) return true
        }
        return false
    }

    fun clickSearchCancel(root: AccessibilityNodeInfo?): Boolean {
        val node = AccessibilityUtil.findBestTextNode(
            root,
            "取消",
            exactMatch = true,
            preferBottom = false,
            excludeEditable = false
        ) ?: return false
        val success = AccessibilityUtil.performClick(service, node)
        AccessibilityUtil.safeRecycle(node)
        return success
    }

    fun clickVideoCallSheetCancel(root: AccessibilityNodeInfo?): Boolean {
        if (!isVideoCallSheetVisible(root)) {
            return false
        }
        val node = AccessibilityUtil.findBestTextNode(
            root,
            "取消",
            exactMatch = true,
            preferBottom = true,
            excludeEditable = false
        ) ?: return false
        val success = AccessibilityUtil.performClick(service, node)
        AccessibilityUtil.safeRecycle(node)
        return success
    }

    fun clickKnownDialogClose(root: AccessibilityNodeInfo?): Boolean {
        for (text in DIALOG_CLOSE_TEXTS) {
            val node = AccessibilityUtil.findBestTextNode(
                root,
                text,
                exactMatch = true,
                preferBottom = true,
                excludeEditable = false
            ) ?: continue
            val success = AccessibilityUtil.performClick(service, node)
            AccessibilityUtil.safeRecycle(node)
            if (success) {
                return true
            }
        }
        return false
    }

    fun clickMoreButton(root: AccessibilityNodeInfo?): Boolean {
        val byId = findNodeByIds(root, WeChatViewIds.MORE_BUTTON_FALLBACK_IDS)
        if (byId != null) {
            val success = AccessibilityUtil.performClick(service, byId)
            DebugLog.d(TAG) { "clickMoreButton: by resource-id, click=$success" }
            AccessibilityUtil.safeRecycle(byId)
            if (success) return true
        }
        for (hint in MORE_BUTTON_HINT_TEXTS) {
            val byDesc = AccessibilityUtil.findBestTextNode(root, hint, exactMatch = false, preferBottom = false)
            if (byDesc != null) {
                val success = AccessibilityUtil.performClick(service, byDesc)
                DebugLog.d(TAG) { "clickMoreButton: by desc='$hint', click=$success" }
                AccessibilityUtil.safeRecycle(byDesc)
                if (success) return true
            }
        }
        val byPlus = AccessibilityUtil.findBestTextNode(root, "+", exactMatch = true, preferBottom = false)
        if (byPlus != null) {
            val success = AccessibilityUtil.performClick(service, byPlus)
            DebugLog.d(TAG) { "clickMoreButton: by text plus, click=$success" }
            AccessibilityUtil.safeRecycle(byPlus)
            if (success) return true
        }
        val bounds = Rect()
        root?.getBoundsInScreen(bounds)
        if (!bounds.isEmpty) {
            val fallbackX = bounds.left + bounds.width() * MORE_BUTTON_X_RATIO
            val fallbackY = bounds.bottom - bounds.height() * MORE_BUTTON_Y_RATIO
            val success = AccessibilityUtil.clickByCoordinate(service, fallbackX, fallbackY)
            DebugLog.d(TAG) { "clickMoreButton: by coordinate x=$fallbackX y=$fallbackY click=$success" }
            if (success) return true
        }
        return false
    }

    fun fillSearchInput(root: AccessibilityNodeInfo?, contactName: String): Boolean {
        val byId = findNodeByIds(root, WeChatViewIds.SEARCH_INPUT)
        if (byId != null) {
            val ok = AccessibilityUtil.setText(byId, contactName)
            AccessibilityUtil.safeRecycle(byId)
            if (ok && verifySearchInputFilled(root, contactName)) return true
        }
        val editableNode = AccessibilityUtil.findFirstEditableNode(root) ?: return false
        val ok = AccessibilityUtil.setText(editableNode, contactName)
        AccessibilityUtil.safeRecycle(editableNode)
        if (!ok) return false
        return verifySearchInputFilled(root, contactName)
    }

    fun verifySearchInputFilled(root: AccessibilityNodeInfo?, contactName: String): Boolean {
        val normalizedName = contactName.trim()
        if (normalizedName.isEmpty()) {
            return false
        }
        val editNode = AccessibilityUtil.findFirstEditableNode(root) ?: return false
        val current = editNode.text?.toString().orEmpty()
        AccessibilityUtil.safeRecycle(editNode)
        return current.trim().contains(normalizedName)
    }

    fun clickVideoCallEntry(root: AccessibilityNodeInfo?): Boolean {
        val node = AccessibilityUtil.findBestTextNode(root, "音视频通话", exactMatch = true, preferBottom = false)
            ?: return false
        val success = AccessibilityUtil.performClick(service, node)
        AccessibilityUtil.safeRecycle(node)
        return success
    }

    fun clickVideoCallOption(root: AccessibilityNodeInfo?): Boolean {
        val node = AccessibilityUtil.findBestTextNode(root, "视频通话", exactMatch = true, preferBottom = true)
            ?: return false
        val success = AccessibilityUtil.performClick(service, node)
        AccessibilityUtil.safeRecycle(node)
        return success
    }

    fun clickVideoCallSheetOption(root: AccessibilityNodeInfo?): Boolean {
        if (!isVideoCallSheetVisible(root)) return false
        val node = AccessibilityUtil.findBestTextNode(root, "视频通话", exactMatch = true, preferBottom = false)
            ?: return false
        val success = AccessibilityUtil.performClick(service, node)
        AccessibilityUtil.safeRecycle(node)
        return success
    }

    fun isVideoCallSheetVisible(root: AccessibilityNodeInfo?): Boolean {
        val snapshot = WeChatUiSnapshot.fromNode(root)
        if (snapshot != null) {
            return WeChatUiSnapshotAnalyzer.isVideoCallSheetVisible(snapshot)
        }
        return hasExactText(root, "视频通话") &&
            hasExactText(root, "语音通话") &&
            hasExactText(root, "取消")
    }

    fun hasExactText(root: AccessibilityNodeInfo?, text: String): Boolean {
        val node = AccessibilityUtil.findBestTextNode(
            root,
            text,
            exactMatch = true,
            preferBottom = false,
            excludeEditable = false
        ) ?: return false
        AccessibilityUtil.safeRecycle(node)
        return true
    }

    fun hasContainingText(root: AccessibilityNodeInfo?, text: String): Boolean {
        val node = AccessibilityUtil.findBestTextNode(
            root,
            text,
            exactMatch = false,
            preferBottom = false,
            excludeEditable = false
        ) ?: return false
        AccessibilityUtil.safeRecycle(node)
        return true
    }

    fun hasNoSearchResult(root: AccessibilityNodeInfo?): Boolean {
        return WeChatUiSnapshot.fromNode(root)?.let(WeChatUiSnapshotAnalyzer::hasNoSearchResult) ?: false
    }

    fun findNodeByIds(root: AccessibilityNodeInfo?, vararg ids: String): AccessibilityNodeInfo? =
        findNodeByIds(root, ids.asIterable())

    fun findNodeByIds(root: AccessibilityNodeInfo?, ids: Iterable<String>): AccessibilityNodeInfo? {
        for (id in ids) {
            val node = AccessibilityUtil.findNodeById(root, id)
            if (node != null) {
                return node
            }
        }
        return null
    }

    fun hasEditableNode(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) {
            return false
        }
        if (root.isEditable || root.className == "android.widget.EditText") {
            return true
        }
        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            val found = hasEditableNode(child)
            AccessibilityUtil.safeRecycle(child)
            if (found) {
                return true
            }
        }
        return false
    }

    fun findContactInMessageList(
        root: AccessibilityNodeInfo?,
        contactNames: Collection<String>
    ): AccessibilityNodeInfo? {
        if (root == null) return null
        val normalizedNames = contactNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (normalizedNames.isEmpty()) return null
        val byId = AccessibilityUtil.findAllById(root, WeChatViewIds.CONTACT_TITLE_SECONDARY)
        val matched = byId.firstOrNull { node ->
            normalizedNames.any { name ->
                node.text?.toString() == name || node.contentDescription?.toString() == name
            }
        }
        byId.forEach { if (it !== matched) AccessibilityUtil.safeRecycle(it) }
        if (matched != null) return matched

        normalizedNames.forEach { contactName ->
            val byDescNodes = AccessibilityUtil.findNodesByContentDescription(root, contactName, exactMatch = true)
            val byDesc = byDescNodes.firstOrNull()
            byDescNodes.forEach { if (it !== byDesc) AccessibilityUtil.safeRecycle(it) }
            if (byDesc != null) return byDesc
        }

        normalizedNames.forEach { contactName ->
            val byText = AccessibilityUtil.findBestTextNode(root, contactName, exactMatch = true, preferBottom = false)
            if (byText != null) return byText
        }
        return null
    }

    fun findContactResultTarget(root: AccessibilityNodeInfo?, contactName: String): ContactResultTarget? {
        if (root == null) return null
        val sections = resolveSearchResultSections(root)
        WeChatViewIds.CONTACT_RESULT_TITLE_IDS.forEach { id ->
            val candidates = AccessibilityUtil.findAllById(root, id)
            var matchedNode: AccessibilityNodeInfo? = null
            var matchedDisplayName: String? = null
            for (node in candidates) {
                val displayName = node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                if (
                    displayName != null &&
                    isInAllowedContactResultSection(node, sections) &&
                    matchesContactResultNode(node, contactName, displayName)
                ) {
                    matchedNode = node
                    matchedDisplayName = displayName
                    break
                }
            }
            candidates.forEach { if (it !== matchedNode) AccessibilityUtil.safeRecycle(it) }
            if (matchedNode != null && matchedDisplayName != null) {
                return ContactResultTarget(matchedNode, matchedDisplayName)
            }
        }
        return null
    }

    fun findNodeByExactText(
        root: AccessibilityNodeInfo?,
        expectedText: String,
        vararg ids: String
    ): AccessibilityNodeInfo? {
        for (id in ids) {
            val nodes = AccessibilityUtil.findAllById(root, id)
            var matched: AccessibilityNodeInfo? = null
            for (node in nodes) {
                if (matchesNodeText(node, expectedText, exactMatch = true)) {
                    matched = node
                    break
                }
            }
            nodes.forEach { node ->
                if (node !== matched) {
                    AccessibilityUtil.safeRecycle(node)
                }
            }
            if (matched != null) {
                return matched
            }
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun matchesContactResultNode(
        node: AccessibilityNodeInfo,
        contactName: String,
        displayName: String
    ): Boolean {
        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        var depth = 0
        while (current != null && depth < MATCH_PARENT_LOOKUP_DEPTH) {
            val snapshot = WeChatUiSnapshot.fromNode(current)
            if (snapshot != null &&
                WeChatUiSnapshotAnalyzer.findContactSearchResultDisplayName(snapshot, contactName) == displayName
            ) {
                AccessibilityUtil.safeRecycle(current)
                return true
            }
            val parent = current.parent
            AccessibilityUtil.safeRecycle(current)
            current = parent
            depth++
        }
        AccessibilityUtil.safeRecycle(current)
        return false
    }

    private fun matchesNodeText(node: AccessibilityNodeInfo?, expectedText: String, exactMatch: Boolean): Boolean {
        val text = node?.text?.toString()
        val desc = node?.contentDescription?.toString()
        return listOfNotNull(text, desc).any { value ->
            if (exactMatch) value == expectedText else value.contains(expectedText)
        }
    }

    private fun resolveSearchResultSections(root: AccessibilityNodeInfo?): SearchResultSections {
        return SearchResultSections(
            contactHeaderCenterY = findSectionHeaderCenterY(root, "联系人"),
            groupHeaderCenterY = findSectionHeaderCenterY(root, "群聊"),
            networkHeaderCenterY = findSectionHeaderCenterY(root, "搜索网络结果")
        )
    }

    private fun findSectionHeaderCenterY(root: AccessibilityNodeInfo?, text: String): Int? {
        val node = AccessibilityUtil.findBestTextNode(
            root,
            text,
            exactMatch = true,
            preferBottom = false,
            excludeEditable = false
        ) ?: return null
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        AccessibilityUtil.safeRecycle(node)
        return if (bounds.isEmpty) null else bounds.centerY()
    }

    private fun isInAllowedContactResultSection(
        node: AccessibilityNodeInfo,
        sections: SearchResultSections
    ): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) {
            return false
        }
        val centerY = bounds.centerY()
        val contactHeaderCenterY = sections.contactHeaderCenterY ?: return false
        if (centerY <= contactHeaderCenterY) return false
        sections.groupHeaderCenterY?.let { if (centerY >= it) return false }
        sections.networkHeaderCenterY?.let { if (centerY >= it) return false }
        return true
    }
}
