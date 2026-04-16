package com.bajianfeng.launcher.automation.wechat.manager

import com.google.android.accessibility.selecttospeak.WeChatUiSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class StateDetectionManagerTest {
    private val manager = StateDetectionManager()

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

        assertEquals(
            StateDetectionManager.DetectedPage.HOME,
            manager.detect(launcherSnapshot, "com.tencent.mm.ui.LauncherUI")
        )
        assertEquals(
            StateDetectionManager.DetectedPage.SEARCH,
            manager.detect(searchSnapshot, "com.tencent.mm.plugin.fts.ui.FTSMainUI")
        )
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

        assertEquals(
            StateDetectionManager.DetectedPage.CHAT,
            manager.detect(chatSnapshot, "com.tencent.mm.ui.chatting.ChattingUI")
        )
        assertEquals(
            StateDetectionManager.DetectedPage.CONTACT_DETAIL,
            manager.detect(contactSnapshot, "com.tencent.mm.plugin.profile.ui.ContactInfoUI")
        )
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
