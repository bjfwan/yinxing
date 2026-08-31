package com.yinxing.launcher.automation.wechat.teaching

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class WeChatTeachingObservationExtractorTest {

    @Test
    fun selectorKeepsStructureButDropsContactText() {
        val node = AccessibilityNodeInfo.obtain().apply {
            className = "android.widget.RelativeLayout"
            viewIdResourceName = "com.tencent.mm:id/kbq"
            text = "wan."
            isClickable = true
            setBoundsInScreen(Rect(100, 300, 500, 500))
        }

        val selector = WeChatTeachingObservationExtractor.selectorFromNode(
            node = node,
            screenWidth = 1000,
            screenHeight = 2000
        )

        assertEquals("com.tencent.mm:id/kbq", selector.resourceId)
        assertEquals("android.widget.RelativeLayout", selector.nodeClass)
        assertNull(selector.semanticLabel)
        assertEquals(0.3f, selector.centerXRatio!!, 0.001f)
        assertEquals(0.2f, selector.centerYRatio!!, 0.001f)
    }

    @Test
    fun knownDescriptionIsReducedToFixedSemanticLabel() {
        val node = AccessibilityNodeInfo.obtain().apply {
            className = "android.widget.ImageButton"
            contentDescription = "更多功能按钮，已折叠"
            isClickable = true
            setBoundsInScreen(Rect(900, 1800, 1000, 2000))
        }

        val selector = WeChatTeachingObservationExtractor.selectorFromNode(node, 1000, 2000)

        assertEquals(WeChatTeachingSemanticLabel.MORE, selector.semanticLabel)
        assertTrue(selector.resourceId == null)
    }

    @Test
    fun nonWechatWindowEventsAreIgnored() {
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
            packageName = "com.example.other"
            className = "com.example.PrivateActivity"
        }

        assertNull(
            WeChatTeachingObservationExtractor.extract(
                event = event,
                activeWindowClass = null,
                screenWidth = 1000,
                screenHeight = 2000,
                elapsedMs = 20L
            )
        )
    }

    @Test
    fun wechatVideoActivityBecomesWindowObservation() {
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
            packageName = "com.tencent.mm"
            className = "com.tencent.mm.plugin.voip.ui.VideoActivity"
        }

        val observation = WeChatTeachingObservationExtractor.extract(
            event = event,
            activeWindowClass = null,
            screenWidth = 1000,
            screenHeight = 2000,
            elapsedMs = 20L
        )

        assertEquals(WeChatTeachingObservationKind.WINDOW, observation?.kind)
        assertEquals("com.tencent.mm.plugin.voip.ui.VideoActivity", observation?.windowClass)
    }

    @Test
    fun textChangeBecomesContactPlaceholderWithoutKeepingEnteredText() {
        val node = AccessibilityNodeInfo.obtain().apply {
            className = "android.widget.EditText"
            viewIdResourceName = "com.tencent.mm:id/search_input"
            text = "视频通话"
            isEditable = true
        }
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED).apply {
            packageName = "com.tencent.mm"
            className = "android.widget.EditText"
            text.add("视频通话")
        }
        shadowOf(event).setSourceNode(node)

        val observation = WeChatTeachingObservationExtractor.extract(
            event,
            "com.tencent.mm.plugin.fts.ui.FTSMainUI",
            1000,
            2000,
            20L
        )

        assertEquals(WeChatTeachingObservationKind.INPUT_CONTACT, observation?.kind)
        assertNull(observation?.selector?.semanticLabel)
        assertFalse(observation.toString().contains("视频通话"))
    }

    @Test
    fun scrollEventBecomesStructuralObservation() {
        val node = AccessibilityNodeInfo.obtain().apply {
            className = "androidx.recyclerview.widget.RecyclerView"
            viewIdResourceName = "com.tencent.mm:id/result_list"
            isScrollable = true
        }
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_SCROLLED).apply {
            packageName = "com.tencent.mm"
            className = "androidx.recyclerview.widget.RecyclerView"
        }
        shadowOf(event).setSourceNode(node)

        val observation = WeChatTeachingObservationExtractor.extract(
            event,
            "com.tencent.mm.plugin.fts.ui.FTSMainUI",
            1000,
            2000,
            20L
        )

        assertEquals(WeChatTeachingObservationKind.SCROLL, observation?.kind)
    }
}
