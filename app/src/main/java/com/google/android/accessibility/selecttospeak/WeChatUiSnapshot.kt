package com.google.android.accessibility.selecttospeak

import android.view.accessibility.AccessibilityNodeInfo
import com.yinxing.launcher.automation.wechat.WeChatViewIds
import com.yinxing.launcher.automation.wechat.util.AccessibilityUtil

internal data class WeChatUiSnapshot(
    val text: String? = null,
    val contentDescription: String? = null,
    val viewIdResourceName: String? = null,
    val className: String? = null,
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val children: List<WeChatUiSnapshot> = emptyList()
) {
    fun flatten(): Sequence<WeChatUiSnapshot> = sequence {
        yield(this@WeChatUiSnapshot)
        children.forEach { child ->
            yieldAll(child.flatten())
        }
    }

    companion object {
        fun fromNode(
            root: AccessibilityNodeInfo?,
            maxDepth: Int = 8,
            maxNodes: Int = 180
        ): WeChatUiSnapshot? {
            if (root == null) return null
            var visited = 0

            fun build(node: AccessibilityNodeInfo?, depth: Int): WeChatUiSnapshot? {
                if (node == null || visited >= maxNodes) return null
                visited++
                val children = if (depth >= maxDepth) {
                    emptyList()
                } else {
                    buildList {
                        for (index in 0 until node.childCount) {
                            val child = node.getChild(index)
                            try {
                                build(child, depth + 1)?.let(::add)
                            } finally {
                                AccessibilityUtil.safeRecycle(child)
                            }
                        }
                    }
                }
                return WeChatUiSnapshot(
                    text = node.text?.toString(),
                    contentDescription = node.contentDescription?.toString(),
                    viewIdResourceName = node.viewIdResourceName,
                    className = node.className?.toString(),
                    clickable = node.isClickable,
                    editable = node.isEditable || node.className == "android.widget.EditText",
                    children = children
                )
            }

            return build(root, depth = 0)
        }
    }
}

internal enum class WeChatDismissAction {
    NONE,
    SEARCH_CANCEL,
    SHEET_CANCEL,
    CLOSE_DIALOG
}

internal object WeChatUiSnapshotAnalyzer {
    private val noSearchResultTexts = listOf("无搜索结果", "没有找到", "无结果")
    private val closeDialogTexts = listOf("关闭", "我知道了", "稍后再说", "以后再说", "暂不")
    private val contactResultTitleIds = WeChatViewIds.CONTACT_RESULT_TITLE_IDS
    private val contactSecondaryFieldLabels = listOf("昵称", "微信号", "微信昵称", "账号")

    fun isSearchPage(snapshot: WeChatUiSnapshot): Boolean {
        return hasEditableNode(snapshot) && (
            hasExactText(snapshot, "取消") ||
                hasExactText(snapshot, "搜索") ||
                hasExactText(snapshot, "搜索指定内容")
            )
    }

    fun isContactInfoPage(snapshot: WeChatUiSnapshot): Boolean {
        return hasExactText(snapshot, "音视频通话") || hasExactText(snapshot, "发消息")
    }

    fun isLauncherReady(snapshot: WeChatUiSnapshot): Boolean {
        if (isSearchPage(snapshot) || isContactInfoPage(snapshot) || isChatPageLike(snapshot)) {
            return false
        }
        val tabs = listOf("微信", "通讯录", "发现", "我")
        return tabs.count { hasExactText(snapshot, it) } >= 2
    }

    fun isChatPageLike(snapshot: WeChatUiSnapshot): Boolean {
        if (!hasEditableNode(snapshot)) {
            return false
        }
        return hasConversationChrome(snapshot)
    }

    fun containsContactName(snapshot: WeChatUiSnapshot, contactName: String): Boolean {
        return containsAnyContactName(snapshot, listOf(contactName))
    }

    fun containsAnyContactName(snapshot: WeChatUiSnapshot, contactNames: Collection<String>): Boolean {
        val normalizedNames = contactNames.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (normalizedNames.isEmpty()) {
            return false
        }
        return snapshot.flatten().any { node ->
            val text = node.text?.trim()
            val description = node.contentDescription?.trim()
            normalizedNames.any { name -> text == name || description == name }
        }
    }

    fun findContactSearchResultDisplayName(snapshot: WeChatUiSnapshot, contactName: String): String? {
        val normalizedName = contactName.trim()
        if (normalizedName.isEmpty()) {
            return null
        }
        val displayName = snapshot.flatten()
            .firstNotNullOfOrNull { node ->
                if (node.viewIdResourceName !in contactResultTitleIds) {
                    return@firstNotNullOfOrNull null
                }
                node.text?.trim()?.takeIf { it.isNotEmpty() }
                    ?: node.contentDescription?.trim()?.takeIf { it.isNotEmpty() }
            }
            ?: return null
        if (displayName == normalizedName) {
            return displayName
        }
        val texts = snapshot.flatten()
            .flatMap { node -> sequenceOf(node.text, node.contentDescription) }
            .mapNotNull { value -> value?.trim()?.takeIf { it.isNotEmpty() } }
        return if (texts.any { matchesContactSecondaryField(it, normalizedName) }) displayName else null
    }

    fun isVideoCallSheetVisible(snapshot: WeChatUiSnapshot): Boolean {
        return hasExactText(snapshot, "视频通话") &&
            hasExactText(snapshot, "语音通话") &&
            hasExactText(snapshot, "取消")
    }

    fun hasNoSearchResult(snapshot: WeChatUiSnapshot): Boolean {
        return noSearchResultTexts.any { text -> hasContainingText(snapshot, text) }
    }

    fun suggestDismissAction(snapshot: WeChatUiSnapshot): WeChatDismissAction {
        return when {
            isVideoCallSheetVisible(snapshot) -> WeChatDismissAction.SHEET_CANCEL
            isSearchPage(snapshot) && hasExactText(snapshot, "取消") -> WeChatDismissAction.SEARCH_CANCEL
            closeDialogTexts.any { text -> hasExactText(snapshot, text) } -> WeChatDismissAction.CLOSE_DIALOG
            else -> WeChatDismissAction.NONE
        }
    }

    private fun matchesContactSecondaryField(text: String, contactName: String): Boolean {
        val compactText = text.trim().replace(" ", "")
        val compactName = contactName.trim().replace(" ", "")
        if (compactName.isEmpty()) {
            return false
        }
        return contactSecondaryFieldLabels.any { label ->
            (compactText.startsWith("$label:") || compactText.startsWith("$label：")) &&
                compactText.substring(label.length + 1).contains(compactName)
        }
    }

    private fun hasConversationChrome(snapshot: WeChatUiSnapshot): Boolean {
        val ids = WeChatViewIds.MORE_BUTTON_BASE_IDS + WeChatViewIds.MESSAGE_TAB_ICON
        if (snapshot.flatten().any { node -> node.viewIdResourceName in ids }) {
            return true
        }
        return hasContainingText(snapshot, "更多") || hasExactText(snapshot, "+")
    }

    private fun hasEditableNode(snapshot: WeChatUiSnapshot): Boolean {
        return snapshot.flatten().any { node -> node.editable }
    }

    private fun hasExactText(snapshot: WeChatUiSnapshot, expectedText: String): Boolean {
        return snapshot.flatten().any { node ->
            node.text == expectedText || node.contentDescription == expectedText
        }
    }

    private fun hasContainingText(snapshot: WeChatUiSnapshot, expectedText: String): Boolean {
        return snapshot.flatten().any { node ->
            node.text?.contains(expectedText) == true ||
                node.contentDescription?.contains(expectedText) == true
        }
    }
}
