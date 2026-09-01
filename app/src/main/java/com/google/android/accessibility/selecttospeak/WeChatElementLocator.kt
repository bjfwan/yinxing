package com.google.android.accessibility.selecttospeak

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.yinxing.launcher.automation.wechat.WeChatViewIds
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingAction
import com.yinxing.launcher.automation.wechat.teaching.WeChatLearnedRulePolicy
import com.yinxing.launcher.automation.wechat.teaching.WeChatLearnedCoordinateResolver
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingProfile
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingSemanticLabel
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingSelector
import com.yinxing.launcher.automation.wechat.util.AccessibilityUtil
import com.yinxing.launcher.common.util.DebugLog

internal data class ContactResultTarget(
    val node: AccessibilityNodeInfo,
    val displayName: String
)

internal data class RecentConversationTarget(
    val node: AccessibilityNodeInfo,
    val hasVideoCallPreview: Boolean
)

internal data class SearchResultSections(
    val groupHeaderCenterY: Int?,
    val networkHeaderCenterY: Int?
)

internal object WeChatSearchResultSectionPolicy {
    fun isAllowed(
        candidateCenterY: Int,
        groupHeaderCenterY: Int?,
        networkHeaderCenterY: Int?
    ): Boolean {
        groupHeaderCenterY?.let { if (candidateCenterY >= it) return false }
        networkHeaderCenterY?.let { if (candidateCenterY >= it) return false }
        return true
    }
}

internal object WeChatContactResultTraversalPolicy {
    private const val MAX_VERIFIED_ROW_ANCESTOR_DEPTH = 5

    fun shouldInspect(depth: Int): Boolean = depth in 0..MAX_VERIFIED_ROW_ANCESTOR_DEPTH
}

