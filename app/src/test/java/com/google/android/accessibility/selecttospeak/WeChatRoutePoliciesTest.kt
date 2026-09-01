package com.google.android.accessibility.selecttospeak

import com.yinxing.launcher.automation.wechat.WeChatClassNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatRoutePoliciesTest {
    @Test
    fun `list target rejects stale negative-width accessibility bounds`() {
        assertFalse(
            WeChatListTargetBoundsPolicy.isSafe(
                WeChatUiBounds(left = 0, top = 286, right = -182, bottom = 354)
            )
        )
        assertTrue(
            WeChatListTargetBoundsPolicy.isSafe(
                WeChatUiBounds(left = 40, top = 286, right = 900, bottom = 354)
            )
        )
    }

    @Test
    fun `home tab requires two selected observations before list clicks`() {
        assertEquals(
            WeChatHomeTabSettleDecision.WAIT,
            WeChatHomeTabSettlePolicy.decide(selected = false, consecutiveSelectedObservations = 5)
        )
        assertEquals(
            WeChatHomeTabSettleDecision.WAIT,
            WeChatHomeTabSettlePolicy.decide(selected = true, consecutiveSelectedObservations = 1)
        )
        assertEquals(
            WeChatHomeTabSettleDecision.READY,
            WeChatHomeTabSettlePolicy.decide(selected = true, consecutiveSelectedObservations = 2)
        )
    }

    @Test
    fun `history shortcut chooses lowest visible message bubble above composer`() {
        val root = WeChatUiBounds(0, 0, 2120, 3000)
        val candidates = listOf(
            WeChatUiBounds(1400, 300, 1945, 460),
            WeChatUiBounds(1500, 1700, 1945, 1860),
            WeChatUiBounds(1621, 2627, 1945, 2760),
            WeChatUiBounds(1600, 2840, 1945, 2970)
        )

        assertEquals(
            WeChatUiBounds(1621, 2627, 1945, 2760),
            WeChatHistoryMessageCandidatePolicy.chooseLatestVisible(root, candidates)
        )
    }

    @Test
    fun `history shortcut falls back to standard menu when call does not start`() {
        assertFalse(
            WeChatHistoryCallFallbackPolicy.shouldFallback(
                historyAttempted = true,
                elapsedMs = 900L,
                currentClass = WeChatClassNames.CHATTING_UI,
                callStatus = WeChatCallStartStatus.PENDING
            )
        )
        assertTrue(
            WeChatHistoryCallFallbackPolicy.shouldFallback(
                historyAttempted = true,
                elapsedMs = 2_500L,
                currentClass = WeChatClassNames.CHATTING_UI,
                callStatus = WeChatCallStartStatus.PENDING
            )
        )
    }
}
