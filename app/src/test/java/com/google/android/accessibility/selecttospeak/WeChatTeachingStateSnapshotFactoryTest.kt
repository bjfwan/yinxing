package com.google.android.accessibility.selecttospeak

import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingSemanticLabel
import org.junit.Assert.assertFalse
import org.junit.Test

class WeChatTeachingStateSnapshotFactoryTest {

    @Test
    fun hiddenControlsDoNotSatisfyReplayState() {
        val state = WeChatTeachingStateSnapshotFactory.create(
            windowClass = "com.tencent.mm.ui.chatting.ChattingUI",
            snapshot = WeChatUiSnapshot(
                children = listOf(
                    WeChatUiSnapshot(
                        text = "视频通话",
                        viewIdResourceName = "com.tencent.mm:id/hidden_video",
                        visibleToUser = false
                    )
                )
            )
        )

        assertFalse(WeChatTeachingSemanticLabel.VIDEO_CALL in state.semanticLabels)
        assertFalse("com.tencent.mm:id/hidden_video" in state.resourceIds)
    }
}
