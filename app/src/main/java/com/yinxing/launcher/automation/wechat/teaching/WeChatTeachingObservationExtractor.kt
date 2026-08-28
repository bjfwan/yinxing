package com.yinxing.launcher.automation.wechat.teaching

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.yinxing.launcher.automation.wechat.WeChatPackage

object WeChatTeachingObservationExtractor {
    private const val MAX_CLICKABLE_PARENT_DEPTH = 6

    fun extract(
        event: AccessibilityEvent,
        activeWindowClass: String?,
        screenWidth: Int,
        screenHeight: Int,
        elapsedMs: Long
    ): WeChatTeachingObservation? {
        if (event.packageName?.toString() != WeChatPackage.NAME) return null
        return when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val windowClass = safeClassName(event.className) ?: return null
                WeChatTeachingObservation(
                    kind = WeChatTeachingObservationKind.WINDOW,
                    windowClass = windowClass,
                    selector = null,
                    elapsedMs = elapsedMs.coerceAtLeast(0L)
                )
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val source = event.source ?: return null
                try {
                    WeChatTeachingObservation(
                        kind = WeChatTeachingObservationKind.CLICK,
                        windowClass = safeClassName(activeWindowClass),
                        selector = selectorFromNode(source, screenWidth, screenHeight),
                        elapsedMs = elapsedMs.coerceAtLeast(0L)
                    )
                } finally {
                    recycleSafely(source)
                }
            }
            else -> null
        }
    }

    fun selectorFromNode(
        node: AccessibilityNodeInfo,
        screenWidth: Int,
        screenHeight: Int
    ): WeChatTeachingSelector {
        val bounds = Rect().also(node::getBoundsInScreen)
        val hasUsableBounds = !bounds.isEmpty && screenWidth > 0 && screenHeight > 0
        val semanticLabel = WeChatTeachingSemanticLabel.fromVisibleValue(node.contentDescription)
            ?: WeChatTeachingSemanticLabel.fromVisibleValue(node.text)
        return WeChatTeachingSelector(
            resourceId = node.viewIdResourceName
                ?.takeIf { it.startsWith("${WeChatPackage.NAME}:id/") }
                ?.take(160),
            nodeClass = safeClassName(node.className),
            semanticLabel = semanticLabel,
            clickableAncestorDepth = clickableAncestorDepth(node),
            centerXRatio = if (hasUsableBounds) {
                (bounds.exactCenterX() / screenWidth).coerceIn(0f, 1f)
            } else {
                null
            },
            centerYRatio = if (hasUsableBounds) {
                (bounds.exactCenterY() / screenHeight).coerceIn(0f, 1f)
            } else {
                null
            }
        )
    }

    private fun clickableAncestorDepth(node: AccessibilityNodeInfo): Int {
        if (node.isClickable) return 0
        var parent = node.parent
        var depth = 1
        while (parent != null && depth <= MAX_CLICKABLE_PARENT_DEPTH) {
            val next = parent.parent
            val clickable = parent.isClickable
            recycleSafely(parent)
            if (clickable) return depth
            parent = next
            depth++
        }
        recycleSafely(parent)
        return -1
    }

    private fun safeClassName(value: CharSequence?): String? =
        value?.toString()?.trim()?.take(200)?.takeIf { it.isNotEmpty() }

    @Suppress("DEPRECATION")
    private fun recycleSafely(node: AccessibilityNodeInfo?) {
        runCatching { node?.recycle() }
    }
}
