package com.yinxing.launcher.automation.wechat.util

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.yinxing.launcher.common.util.DebugLog

object AccessibilityUtil {

    private const val TAG = "WeChatAccessibility"
    private const val MAX_TRAVERSE_DEPTH = 12
    private const val TAP_DURATION_MS = 60L
    private const val MAX_CLICK_PARENT_DEPTH = 6

    fun findNodeById(root: AccessibilityNodeInfo?, id: String): AccessibilityNodeInfo? {
        if (root == null) return null
        return root.findAccessibilityNodeInfosByViewId(id).firstOrNull()
    }

    fun findAllById(root: AccessibilityNodeInfo?, id: String): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()
        return root.findAccessibilityNodeInfosByViewId(id)
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
        // findAccessibilityNodeInfosByText 只匹配 text 属性，不匹配 contentDescription
        // 先用系统 API 找，再用递归兜底找 contentDescription
        val candidates = root.findAccessibilityNodeInfosByText(text).toMutableList()
        val fromDesc = findNodesByContentDescription(root, text, exactMatch)
        for (node in fromDesc) {
            if (candidates.none { it == node }) candidates.add(node)
        }

        if (candidates.isEmpty()) return null

        val filtered = candidates.filter { node ->
            val matchesText = if (exactMatch) {
                node.text?.toString() == text || node.contentDescription?.toString() == text
            } else {
                node.text?.toString()?.contains(text) == true ||
                    node.contentDescription?.toString()?.contains(text) == true
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

    fun findNodesByContentDescription(
        root: AccessibilityNodeInfo?,
        text: String,
        exactMatch: Boolean = true
    ): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()
        val result = mutableListOf<AccessibilityNodeInfo>()

        fun traverse(node: AccessibilityNodeInfo, depth: Int): Boolean {
            if (depth > MAX_TRAVERSE_DEPTH) return false
            val desc = node.contentDescription?.toString()
            val matched = if (exactMatch) desc == text else desc?.contains(text) == true
            if (matched) result.add(node)
            val childCount = node.childCount
            if (childCount > 0) {
                for (i in 0 until childCount) {
                    val child = node.getChild(i) ?: continue
                    val keep = traverse(child, depth + 1)
                    if (!keep) {
                        safeRecycle(child)
                    }
                }
            }
            return matched
        }

        traverse(root, 0)
        return result
    }

    fun findFirstEditableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        return findFirstEditableNodeInternal(root, 0)
    }

    private fun findFirstEditableNodeInternal(
        node: AccessibilityNodeInfo,
        depth: Int
    ): AccessibilityNodeInfo? {
        if (depth > MAX_TRAVERSE_DEPTH) return null
        if (node.isEditable || node.className == "android.widget.EditText") {
            return node
        }
        val childCount = node.childCount
        if (childCount == 0) return null
        for (index in 0 until childCount) {
            val child = node.getChild(index) ?: continue
            val match = findFirstEditableNodeInternal(child, depth + 1)
            if (match != null) {
                if (match !== child) {
                    safeRecycle(child)
                }
                return match
            }
            safeRecycle(child)
        }
        return null
    }

    fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) {
            DebugLog.w(TAG, "clickNode: node is null")
            return false
        }

        if (node.isClickable) {
            val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            DebugLog.d(TAG) { "clickNode: direct click result=$success node=${summarizeNode(node)}" }
            return success
        }

        var parent = node.parent
        var depth = 0
        while (parent != null && depth < MAX_CLICK_PARENT_DEPTH) {
            if (parent.isClickable) {
                val success = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                DebugLog.d(TAG) {
                    "clickNode: parent click depth=$depth result=$success node=${summarizeNode(parent)}"
                }
                return success
            }
            parent = parent.parent
            depth++
        }

        DebugLog.w(TAG, "clickNode: no clickable ancestor for ${summarizeNode(node)}")
        return false
    }

    fun performClick(service: AccessibilityService, node: AccessibilityNodeInfo?): Boolean {
        return clickNode(node) || clickNodeByBounds(service, node)
    }

    fun clickNodeByBounds(service: AccessibilityService, node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty) return false
        return clickByCoordinate(service, rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    fun clickByCoordinate(service: AccessibilityService, x: Float, y: Float): Boolean {
        DebugLog.d(TAG) { "clickByCoordinate: x=$x y=$y" }
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
            .build()

        return service.dispatchGesture(gesture, null, null)
    }

    fun setText(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null) {
            DebugLog.w(TAG, "setText: node is null")
            return false
        }
        DebugLog.i(TAG) { "setText: '$text' on ${summarizeNode(node)}" }
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    @Suppress("DEPRECATION")
    fun safeRecycle(node: AccessibilityNodeInfo?) {
        try {
            node?.recycle()
        } catch (_: IllegalStateException) {
        }
    }

    fun summarizeNode(node: AccessibilityNodeInfo?): String {
        if (node == null) return "null"
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val parts = mutableListOf<String>()
        parts += "class=${node.className ?: "null"}"
        parts += "package=${node.packageName ?: "null"}"
        node.viewIdResourceName?.takeIf { it.isNotBlank() }?.let { parts += "id=$it" }
        normalize(node.text)?.let { parts += "text=$it" }
        normalize(node.contentDescription)?.let { parts += "desc=$it" }
        parts += "clickable=${node.isClickable}"
        parts += "enabled=${node.isEnabled}"
        parts += "editable=${node.isEditable}"
        parts += "bounds=$bounds"
        parts += "children=${node.childCount}"
        return parts.joinToString(", ")
    }

    fun dumpTree(root: AccessibilityNodeInfo?, maxDepth: Int = 8, maxNodes: Int = 160): String {
        if (root == null) return "root=null"
        val builder = StringBuilder()
        var visited = 0

        fun append(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || visited >= maxNodes) return
            repeat(depth) { builder.append("  ") }
            builder.append("- ").append(summarizeNode(node)).append('\n')
            visited++
            if (depth >= maxDepth) return
            for (index in 0 until node.childCount) {
                if (visited >= maxNodes) return
                val child = node.getChild(index) ?: continue
                append(child, depth + 1)
                safeRecycle(child)
            }
        }

        append(root, 0)
        if (visited >= maxNodes) {
            builder.append("... truncated at ").append(maxNodes).append(" nodes")
        }
        return builder.toString()
    }

    private fun normalize(value: CharSequence?, maxLength: Int = 40): String? {
        val text = value?.toString()?.replace('\n', ' ')?.trim().orEmpty()
        if (text.isEmpty()) return null
        return if (text.length <= maxLength) text else text.take(maxLength) + "..."
    }
}
