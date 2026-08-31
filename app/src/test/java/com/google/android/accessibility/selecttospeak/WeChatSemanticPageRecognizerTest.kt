package com.google.android.accessibility.selecttospeak

import com.yinxing.launcher.automation.wechat.WeChatClassNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatSemanticPageRecognizerTest {
    @Test
    fun `recognizer classifies known pages with reliable confidence`() {
        val home = node(children = listOf(node(text = "微信"), node(text = "通讯录"), node(text = "发现")))
        val search = node(children = listOf(node(text = "搜索"), node(text = "取消"), node(editable = true)))
        val sheet = node(children = listOf(node(text = "语音通话"), node(text = "视频通话"), node(text = "取消")))

        assertEquals(WeChatSemanticPage.HOME, WeChatSemanticPageRecognizer.recognize(home).page)
        assertTrue(WeChatSemanticPageRecognizer.recognize(search).reliable)
        assertEquals(WeChatSemanticPage.VIDEO_SHEET, WeChatSemanticPageRecognizer.recognize(sheet).page)
    }

    @Test
    fun `failure replay still produces semantic no-result page`() {
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
        assertEquals(WeChatSemanticPage.NO_RESULT, WeChatSemanticPageRecognizer.recognize(decoded.root).page)
    }

    @Test
    fun `launcher activity keeps home stable while accessibility tree is loading`() {
        val result = WeChatSemanticPageRecognizer.recognize(
            snapshot = node(),
            currentClass = WeChatClassNames.LAUNCHER_UI
        )

        assertEquals(WeChatSemanticPage.HOME, result.page)
        assertTrue(result.reliable)
        assertTrue(result.evidence.contains("launcher_activity"))
    }

    @Test
    fun `chat activity identifies page type while content tree is restricted`() {
        val result = WeChatSemanticPageRecognizer.recognize(
            snapshot = node(),
            currentClass = WeChatClassNames.CHATTING_UI
        )

        assertEquals(WeChatSemanticPage.CHAT, result.page)
        assertTrue(result.reliable)
        assertTrue(result.evidence.contains("chatting_activity"))
    }

    @Test
    fun `expected video dialog identifies sheet while content tree is restricted`() {
        val result = WeChatSemanticPageRecognizer.recognize(
            snapshot = node(),
            currentClass = "com.tencent.mm.ui.widget.dialog.a4",
            expectingVideoSheet = true
        )

        assertEquals(WeChatSemanticPage.VIDEO_SHEET, result.page)
        assertTrue(result.reliable)
        assertTrue(result.evidence.contains("expected_video_dialog"))
    }

    @Test
    fun `generic video dialog is not trusted outside video selection step`() {
        val result = WeChatSemanticPageRecognizer.recognize(
            snapshot = node(),
            currentClass = "com.tencent.mm.ui.widget.dialog.a4"
        )

        assertEquals(WeChatSemanticPage.UNKNOWN, result.page)
    }

    @Test
    fun `reliable semantic page wins over launcher activity fallback`() {
        val sheet = node(
            children = listOf(
                node(text = "语音通话"),
                node(text = "视频通话"),
                node(text = "取消")
            )
        )

        val result = WeChatSemanticPageRecognizer.recognize(
            snapshot = sheet,
            currentClass = WeChatClassNames.LAUNCHER_UI
        )

        assertEquals(WeChatSemanticPage.VIDEO_SHEET, result.page)
    }

    private fun node(
        text: String? = null,
        editable: Boolean = false,
        children: List<WeChatUiSnapshot> = emptyList()
    ): WeChatUiSnapshot = WeChatUiSnapshot(
        text = text,
        editable = editable,
        children = children
    )
}
