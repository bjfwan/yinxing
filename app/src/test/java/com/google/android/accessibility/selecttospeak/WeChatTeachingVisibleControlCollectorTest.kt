package com.google.android.accessibility.selecttospeak

import com.yinxing.launcher.automation.wechat.WeChatClassNames
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingAction
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingSemanticLabel
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
        assertEquals(
            WeChatTeachingSemanticLabel.AUDIO_VIDEO_MENU,
            observations.first { it.action == WeChatTeachingAction.OPEN_VIDEO_MENU }
                .observation.selector?.semanticLabel
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

    @Test
    fun hiddenVideoControlDoesNotPretendTheChatMenuIsOpen() {
        val snapshot = node(
            children = listOf(
                node(
                    contentDescription = "更多功能按钮，已折叠",
                    viewId = "com.tencent.mm:id/bjz",
                    className = "android.widget.ImageButton",
                    bounds = WeChatUiBounds(900, 1800, 1000, 2000)
                ),
                node(
                    text = "视频通话",
                    viewId = "com.tencent.mm:id/hidden_video",
                    className = "android.widget.TextView",
                    visibleToUser = false,
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
            listOf(WeChatTeachingAction.OPEN_MORE),
            observations.map { it.action }
        )
    }

    @Test
    fun reusedMoreIdPrefersTheBottomComposerButtonOverChatContent() {
        val snapshot = node(
            children = listOf(
                node(
                    viewId = "com.tencent.mm:id/bjz",
                    className = "android.widget.ImageButton",
                    bounds = WeChatUiBounds(900, 1150, 1000, 1250)
                ),
                node(
                    contentDescription = "更多功能按钮，已折叠",
                    viewId = "com.tencent.mm:id/bjz",
                    className = "android.widget.ImageButton",
                    bounds = WeChatUiBounds(900, 1850, 1000, 1950)
                )
            )
        )

        val more = WeChatTeachingVisibleControlCollector.collect(
            snapshot = snapshot,
            activeWindowClass = WeChatClassNames.CHATTING_UI,
            screenWidth = 1000,
            screenHeight = 2000,
            elapsedMs = 500L
        ).single()

        assertEquals(WeChatTeachingAction.OPEN_MORE, more.action)
        assertEquals(0.95f, more.observation.selector?.centerYRatio)
    }

    private fun node(
        text: String? = null,
        contentDescription: String? = null,
        viewId: String? = null,
        className: String? = null,
        editable: Boolean = false,
        visibleToUser: Boolean = true,
        bounds: WeChatUiBounds? = null,
        children: List<WeChatUiSnapshot> = emptyList()
    ) = WeChatUiSnapshot(
        text = text,
        contentDescription = contentDescription,
        viewIdResourceName = viewId,
        className = className,
        editable = editable,
        visibleToUser = visibleToUser,
        bounds = bounds,
        children = children
    )
}
