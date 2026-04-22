package com.yinxing.launcher.automation.wechat.manager

import com.google.android.accessibility.selecttospeak.WeChatUiSnapshot
import com.google.android.accessibility.selecttospeak.WeChatUiSnapshotAnalyzer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StateDetectionManagerTest {
    @Test
    fun detectPrefersSnapshotForLauncherAndSearchPages() {
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
                node(text = "取消"),
                node(className = "android.widget.EditText", editable = true, text = "搜索")
            )
        )

        assertTrue(WeChatUiSnapshotAnalyzer.isLauncherReady(launcherSnapshot))
        assertTrue(WeChatUiSnapshotAnalyzer.isSearchPage(searchSnapshot))
    }

    @Test
    fun detectRecognizesChatAndContactDetailPages() {
        val chatSnapshot = node(
            children = listOf(
                node(text = "+"),
                node(className = "android.widget.EditText", editable = true),
                node(contentDescription = "更多功能")
            )
        )
        val contactSnapshot = node(
            children = listOf(
                node(text = "发消息"),
                node(text = "音视频通话")
            )
        )

        assertTrue(WeChatUiSnapshotAnalyzer.isChatPageLike(chatSnapshot))
        assertTrue(WeChatUiSnapshotAnalyzer.isContactInfoPage(contactSnapshot))
        assertFalse(WeChatUiSnapshotAnalyzer.isLauncherReady(chatSnapshot))
    }

    private fun node(
        text: String? = null,
        contentDescription: String? = null,
        className: String? = null,
        editable: Boolean = false,
        children: List<WeChatUiSnapshot> = emptyList()
    ): WeChatUiSnapshot {
        return WeChatUiSnapshot(
            text = text,
            contentDescription = contentDescription,
            className = className,
            editable = editable,
            children = children
        )
    }
}

