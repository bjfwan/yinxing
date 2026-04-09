package com.bajianfeng.launcher.automation.wechat.util

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

object AccessibilityUtil {

    fun findNodeByText(root: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (root == null) return null
        return root.findAccessibilityNodeInfosByText(text).firstOrNull()
    }

    fun findNodeById(root: AccessibilityNodeInfo?, id: String): AccessibilityNodeInfo? {
        if (root == null) return null
        return root.findAccessibilityNodeInfosByViewId(id).firstOrNull()
    }

    fun findAllByText(root: AccessibilityNodeInfo?, text: String): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()
        return root.findAccessibilityNodeInfosByText(text)
    }

    fun findBestTextNode(
        root: AccessibilityNodeInfo?,
        text: String,
        exactMatch: Boolean = true,
        preferBottom: Boolean = false,
        excludeEditable: Boolean = true
    ): AccessibilityNodeInfo? {
        if (root == null) return null
        val candidates = root.findAccessibilityNodeInfosByText(text)
        if (candidates.isEmpty()) {
            return null
        }

        val filtered = candidates.filter { node ->
            val matchesText = if (exactMatch) {
                node.text?.toString() == text || node.contentDescription?.toString() == text
            } else {
                node.text?.toString()?.contains(text) == true || node.contentDescription?.toString()?.contains(text) == true
            }
            matchesText && (!excludeEditable || !node.isEditable)
        }

        val target = filtered.maxByOrNull { node ->
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (preferBottom) bounds.centerY() else -bounds.centerY()
        }

        candidates.forEach { node ->
            if (node != target) {
                safeRecycle(node)
            }
        }

        return target
    }

    fun findFirstEditableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.isEditable || root.className == "android.widget.EditText") {
            return root
        }
        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            val match = findFirstEditableNode(child)
            if (match != null) {
                return match
            }
        }
        return null
    }

    fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 10) {
            if (parent.isClickable) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            parent = parent.parent
            depth++
        }

        return false
    }

    fun performClick(service: AccessibilityService, node: AccessibilityNodeInfo?): Boolean {
        return clickNode(node) || clickNodeByBounds(service, node)
    }

    fun clickNodeByBounds(service: AccessibilityService, node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return clickByCoordinate(service, rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    fun clickByCoordinate(service: AccessibilityService, x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        service.dispatchGesture(gesture, null, null)
        return true
    }

    fun scrollDown(service: AccessibilityService, screenWidth: Int, screenHeight: Int): Boolean {
        val centerX = screenWidth / 2f
        val startY = screenHeight * 0.7f
        val endY = screenHeight * 0.3f

        val path = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()

        service.dispatchGesture(gesture, null, null)
        return true
    }

    fun scrollNodeDown(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    fun setText(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null) return false
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    fun safeRecycle(node: AccessibilityNodeInfo?) {
        try {
            node?.recycle()
        } catch (_: IllegalStateException) {
        }
    }

    fun recycleNodes(vararg nodes: AccessibilityNodeInfo?) {
        nodes.forEach { safeRecycle(it) }
    }

    fun findScrollableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.isScrollable) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val scrollable = findScrollableNode(child)
            if (scrollable != null) return scrollable
        }
        return null
    }

}
