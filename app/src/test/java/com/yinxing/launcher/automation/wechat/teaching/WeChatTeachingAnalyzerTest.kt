package com.yinxing.launcher.automation.wechat.teaching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatTeachingAnalyzerTest {

    private val fingerprint = WeChatTeachingFingerprint(
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

    @Test
    fun completeOrderedDemonstrationCreatesDeviceRule() {
        val result = WeChatTeachingAnalyzer.analyze(
            observations = completeObservations(),
            fingerprint = fingerprint,
            createdAtEpochMs = 1_000L
        )

        assertTrue(result is WeChatTeachingResult.Complete)
        val profile = (result as WeChatTeachingResult.Complete).profile
        assertEquals(
            listOf(
                WeChatTeachingAction.OPEN_SEARCH,
                WeChatTeachingAction.OPEN_CONTACT,
                WeChatTeachingAction.OPEN_MORE,
                WeChatTeachingAction.OPEN_VIDEO_MENU,
                WeChatTeachingAction.START_VIDEO_CALL
            ),
            profile.steps.map { it.action }
        )
        assertTrue(profile.reliabilityScore >= 80)
        assertEquals(WeChatTeachingReliability.RELIABLE, profile.reliability)
        assertEquals(fingerprint, profile.fingerprint)
    }

    @Test
    fun reachingVideoActivityIsRequiredBeforeRuleIsComplete() {
        val result = WeChatTeachingAnalyzer.analyze(
            observations = completeObservations().dropLast(1),
            fingerprint = fingerprint,
            createdAtEpochMs = 1_000L
        )

        assertTrue(result is WeChatTeachingResult.Incomplete)
        assertTrue(
            (result as WeChatTeachingResult.Incomplete).missing.contains(
                WeChatTeachingRequirement.CALL_PAGE_REACHED
            )
        )
    }

    @Test
    fun outOfOrderActionsDoNotCreateRule() {
        val observations = completeObservations().toMutableList().apply {
            val more = removeAt(2)
            add(4, more)
        }

        val result = WeChatTeachingAnalyzer.analyze(
            observations = observations,
            fingerprint = fingerprint,
            createdAtEpochMs = 1_000L
        )

        assertTrue(result is WeChatTeachingResult.Incomplete)
    }

    @Test
    fun arbitraryContactAndChatTextAreNeverConvertedToSemanticLabels() {
        assertNull(WeChatTeachingSemanticLabel.fromVisibleValue("wan."))
        assertNull(WeChatTeachingSemanticLabel.fromVisibleValue("今晚记得视频"))
        assertNull(WeChatTeachingSemanticLabel.fromVisibleValue("13800138000"))
        assertEquals(
            WeChatTeachingSemanticLabel.SEARCH,
            WeChatTeachingSemanticLabel.fromVisibleValue("搜索")
        )
        assertEquals(
            WeChatTeachingSemanticLabel.MORE,
            WeChatTeachingSemanticLabel.fromVisibleValue("更多功能按钮，已折叠")
        )
        assertEquals(
            WeChatTeachingSemanticLabel.VIDEO_CALL,
            WeChatTeachingSemanticLabel.fromVisibleValue("视频通话")
        )
    }

    @Test
    fun contactWhoseNameLooksLikeAControlIsStillTreatedAsContactWithoutKeepingLabel() {
        val observations = completeObservations().toMutableList()
        observations[1] = observations[1].copy(
            selector = observations[1].selector?.copy(
                semanticLabel = WeChatTeachingSemanticLabel.VIDEO_CALL
            )
        )

        val result = WeChatTeachingAnalyzer.analyze(
            observations = observations,
            fingerprint = fingerprint,
            createdAtEpochMs = 1_000L
        )

        assertTrue(result is WeChatTeachingResult.Complete)
        val contactSelector = (result as WeChatTeachingResult.Complete)
            .profile
            .steps
            .first { it.action == WeChatTeachingAction.OPEN_CONTACT }
            .selector
        assertNull(contactSelector.semanticLabel)
    }

    @Test
    fun generatedProfileContainsNoObservedFreeFormTextField() {
        val profile = (
            WeChatTeachingAnalyzer.analyze(
                observations = completeObservations(),
                fingerprint = fingerprint,
                createdAtEpochMs = 1_000L
            ) as WeChatTeachingResult.Complete
            ).profile

        val rendered = profile.toString()
        assertFalse(rendered.contains("wan.", ignoreCase = true))
        assertFalse(rendered.contains("今晚记得视频"))
    }

    @Test
    fun completedFlowWithOnlyWeakPositionSelectorsIsRejected() {
        val weak = completeObservations().map { observation ->
            observation.copy(
                selector = observation.selector?.copy(
                    resourceId = null,
                    nodeClass = null
                )
            )
        }

        val result = WeChatTeachingAnalyzer.analyze(
            observations = weak,
            fingerprint = fingerprint,
            createdAtEpochMs = 1_000L
        )

        assertTrue(result is WeChatTeachingResult.Incomplete)
        assertTrue(
            (result as WeChatTeachingResult.Incomplete).missing.contains(
                WeChatTeachingRequirement.SELECTOR_QUALITY
            )
        )
    }

    @Test
    fun weakSemanticBuiltInStepIsKeptForVerificationButStillFailsRuleQuality() {
        val weakVideoMenu = click(
            windowClass = "com.tencent.mm.ui.chatting.ChattingUI",
            label = WeChatTeachingSemanticLabel.VIDEO_CALL,
            id = null,
            x = 0.62f,
            y = 0.72f
        ).copy(
            source = WeChatTeachingObservationSource.VISIBLE_CONTROL,
            selector = WeChatTeachingSelector(
                resourceId = null,
                nodeClass = null,
                semanticLabel = WeChatTeachingSemanticLabel.VIDEO_CALL,
                clickableAncestorDepth = -1,
                centerXRatio = 0.62f,
                centerYRatio = 0.72f
            )
        )
        val dialog = window("com.tencent.mm.ui.widget.dialog.a4", 1_000L)

        val result = WeChatTeachingAnalyzer.analyze(
            listOf(weakVideoMenu, dialog),
            fingerprint,
            2_000L
        ) as WeChatTeachingResult.Incomplete

        assertEquals(
            listOf(WeChatTeachingAction.OPEN_VIDEO_MENU),
            result.profile?.steps?.map { it.action }
        )
        assertTrue(result.missing.contains(WeChatTeachingRequirement.SELECTOR_QUALITY))
    }

    @Test
    fun successfulFlowKeepsEveryStrongSelectorEvenWhenSomeStepsWereNotObserved() {
        val observations = completeObservations().filterIndexed { index, _ -> index >= 2 }

        val result = WeChatTeachingAnalyzer.analyze(
            observations = observations,
            fingerprint = fingerprint,
            createdAtEpochMs = 1_000L
        )

        assertTrue(result is WeChatTeachingResult.Incomplete)
        val incomplete = result as WeChatTeachingResult.Incomplete
        assertEquals(
            listOf(
                WeChatTeachingAction.OPEN_MORE,
                WeChatTeachingAction.OPEN_VIDEO_MENU,
                WeChatTeachingAction.START_VIDEO_CALL
            ),
            incomplete.profile?.steps?.map { it.action }
        )
        assertTrue(incomplete.missing.contains(WeChatTeachingRequirement.OPEN_SEARCH))
        assertTrue(incomplete.missing.contains(WeChatTeachingRequirement.OPEN_CONTACT))
    }

    @Test
    fun unknownClickedSelectorsAreMatchedByTheirSuccessfulPageTransitions() {
        val unknownVideoEntry = click(
            windowClass = "com.tencent.mm.ui.chatting.ChattingUI",
            label = null,
            id = "com.tencent.mm:id/device_video_entry",
            x = 0.7f,
            y = 0.2f
        )
        val dialog = WeChatTeachingObservation(
            kind = WeChatTeachingObservationKind.WINDOW,
            windowClass = "com.tencent.mm.ui.widget.dialog.a4",
            selector = null,
            elapsedMs = 2_000L
        )
        val visibleBuiltInCandidate = click(
            windowClass = "com.tencent.mm.ui.chatting.ChattingUI",
            label = WeChatTeachingSemanticLabel.VIDEO_CALL,
            id = null,
            x = 0.5f,
            y = 0.5f
        ).copy(source = WeChatTeachingObservationSource.VISIBLE_CONTROL)
        val unknownStart = click(
            windowClass = "com.tencent.mm.ui.widget.dialog.a4",
            label = null,
            id = "com.tencent.mm:id/device_video_start",
            x = 0.5f,
            y = 0.7f
        )
        val callPage = WeChatTeachingObservation(
            kind = WeChatTeachingObservationKind.WINDOW,
            windowClass = "com.tencent.mm.plugin.voip.ui.VideoActivity",
            selector = null,
            elapsedMs = 3_000L
        )

        val result = WeChatTeachingAnalyzer.analyze(
            listOf(
                unknownVideoEntry,
                visibleBuiltInCandidate,
                dialog,
                unknownStart,
                callPage
            ),
            fingerprint,
            4_000L
        ) as WeChatTeachingResult.Incomplete

        assertEquals(
            listOf(
                WeChatTeachingAction.OPEN_VIDEO_MENU,
                WeChatTeachingAction.START_VIDEO_CALL
            ),
            result.profile?.steps?.map { it.action }
        )
        assertEquals(
            "com.tencent.mm:id/device_video_entry",
            result.profile?.steps?.first()?.selector?.resourceId
        )
    }

    @Test
    fun anEarlierUnrelatedClickIsNotBoundToALaterSuccessfulTransition() {
        val unrelated = click(
            windowClass = "com.tencent.mm.ui.LauncherUI",
            label = null,
            id = "com.tencent.mm:id/unrelated_tab",
            x = 0.2f,
            y = 0.9f
        )
        val actualSearch = click(
            windowClass = "com.tencent.mm.ui.LauncherUI",
            label = WeChatTeachingSemanticLabel.SEARCH,
            id = "com.tencent.mm:id/jha",
            x = 0.8f,
            y = 0.1f
        )
        val searchPage = WeChatTeachingObservation(
            kind = WeChatTeachingObservationKind.WINDOW,
            windowClass = "com.tencent.mm.plugin.fts.ui.FTSMainUI",
            selector = null,
            elapsedMs = 1_000L
        )

        val result = WeChatTeachingAnalyzer.analyze(
            listOf(unrelated, actualSearch, searchPage),
            fingerprint,
            2_000L
        ) as WeChatTeachingResult.Incomplete

        val searchStep = result.profile?.steps?.single {
            it.action == WeChatTeachingAction.OPEN_SEARCH
        }
        assertEquals("com.tencent.mm:id/jha", searchStep?.selector?.resourceId)
    }

    @Test
    fun visibleControlAloneDoesNotPretendTheStepWasCompleted() {
        val visibleSearch = click(
            windowClass = "com.tencent.mm.ui.LauncherUI",
            label = WeChatTeachingSemanticLabel.SEARCH,
            id = "com.tencent.mm:id/jha",
            x = 0.82f,
            y = 0.08f
        ).copy(source = WeChatTeachingObservationSource.VISIBLE_CONTROL)

        val result = WeChatTeachingAnalyzer.analyze(
            listOf(visibleSearch),
            fingerprint,
            2_000L
        ) as WeChatTeachingResult.Incomplete

        assertNull(result.profile)
        assertTrue(result.missing.contains(WeChatTeachingRequirement.OPEN_SEARCH))
    }

    @Test
    fun visibleControlsRecoverAfterAWrongBranchAndUseTheSuccessfulAttempt() {
        val firstMore = click(
            windowClass = "com.tencent.mm.ui.chatting.ChattingUI",
            label = WeChatTeachingSemanticLabel.MORE,
            id = "com.tencent.mm:id/first_more",
            x = 0.93f,
            y = 0.95f
        ).copy(source = WeChatTeachingObservationSource.VISIBLE_CONTROL)
        val wrongPage = window("com.tencent.mm.ui.SingleChatInfoUI", 700L)
        val chatAgain = window("com.tencent.mm.ui.chatting.ChattingUI", 900L)
        val successfulMore = firstMore.copy(
            selector = firstMore.selector?.copy(resourceId = "com.tencent.mm:id/retry_more"),
            elapsedMs = 1_000L
        )
        val videoMenu = click(
            windowClass = "com.tencent.mm.ui.chatting.ChattingUI",
            label = WeChatTeachingSemanticLabel.VIDEO_CALL,
            id = "com.tencent.mm:id/video_call_menu",
            x = 0.62f,
            y = 0.72f
        ).copy(source = WeChatTeachingObservationSource.VISIBLE_CONTROL)
        val videoMenuClick = click(
            windowClass = "com.tencent.mm.ui.chatting.ChattingUI",
            label = null,
            id = "com.tencent.mm:id/video_menu_clicked_parent",
            x = 0.62f,
            y = 0.72f
        ).copy(elapsedMs = 1_200L)
        val transientChatWindow = window("com.tencent.mm.ui.chatting.ChattingUI", 1_300L)
        val dialog = window("com.tencent.mm.ui.widget.dialog.a4", 1_500L)
        val startVideo = click(
            windowClass = "com.tencent.mm.ui.widget.dialog.a4",
            label = WeChatTeachingSemanticLabel.VIDEO_CALL,
            id = "com.tencent.mm:id/video_call_option",
            x = 0.5f,
            y = 0.78f
        ).copy(source = WeChatTeachingObservationSource.VISIBLE_CONTROL)
        val startVideoClick = click(
            windowClass = "com.tencent.mm.ui.widget.dialog.a4",
            label = null,
            id = "com.tencent.mm:id/start_video_clicked_parent",
            x = 0.5f,
            y = 0.78f
        ).copy(elapsedMs = 1_650L)
        val transientLauncherWindow = window("com.tencent.mm.ui.LauncherUI", 1_700L)
        val callPage = window("com.tencent.mm.plugin.voip.ui.VideoActivity", 2_000L)

        val result = WeChatTeachingAnalyzer.analyze(
            listOf(
                firstMore,
                wrongPage,
                chatAgain,
                successfulMore,
                videoMenu,
                videoMenuClick,
                transientChatWindow,
                dialog,
                startVideo,
                startVideoClick,
                transientLauncherWindow,
                callPage
            ),
            fingerprint,
            3_000L
        ) as WeChatTeachingResult.Incomplete

        assertEquals(
            listOf(
                WeChatTeachingAction.OPEN_MORE,
                WeChatTeachingAction.OPEN_VIDEO_MENU,
                WeChatTeachingAction.START_VIDEO_CALL
            ),
            result.profile?.steps?.map { it.action }
        )
        assertEquals(
            "com.tencent.mm:id/retry_more",
            result.profile?.steps?.first()?.selector?.resourceId
        )
        assertEquals(
            "com.tencent.mm:id/video_call_menu",
            result.profile?.steps?.first { it.action == WeChatTeachingAction.OPEN_VIDEO_MENU }
                ?.selector
                ?.resourceId
        )
    }

    private fun completeObservations(): List<WeChatTeachingObservation> = listOf(
        click(
            windowClass = "com.tencent.mm.ui.LauncherUI",
            label = WeChatTeachingSemanticLabel.SEARCH,
            id = "com.tencent.mm:id/jha",
            x = 0.82f,
            y = 0.08f
        ),
        click(
            windowClass = "com.tencent.mm.plugin.fts.ui.FTSMainUI",
            label = null,
            id = "com.tencent.mm:id/kbq",
            x = 0.42f,
            y = 0.22f
        ),
        click(
            windowClass = "com.tencent.mm.ui.chatting.ChattingUI",
            label = WeChatTeachingSemanticLabel.MORE,
            id = "com.tencent.mm:id/bjz",
            x = 0.93f,
            y = 0.95f
        ),
        click(
            windowClass = "com.tencent.mm.ui.chatting.ChattingUI",
            label = WeChatTeachingSemanticLabel.VIDEO_CALL,
            id = "com.tencent.mm:id/video_call_menu",
            x = 0.18f,
            y = 0.78f
        ),
        click(
            windowClass = "com.tencent.mm.ui.widget.dialog.a4",
            label = WeChatTeachingSemanticLabel.VIDEO_CALL,
            id = "com.tencent.mm:id/video_call_option",
            x = 0.5f,
            y = 0.73f
        ),
        WeChatTeachingObservation(
            kind = WeChatTeachingObservationKind.WINDOW,
            windowClass = "com.tencent.mm.plugin.voip.ui.VideoActivity",
            selector = null,
            elapsedMs = 4_000L
        )
    )

    private fun click(
        windowClass: String,
        label: WeChatTeachingSemanticLabel?,
        id: String?,
        x: Float,
        y: Float
    ) = WeChatTeachingObservation(
        kind = WeChatTeachingObservationKind.CLICK,
        windowClass = windowClass,
        selector = WeChatTeachingSelector(
            resourceId = id,
            nodeClass = "android.widget.LinearLayout",
            semanticLabel = label,
            clickableAncestorDepth = 0,
            centerXRatio = x,
            centerYRatio = y
        ),
        elapsedMs = 500L
    )

    private fun window(windowClass: String, elapsedMs: Long) = WeChatTeachingObservation(
        kind = WeChatTeachingObservationKind.WINDOW,
        windowClass = windowClass,
        selector = null,
        elapsedMs = elapsedMs
    )
}
