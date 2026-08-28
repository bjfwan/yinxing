package com.google.android.accessibility.selecttospeak

internal data class WeChatTeachingOverlayPosition(
    val x: Int,
    val y: Int
)

internal object WeChatTeachingDragBounds {
    fun clamp(
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        viewWidth: Int,
        viewHeight: Int,
        margin: Int
    ): WeChatTeachingOverlayPosition {
        val safeMargin = margin.coerceAtLeast(0)
        val maxX = (screenWidth - viewWidth - safeMargin).coerceAtLeast(safeMargin)
        val maxY = (screenHeight - viewHeight - safeMargin).coerceAtLeast(safeMargin)
        return WeChatTeachingOverlayPosition(
            x = x.coerceIn(safeMargin, maxX),
            y = y.coerceIn(safeMargin, maxY)
        )
    }
}
