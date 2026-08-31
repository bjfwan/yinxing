package com.google.android.accessibility.selecttospeak

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatFailureSampleFactoryTest {
    @Test
    fun `builds a stable structural sample without page text`() {
        val firstRoot = snapshot("张三", "今晚八点见")
        val secondRoot = snapshot("李四", "另一条聊天内容")
        val session = failureSnapshot()

        val first = WeChatFailureSampleFactory.create(
            errorCode = "WECHAT_WAITING_CONTACT_RESULT_FAILED",
            session = session,
            route = "SEARCH",
            root = firstRoot,
            windowClass = "com.tencent.mm.ui.chatting.ChattingUI",
        )
        val second = WeChatFailureSampleFactory.create(
            errorCode = "WECHAT_WAITING_CONTACT_RESULT_FAILED",
            session = session.copy(contactName = "李四"),
            route = "SEARCH",
            root = secondRoot,
            windowClass = "com.tencent.mm.ui.chatting.ChattingUI",
        )

        assertEquals(first.fingerprint, second.fingerprint)
        assertEquals(listOf("com.tencent.mm:id/result"), first.uiState.resourceIds)
        assertEquals(3, first.uiState.nodeCount)
        assertEquals(1, first.uiState.clickableCount)
        assertEquals(2, first.uiState.maxDepth)
        val serialized = first.toJson().toString()
        assertFalse(serialized.contains("张三"))
        assertFalse(serialized.contains("今晚八点见"))
        assertTrue(serialized.contains("com.tencent.mm:id/result"))
    }

    @Test
    fun `changes fingerprint when the semantic failure changes`() {
        val root = snapshot("张三", "消息")
        val original = WeChatFailureSampleFactory.create(
            "WECHAT_WAITING_CONTACT_RESULT_FAILED",
            failureSnapshot(),
            "SEARCH",
            root,
            "com.tencent.mm.ui.chatting.ChattingUI",
        )
        val changed = WeChatFailureSampleFactory.create(
            "WECHAT_WAITING_CONTACT_RESULT_FAILED",
            failureSnapshot().copy(capabilityFailure = "ACTION_FAILED"),
            "SEARCH",
            root,
            "com.tencent.mm.ui.chatting.ChattingUI",
        )

        assertNotEquals(original.fingerprint, changed.fingerprint)
    }

    private fun snapshot(contact: String, message: String) = WeChatUiSnapshot(
        className = "android.widget.FrameLayout",
        children = listOf(
            WeChatUiSnapshot(
                text = contact,
                viewIdResourceName = "com.tencent.mm:id/result",
                className = "android.widget.TextView",
                clickable = true,
                children = listOf(
                    WeChatUiSnapshot(
                        text = message,
                        viewIdResourceName = "other.app:id/private",
                        className = "other.app.PrivateView",
                    ),
                ),
            ),
        ),
    )

    private fun failureSnapshot() = WeChatFailureSnapshot(
        step = "WAITING_CONTACT_RESULT",
        contactName = "张三",
        startedAt = 1L,
        stepStartedAt = 2L,
        actionAttempts = mapOf("search" to 2),
        stepHistory = listOf("WAITING_HOME", "WAITING_CONTACT_RESULT"),
        stepDurations = mapOf("waiting_home" to 100L),
        lastDetectedPage = "SEARCH",
        lastProgressAt = 3L,
        lastAnnouncedMessage = "未找到张三",
        lastSemanticPage = "SEARCH",
        taskStep = "WAITING_CONTACT_RESULT",
        taskReason = "search_result_not_found",
        capabilityId = "OPEN_SEARCH_RESULT",
        capabilityFailure = "SEARCH_RESULT_NOT_FOUND",
    )
}
