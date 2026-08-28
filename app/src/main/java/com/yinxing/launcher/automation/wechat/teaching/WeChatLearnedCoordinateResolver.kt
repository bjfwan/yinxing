package com.yinxing.launcher.automation.wechat.teaching

data class WeChatLearnedCoordinate(val x: Float, val y: Float)

object WeChatLearnedCoordinateResolver {
    fun resolve(
        selector: WeChatTeachingSelector,
        screenWidth: Int,
        screenHeight: Int
    ): WeChatLearnedCoordinate? {
        if (screenWidth <= 0 || screenHeight <= 0) return null
        val xRatio = selector.centerXRatio?.takeIf { it in 0f..1f } ?: return null
        val yRatio = selector.centerYRatio?.takeIf { it in 0f..1f } ?: return null
        return WeChatLearnedCoordinate(
            x = screenWidth * xRatio,
            y = screenHeight * yRatio
        )
    }
}
