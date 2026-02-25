package com.bajianfeng.launcher.util

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

object AccessibilityUtil {

    private const val TAG = "AccessibilityUtil"

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

    fun clickNodeByBounds(service: AccessibilityService, node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return clickByCoordinate(service, rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    fun clickByCoordinate(service: AccessibilityService, x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false

        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        service.dispatchGesture(gesture, null, null)
        return true
    }

    fun scrollDown(service: AccessibilityService, screenWidth: Int, screenHeight: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false

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

    fun dumpParentChain(node: AccessibilityNodeInfo?) {
        if (node == null) return
        val rect = Rect()
        node.getBoundsInScreen(rect)
        Log.d(TAG, "TARGET: [${node.className}] text='${node.text}' id='${node.viewIdResourceName}' clickable=${node.isClickable} bounds=$rect childCount=${node.childCount}")

        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 15) {
            val pRect = Rect()
            parent.getBoundsInScreen(pRect)
            Log.d(TAG, "PARENT[$depth]: [${parent.className}] text='${parent.text}' id='${parent.viewIdResourceName}' clickable=${parent.isClickable} bounds=$pRect childCount=${parent.childCount}")
            parent = parent.parent
            depth++
        }
    }

    fun dumpNodeTree(node: AccessibilityNodeInfo?, depth: Int = 0) {
        if (node == null || depth > 5) return
        val indent = "  ".repeat(depth)
        val rect = Rect()
        node.getBoundsInScreen(rect)
        Log.d(TAG, "${indent}[${node.className}] text='${node.text}' desc='${node.contentDescription}' id='${node.viewIdResourceName}' clickable=${node.isClickable} bounds=$rect")
        for (i in 0 until node.childCount) {
            dumpNodeTree(node.getChild(i), depth + 1)
        }
    }
}
