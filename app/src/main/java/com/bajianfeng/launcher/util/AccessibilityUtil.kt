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
            Log.d(TAG, "clickNode: 节点本身可点击")
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 10) {
            if (parent.isClickable) {
                Log.d(TAG, "clickNode: 在第${depth}层父节点找到可点击节点")
                val result = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return result
            }
            val nextParent = parent.parent
            parent = nextParent
            depth++
        }
        
        Log.d(TAG, "clickNode: 未找到可点击节点，尝试坐标点击")
        return false
    }
    
    fun clickNodeByBounds(service: AccessibilityService, node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val x = rect.centerX().toFloat()
        val y = rect.centerY().toFloat()
        
        Log.d(TAG, "clickNodeByBounds: 坐标点击 x=$x, y=$y")
        
        return clickByCoordinate(service, x, y)
    }

    fun clickByCoordinate(service: AccessibilityService, x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.e(TAG, "clickByCoordinate: API版本不支持手势")
            return false
        }
        
        val path = Path()
        path.moveTo(x, y)
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        
        var result = false
        val latch = java.util.concurrent.CountDownLatch(1)
        
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "clickByCoordinate: 手势完成")
                result = true
                latch.countDown()
            }
            
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.e(TAG, "clickByCoordinate: 手势取消")
                result = false
                latch.countDown()
            }
        }, null)
        
        latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
        return result
    }
    
    fun setText(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null) return false
        
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }
    
    fun safeRecycle(node: AccessibilityNodeInfo?) {
        try {
            node?.recycle()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "safeRecycle: 节点已回收")
        }
    }
    
    fun recycleNodes(vararg nodes: AccessibilityNodeInfo?) {
        nodes.forEach { safeRecycle(it) }
    }
    
    fun dumpNodeTree(node: AccessibilityNodeInfo?, depth: Int = 0) {
        if (node == null) return
        val indent = "  ".repeat(depth)
        val rect = Rect()
        node.getBoundsInScreen(rect)
        Log.d(TAG, "${indent}[${node.className}] text='${node.text}' " +
                "clickable=${node.isClickable} editable=${node.isEditable} " +
                "bounds=$rect")
        for (i in 0 until node.childCount) {
            dumpNodeTree(node.getChild(i), depth + 1)
        }
    }
}
