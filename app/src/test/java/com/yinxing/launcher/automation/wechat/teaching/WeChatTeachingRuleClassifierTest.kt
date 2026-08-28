package com.yinxing.launcher.automation.wechat.teaching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatTeachingRuleClassifierTest {

    @Test
    fun controlsAlreadyCoveredByBuiltInRulesAreVerifiedButNotLearned() {
        val profile = profile(
            listOf(
                step(
                    WeChatTeachingAction.OPEN_SEARCH,
                    "com.tencent.mm:id/jha",
                    WeChatTeachingSemanticLabel.SEARCH
                ),
                step(
                    WeChatTeachingAction.OPEN_MORE,
                    "com.tencent.mm:id/bjz",
                    WeChatTeachingSemanticLabel.MORE
                ),
                step(
                    WeChatTeachingAction.START_VIDEO_CALL,
                    null,
                    WeChatTeachingSemanticLabel.VIDEO_CALL
                )
            )
        )

        val classification = WeChatTeachingRuleClassifier.classify(profile)

        assertEquals(
            setOf(
                WeChatTeachingAction.OPEN_SEARCH,
                WeChatTeachingAction.OPEN_MORE,
                WeChatTeachingAction.START_VIDEO_CALL
            ),
            classification.verifiedActions
        )
        assertTrue(classification.learnedSteps.isEmpty())
    }

    @Test
    fun unknownSelectorConfirmedByTheDemonstrationBecomesADifferenceRule() {
        val unknown = step(
            WeChatTeachingAction.START_VIDEO_CALL,
            "com.tencent.mm:id/device_specific_video",
            null
        )

        val classification = WeChatTeachingRuleClassifier.classify(profile(listOf(unknown)))

        assertTrue(classification.verifiedActions.isEmpty())
        assertEquals(listOf(unknown), classification.learnedSteps)
    }

    private fun profile(steps: List<WeChatTeachingStep>) = WeChatTeachingProfile(
        fingerprint = fingerprint(),
        steps = steps,
        reliabilityScore = 70,
        reliability = WeChatTeachingReliability.USABLE_WITH_POSITION_FALLBACK,
        createdAtEpochMs = 1_000L
    )

    private fun step(
        action: WeChatTeachingAction,
        resourceId: String?,
        semanticLabel: WeChatTeachingSemanticLabel?
    ) = WeChatTeachingStep(
        action = action,
        windowClass = when (action) {
            WeChatTeachingAction.OPEN_SEARCH -> "com.tencent.mm.ui.LauncherUI"
            WeChatTeachingAction.OPEN_CONTACT -> "com.tencent.mm.plugin.fts.ui.FTSMainUI"
            WeChatTeachingAction.OPEN_MORE,
            WeChatTeachingAction.OPEN_VIDEO_MENU -> "com.tencent.mm.ui.chatting.ChattingUI"
            WeChatTeachingAction.START_VIDEO_CALL -> "com.tencent.mm.ui.widget.dialog.a4"
        },
        expectedWindowClass = "expected",
        selector = WeChatTeachingSelector(
            resourceId = resourceId,
            nodeClass = "android.widget.TextView",
            semanticLabel = semanticLabel,
            clickableAncestorDepth = 0,
            centerXRatio = 0.5f,
            centerYRatio = 0.5f
        )
    )

    private fun fingerprint() = WeChatTeachingFingerprint(
        manufacturer = "vivo",
        model = "V2285A",
        androidSdk = 36,
        screenWidth = 1080,
        screenHeight = 2400,
        densityDpi = 440,
        fontScalePermille = 1000,
        localeTag = "zh-CN",
        weChatVersionName = "8.0.76",
        weChatVersionCode = 3141
    )
}
