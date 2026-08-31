package com.yinxing.launcher.feature.callreturn

import org.junit.Assert.assertEquals
import org.junit.Test

class WeChatCallWindowTrackerTest {
    @Test
    fun genericContentWindowDoesNotReplacePostCallActivity() {
        assertEquals(
            "com.tencent.mm.ui.chatting.ChattingUI",
            WeChatCallWindowTracker.remember(
                previousClass = "com.tencent.mm.ui.chatting.ChattingUI",
                packageName = "com.tencent.mm",
                observedClass = "android.widget.FrameLayout"
            )
        )
    }

    @Test
    fun videoAndPostCallActivitiesReplacePreviousClass() {
        val video = WeChatCallWindowTracker.remember(
            previousClass = null,
            packageName = "com.tencent.mm",
            observedClass = "com.tencent.mm.plugin.voip.ui.VideoActivity"
        )
        val chat = WeChatCallWindowTracker.remember(
            previousClass = video,
            packageName = "com.tencent.mm",
            observedClass = "com.tencent.mm.ui.chatting.ChattingUI"
        )

        assertEquals("com.tencent.mm.plugin.voip.ui.VideoActivity", video)
        assertEquals("com.tencent.mm.ui.chatting.ChattingUI", chat)
    }

    @Test
    fun foreignPackageCannotChangeTrackedWechatActivity() {
        assertEquals(
            "com.tencent.mm.plugin.voip.ui.VideoActivity",
            WeChatCallWindowTracker.remember(
                previousClass = "com.tencent.mm.plugin.voip.ui.VideoActivity",
                packageName = "com.android.systemui",
                observedClass = "android.widget.FrameLayout"
            )
        )
    }
}
