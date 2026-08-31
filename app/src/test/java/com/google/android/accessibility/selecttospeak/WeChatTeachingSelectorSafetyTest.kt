package com.google.android.accessibility.selecttospeak

import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingSemanticLabel
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingSelector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatTeachingSelectorSafetyTest {

    @Test
    fun reusedResourceIdCannotTurnVideoCallIntoTransfer() {
        assertFalse(
            WeChatTeachingSelectorSafety.matchesExpectedSemantic(
                expected = WeChatTeachingSemanticLabel.VIDEO_CALL,
                visibleValues = sequenceOf("转账", null)
            )
        )
        assertTrue(
            WeChatTeachingSelectorSafety.matchesExpectedSemantic(
                expected = WeChatTeachingSemanticLabel.VIDEO_CALL,
                visibleValues = sequenceOf("视频通话")
            )
        )
    }

    @Test
    fun learnedResourceCandidateMustBeVisibleAndMatchRecordedSemantic() {
        val videoSelector = selector(WeChatTeachingSemanticLabel.VIDEO_CALL)

        assertFalse(
            WeChatTeachingSelectorSafety.allowsResourceCandidate(
                selector = videoSelector,
                isVisible = true,
                visibleValues = sequenceOf("聊天信息", "转账")
            )
        )
        assertFalse(
            WeChatTeachingSelectorSafety.allowsResourceCandidate(
                selector = videoSelector,
                isVisible = false,
                visibleValues = sequenceOf("视频通话")
            )
        )
        assertTrue(
            WeChatTeachingSelectorSafety.allowsResourceCandidate(
                selector = videoSelector,
                isVisible = true,
                visibleValues = sequenceOf("视频通话")
            )
        )
    }

    @Test
    fun semanticSelectorCannotFallBackToBlindCoordinates() {
        val semanticSelector = selector(WeChatTeachingSemanticLabel.VIDEO_CALL)
        val positionalSelector = selector(null)

        assertFalse(WeChatTeachingSelectorSafety.allowsCoordinateFallback(semanticSelector))
        assertTrue(WeChatTeachingSelectorSafety.allowsCoordinateFallback(positionalSelector))
    }

    @Test
    fun audioVideoEntryAndFinalVideoChoiceAreDifferentSemantics() {
        assertTrue(
            WeChatTeachingSelectorSafety.matchesExpectedSemantic(
                expected = WeChatTeachingSemanticLabel.AUDIO_VIDEO_MENU,
                visibleValues = sequenceOf("音视频通话")
            )
        )
        assertFalse(
            WeChatTeachingSelectorSafety.matchesExpectedSemantic(
                expected = WeChatTeachingSemanticLabel.VIDEO_CALL,
                visibleValues = sequenceOf("音视频通话")
            )
        )
    }

    private fun selector(label: WeChatTeachingSemanticLabel?) = WeChatTeachingSelector(
        resourceId = "com.tencent.mm:id/reused",
        nodeClass = "android.widget.TextView",
        semanticLabel = label,
        clickableAncestorDepth = 0,
        centerXRatio = 0.5f,
        centerYRatio = 0.5f
    )
}
