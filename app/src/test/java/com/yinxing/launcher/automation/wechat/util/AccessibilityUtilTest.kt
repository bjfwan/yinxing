package com.yinxing.launcher.automation.wechat.util

import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccessibilityUtilTest {

    @Test
    fun findFirstEditableNode_returnsNull_whenTreeHasNoEditable() {
        val root = node()
        val child = node()
        shadowOf(root).addChild(child)

        assertNull(AccessibilityUtil.findFirstEditableNode(root))
    }

    @Test
    fun findFirstEditableNode_returnsRoot_whenRootIsEditable() {
        val root = node(editable = true)

        assertSame(root, AccessibilityUtil.findFirstEditableNode(root))
    }

    @Test
    fun findFirstEditableNode_findsEditableDescendant() {
        val root = node()
        val outer = node()
        val inner = node(editable = true)
        shadowOf(root).addChild(outer)
        shadowOf(outer).addChild(inner)

        val match = AccessibilityUtil.findFirstEditableNode(root)
        assertNotNull(match)
        assertTrue(match!!.isEditable)
        AccessibilityUtil.safeRecycle(match)
    }

    @Test
    fun findFirstEditableNode_findsEditTextByClassName() {
        val root = node()
        val edit = node().apply { className = "android.widget.EditText" }
        shadowOf(root).addChild(edit)

        val match = AccessibilityUtil.findFirstEditableNode(root)
        assertNotNull(match)
        assertEquals("android.widget.EditText", match!!.className?.toString())
        AccessibilityUtil.safeRecycle(match)
    }

    @Test
    fun findFirstEditableNode_walksMultipleSiblings_beforeMatching() {
        val root = node()
        val a = node()
        val b = node()
        val c = node(editable = true)
        shadowOf(root).addChild(a)
        shadowOf(root).addChild(b)
        shadowOf(root).addChild(c)

        val match = AccessibilityUtil.findFirstEditableNode(root)
        assertNotNull(match)
        assertTrue(match!!.isEditable)
        AccessibilityUtil.safeRecycle(match)
    }

    @Test
    fun findNodesByContentDescription_returnsEmpty_whenNoMatch() {
        val root = node()
        shadowOf(root).addChild(node(desc = "miss"))
        shadowOf(root).addChild(node(desc = "another"))

        val matches = AccessibilityUtil.findNodesByContentDescription(root, "hit", exactMatch = true)
        assertTrue(matches.isEmpty())
    }

    @Test
    fun findNodesByContentDescription_collectsAllExactMatches() {
        val root = node()
        val a = node(desc = "hit")
        val b = node(desc = "hit-deeper")
        val c = node(desc = "hit")
        shadowOf(root).addChild(a)
        shadowOf(root).addChild(b)
        shadowOf(root).addChild(c)

        val matches = AccessibilityUtil.findNodesByContentDescription(root, "hit", exactMatch = true)
        assertEquals(2, matches.size)
        assertTrue(matches.all { it.contentDescription?.toString() == "hit" })
        matches.forEach(AccessibilityUtil::safeRecycle)
    }

    @Test
    fun findNodesByContentDescription_collectsContainsMatches() {
        val root = node()
        val a = node(desc = "alpha hit beta")
        val b = node(desc = "no")
        shadowOf(root).addChild(a)
        shadowOf(root).addChild(b)

        val matches = AccessibilityUtil.findNodesByContentDescription(root, "hit", exactMatch = false)
        assertEquals(1, matches.size)
        matches.forEach(AccessibilityUtil::safeRecycle)
    }

    @Test
    fun findNodesByContentDescription_keepsRecursionInsideDepthBudget() {
        val root = node()
        var current = root
        repeat(20) {
            val next = node()
            shadowOf(current).addChild(next)
            current = next
        }
        shadowOf(current).addChild(node(desc = "hit"))

        val matches = AccessibilityUtil.findNodesByContentDescription(root, "hit", exactMatch = true)
        assertTrue(matches.isEmpty())
    }

    @Test
    fun findFirstEditableNode_keepsRecursionInsideDepthBudget() {
        val root = node()
        var current = root
        repeat(20) {
            val next = node()
            shadowOf(current).addChild(next)
            current = next
        }
        shadowOf(current).addChild(node(editable = true))

        val match = AccessibilityUtil.findFirstEditableNode(root)
        assertNull(match)
    }

    @Test
    fun rootRemainsUsable_afterFindNodesByContentDescription() {
        val root = node(desc = "label")
        shadowOf(root).addChild(node(desc = "child"))
        shadowOf(root).addChild(node(desc = "child"))

        AccessibilityUtil.findNodesByContentDescription(root, "child", exactMatch = true)
            .forEach(AccessibilityUtil::safeRecycle)

        assertEquals("label", root.contentDescription?.toString())
    }

    @Test
    fun rootRemainsUsable_afterFindFirstEditableNode() {
        val root = node()
        val outer = node()
        val inner = node(editable = true)
        shadowOf(root).addChild(outer)
        shadowOf(outer).addChild(inner)

        val match = AccessibilityUtil.findFirstEditableNode(root)
        AccessibilityUtil.safeRecycle(match)

        assertFalse(root.isEditable)
    }

    private fun node(desc: String? = null, editable: Boolean = false): AccessibilityNodeInfo {
        val info = AccessibilityNodeInfo.obtain()
        if (desc != null) info.contentDescription = desc
        if (editable) info.isEditable = true
        return info
    }
}
