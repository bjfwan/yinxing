package com.yinxing.launcher.automation.wechat.teaching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatTeachingRouteSafetyTest {

    @Test
    fun terminalVideoStepDropsActionsRecordedAfterTheCallConnected() {
        val videoStep = step(
            type = WeChatTeachingRouteStepType.CLICK_CONTROL,
            label = WeChatTeachingSemanticLabel.VIDEO_CALL,
            expectedWindow = "com.tencent.mm.ui.LauncherUI"
        )
        val polluted = route(
            startWindow = "com.tencent.mm.ui.chatting.ChattingUI",
            steps = listOf(
                step(
                    WeChatTeachingRouteStepType.CLICK_CONTROL,
                    WeChatTeachingSemanticLabel.MORE,
                    "com.tencent.mm.plugin.voip.ui.VideoActivity"
                ),
                step(
                    WeChatTeachingRouteStepType.CLICK_CONTROL,
                    WeChatTeachingSemanticLabel.AUDIO_VIDEO_MENU
                ),
                videoStep,
                step(WeChatTeachingRouteStepType.SCROLL_FORWARD),
                step(WeChatTeachingRouteStepType.GO_BACK)
            )
        )

        val safe = WeChatTeachingRouteSafetyPolicy.prepareForReplay(polluted)

        requireNotNull(safe)
        assertEquals(3, safe.steps.size)
        assertEquals(videoStep.selector, safe.steps.last().selector)
        assertEquals(
            "com.tencent.mm.plugin.voip.ui.VideoActivity",
            safe.steps.last().expectedState?.windowClass
        )
        assertFalse(WeChatTeachingRouteTerminalPolicy.isTerminal(safe.steps.first()))
        assertEquals(
            setOf(WeChatTeachingSemanticLabel.AUDIO_VIDEO_MENU),
            safe.steps.first().expectedState?.semanticLabels
        )
        assertTrue(WeChatTeachingRouteTerminalPolicy.isTerminal(safe.steps.last()))
    }

    @Test
    fun homeRouteWithoutContactPlaceholderIsRejected() {
        val unsafe = route(
            startWindow = "com.tencent.mm.ui.LauncherUI",
            steps = listOf(
                step(WeChatTeachingRouteStepType.CLICK_CONTROL, WeChatTeachingSemanticLabel.SEARCH),
                step(WeChatTeachingRouteStepType.CLICK_CONTROL),
                step(WeChatTeachingRouteStepType.CLICK_CONTROL, WeChatTeachingSemanticLabel.VIDEO_CALL)
            )
        )

        assertNull(WeChatTeachingRouteSafetyPolicy.prepareForReplay(unsafe))
    }

    @Test
    fun chatSuffixRouteCanBeReplayedWithoutAContactPlaceholder() {
        val suffix = route(
            startWindow = "com.tencent.mm.ui.chatting.ChattingUI",
            steps = listOf(
                step(WeChatTeachingRouteStepType.CLICK_CONTROL, WeChatTeachingSemanticLabel.MORE),
                step(
                    WeChatTeachingRouteStepType.CLICK_CONTROL,
                    WeChatTeachingSemanticLabel.AUDIO_VIDEO_MENU
                ),
                step(WeChatTeachingRouteStepType.CLICK_CONTROL, WeChatTeachingSemanticLabel.VIDEO_CALL)
            )
        )

        assertEquals(3, WeChatTeachingRouteSafetyPolicy.prepareForReplay(suffix)?.steps?.size)
    }

    @Test
    fun alreadyOpenVideoMenuDoesNotReplayTheMoreButton() {
        val suffix = route(
            startWindow = "com.tencent.mm.ui.chatting.ChattingUI",
            startLabels = setOf(WeChatTeachingSemanticLabel.AUDIO_VIDEO_MENU),
            steps = listOf(
                step(WeChatTeachingRouteStepType.CLICK_CONTROL, WeChatTeachingSemanticLabel.MORE),
                step(
                    WeChatTeachingRouteStepType.CLICK_CONTROL,
                    WeChatTeachingSemanticLabel.AUDIO_VIDEO_MENU
                ),
                step(
                    WeChatTeachingRouteStepType.CLICK_CONTROL,
                    WeChatTeachingSemanticLabel.VIDEO_CALL,
                    "com.tencent.mm.plugin.voip.ui.VideoActivity"
                )
            )
        )

        val safe = WeChatTeachingRouteSafetyPolicy.prepareForReplay(suffix)

        requireNotNull(safe)
        assertEquals(
            listOf(
                WeChatTeachingSemanticLabel.AUDIO_VIDEO_MENU,
                WeChatTeachingSemanticLabel.VIDEO_CALL
            ),
            safe.steps.mapNotNull { it.selector?.semanticLabel }
        )
    }

    @Test
    fun confirmedVideoFreezesFurtherRecording() {
        assertTrue(WeChatTeachingCapturePolicy.shouldCapture(videoCallConfirmed = false))
        assertFalse(WeChatTeachingCapturePolicy.shouldCapture(videoCallConfirmed = true))
    }

    @Test
    fun confirmedVideoStillTracksTheLatestWechatWindow() {
        val state = WeChatTeachingCapturePolicy.decide(
            videoCallConfirmed = true,
            currentWindowClass = "com.tencent.mm.plugin.voip.ui.VideoActivity",
            observedWindowClass = "com.tencent.mm.ui.chatting.ChattingUI"
        )

        assertFalse(state.shouldCapture)
        assertEquals("com.tencent.mm.ui.chatting.ChattingUI", state.currentWindowClass)
    }

    @Test
    fun audioVideoMenuIsNotMistakenForTheFinalVideoChoice() {
        val safe = WeChatTeachingRouteSafetyPolicy.prepareForReplay(
            route(
                startWindow = "com.tencent.mm.ui.chatting.ChattingUI",
                steps = listOf(
                    step(WeChatTeachingRouteStepType.CLICK_CONTROL, WeChatTeachingSemanticLabel.MORE),
                    step(WeChatTeachingRouteStepType.SCROLL_FORWARD),
                    step(
                        WeChatTeachingRouteStepType.CLICK_CONTROL,
                        WeChatTeachingSemanticLabel.AUDIO_VIDEO_MENU
                    ),
                    step(
                        WeChatTeachingRouteStepType.CLICK_CONTROL,
                        WeChatTeachingSemanticLabel.VIDEO_CALL,
                        "com.tencent.mm.plugin.voip.ui.VideoActivity"
                    )
                )
            )
        )

        requireNotNull(safe)
        assertEquals(
            listOf(
                WeChatTeachingSemanticLabel.MORE,
                WeChatTeachingSemanticLabel.AUDIO_VIDEO_MENU,
                WeChatTeachingSemanticLabel.VIDEO_CALL
            ),
            safe.steps.mapNotNull { it.selector?.semanticLabel }
        )
        assertFalse(safe.steps.any { it.type == WeChatTeachingRouteStepType.SCROLL_FORWARD })
        assertEquals(
            setOf(WeChatTeachingSemanticLabel.AUDIO_VIDEO_MENU),
            safe.steps.first().expectedState?.semanticLabels
        )
        assertTrue(WeChatTeachingRouteTerminalPolicy.isTerminal(safe.steps.last()))
    }

    @Test
    fun oldTwoStepChatRouteThatSkipsTheVideoChoiceIsRejected() {
        val unsafe = route(
            startWindow = "com.tencent.mm.ui.chatting.ChattingUI",
            steps = listOf(
                step(WeChatTeachingRouteStepType.CLICK_CONTROL, WeChatTeachingSemanticLabel.MORE),
                step(
                    WeChatTeachingRouteStepType.CLICK_CONTROL,
                    WeChatTeachingSemanticLabel.VIDEO_CALL,
                    "com.tencent.mm.plugin.voip.ui.VideoActivity"
                )
            )
        )

        assertNull(WeChatTeachingRouteSafetyPolicy.prepareForReplay(unsafe))
    }

    @Test
    fun repeatedVisibleCandidatesDoNotReplayTheMoreMenuTwice() {
        val captured = route(
            startWindow = "com.tencent.mm.ui.LauncherUI",
            steps = listOf(
                step(
                    WeChatTeachingRouteStepType.CLICK_CONTROL,
                    WeChatTeachingSemanticLabel.SEARCH
                ).withId("search"),
                step(WeChatTeachingRouteStepType.INPUT_CONTACT_PLACEHOLDER),
                step(WeChatTeachingRouteStepType.CLICK_CONTROL).withId("contact"),
                step(
                    WeChatTeachingRouteStepType.CLICK_CONTROL,
                    WeChatTeachingSemanticLabel.MORE
                ).withId("more-first"),
                step(
                    WeChatTeachingRouteStepType.CLICK_CONTROL,
                    WeChatTeachingSemanticLabel.AUDIO_VIDEO_MENU
                ).withId("audio-video"),
                step(
                    WeChatTeachingRouteStepType.CLICK_CONTROL,
                    WeChatTeachingSemanticLabel.MORE
                ).withId("more-duplicate"),
                step(WeChatTeachingRouteStepType.CLICK_CONTROL).withId("stale-container"),
                step(
                    WeChatTeachingRouteStepType.CLICK_CONTROL,
                    WeChatTeachingSemanticLabel.VIDEO_CALL,
                    "com.tencent.mm.plugin.voip.ui.VideoActivity"
                ).withId("video-choice")
            )
        )

        val safe = WeChatTeachingRouteSafetyPolicy.prepareForReplay(captured)

        requireNotNull(safe)
        assertEquals(
            listOf(
                "com.tencent.mm:id/search",
                "com.tencent.mm:id/contact",
                "com.tencent.mm:id/more-first",
                "com.tencent.mm:id/audio-video",
                "com.tencent.mm:id/video-choice"
            ),
            safe.steps.mapNotNull { it.selector?.resourceId }
        )
        assertEquals(1, safe.steps.count {
            it.selector?.semanticLabel == WeChatTeachingSemanticLabel.MORE
        })
        assertEquals(1, safe.steps.count {
            it.selector?.semanticLabel == WeChatTeachingSemanticLabel.AUDIO_VIDEO_MENU
        })
        assertEquals(1, safe.steps.count {
            it.selector?.semanticLabel == WeChatTeachingSemanticLabel.VIDEO_CALL
        })
    }

    private fun route(
        startWindow: String,
        startLabels: Set<WeChatTeachingSemanticLabel> = emptySet(),
        steps: List<WeChatTeachingRouteStep>
    ) = WeChatTeachingRoute(
        routeId = "captured-route",
        fingerprint = WeChatTeachingFingerprint(
            "vivo", "V2285A", 35, 1080, 2400, 480, 1000, "zh-CN", "8.0.76", 3141
        ),
        startState = WeChatTeachingStateFingerprint(startWindow, startLabels, emptySet()),
        steps = steps,
        endEvidence = WeChatTeachingRouteEndEvidence.VIDEO_CALL_CONFIRMED,
        source = WeChatTeachingRouteSource.DEMONSTRATION,
        priority = 0,
        reliabilityScore = 72,
        lifecycle = WeChatTeachingRouteLifecycle.CANDIDATE,
        createdAtEpochMs = 1L
    )

    private fun step(
        type: WeChatTeachingRouteStepType,
        label: WeChatTeachingSemanticLabel? = null,
        expectedWindow: String? = null
    ) = WeChatTeachingRouteStep(
        type = type,
        selector = if (type in setOf(
                WeChatTeachingRouteStepType.CLICK_CONTROL,
                WeChatTeachingRouteStepType.CLICK_RELATIVE_POSITION
            )
        ) {
            WeChatTeachingSelector(
                resourceId = "com.tencent.mm:id/safe",
                nodeClass = "android.widget.TextView",
                semanticLabel = label,
                clickableAncestorDepth = 0,
                centerXRatio = 0.5f,
                centerYRatio = 0.5f
            )
        } else {
            null
        },
        expectedState = expectedWindow?.let {
            WeChatTeachingStateFingerprint(it, emptySet(), emptySet())
        }
    )

    private fun WeChatTeachingRouteStep.withId(id: String) = copy(
        selector = requireNotNull(selector).copy(resourceId = "com.tencent.mm:id/$id")
    )
}
