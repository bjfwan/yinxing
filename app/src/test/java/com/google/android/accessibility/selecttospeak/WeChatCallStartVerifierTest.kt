package com.google.android.accessibility.selecttospeak

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatCallStartVerifierTest {

    @Test
    fun singleConfirmedSnapshotDoesNotCompleteTheTask() {
        val assessment = WeChatCallStartVerifier.assess(
            snapshot = node(children = listOf(node(text = "正在等待对方接受邀请"))),
            className = null
        )

        val decision = WeChatCallVerificationPolicy.decide(
            WeChatCallVerificationState(),
            assessment
        )

        assertEquals(WeChatCallVerificationAction.WAIT, decision.action)
        assertEquals(1, decision.nextState.consecutiveConfirmations)
    }

    @Test
    fun twoConsecutiveConfirmedSnapshotsCompleteTheTask() {
        val assessment = WeChatCallStartVerifier.assess(
            snapshot = null,
            className = "com.tencent.mm.plugin.voip.ui.VideoActivity"
        )
        val first = WeChatCallVerificationPolicy.decide(
            WeChatCallVerificationState(),
            assessment
        )
        val second = WeChatCallVerificationPolicy.decide(first.nextState, assessment)

        assertEquals(WeChatCallVerificationAction.COMPLETE, second.action)
        assertEquals(2, second.nextState.consecutiveConfirmations)
    }

    @Test
    fun nonWechatVoipClassDoesNotConfirmTheCall() {
        val assessment = WeChatCallStartVerifier.assess(
            snapshot = null,
            className = "com.example.plugin.voip.ui.VideoActivity"
        )

        assertEquals(WeChatCallStartStatus.PENDING, assessment.status)
    }

    @Test
    fun returningToPendingEvidenceResetsConfirmationCount() {
        val confirmed = WeChatCallStartVerifier.assess(
            snapshot = node(children = listOf(node(text = "等待对方接听"))),
            className = null
        )
        val first = WeChatCallVerificationPolicy.decide(
            WeChatCallVerificationState(),
            confirmed
        )
        val pending = WeChatCallStartVerifier.assess(
            snapshot = node(children = listOf(node(text = "妈妈"), node(text = "+"))),
            className = "com.tencent.mm.ui.chatting.ChattingUI"
        )
        val reset = WeChatCallVerificationPolicy.decide(first.nextState, pending)

        assertEquals(WeChatCallVerificationAction.WAIT, reset.action)
        assertEquals(0, reset.nextState.consecutiveConfirmations)
    }

    @Test
    fun videoOptionSheetIsNotMistakenForAnOutgoingCall() {
        val assessment = WeChatCallStartVerifier.assess(
            snapshot = node(
                children = listOf(
                    node(text = "语音通话"),
                    node(text = "视频通话"),
                    node(text = "取消")
                )
            ),
            className = null
        )

        assertEquals(WeChatCallStartStatus.PENDING, assessment.status)
    }

    @Test
    fun visibleVideoOptionSheetOverridesACachedVoipClassName() {
        val assessment = WeChatCallStartVerifier.assess(
            snapshot = node(
                children = listOf(
                    node(text = "语音通话"),
                    node(text = "视频通话"),
                    node(text = "取消")
                )
            ),
            className = "com.tencent.mm.plugin.voip.ui.VideoActivity"
        )

        assertEquals(WeChatCallStartStatus.PENDING, assessment.status)
    }

    @Test
    fun callControlsConfirmAnOutgoingCall() {
        val assessment = WeChatCallStartVerifier.assess(
            snapshot = node(
                children = listOf(
                    node(text = "静音"),
                    node(text = "切换摄像头"),
                    node(text = "挂断")
                )
            ),
            className = null
        )

        assertEquals(WeChatCallStartStatus.CONFIRMED, assessment.status)
        assertTrue(assessment.reasons.contains("call_controls"))
    }

    @Test
    fun historicalDeclineTextDoesNotConfirmANewOutboundCall() {
        val assessment = WeChatCallStartVerifier.assess(
            snapshot = node(children = listOf(node(text = "对方已拒绝"))),
            className = null
        )

        assertEquals(WeChatCallStartStatus.PENDING, assessment.status)
    }

    @Test
    fun cameraPermissionBlockFailsImmediatelyWithConcreteMessage() {
        val assessment = WeChatCallStartVerifier.assess(
            snapshot = node(children = listOf(node(text = "无法使用相机，请在设置中开启权限"))),
            className = null
        )
        val decision = WeChatCallVerificationPolicy.decide(
            WeChatCallVerificationState(),
            assessment
        )

        assertEquals(WeChatCallStartStatus.BLOCKED, assessment.status)
        assertEquals(WeChatCallVerificationAction.FAIL, decision.action)
        assertEquals("微信缺少视频通话权限", assessment.userMessage)
    }

    @Test
    fun networkBlockFailsImmediatelyWithConcreteMessage() {
        val assessment = WeChatCallStartVerifier.assess(
            snapshot = node(children = listOf(node(text = "网络连接失败，请检查网络"))),
            className = null
        )

        assertEquals(WeChatCallStartStatus.BLOCKED, assessment.status)
        assertEquals("微信视频网络连接失败", assessment.userMessage)
    }

    private fun node(
        text: String? = null,
        children: List<WeChatUiSnapshot> = emptyList()
    ): WeChatUiSnapshot {
        return WeChatUiSnapshot(text = text, children = children)
    }
}
