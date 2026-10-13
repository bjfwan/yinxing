package com.google.android.accessibility.selecttospeak

import org.junit.Assert.assertEquals
import org.junit.Test

class WeChatLoginPageDetectorTest {

    @Test
    fun loginPageWithPhoneLoginContextIsDetected() {
        val snapshot = node(
            children = listOf(
                node(text = "登录"),
                node(text = "手机号登录"),
                node(text = "找回密码")
            )
        )

        assertEquals(
            WeChatLoginPageDetector.Gate.LOGIN,
            WeChatLoginPageDetector.detect(snapshot)
        )
    }

    @Test
    fun agreementGatePageIsDetected() {
        val snapshot = node(
            children = listOf(
                node(text = "微信隐私保护指引"),
                node(text = "我已阅读并同意上述条款"),
                node(text = "同意"),
                node(text = "不同意")
            )
        )

        assertEquals(
            WeChatLoginPageDetector.Gate.AGREEMENT,
            WeChatLoginPageDetector.detect(snapshot)
        )
    }

    @Test
    fun singleAgreementKeywordAloneIsNotEnough() {
        val snapshot = node(
            children = listOf(
                node(text = "wan. 发来消息"),
                node(text = "我已阅读并同意")
            )
        )

        assertEquals(
            WeChatLoginPageDetector.Gate.NONE,
            WeChatLoginPageDetector.detect(snapshot)
        )
    }

    @Test
    fun chatListIsNotFlagged() {
        val snapshot = node(
            children = listOf(
                node(text = "微信"),
                node(text = "通讯录"),
                node(text = "发现"),
                node(text = "wan. 发来消息")
            )
        )

        assertEquals(
            WeChatLoginPageDetector.Gate.NONE,
            WeChatLoginPageDetector.detect(snapshot)
        )
    }

    @Test
    fun emptySnapshotIsNone() {
        assertEquals(WeChatLoginPageDetector.Gate.NONE, WeChatLoginPageDetector.detect(null))
        assertEquals(WeChatLoginPageDetector.Gate.NONE, WeChatLoginPageDetector.detect(node()))
    }

    @Test
    fun loginClassNameAloneIsDetected() {
        assertEquals(
            WeChatLoginPageDetector.Gate.LOGIN,
            WeChatLoginPageDetector.detect(null, "com.tencent.mm.plugin.account.ui.LoginPasswordUI")
        )
        assertEquals(
            WeChatLoginPageDetector.Gate.LOGIN,
            WeChatLoginPageDetector.detect(
                node(),
                "com.tencent.mm.plugin.account.ui.MobileInputUI"
            )
        )
    }

    @Test
    fun nonLoginClassNameDoesNotFlag() {
        val snapshot = node(
            children = listOf(
                node(text = "微信"),
                node(text = "通讯录")
            )
        )
        assertEquals(
            WeChatLoginPageDetector.Gate.NONE,
            WeChatLoginPageDetector.detect(snapshot, "com.tencent.mm.ui.LauncherUI")
        )
        assertEquals(
            WeChatLoginPageDetector.Gate.NONE,
            WeChatLoginPageDetector.detect(null, null)
        )
    }

    private fun node(
        text: String? = null,
        children: List<WeChatUiSnapshot> = emptyList()
    ): WeChatUiSnapshot {
        return WeChatUiSnapshot(text = text, children = children)
    }
}
