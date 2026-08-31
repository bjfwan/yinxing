package com.yinxing.launcher.feature.callreturn

import org.junit.Assert.assertEquals
import org.junit.Test

class CallReturnWindowPolicyTest {
    @Test
    fun wechatCallPageKeepsSessionActive() {
        assertEquals(
            CallReturnWindowAction.IGNORE,
            CallReturnWindowPolicy.decide(
                origin = CallReturnOrigin.WECHAT_VIDEO,
                appPackage = "com.yinxing.launcher",
                packageName = "com.tencent.mm",
                className = "com.tencent.mm.plugin.voip.ui.VideoActivity"
            )
        )
    }

    @Test
    fun wechatChatPageRequestsStableEndCheck() {
        assertEquals(
            CallReturnWindowAction.CHECK_WECHAT_ENDED,
            CallReturnWindowPolicy.decide(
                origin = CallReturnOrigin.WECHAT_VIDEO,
                appPackage = "com.yinxing.launcher",
                packageName = "com.tencent.mm",
                className = "com.tencent.mm.ui.chatting.ChattingUI"
            )
        )
    }

    @Test
    fun unrelatedAppMarksWechatSessionAsUserEscaped() {
        assertEquals(
            CallReturnWindowAction.USER_ESCAPED,
            CallReturnWindowPolicy.decide(
                origin = CallReturnOrigin.WECHAT_VIDEO,
                appPackage = "com.yinxing.launcher",
                packageName = "com.example.reader",
                className = "com.example.reader.MainActivity"
            )
        )
    }

    @Test
    fun systemInCallUiDoesNotCountAsUserEscape() {
        assertEquals(
            CallReturnWindowAction.IGNORE,
            CallReturnWindowPolicy.decide(
                origin = CallReturnOrigin.SYSTEM_PHONE,
                appPackage = "com.yinxing.launcher",
                packageName = "com.android.incallui",
                className = "com.android.incallui.InCallActivity"
            )
        )
    }

    @Test
    fun unrelatedAppMarksSystemCallSessionAsUserEscaped() {
        assertEquals(
            CallReturnWindowAction.USER_ESCAPED,
            CallReturnWindowPolicy.decide(
                origin = CallReturnOrigin.SYSTEM_PHONE,
                appPackage = "com.yinxing.launcher",
                packageName = "com.example.reader",
                className = "com.example.reader.MainActivity"
            )
        )
    }
}
