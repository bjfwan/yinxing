package com.yinxing.launcher.automation.wechat.teaching

import org.junit.Assert.assertEquals
import org.junit.Test

class WeChatTeachingProgressTrackerTest {

    @Test
    fun recordingStartsWithWeChatOpened() {
        assertEquals(
            WeChatTeachingProgress.WECHAT_OPENED,
            WeChatTeachingProgressTracker.latest(emptyList())
        )
    }

    @Test
    fun enteringChatAdvancesToContactOpened() {
        assertEquals(
            WeChatTeachingProgress.CONTACT_OPENED,
            WeChatTeachingProgressTracker.latest(
                listOf(window("com.tencent.mm.ui.chatting.ChattingUI"))
            )
        )
    }

    @Test
    fun videoDialogAdvancesToVideoOpened() {
        assertEquals(
            WeChatTeachingProgress.VIDEO_OPENED,
            WeChatTeachingProgressTracker.latest(
                listOf(
                    window("com.tencent.mm.ui.chatting.ChattingUI"),
                    window("com.tencent.mm.ui.widget.dialog.a4")
                )
            )
        )
    }

    @Test
    fun videoClickAdvancesWhenDialogWindowIsNotExposed() {
        assertEquals(
            WeChatTeachingProgress.VIDEO_OPENED,
            WeChatTeachingProgressTracker.latest(
                listOf(
                    window("com.tencent.mm.ui.chatting.ChattingUI"),
                    click(
                        windowClass = "com.tencent.mm.ui.chatting.ChattingUI",
                        label = WeChatTeachingSemanticLabel.VIDEO_CALL
                    )
                )
            )
        )
    }

    @Test
    fun videoActivityDoesNotAdvanceUntilVideoIsConfirmed() {
        assertEquals(
            WeChatTeachingProgress.WECHAT_OPENED,
            WeChatTeachingProgressTracker.latest(
                observations = listOf(window("com.tencent.mm.plugin.voip.ui.VideoActivity")),
                videoCallConfirmed = false
            )
        )
    }

    @Test
    fun confirmedVideoAdvancesToCallStartedWithoutRequiringOneFixedRoute() {
        assertEquals(
            WeChatTeachingProgress.CALL_STARTED,
            WeChatTeachingProgressTracker.latest(
                observations = listOf(window("com.tencent.mm.plugin.voip.ui.VideoActivity")),
                videoCallConfirmed = true
            )
        )
    }

    @Test
    fun confirmedVideoAdvancesWhenWechatDoesNotExposeTheVoipWindowEvent() {
        assertEquals(
            WeChatTeachingProgress.CALL_STARTED,
            WeChatTeachingProgressTracker.latest(
                observations = listOf(window("com.tencent.mm.ui.chatting.ChattingUI")),
                videoCallConfirmed = true
            )
        )
    }

    @Test
    fun laterWindowEventsDoNotMoveProgressBackward() {
        assertEquals(
            WeChatTeachingProgress.CALL_STARTED,
            WeChatTeachingProgressTracker.latest(
                observations = listOf(
                    window("com.tencent.mm.plugin.voip.ui.VideoActivity"),
                    window("com.tencent.mm.ui.LauncherUI")
                ),
                videoCallConfirmed = true
            )
        )
    }

    private fun window(windowClass: String) = WeChatTeachingObservation(
        kind = WeChatTeachingObservationKind.WINDOW,
        windowClass = windowClass,
        selector = null,
        elapsedMs = 0L
    )

    private fun click(
        windowClass: String,
        label: WeChatTeachingSemanticLabel
    ) = WeChatTeachingObservation(
        kind = WeChatTeachingObservationKind.CLICK,
        windowClass = windowClass,
        selector = WeChatTeachingSelector(
            resourceId = null,
            nodeClass = "android.widget.TextView",
            semanticLabel = label,
            clickableAncestorDepth = 0,
            centerXRatio = 0.5f,
            centerYRatio = 0.5f
        ),
        elapsedMs = 0L
    )
}
