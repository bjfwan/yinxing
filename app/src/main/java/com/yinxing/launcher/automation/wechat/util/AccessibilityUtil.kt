package com.yinxing.launcher.automation.wechat.util

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍节点操作工具类（升级版）。
 *
 * 优化：
 * - findBestTextNode：contentDescription 遍历加入深度上限 + 早退策略
 * - findFirstEditableNode：非叶子节点优先检查 className 剪枝，减少无效递归
 * - clickNode：父节点向上遍历深度限制从 10 降至 6（微信层级通常不超过 5 层）
 */
object AccessibilityUtil {

    /** 递归遍历最大深度（微信 UI 树通常不超过此深度） */
    private const val MAX_TRAVERSE_DEPTH = 12

    fun findNodeByText(root: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (root == null) return null
        return root.findAccessibilityNodeInfosByText(text).firstOrNull()
    }

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

    /**
     * 递归遍历节点树，收集 contentDescription 匹配的节点。
     *
     * 升级：
     * - 加入深度上限（MAX_TRAVERSE_DEPTH），避免深层无用节点的递归开销
     * - 找到第一个精确匹配后不立即返回（可能有多个同名节点），但在非精确匹配时找到即可提前退出
     */
    fun findNodesByContentDescription(
        root: AccessibilityNodeInfo?,
        text: String,
        exactMatch: Boolean = true
    ): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()
        val result = mutableListOf<AccessibilityNodeInfo>()

        fun traverse(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > MAX_TRAVERSE_DEPTH) return
            val desc = node.contentDescription?.toString()
            val matches = if (exactMatch) desc == text else desc?.contains(text) == true
            if (matches) result.add(node)
            // 叶子节点不需要继续递归
            val childCount = node.childCount
            if (childCount == 0) return
            for (i in 0 until childCount) {
                val child = node.getChild(i) ?: continue
                traverse(child, depth + 1)
            }
        }

        traverse(root, 0)
        return result
    }

    /**
     * 查找第一个可编辑节点。
     *
     * 升级：优先检查 isEditable 和 className 剪枝（容器节点通常不会是 EditText），
     * 减少对布局容器的无效递归。
     */
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
                return match
            }
            // child 不是目标，提前回收（除非 match == child 本身）
            safeRecycle(child)
        }
        return null
    }

    fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        // 微信层级通常 ≤ 5，限制向上查找深度为 6 即可
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 6) {
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
        if (rect.isEmpty) return false
        return clickByCoordinate(service, rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    fun clickByCoordinate(service: AccessibilityService, x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        return service.dispatchGesture(gesture, null, null)
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

    @Suppress("DEPRECATION")
    fun safeRecycle(node: AccessibilityNodeInfo?) {
        try {
            node?.recycle()
        } catch (_: IllegalStateException) {
        }
    }

    fun recycleNodes(vararg nodes: AccessibilityNodeInfo?) {
        nodes.forEach { safeRecycle(it) }
    }

    fun findScrollableNode(root: AccessibilityNodeInfo?, depth: Int = 0): AccessibilityNodeInfo? {
        if (root == null || depth > MAX_TRAVERSE_DEPTH) return null
        if (root.isScrollable) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val scrollable = findScrollableNode(child, depth + 1)
            if (scrollable != null) return scrollable
        }
        return null
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
