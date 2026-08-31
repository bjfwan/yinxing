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
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> structuralObservation(
                event = event,
                kind = WeChatTeachingObservationKind.INPUT_CONTACT,
                activeWindowClass = activeWindowClass,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                elapsedMs = elapsedMs
            )
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> structuralObservation(
                event = event,
                kind = WeChatTeachingObservationKind.SCROLL,
                activeWindowClass = activeWindowClass,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                elapsedMs = elapsedMs
            )
            else -> null
        }
    }

    private fun structuralObservation(
        event: AccessibilityEvent,
        kind: WeChatTeachingObservationKind,
        activeWindowClass: String?,
        screenWidth: Int,
        screenHeight: Int,
        elapsedMs: Long
    ): WeChatTeachingObservation? {
        val source = event.source ?: return null
        return try {
            val selector = selectorFromNode(source, screenWidth, screenHeight)
            WeChatTeachingObservation(
                kind = kind,
                windowClass = safeClassName(activeWindowClass),
                selector = if (kind == WeChatTeachingObservationKind.INPUT_CONTACT) {
                    selector.copy(semanticLabel = null)
                } else {
                    selector
                },
                elapsedMs = elapsedMs.coerceAtLeast(0L)
            )
        } finally {
            recycleSafely(source)
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
