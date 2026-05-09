package com.google.android.accessibility.selecttospeak

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatVideoTaskEngineTest {
    @Test
    fun semanticRecognizerClassifiesReplayablePagesWithConfidence() {
        val home = node(children = listOf(node(text = "微信"), node(text = "通讯录"), node(text = "发现")))
        val search = node(children = listOf(node(text = "搜索"), node(text = "取消"), node(editable = true)))
        val sheet = node(children = listOf(node(text = "语音通话"), node(text = "视频通话"), node(text = "取消")))

        assertEquals(WeChatSemanticPage.HOME, WeChatSemanticPageRecognizer.recognize(home).page)
        assertTrue(WeChatSemanticPageRecognizer.recognize(search).reliable)
        assertEquals(WeChatSemanticPage.VIDEO_SHEET, WeChatSemanticPageRecognizer.recognize(sheet).page)
    }

    @Test
    fun taskEngineCompletesHappyPathFromSemanticPagesAndScoredContact() {
        val engine = WeChatVideoTaskEngine()
        var state = WeChatVideoTaskState(contactName = "妈妈")

        var decision = engine.decide(state, result(WeChatSemanticPage.HOME))
        assertEquals(WeChatVideoTaskAction.OPEN_SEARCH, decision.action)
        state = decision.nextState

        decision = engine.decide(state, result(WeChatSemanticPage.SEARCH))
        assertEquals(WeChatVideoTaskAction.TYPE_CONTACT, decision.action)
        state = decision.nextState

        decision = engine.decide(
            state,
            result(WeChatSemanticPage.SEARCH),
            WeChatTargetScore(displayName = "妈妈", score = 90, reasons = listOf("title_id", "exact_title"))
        )
        assertEquals(WeChatVideoTaskAction.OPEN_CONTACT, decision.action)
        assertEquals("妈妈", decision.nextState.resolvedDisplayName)
        state = decision.nextState

        decision = engine.decide(state, result(WeChatSemanticPage.CONTACT_DETAIL))
        assertEquals(WeChatVideoTaskAction.OPEN_VIDEO_ENTRY, decision.action)
        state = decision.nextState

        decision = engine.decide(state, result(WeChatSemanticPage.VIDEO_SHEET))
        assertEquals(WeChatVideoTaskAction.CONFIRM_VIDEO_CALL, decision.action)
        assertEquals(WeChatVideoTaskStep.COMPLETED, decision.nextState.step)
    }

    @Test
    fun taskEngineFailsOnNoResultAndRecoversAfterRepeatedUnknownPages() {
        val engine = WeChatVideoTaskEngine(maxAttemptsPerStep = 2)
        val contactResultState = WeChatVideoTaskState(
            contactName = "妈妈",
            step = WeChatVideoTaskStep.WAITING_CONTACT_RESULT
        )

        val noResult = engine.decide(contactResultState, result(WeChatSemanticPage.NO_RESULT))
        assertEquals(WeChatVideoTaskAction.FAIL, noResult.action)
        assertEquals(WeChatVideoTaskStep.FAILED, noResult.nextState.step)

        val unknown = result(WeChatSemanticPage.UNKNOWN, confidence = 20)
        val first = engine.decide(contactResultState, unknown)
        val second = engine.decide(first.nextState, unknown)

        assertEquals(WeChatVideoTaskAction.WAIT, first.action)
        assertEquals(WeChatVideoTaskAction.RECOVER_HOME, second.action)
        assertEquals(WeChatVideoTaskStep.WAITING_HOME, second.nextState.step)
    }

    @Test
    fun weakContactScoreDoesNotOpenContact() {
        val engine = WeChatVideoTaskEngine()
        val state = WeChatVideoTaskState(
            contactName = "wan.",
            step = WeChatVideoTaskStep.WAITING_CONTACT_RESULT
        )
        val decision = engine.decide(
            state,
            result(WeChatSemanticPage.SEARCH),
            WeChatTargetScore(displayName = "wan.", score = 30, reasons = listOf("network_marker"))
        )

        assertEquals(WeChatVideoTaskAction.WAIT, decision.action)
        assertFalse(decision.nextState.step == WeChatVideoTaskStep.WAITING_CONTACT_DETAIL)
    }

    @Test
    fun replayedFailureSnapshotCanDriveTaskDecision() {
        val replay = WeChatFailureReplay(
            message = "没有找到联系人",
            createdAt = 1L,
            session = null,
            root = node(
                children = listOf(
                    node(text = "搜索"),
                    node(text = "取消"),
                    node(editable = true),
                    node(text = "无搜索结果")
                )
            )
        )
        val decoded = WeChatFailureDiagnostics.decodeReplay(WeChatFailureDiagnostics.encodeReplay(replay))
        val page = WeChatSemanticPageRecognizer.recognize(decoded.root)
        val decision = WeChatVideoTaskEngine().decide(
            WeChatVideoTaskState(
                contactName = "妈妈",
                step = WeChatVideoTaskStep.WAITING_CONTACT_RESULT
            ),
            page
        )

        assertEquals(WeChatSemanticPage.NO_RESULT, page.page)
        assertEquals(WeChatVideoTaskAction.FAIL, decision.action)
        assertEquals("no_contact_result", decision.reason)
    }

    private fun result(
        page: WeChatSemanticPage,
        confidence: Int = 90
    ) = WeChatSemanticPageResult(page, confidence, listOf(page.name.lowercase()))

    private fun node(
        text: String? = null,
        contentDescription: String? = null,
        viewIdResourceName: String? = null,
        className: String? = null,
        clickable: Boolean = false,
        editable: Boolean = false,
        children: List<WeChatUiSnapshot> = emptyList()
    ): WeChatUiSnapshot {
        return WeChatUiSnapshot(
            text = text,
            contentDescription = contentDescription,
            viewIdResourceName = viewIdResourceName,
            className = className,
            clickable = clickable,
            editable = editable,
            children = children
        )
    }
}
