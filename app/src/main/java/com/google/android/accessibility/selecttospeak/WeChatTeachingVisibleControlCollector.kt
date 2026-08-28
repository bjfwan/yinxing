package com.google.android.accessibility.selecttospeak

import com.yinxing.launcher.automation.wechat.WeChatClassNames
import com.yinxing.launcher.automation.wechat.WeChatPackage
import com.yinxing.launcher.automation.wechat.WeChatViewIds
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingAction
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingObservation
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingObservationKind
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingObservationSource
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingSelector
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingSemanticLabel

internal data class WeChatTeachingVisibleControl(
    val action: WeChatTeachingAction,
    val observation: WeChatTeachingObservation
)

internal object WeChatTeachingVisibleControlCollector {
    private const val VIDEO_DIALOG_PREFIX = "com.tencent.mm.ui.widget.dialog."
    private const val VIDEO_ACTIVITY = "com.tencent.mm.plugin.voip.ui.VideoActivity"

    fun canonicalWindowClass(snapshot: WeChatUiSnapshot?, observedClass: String?): String? {
        if (observedClass.orEmpty().startsWith(VIDEO_DIALOG_PREFIX) || observedClass == VIDEO_ACTIVITY) {
            return observedClass
        }
        if (snapshot == null) return observedClass
        return when {
            WeChatUiSnapshotAnalyzer.isSearchPage(snapshot) -> WeChatClassNames.SEARCH_UI
            WeChatUiSnapshotAnalyzer.isChatPageLike(snapshot) -> WeChatClassNames.CHATTING_UI
            WeChatUiSnapshotAnalyzer.isContactInfoPage(snapshot) -> WeChatClassNames.CONTACT_INFO
            else -> observedClass
        }
    }

    fun collect(
        snapshot: WeChatUiSnapshot?,
        activeWindowClass: String?,
        screenWidth: Int,
        screenHeight: Int,
        elapsedMs: Long
    ): List<WeChatTeachingVisibleControl> {
        if (snapshot == null || screenWidth <= 0 || screenHeight <= 0) return emptyList()
        val nodes = snapshot.flatten().toList()
        val candidates = when {
            activeWindowClass == WeChatClassNames.LAUNCHER_UI -> listOfNotNull(
                findControl(
                    nodes,
                    WeChatTeachingAction.OPEN_SEARCH,
                    WeChatTeachingSemanticLabel.SEARCH,
                    screenWidth,
                    screenHeight
                ) { node ->
                    node.viewIdResourceName in WeChatViewIds.TOP_SEARCH_BAR_IDS ||
                        semantic(node) == WeChatTeachingSemanticLabel.SEARCH
                }
            )
            activeWindowClass == WeChatClassNames.SEARCH_UI -> listOfNotNull(
                findControl(
                    nodes,
                    WeChatTeachingAction.OPEN_CONTACT,
                    null,
                    screenWidth,
                    screenHeight
                ) { node -> node.viewIdResourceName in WeChatViewIds.CONTACT_RESULT_TITLE_IDS }
            )
            activeWindowClass == WeChatClassNames.CHATTING_UI -> listOfNotNull(
                findControl(
                    nodes,
                    WeChatTeachingAction.OPEN_MORE,
                    WeChatTeachingSemanticLabel.MORE,
                    screenWidth,
                    screenHeight
                ) { node ->
                    node.viewIdResourceName in WeChatViewIds.MORE_BUTTON_FALLBACK_IDS ||
                        semantic(node) == WeChatTeachingSemanticLabel.MORE ||
                        node.text == "+" || node.contentDescription == "+"
                },
                findControl(
                    nodes,
                    WeChatTeachingAction.OPEN_VIDEO_MENU,
                    WeChatTeachingSemanticLabel.VIDEO_CALL,
                    screenWidth,
                    screenHeight
                ) { node ->
                    node.text == "音视频通话" ||
                        node.contentDescription == "音视频通话" ||
                        (
                            (node.text == "视频通话" || node.contentDescription == "视频通话") &&
                                node.bounds?.centerY?.let { it >= screenHeight / 2 } == true
                            )
                }
            )
            activeWindowClass == WeChatClassNames.CONTACT_INFO -> listOfNotNull(
                findControl(
                    nodes,
                    WeChatTeachingAction.OPEN_VIDEO_MENU,
                    WeChatTeachingSemanticLabel.VIDEO_CALL,
                    screenWidth,
                    screenHeight
                ) { node -> node.text == "音视频通话" || node.contentDescription == "音视频通话" }
            )
            activeWindowClass.orEmpty().startsWith(VIDEO_DIALOG_PREFIX) -> listOfNotNull(
                findControl(
                    nodes,
                    WeChatTeachingAction.START_VIDEO_CALL,
                    WeChatTeachingSemanticLabel.VIDEO_CALL,
                    screenWidth,
                    screenHeight
                ) { node -> node.text == "视频通话" || node.contentDescription == "视频通话" }
            )
            else -> emptyList()
        }
        return candidates.map { (action, selector) ->
            WeChatTeachingVisibleControl(
                action = action,
                observation = WeChatTeachingObservation(
                    kind = WeChatTeachingObservationKind.CLICK,
                    windowClass = activeWindowClass,
                    selector = selector,
                    elapsedMs = elapsedMs.coerceAtLeast(0L),
                    source = WeChatTeachingObservationSource.VISIBLE_CONTROL
                )
            )
        }
    }

    private fun findControl(
        nodes: List<WeChatUiSnapshot>,
        action: WeChatTeachingAction,
        forcedLabel: WeChatTeachingSemanticLabel?,
        screenWidth: Int,
        screenHeight: Int,
        predicate: (WeChatUiSnapshot) -> Boolean
    ): Pair<WeChatTeachingAction, WeChatTeachingSelector>? {
        val node = nodes.firstOrNull { candidate ->
            predicate(candidate) && candidate.bounds != null && (
                candidate.viewIdResourceName?.startsWith("${WeChatPackage.NAME}:id/") == true ||
                    candidate.className != null ||
                    forcedLabel != null
                )
        } ?: return null
        val bounds = requireNotNull(node.bounds)
        return action to WeChatTeachingSelector(
            resourceId = node.viewIdResourceName
                ?.takeIf { it.startsWith("${WeChatPackage.NAME}:id/") }
                ?.take(160),
            nodeClass = node.className?.take(200),
            semanticLabel = forcedLabel,
            clickableAncestorDepth = if (node.clickable) 0 else -1,
            centerXRatio = ((bounds.left + bounds.right) / 2f / screenWidth).coerceIn(0f, 1f),
            centerYRatio = ((bounds.top + bounds.bottom) / 2f / screenHeight).coerceIn(0f, 1f)
        )
    }

    private fun semantic(node: WeChatUiSnapshot): WeChatTeachingSemanticLabel? =
        WeChatTeachingSemanticLabel.fromVisibleValue(node.contentDescription)
            ?: WeChatTeachingSemanticLabel.fromVisibleValue(node.text)
}
