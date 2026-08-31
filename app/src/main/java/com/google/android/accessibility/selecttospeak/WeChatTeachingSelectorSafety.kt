package com.google.android.accessibility.selecttospeak

import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingSemanticLabel
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingSelector

internal object WeChatTeachingSelectorSafety {
    fun allowsResourceCandidate(
        selector: WeChatTeachingSelector,
        isVisible: Boolean,
        visibleValues: Sequence<CharSequence?>
    ): Boolean = isVisible && matchesExpectedSemantic(
        expected = selector.semanticLabel,
        visibleValues = visibleValues
    )

    fun matchesExpectedSemantic(
        expected: WeChatTeachingSemanticLabel?,
        visibleValues: Sequence<CharSequence?>
    ): Boolean {
        if (expected == null) return true
        return visibleValues
            .mapNotNull(WeChatTeachingSemanticLabel::fromVisibleValue)
            .any { it == expected }
    }

    fun allowsCoordinateFallback(selector: WeChatTeachingSelector): Boolean =
        selector.semanticLabel == null
}
