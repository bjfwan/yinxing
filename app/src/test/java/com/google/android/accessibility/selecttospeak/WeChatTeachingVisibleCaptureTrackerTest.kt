package com.google.android.accessibility.selecttospeak

import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatTeachingVisibleCaptureTrackerTest {

    @Test
    fun sameActionCanBeCapturedAgainAfterLeavingAndReturningToTheWindow() {
        val tracker = WeChatTeachingVisibleCaptureTracker()

        tracker.observeWindow("chat")
        assertTrue(tracker.shouldCapture(WeChatTeachingAction.OPEN_MORE))
        assertFalse(tracker.shouldCapture(WeChatTeachingAction.OPEN_MORE))

        tracker.observeWindow("wrong-page")
        tracker.observeWindow("chat")
        assertTrue(tracker.shouldCapture(WeChatTeachingAction.OPEN_MORE))
    }
}
