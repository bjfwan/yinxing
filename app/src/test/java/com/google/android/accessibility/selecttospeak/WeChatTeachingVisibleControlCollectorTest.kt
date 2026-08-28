package com.google.android.accessibility.selecttospeak

import com.yinxing.launcher.automation.wechat.WeChatClassNames
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingAction
import org.junit.Assert.assertEquals
import org.junit.Test

class WeChatTeachingVisibleControlCollectorTest {

    @Test
    fun launcherClassWithChatContentIsCanonicalizedToChat() {
        val snapshot = node(
            children = listOf(
                node(editable = true),
                node(
                    contentDescription = "更多功能按钮，已折叠",
                    viewId = "com.tencent.mm:id/bjz",
                    bounds = WeChatUiBounds(900, 1800, 1000, 2000)
                )
            )
        )

        assertEquals(
            WeChatClassNames.CHATTING_UI,
            WeChatTeachingVisibleControlCollector.canonicalWindowClass(
                snapshot,
                WeChatClassNames.LAUNCHER_UI
            )
        )
    }

    @Test
    fun visibleChatControlsBecomeReusableSelectorObservations() {
        val snapshot = node(
            children = listOf(
                node(
                    contentDescription = "更多功能按钮，已折叠",
                    viewId = "com.tencent.mm:id/bjz",
                    className = "android.widget.ImageButton",
                    bounds = WeChatUiBounds(900, 1800, 1000, 2000)
                ),
                node(
                    text = "音视频通话",
                    className = "android.widget.TextView",
                    bounds = WeChatUiBounds(100, 1400, 500, 1600)
                )
            )
        )

        val observations = WeChatTeachingVisibleControlCollector.collect(
            snapshot = snapshot,
            activeWindowClass = WeChatClassNames.CHATTING_UI,
            screenWidth = 1000,
            screenHeight = 2000,
            elapsedMs = 500L
        )

        assertEquals(
            listOf(WeChatTeachingAction.OPEN_MORE, WeChatTeachingAction.OPEN_VIDEO_MENU),
            observations.map { it.action }
        )
        assertEquals("com.tencent.mm:id/bjz", observations.first().observation.selector?.resourceId)
    }

    @Test
    fun bottomVideoCallLabelIsAcceptedAsTheChatVideoMenu() {
        val snapshot = node(
            children = listOf(
                node(
                    text = "视频通话",
                    viewId = "com.tencent.mm:id/device_video_menu",
                    className = "android.widget.TextView",
                    bounds = WeChatUiBounds(480, 1400, 720, 1600)
                )
            )
        )

        val observations = WeChatTeachingVisibleControlCollector.collect(
            snapshot = snapshot,
            activeWindowClass = WeChatClassNames.CHATTING_UI,
            screenWidth = 1000,
            screenHeight = 2000,
            elapsedMs = 500L
        )

        assertEquals(
            listOf(WeChatTeachingAction.OPEN_VIDEO_MENU),
            observations.map { it.action }
        )
    }

    private fun node(
        text: String? = null,
        contentDescription: String? = null,
        viewId: String? = null,
        className: String? = null,
        editable: Boolean = false,
        bounds: WeChatUiBounds? = null,
        children: List<WeChatUiSnapshot> = emptyList()
    ) = WeChatUiSnapshot(
        text = text,
        contentDescription = contentDescription,
        viewIdResourceName = viewId,
        className = className,
        editable = editable,
        bounds = bounds,
        children = children
    )
}
