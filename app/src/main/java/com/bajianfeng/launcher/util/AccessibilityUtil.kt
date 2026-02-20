package com.bajianfeng.launcher.util

import android.view.accessibility.AccessibilityNodeInfo

object AccessibilityUtil {
    fun findNodeByText(root: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (root == null) return null
        
        val nodes = root.findAccessibilityNodeInfosByText(text)
        return nodes.firstOrNull()
    }
    
    fun findNodeById(root: AccessibilityNodeInfo?, id: String): AccessibilityNodeInfo? {
        if (root == null) return null
        
        val nodes = root.findAccessibilityNodeInfosByViewId(id)
        return nodes.firstOrNull()
    }
    
    fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            parent = parent.parent
        }
        
        return false
    }
    
    fun setText(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null) return false
        
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val arguments = android.os.Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }
    
    fun recycleNodes(vararg nodes: AccessibilityNodeInfo?) {
        nodes.forEach { it?.recycle() }
    }
}
