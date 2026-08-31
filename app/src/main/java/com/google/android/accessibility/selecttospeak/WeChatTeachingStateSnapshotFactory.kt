package com.google.android.accessibility.selecttospeak

import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingSemanticLabel
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingStateFingerprint

internal object WeChatTeachingStateSnapshotFactory {
    private val safeResourceId = Regex("^com\\.tencent\\.mm:id/[A-Za-z0-9_]+$")

    fun create(
        windowClass: String?,
        snapshot: WeChatUiSnapshot?
    ): WeChatTeachingStateFingerprint {
        val labels = linkedSetOf<WeChatTeachingSemanticLabel>()
        val ids = linkedSetOf<String>()
        fun collect(node: WeChatUiSnapshot?) {
            if (node == null || !node.visibleToUser) return
            node.viewIdResourceName?.takeIf(safeResourceId::matches)?.let(ids::add)
            WeChatTeachingSemanticLabel.fromVisibleValue(node.contentDescription)?.let(labels::add)
            WeChatTeachingSemanticLabel.fromVisibleValue(node.text)?.let(labels::add)
            node.children.forEach(::collect)
        }
        collect(snapshot)
        return WeChatTeachingStateFingerprint(
            windowClass = windowClass?.takeIf { it.startsWith("com.tencent.mm.") },
            semanticLabels = labels,
            resourceIds = ids
        )
    }
}
