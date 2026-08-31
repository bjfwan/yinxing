package com.yinxing.launcher.automation.wechat.teaching

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatTeachingBackInferenceTest {

    @Test
    fun returningToAnEarlierWindowWithoutClickInfersGlobalBack() {
        val history = listOf(
            window("com.tencent.mm.ui.LauncherUI", 0L),
            window("com.tencent.mm.ui.chatting.ChattingUI", 100L)
        )

        assertTrue(
            WeChatTeachingBackInference.shouldInfer(
                history,
                previousWindowClass = "com.tencent.mm.ui.chatting.ChattingUI",
                currentWindowClass = "com.tencent.mm.ui.LauncherUI"
            )
        )
    }

    @Test
    fun explicitControlClickIsKeptAsClickInsteadOfInventingGlobalBack() {
        val history = listOf(
            window("com.tencent.mm.ui.LauncherUI", 0L),
            window("com.tencent.mm.ui.chatting.ChattingUI", 100L),
            WeChatTeachingObservation(
                kind = WeChatTeachingObservationKind.CLICK,
                windowClass = "com.tencent.mm.ui.chatting.ChattingUI",
                selector = WeChatTeachingSelector(
                    "com.tencent.mm:id/back",
                    "android.widget.ImageButton",
                    null,
                    0,
                    0.05f,
                    0.05f
                ),
                elapsedMs = 150L
            )
        )

        assertFalse(
            WeChatTeachingBackInference.shouldInfer(
                history,
                previousWindowClass = "com.tencent.mm.ui.chatting.ChattingUI",
                currentWindowClass = "com.tencent.mm.ui.LauncherUI"
            )
        )
    }

    private fun window(name: String, elapsed: Long) = WeChatTeachingObservation(
        WeChatTeachingObservationKind.WINDOW,
        name,
        null,
        elapsed
    )
}
