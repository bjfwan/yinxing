package com.yinxing.launcher.automation.wechat.teaching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeChatLearnedRulePolicyTest {

    @Test
    fun builtInSuccessNeverUsesLearnedSelector() {
        val profile = profile()

        assertNull(
            WeChatLearnedRulePolicy.selectorForFallback(
                profile = profile,
                currentFingerprint = profile.fingerprint,
                action = WeChatTeachingAction.OPEN_SEARCH,
                builtInSucceeded = true
            )
        )
    }

    @Test
    fun compatibleProfileCanProvideFallbackSelector() {
        val profile = profile()

        assertEquals(
            profile.steps.first().selector,
            WeChatLearnedRulePolicy.selectorForFallback(
                profile = profile,
                currentFingerprint = profile.fingerprint,
                action = WeChatTeachingAction.OPEN_SEARCH,
                builtInSucceeded = false
            )
        )
    }

    @Test
    fun deviceOrWechatVersionMismatchRejectsLearnedSelector() {
        val profile = profile()

        assertNull(
            WeChatLearnedRulePolicy.selectorForFallback(
                profile = profile,
                currentFingerprint = profile.fingerprint.copy(weChatVersionCode = 9999),
                action = WeChatTeachingAction.OPEN_SEARCH,
                builtInSucceeded = false
            )
        )
        assertNull(
            WeChatLearnedRulePolicy.selectorForFallback(
                profile = profile,
                currentFingerprint = profile.fingerprint.copy(model = "other"),
                action = WeChatTeachingAction.OPEN_SEARCH,
                builtInSucceeded = false
            )
        )
    }

    @Test
    fun finalCallFallbackRequiresTheRecordedDialogWindow() {
        val profile = profile().copy(
            steps = listOf(
                profile().steps.first().copy(
                    action = WeChatTeachingAction.START_VIDEO_CALL,
                    windowClass = "com.tencent.mm.ui.widget.dialog.a4"
                )
            )
        )

        assertNull(
            WeChatLearnedRulePolicy.selectorForWindowFallback(
                profile,
                WeChatTeachingAction.START_VIDEO_CALL,
                "com.tencent.mm.ui.chatting.ChattingUI"
            )
        )
        assertEquals(
            profile.steps.first().selector,
            WeChatLearnedRulePolicy.selectorForWindowFallback(
                profile,
                WeChatTeachingAction.START_VIDEO_CALL,
                "com.tencent.mm.ui.widget.dialog.a4"
            )
        )
    }

    @Test
    fun learnedFallbackReturnsOnlyTheStepRecordedForTheCurrentPage() {
        val profile = profile()

        assertEquals(
            profile.steps.first(),
            WeChatLearnedRulePolicy.stepForWindowFallback(
                profile = profile,
                action = WeChatTeachingAction.OPEN_SEARCH,
                currentWindowClass = "com.tencent.mm.ui.LauncherUI"
            )
        )
        assertNull(
            WeChatLearnedRulePolicy.stepForWindowFallback(
                profile = profile,
                action = WeChatTeachingAction.OPEN_SEARCH,
                currentWindowClass = "com.tencent.mm.ui.chatting.ChattingUI"
            )
        )
        assertNull(
            WeChatLearnedRulePolicy.stepForWindowFallback(
                profile = profile,
                action = WeChatTeachingAction.OPEN_SEARCH,
                currentWindowClass = null
            )
        )
    }

    @Test
    fun learnedRatiosResolveAgainstTheSameFullScreenCoordinateSpace() {
        val selector = profile().steps.first().selector

        assertEquals(
            WeChatLearnedCoordinate(864f, 240f),
            WeChatLearnedCoordinateResolver.resolve(selector, 1080, 2400)
        )
        assertNull(WeChatLearnedCoordinateResolver.resolve(selector, 0, 2400))
    }

    @Test
    fun lowReliabilityProfileIsNeverActivated() {
        val profile = profile().copy(
            reliabilityScore = 30,
            reliability = WeChatTeachingReliability.LOW
        )

        assertNull(
            WeChatLearnedRulePolicy.compatibleProfile(profile, profile.fingerprint)
        )
    }

    private fun profile(): WeChatTeachingProfile {
        val fingerprint = WeChatTeachingFingerprint(
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
        return WeChatTeachingProfile(
            fingerprint = fingerprint,
            steps = listOf(
                WeChatTeachingStep(
                    action = WeChatTeachingAction.OPEN_SEARCH,
                    windowClass = "com.tencent.mm.ui.LauncherUI",
                    expectedWindowClass = "com.tencent.mm.plugin.fts.ui.FTSMainUI",
                    selector = WeChatTeachingSelector(
                        resourceId = "com.tencent.mm:id/jha",
                        nodeClass = "android.widget.RelativeLayout",
                        semanticLabel = WeChatTeachingSemanticLabel.SEARCH,
                        clickableAncestorDepth = 0,
                        centerXRatio = 0.8f,
                        centerYRatio = 0.1f
                    )
                )
            ),
            reliabilityScore = 90,
            reliability = WeChatTeachingReliability.RELIABLE,
            createdAtEpochMs = 1_000L
        )
    }
}