internal class WeChatElementLocator(
    private val service: AccessibilityService,
    private val learnedProfileProvider: () -> WeChatTeachingProfile? = { null },
    private val currentWindowClassProvider: () -> String? = { null }
) {

    private companion object {
        const val TAG = "WeChatElementLocator"
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

    fun clickContactsTab(root: AccessibilityNodeInfo?): Boolean {
        val node = AccessibilityUtil.findBestTextNode(
            root,
            "通讯录",
            exactMatch = true,
            preferBottom = true,
            excludeEditable = false
        ) ?: return false
        val success = AccessibilityUtil.performClick(service, node)
        AccessibilityUtil.safeRecycle(node)
        return success
    }

    fun isBottomTabSelected(root: AccessibilityNodeInfo?, label: String): Boolean {
        val candidates = AccessibilityUtil.findAllById(root, WeChatViewIds.MESSAGE_TAB_ICON)
        val selected = candidates.any { node ->
            node.isSelected && (
                node.text?.toString() == label ||
                    node.contentDescription?.toString() == label
                )
        }
        candidates.forEach(AccessibilityUtil::safeRecycle)
        return selected
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
        if (clickLearnedSelector(root, WeChatTeachingAction.OPEN_SEARCH)) return true
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
        if (clickLearnedSelector(root, WeChatTeachingAction.OPEN_MORE)) return true
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
            if (ok) return true
        }
        val editableNode = AccessibilityUtil.findFirstEditableNode(root) ?: return false
        val ok = AccessibilityUtil.setText(editableNode, contactName)
        AccessibilityUtil.safeRecycle(editableNode)
        return ok
    }

    fun verifySearchInputFilled(root: AccessibilityNodeInfo?, contactName: String): Boolean {
        val normalizedName = contactName.trim()
        if (normalizedName.isEmpty()) {
            return false
        }
        val editNode = findNodeByIds(root, WeChatViewIds.SEARCH_INPUT)
            ?: AccessibilityUtil.findFirstEditableNode(root)
            ?: return false
        val current = editNode.text?.toString().orEmpty()
        AccessibilityUtil.safeRecycle(editNode)
        return current.trim().contains(normalizedName)
    }

    fun clickVideoCallEntry(root: AccessibilityNodeInfo?, allowLearnedFallback: Boolean = false): Boolean {
        val node = AccessibilityUtil.findBestTextNode(root, "音视频通话", exactMatch = true, preferBottom = false)
        if (node != null) {
            val success = AccessibilityUtil.performClick(service, node)
            AccessibilityUtil.safeRecycle(node)
            if (success) return true
        }
        return allowLearnedFallback &&
            clickLearnedSelector(root, WeChatTeachingAction.OPEN_VIDEO_MENU)
    }

    fun clickVideoCallOption(root: AccessibilityNodeInfo?): Boolean {
        val node = AccessibilityUtil.findBestTextNode(root, "视频通话", exactMatch = true, preferBottom = true)
            ?: return false
        val success = AccessibilityUtil.performClick(service, node)
        AccessibilityUtil.safeRecycle(node)
        return success
    }

    fun clickVideoCallSheetOption(
        root: AccessibilityNodeInfo?,
        allowLearnedFallback: Boolean = false
    ): Boolean {
        if (isVideoCallSheetVisible(root)) {
            val node = AccessibilityUtil.findBestTextNode(root, "视频通话", exactMatch = true, preferBottom = false)
            if (node != null) {
                val success = AccessibilityUtil.performClick(service, node)
                AccessibilityUtil.safeRecycle(node)
                if (success) return true
            }
        }
        return allowLearnedFallback &&
            clickLearnedSelector(root, WeChatTeachingAction.START_VIDEO_CALL)
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
            isSafeVisibleListTarget(node) && normalizedNames.any { name ->
                node.text?.toString() == name || node.contentDescription?.toString() == name
            }
        }
        byId.forEach { if (it !== matched) AccessibilityUtil.safeRecycle(it) }
        if (matched != null) return matched

        normalizedNames.forEach { contactName ->
            val byDescNodes = AccessibilityUtil.findNodesByContentDescription(root, contactName, exactMatch = true)
            val byDesc = byDescNodes.firstOrNull(::isSafeVisibleListTarget)
            byDescNodes.forEach { if (it !== byDesc) AccessibilityUtil.safeRecycle(it) }
            if (byDesc != null) return byDesc
        }

        normalizedNames.forEach { contactName ->
            val byText = AccessibilityUtil.findBestTextNode(root, contactName, exactMatch = true, preferBottom = false)
            if (byText != null && isSafeVisibleListTarget(byText)) return byText
            AccessibilityUtil.safeRecycle(byText)
        }
        return null
    }

    fun findContactResultTarget(root: AccessibilityNodeInfo?, contactName: String): ContactResultTarget? {
        if (root == null) return null
        val sections = resolveSearchResultSections(root)
        val learnedId = WeChatLearnedRulePolicy.stepForWindowFallback(
            profile = learnedProfileProvider(),
            action = WeChatTeachingAction.OPEN_CONTACT,
            currentWindowClass = currentWindowClassProvider()
        )
            ?.selector
            ?.resourceId
            ?.takeIf(::isSafeWeChatResourceId)
        (WeChatViewIds.CONTACT_RESULT_TITLE_IDS + listOfNotNull(learnedId)).forEach { id ->
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

    fun findRecentConversationTarget(
        root: AccessibilityNodeInfo?,
        contactNames: Collection<String>
    ): RecentConversationTarget? {
        val node = findContactInMessageList(root, contactNames) ?: return null
        return RecentConversationTarget(
            node = node,
            hasVideoCallPreview = hasVideoCallPreviewInAncestor(node, contactNames)
        )
    }

    fun findContactInContactsList(
        root: AccessibilityNodeInfo?,
        contactNames: Collection<String>
    ): AccessibilityNodeInfo? {
        if (root == null) return null
        val normalizedNames = contactNames.map(String::trim).filter(String::isNotEmpty).distinct()
        for (contactName in normalizedNames) {
            val textNodes = AccessibilityUtil.findAllByText(root, contactName)
            val textMatch = textNodes.firstOrNull { node ->
                isSafeVisibleListTarget(node) && matchesNodeText(node, contactName, exactMatch = true)
            }
            textNodes.forEach { if (it !== textMatch) AccessibilityUtil.safeRecycle(it) }
            if (textMatch != null) return textMatch

            val descriptionNodes = AccessibilityUtil.findNodesByContentDescription(
                root,
                contactName,
                exactMatch = true
            )
            val descriptionMatch = descriptionNodes.firstOrNull(::isSafeVisibleListTarget)
            descriptionNodes.forEach { if (it !== descriptionMatch) AccessibilityUtil.safeRecycle(it) }
            if (descriptionMatch != null) return descriptionMatch
        }
        return null
    }

    fun scrollContactsForward(root: AccessibilityNodeInfo?): Boolean =
        performScrollForward(root)

    fun findLatestVisibleMessageBubble(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val rootRect = Rect().also(root::getBoundsInScreen)
        if (rootRect.isEmpty) return null
        val allCandidates = AccessibilityUtil.findAllById(root, WeChatViewIds.MESSAGE_BUBBLE)
        val candidates = allCandidates.filter { it.isVisibleToUser }
        allCandidates.forEach { if (it !in candidates) AccessibilityUtil.safeRecycle(it) }
        val candidateBounds = candidates.associateWith { node ->
            Rect().also(node::getBoundsInScreen).let { bounds ->
                WeChatUiBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
            }
        }
        val selectedBounds = WeChatHistoryMessageCandidatePolicy.chooseLatestVisible(
            rootBounds = WeChatUiBounds(rootRect.left, rootRect.top, rootRect.right, rootRect.bottom),
            candidates = candidateBounds.values.toList()
        )
        val selected = candidateBounds.entries.firstOrNull { it.value == selectedBounds }?.key
        candidates.forEach { if (it !== selected) AccessibilityUtil.safeRecycle(it) }
        return selected
    }

    fun clickChatInfoButton(root: AccessibilityNodeInfo?): Boolean {
        val byId = findNodeByIds(root, WeChatViewIds.CHAT_INFO_BUTTON_IDS)
        if (byId != null) {
            val success = AccessibilityUtil.performClick(service, byId)
            AccessibilityUtil.safeRecycle(byId)
            if (success) return true
        }
        val byDescription = AccessibilityUtil.findBestTextNode(
            root,
            "更多信息",
            exactMatch = false,
            preferBottom = false,
            excludeEditable = false
        ) ?: return false
        val success = AccessibilityUtil.performClick(service, byDescription)
        AccessibilityUtil.safeRecycle(byDescription)
        return success
    }

    fun clickChatInfoContact(
        root: AccessibilityNodeInfo?,
        contactNames: Collection<String>
    ): Boolean {
        val normalizedNames = contactNames.map(String::trim).filter(String::isNotEmpty).distinct()
        if (normalizedNames.isEmpty()) return false
        val candidates = AccessibilityUtil.findAllById(root, WeChatViewIds.CHAT_INFO_CONTACT_NAME)
        val target = candidates.firstOrNull { node ->
            normalizedNames.any { name -> matchesNodeText(node, name, exactMatch = true) }
        }
        candidates.forEach { if (it !== target) AccessibilityUtil.safeRecycle(it) }
        if (target == null) return false
        val success = AccessibilityUtil.performClick(service, target)
        AccessibilityUtil.safeRecycle(target)
        return success
    }

    private fun clickLearnedSelector(
        root: AccessibilityNodeInfo?,
        action: WeChatTeachingAction
    ): Boolean {
        val currentWindowClass = currentWindowClassProvider()
        val step = WeChatLearnedRulePolicy.stepForWindowFallback(
            profile = learnedProfileProvider(),
            action = action,
            currentWindowClass = currentWindowClass
        )
        if (step == null) {
            DebugLog.d(TAG) {
                "clickLearnedSelector: action=$action rejected for window=$currentWindowClass"
            }
            return false
        }
        return clickTeachingSelector(root, step.selector, action)
    }

    fun clickTeachingSelector(
        root: AccessibilityNodeInfo?,
        selector: WeChatTeachingSelector
    ): Boolean = clickTeachingSelector(root, selector, null)

    private fun clickTeachingSelector(
        root: AccessibilityNodeInfo?,
        selector: WeChatTeachingSelector,
        learnedAction: WeChatTeachingAction?
    ): Boolean {
        val expectedSemantic = selector.semanticLabel
        val resourceId = selector.resourceId?.takeIf(::isSafeWeChatResourceId)
        if (resourceId != null) {
            val candidates = AccessibilityUtil.findAllById(root, resourceId)
            val node = candidates.firstOrNull { candidate ->
                matchesTeachingSelector(candidate, selector)
            }
            candidates.forEach { candidate ->
                if (candidate !== node) AccessibilityUtil.safeRecycle(candidate)
            }
            if (node != null) {
                val success = AccessibilityUtil.performClick(service, node)
                AccessibilityUtil.safeRecycle(node)
                learnedAction?.let { action ->
                    DebugLog.d(TAG) { "clickLearnedSelector: action=$action byId=$success" }
                }
                if (success) return true
            }
        }
        if (expectedSemantic != null) {
            val semanticNode = findTeachingSemanticNode(root, expectedSemantic)
            if (semanticNode != null) {
                val success = AccessibilityUtil.performClick(service, semanticNode)
                AccessibilityUtil.safeRecycle(semanticNode)
                if (success) return true
            }
            DebugLog.w(
                TAG,
                "clickTeachingSelector: expected=$expectedSemantic not visible; refusing blind click"
            )
        }
        if (!WeChatTeachingSelectorSafety.allowsCoordinateFallback(selector)) return false
        return clickLearnedCoordinate(
            learnedAction ?: WeChatTeachingAction.OPEN_SEARCH,
            selector
        )
    }

    private fun matchesTeachingSelector(
        node: AccessibilityNodeInfo,
        selector: WeChatTeachingSelector
    ): Boolean {
        val snapshot = WeChatUiSnapshot.fromNode(node, maxDepth = 4, maxNodes = 40)
        return WeChatTeachingSelectorSafety.allowsResourceCandidate(
            selector = selector,
            isVisible = node.isVisibleToUser,
            visibleValues = snapshot?.flatten()?.flatMap { candidate ->
                sequenceOf(candidate.text, candidate.contentDescription)
            } ?: emptySequence()
        )
    }

    private fun matchesTeachingSemantic(
        node: AccessibilityNodeInfo,
        expected: WeChatTeachingSemanticLabel?
    ): Boolean {
        val snapshot = WeChatUiSnapshot.fromNode(node, maxDepth = 4, maxNodes = 40)
            ?: return expected == null
        return WeChatTeachingSelectorSafety.matchesExpectedSemantic(
            expected = expected,
            visibleValues = snapshot.flatten().flatMap { candidate ->
                sequenceOf(candidate.text, candidate.contentDescription)
            }
        )
    }

    private fun findTeachingSemanticNode(
        root: AccessibilityNodeInfo?,
        label: WeChatTeachingSemanticLabel
    ): AccessibilityNodeInfo? {
        val hints = when (label) {
            WeChatTeachingSemanticLabel.SEARCH -> listOf("搜索", "Search", "搜索联系人")
            WeChatTeachingSemanticLabel.MORE -> listOf("更多功能按钮", "更多", "更多功能")
            WeChatTeachingSemanticLabel.AUDIO_VIDEO_MENU -> listOf("音视频通话")
            WeChatTeachingSemanticLabel.VIDEO_CALL -> listOf("视频通话")
            WeChatTeachingSemanticLabel.VOICE_CALL -> listOf("语音通话")
        }
        for (hint in hints) {
            val candidate = AccessibilityUtil.findBestTextNode(
                root = root,
                text = hint,
                exactMatch = false,
                preferBottom = label == WeChatTeachingSemanticLabel.MORE,
                excludeEditable = false
            ) ?: continue
            if (candidate.isVisibleToUser && matchesTeachingSemantic(candidate, label)) {
                return candidate
            }
            AccessibilityUtil.safeRecycle(candidate)
        }
        return null
    }

    fun scrollTeachingSelector(
        root: AccessibilityNodeInfo?,
        selector: WeChatTeachingSelector?
    ): Boolean {
        val byId = selector?.resourceId
            ?.takeIf(::isSafeWeChatResourceId)
            ?.let { AccessibilityUtil.findNodeById(root, it) }
        val target = byId ?: root ?: return false
        val success = target.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        if (byId != null) AccessibilityUtil.safeRecycle(byId)
        return success
    }

    private fun clickLearnedCoordinate(
        action: WeChatTeachingAction,
        selector: WeChatTeachingSelector
    ): Boolean {
        val metrics = service.resources.displayMetrics
        val coordinate = WeChatLearnedCoordinateResolver.resolve(
            selector,
            metrics.widthPixels,
            metrics.heightPixels
        ) ?: return false
        val success = AccessibilityUtil.clickByCoordinate(service, coordinate.x, coordinate.y)
        DebugLog.d(TAG) { "clickLearnedSelector: action=$action byPosition=$success" }
        return success
    }

    private fun isSafeWeChatResourceId(value: String): Boolean =
        value.matches(Regex("^com\\.tencent\\.mm:id/[A-Za-z0-9_]+$"))

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
        while (current != null && WeChatContactResultTraversalPolicy.shouldInspect(depth)) {
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
        return WeChatSearchResultSectionPolicy.isAllowed(
            candidateCenterY = bounds.centerY(),
            groupHeaderCenterY = sections.groupHeaderCenterY,
            networkHeaderCenterY = sections.networkHeaderCenterY
        )
    }

    @Suppress("DEPRECATION")
    private fun hasVideoCallPreviewInAncestor(
        node: AccessibilityNodeInfo,
        contactNames: Collection<String>
    ): Boolean {
        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        var depth = 0
        while (current != null && WeChatContactResultTraversalPolicy.shouldInspect(depth)) {
            val snapshot = WeChatUiSnapshot.fromNode(current, maxDepth = 6, maxNodes = 80)
            if (
                snapshot != null &&
                WeChatUiSnapshotAnalyzer.hasRecentVideoCallPreview(snapshot, contactNames)
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

    private fun performScrollForward(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (
            node.isVisibleToUser &&
            node.isScrollable &&
            node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        ) {
            return true
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val success = performScrollForward(child)
            AccessibilityUtil.safeRecycle(child)
            if (success) return true
        }
        return false
    }

    private fun isSafeVisibleListTarget(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser) return false
        val bounds = Rect().also(node::getBoundsInScreen)
        return WeChatListTargetBoundsPolicy.isSafe(
            WeChatUiBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
        )
    }
}
