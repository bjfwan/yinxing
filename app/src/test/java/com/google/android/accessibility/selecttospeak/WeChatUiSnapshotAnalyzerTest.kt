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

    @Test
    fun matchesRemarkedContactResultByNicknameOrWechatId() {
        val remarkResultSnapshot = node(
            clickable = true,
            children = listOf(
                node(text = "挑战自我", viewIdResourceName = "com.tencent.mm:id/odf"),
                node(text = "昵称: wan.")
            )
        )
        val wechatIdResultSnapshot = node(
            clickable = true,
            children = listOf(
                node(text = "王二", viewIdResourceName = "com.tencent.mm:id/kbq"),
                node(text = "微信号：wxid_wanan")
            )
        )

        assertEquals("挑战自我", WeChatUiSnapshotAnalyzer.findContactSearchResultDisplayName(remarkResultSnapshot, "wan."))
        assertEquals("王二", WeChatUiSnapshotAnalyzer.findContactSearchResultDisplayName(wechatIdResultSnapshot, "wxid_wanan"))
    }

    @Test
    fun ignoresPlainNetworkResultRowsAndSupportsResolvedAliasDetection() {
        val networkResultSnapshot = node(
            clickable = true,
            children = listOf(
                node(text = "wan.")
            )
        )
        val conversationSnapshot = node(
            children = listOf(
                node(text = "挑战自我"),
                node(text = "发消息")
            )
        )

        assertEquals(null, WeChatUiSnapshotAnalyzer.findContactSearchResultDisplayName(networkResultSnapshot, "wan."))
        assertTrue(WeChatUiSnapshotAnalyzer.containsAnyContactName(conversationSnapshot, listOf("wan.", "挑战自我")))
        assertFalse(WeChatUiSnapshotAnalyzer.containsContactName(conversationSnapshot, "wan."))
    }

    @Test
    fun requiresKnownTitleOrSecondaryFieldForSearchResultDisplayName() {
        val genericExactTextSnapshot = node(
            clickable = true,
            children = listOf(
                node(text = "wan."),
                node(text = "搜索网络结果")
            )
        )
        val unlabeledAliasSnapshot = node(
            clickable = true,
            children = listOf(
                node(text = "挑战自我", viewIdResourceName = "com.tencent.mm:id/odf"),
                node(text = "wan.")
            )
        )
        val labeledAliasSnapshot = node(
            clickable = true,
            children = listOf(
                node(text = "挑战自我", viewIdResourceName = "com.tencent.mm:id/odf"),
                node(text = "昵称：wan.")
            )
        )

        assertEquals(null, WeChatUiSnapshotAnalyzer.findContactSearchResultDisplayName(genericExactTextSnapshot, "wan."))
        assertEquals(null, WeChatUiSnapshotAnalyzer.findContactSearchResultDisplayName(unlabeledAliasSnapshot, "wan."))
        assertEquals("挑战自我", WeChatUiSnapshotAnalyzer.findContactSearchResultDisplayName(labeledAliasSnapshot, "wan."))
    }

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
