package com.google.android.accessibility.selecttospeak

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatUiSnapshotAnalyzerTest {
    @Test
    fun identifiesLauncherAndSearchPagesFromReplayableSnapshots() {
        val launcherSnapshot = node(
            children = listOf(
                node(text = "微信"),
                node(text = "通讯录"),
                node(text = "发现"),
                node(text = "我")
            )
        )
        val searchSnapshot = node(
            children = listOf(
                node(className = "android.widget.EditText", editable = true),
                node(text = "搜索"),
                node(text = "取消")
            )
        )

        assertTrue(WeChatUiSnapshotAnalyzer.isLauncherReady(launcherSnapshot))
        assertTrue(WeChatUiSnapshotAnalyzer.isSearchPage(searchSnapshot))
        assertFalse(WeChatUiSnapshotAnalyzer.isLauncherReady(searchSnapshot))
    }

    @Test
    fun identifiesContactInfoAndChatLikePages() {
        val contactInfoSnapshot = node(
            children = listOf(
                node(text = "发消息"),
                node(text = "音视频通话")
            )
        )
        val chatSnapshot = node(
            children = listOf(
                node(className = "android.widget.EditText", editable = true),
                node(contentDescription = "更多"),
                node(text = "+")
            )
        )

        assertTrue(WeChatUiSnapshotAnalyzer.isContactInfoPage(contactInfoSnapshot))
        assertTrue(WeChatUiSnapshotAnalyzer.isChatPageLike(chatSnapshot))
        assertFalse(WeChatUiSnapshotAnalyzer.isContactInfoPage(chatSnapshot))
    }

    @Test
    fun detectsContactNameNoResultAndDismissActions() {
        val contactSnapshot = node(
            children = listOf(
                node(text = "妈妈"),
                node(text = "发消息")
            )
        )
        val noResultSnapshot = node(
            children = listOf(
                node(className = "android.widget.EditText", editable = true),
                node(text = "搜索"),
                node(text = "取消"),
                node(text = "无搜索结果")
            )
        )

        val videoSheetSnapshot = node(
            children = listOf(
                node(text = "语音通话"),
                node(text = "视频通话"),
                node(text = "取消")
            )
        )
        val dialogSnapshot = node(
            children = listOf(
                node(text = "我知道了"),
                node(text = "稍后再说")
            )
        )

        assertTrue(WeChatUiSnapshotAnalyzer.containsContactName(contactSnapshot, "妈妈"))
        assertTrue(WeChatUiSnapshotAnalyzer.hasNoSearchResult(noResultSnapshot))
        assertTrue(WeChatUiSnapshotAnalyzer.isVideoCallSheetVisible(videoSheetSnapshot))
        assertEquals(WeChatDismissAction.SHEET_CANCEL, WeChatUiSnapshotAnalyzer.suggestDismissAction(videoSheetSnapshot))
        assertEquals(WeChatDismissAction.CLOSE_DIALOG, WeChatUiSnapshotAnalyzer.suggestDismissAction(dialogSnapshot))
        assertEquals(WeChatDismissAction.SEARCH_CANCEL, WeChatUiSnapshotAnalyzer.suggestDismissAction(noResultSnapshot))
    }

    private fun node(
        text: String? = null,
        contentDescription: String? = null,
        viewIdResourceName: String? = null,
        className: String? = null,
        editable: Boolean = false,
        children: List<WeChatUiSnapshot> = emptyList()
    ): WeChatUiSnapshot {
        return WeChatUiSnapshot(
            text = text,
            contentDescription = contentDescription,
            viewIdResourceName = viewIdResourceName,
            className = className,
            editable = editable,
            children = children
        )
    }
}
