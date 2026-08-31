package com.yinxing.launcher.feature.callreturn

import android.media.AudioManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatCallEndPolicyTest {
    @Test
    fun stableChatPageWithNormalAudioMeansCallEnded() {
        assertTrue(
            WeChatCallEndPolicy.shouldComplete(
                packageName = "com.tencent.mm",
                className = "com.tencent.mm.ui.chatting.ChattingUI",
                videoActivityVisible = false,
                audioMode = AudioManager.MODE_NORMAL
            )
        )
    }

    @Test
    fun visibleVideoActivityKeepsCallActive() {
        assertFalse(
            WeChatCallEndPolicy.shouldComplete(
                packageName = "com.tencent.mm",
                className = "com.tencent.mm.ui.chatting.ChattingUI",
                videoActivityVisible = true,
                audioMode = AudioManager.MODE_NORMAL
            )
        )
    }

    @Test
    fun communicationAudioModeKeepsMinimizedCallActive() {
        assertFalse(
            WeChatCallEndPolicy.shouldComplete(
                packageName = "com.tencent.mm",
                className = "com.tencent.mm.ui.LauncherUI",
                videoActivityVisible = false,
                audioMode = AudioManager.MODE_IN_COMMUNICATION
            )
        )
    }

    @Test
    fun foreignPageCannotCompleteWechatCall() {
        assertFalse(
            WeChatCallEndPolicy.shouldComplete(
                packageName = "com.example.reader",
                className = "com.example.reader.MainActivity",
                videoActivityVisible = false,
                audioMode = AudioManager.MODE_NORMAL
            )
        )
    }
}
