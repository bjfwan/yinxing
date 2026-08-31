package com.yinxing.launcher.automation.wechat.teaching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatTeachingGenericAnalyzerTest {

    @Test
    fun capturedVisibleStartStateIsPreservedWithoutBrittleResourceIds() {
        val initialState = WeChatTeachingStateFingerprint(
            windowClass = "com.tencent.mm.ui.chatting.ChattingUI",
            semanticLabels = setOf(
                WeChatTeachingSemanticLabel.MORE,
                WeChatTeachingSemanticLabel.AUDIO_VIDEO_MENU
            ),
            resourceIds = setOf("com.tencent.mm:id/dynamic")
        )
        val observations = listOf(
            observation(
                WeChatTeachingObservationKind.CLICK,
                "com.tencent.mm.ui.chatting.ChattingUI",
                selector().copy(semanticLabel = WeChatTeachingSemanticLabel.AUDIO_VIDEO_MENU),
                100L
            ),
            observation(
                WeChatTeachingObservationKind.WINDOW,
                "com.tencent.mm.ui.widget.dialog.a4",
                null,
                150L
            ),
            observation(
                WeChatTeachingObservationKind.CLICK,
                "com.tencent.mm.ui.widget.dialog.a4",
                selector().copy(semanticLabel = WeChatTeachingSemanticLabel.VIDEO_CALL),
                175L
            ),
            observation(
                WeChatTeachingObservationKind.WINDOW,
                "com.tencent.mm.plugin.voip.ui.VideoActivity",
                null,
                200L
            )
        )

        val route = (WeChatTeachingGenericAnalyzer.analyze(
            observations = observations,
            fingerprint = fingerprint(),
            videoCallConfirmed = true,
            createdAtEpochMs = 1_000L,
            initialState = initialState
        ) as WeChatTeachingGenericResult.Complete).route

        assertEquals(initialState.windowClass, route.startState.windowClass)
        assertEquals(initialState.semanticLabels, route.startState.semanticLabels)
        assertTrue(route.startState.resourceIds.isEmpty())
    }

    @Test
    fun arbitrarySuccessfulObservationSequenceBecomesGenericRoute() {
        val observations = listOf(
            observation(WeChatTeachingObservationKind.LAUNCH, "com.tencent.mm.ui.LauncherUI", null, 0L),
            observation(WeChatTeachingObservationKind.CLICK, "com.tencent.mm.ui.LauncherUI", selector(), 100L),
            observation(WeChatTeachingObservationKind.INPUT_CONTACT, "com.tencent.mm.plugin.fts.ui.FTSMainUI", selector(), 200L),
            observation(WeChatTeachingObservationKind.SCROLL, "com.tencent.mm.plugin.fts.ui.FTSMainUI", selector(), 300L),
            observation(WeChatTeachingObservationKind.BACK, "com.tencent.mm.ui.chatting.ChattingUI", null, 400L),
            observation(WeChatTeachingObservationKind.WINDOW, "com.tencent.mm.plugin.voip.ui.VideoActivity", null, 500L)
        )

        val result = WeChatTeachingGenericAnalyzer.analyze(
            observations = observations,
            fingerprint = fingerprint(),
            videoCallConfirmed = true,
            createdAtEpochMs = 1_000L
        )

        assertTrue(result is WeChatTeachingGenericResult.Complete)
        val route = (result as WeChatTeachingGenericResult.Complete).route
        assertEquals(
            listOf(
                WeChatTeachingRouteStepType.LAUNCH_WECHAT,
                WeChatTeachingRouteStepType.CLICK_CONTROL,
                WeChatTeachingRouteStepType.INPUT_CONTACT_PLACEHOLDER,
                WeChatTeachingRouteStepType.SCROLL_FORWARD,
                WeChatTeachingRouteStepType.GO_BACK,
                WeChatTeachingRouteStepType.WAIT_FOR_STATE
            ),
            route.steps.map { it.type }
        )
        assertEquals(WeChatTeachingRouteLifecycle.CANDIDATE, route.lifecycle)
        assertFalse(route.toString().contains("张三"))
    }

    @Test
    fun activeSampleRequiresSubsequentStateEvidence() {
        val sampled = observation(
            WeChatTeachingObservationKind.CLICK,
            "com.tencent.mm.ui.widget.dialog.a4",
            selector().copy(semanticLabel = WeChatTeachingSemanticLabel.VIDEO_CALL),
            100L,
            WeChatTeachingObservationSource.VISIBLE_CONTROL
        )

        val withoutEvidence = WeChatTeachingGenericAnalyzer.analyze(
            listOf(sampled),
            fingerprint(),
            videoCallConfirmed = true,
            createdAtEpochMs = 1_000L
        ) as WeChatTeachingGenericResult.Incomplete
        val withEvidence = WeChatTeachingGenericAnalyzer.analyze(
            listOf(
                sampled,
                observation(
                    WeChatTeachingObservationKind.WINDOW,
                    "com.tencent.mm.plugin.voip.ui.VideoActivity",
                    null,
                    200L
                )
            ),
            fingerprint(),
            videoCallConfirmed = true,
            createdAtEpochMs = 1_000L
        )

        assertTrue(withoutEvidence.route == null || withoutEvidence.route.steps.isEmpty())
        assertTrue(withEvidence is WeChatTeachingGenericResult.Complete)
    }

    @Test
    fun voiceOrUnconfirmedCallNeverCompletesRoute() {
        val observations = listOf(
            observation(WeChatTeachingObservationKind.CLICK, "com.tencent.mm.ui.widget.dialog.a4", selector(), 100L),
            observation(WeChatTeachingObservationKind.WINDOW, "com.tencent.mm.plugin.voip.ui.VideoActivity", null, 200L)
        )

        val result = WeChatTeachingGenericAnalyzer.analyze(
            observations,
            fingerprint(),
            videoCallConfirmed = false,
            createdAtEpochMs = 1_000L
        )

        assertTrue(result is WeChatTeachingGenericResult.Incomplete)
        assertEquals(WeChatTeachingGenericFailure.VIDEO_NOT_CONFIRMED, (result as WeChatTeachingGenericResult.Incomplete).reason)
    }

    @Test
    fun realClickWinsOverEarlierVisibleCandidatesForTheSameTransition() {
        val visibleWrong = observation(
            WeChatTeachingObservationKind.CLICK,
            "com.tencent.mm.ui.widget.dialog.a4",
            selector().copy(
                resourceId = "com.tencent.mm:id/wrong",
                semanticLabel = WeChatTeachingSemanticLabel.VIDEO_CALL
            ),
            100L,
            WeChatTeachingObservationSource.VISIBLE_CONTROL
        )
        val real = observation(
            WeChatTeachingObservationKind.CLICK,
            "com.tencent.mm.ui.widget.dialog.a4",
            selector().copy(resourceId = "com.tencent.mm:id/actual"),
            150L
        )
        val call = observation(
            WeChatTeachingObservationKind.WINDOW,
            "com.tencent.mm.plugin.voip.ui.VideoActivity",
            null,
            200L
        )

        val route = (WeChatTeachingGenericAnalyzer.analyze(
            listOf(visibleWrong, real, call),
            fingerprint(),
            videoCallConfirmed = true,
            createdAtEpochMs = 1_000L
        ) as WeChatTeachingGenericResult.Complete).route

        assertEquals(
            listOf("com.tencent.mm:id/actual"),
            route.steps.mapNotNull { it.selector?.resourceId }
        )
        assertEquals(
            WeChatTeachingSemanticLabel.VIDEO_CALL,
            route.steps.first {
                it.selector?.resourceId == "com.tencent.mm:id/actual"
            }.selector?.semanticLabel
        )
    }

    @Test
    fun callPageWithoutAnyReusableActionIsUploadedAsUnknownInsteadOfSavedAsRoute() {
        val result = WeChatTeachingGenericAnalyzer.analyze(
            observations = listOf(
                observation(
                    WeChatTeachingObservationKind.WINDOW,
                    "com.tencent.mm.plugin.voip.ui.VideoActivity",
                    null,
                    200L
                )
            ),
            fingerprint = fingerprint(),
            videoCallConfirmed = true,
            createdAtEpochMs = 1_000L
        )

        assertTrue(result is WeChatTeachingGenericResult.Incomplete)
        assertEquals(
            WeChatTeachingGenericFailure.NO_REUSABLE_STEPS,
            (result as WeChatTeachingGenericResult.Incomplete).reason
        )
        assertTrue(result.route == null)
    }

    @Test
    fun actionsObservedAfterVideoConfirmationAreNotPartOfTheRoute() {
        val observations = listOf(
            observation(
                WeChatTeachingObservationKind.WINDOW,
                "com.tencent.mm.ui.chatting.ChattingUI",
                null,
                0L
            ),
            observation(
                WeChatTeachingObservationKind.CLICK,
                "com.tencent.mm.ui.chatting.ChattingUI",
                selector().copy(semanticLabel = WeChatTeachingSemanticLabel.MORE),
                100L
            ),
            observation(
                WeChatTeachingObservationKind.CLICK,
                "com.tencent.mm.ui.chatting.ChattingUI",
                selector().copy(semanticLabel = WeChatTeachingSemanticLabel.AUDIO_VIDEO_MENU),
                150L
            ),
            observation(
                WeChatTeachingObservationKind.CLICK,
                "com.tencent.mm.ui.widget.dialog.a4",
                selector().copy(semanticLabel = WeChatTeachingSemanticLabel.VIDEO_CALL),
                200L
            ),
            observation(
                WeChatTeachingObservationKind.WINDOW,
                "com.tencent.mm.plugin.voip.ui.VideoActivity",
                null,
                300L
            ),
            observation(
                WeChatTeachingObservationKind.SCROLL,
                "com.tencent.mm.ui.chatting.ChattingUI",
                selector(),
                400L
            ),
            observation(
                WeChatTeachingObservationKind.BACK,
                "com.tencent.mm.ui.chatting.ChattingUI",
                null,
                500L
            )
        )

        val route = (WeChatTeachingGenericAnalyzer.analyze(
            observations,
            fingerprint(),
            videoCallConfirmed = true,
            createdAtEpochMs = 1_000L
        ) as WeChatTeachingGenericResult.Complete).route

        assertEquals(WeChatTeachingSemanticLabel.VIDEO_CALL, route.steps.last().selector?.semanticLabel)
        assertFalse(route.steps.any { it.type == WeChatTeachingRouteStepType.GO_BACK })
        assertFalse(route.steps.any { it.type == WeChatTeachingRouteStepType.SCROLL_FORWARD })
    }

    @Test
    fun consecutiveVisibleControlsOnTheSamePageRemainReplayableInOrder() {
        val observations = listOf(
            observation(
                WeChatTeachingObservationKind.WINDOW,
                "com.tencent.mm.ui.chatting.ChattingUI",
                null,
                0L
            ),
            observation(
                WeChatTeachingObservationKind.CLICK,
                "com.tencent.mm.ui.chatting.ChattingUI",
                selector().copy(semanticLabel = WeChatTeachingSemanticLabel.MORE),
                100L,
                WeChatTeachingObservationSource.VISIBLE_CONTROL
            ),
            observation(
                WeChatTeachingObservationKind.CLICK,
                "com.tencent.mm.ui.chatting.ChattingUI",
                selector().copy(semanticLabel = WeChatTeachingSemanticLabel.AUDIO_VIDEO_MENU),
                150L,
                WeChatTeachingObservationSource.VISIBLE_CONTROL
            ),
            observation(
                WeChatTeachingObservationKind.WINDOW,
                "com.tencent.mm.ui.widget.dialog.a4",
                null,
                200L
            ),
            observation(
                WeChatTeachingObservationKind.CLICK,
                "com.tencent.mm.ui.widget.dialog.a4",
                selector().copy(semanticLabel = WeChatTeachingSemanticLabel.VIDEO_CALL),
                250L
            ),
            observation(
                WeChatTeachingObservationKind.WINDOW,
                "com.tencent.mm.plugin.voip.ui.VideoActivity",
                null,
                300L
            )
        )

        val route = (WeChatTeachingGenericAnalyzer.analyze(
            observations,
            fingerprint(),
            videoCallConfirmed = true,
            createdAtEpochMs = 1_000L
        ) as WeChatTeachingGenericResult.Complete).route

        assertEquals(
            listOf(
                WeChatTeachingSemanticLabel.MORE,
                WeChatTeachingSemanticLabel.AUDIO_VIDEO_MENU,
                WeChatTeachingSemanticLabel.VIDEO_CALL
            ),
            route.steps.mapNotNull { it.selector?.semanticLabel }
        )
    }

    private fun observation(
        kind: WeChatTeachingObservationKind,
        windowClass: String,
        selector: WeChatTeachingSelector?,
        elapsed: Long,
        source: WeChatTeachingObservationSource = WeChatTeachingObservationSource.ACCESSIBILITY_EVENT
    ) = WeChatTeachingObservation(kind, windowClass, selector, elapsed, source)

    private fun selector() = WeChatTeachingSelector(
        "com.tencent.mm:id/safe",
        "android.widget.TextView",
        null,
        0,
        0.5f,
        0.5f
    )

    private fun fingerprint() = WeChatTeachingFingerprint(
        "vivo", "V2285A", 36, 1080, 2400, 440, 1000, "zh-CN", "8.0.76", 3141
    )
}
