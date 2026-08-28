package com.google.android.accessibility.selecttospeak

import org.junit.Assert.assertEquals
import org.junit.Test

class WeChatTeachingDragBoundsTest {

    @Test
    fun dragPositionIsKeptInsideVisibleScreen() {
        val position = WeChatTeachingDragBounds.clamp(
            x = 980,
            y = -40,
            screenWidth = 1080,
            screenHeight = 2400,
            viewWidth = 180,
            viewHeight = 300,
            margin = 12
        )

        assertEquals(888, position.x)
        assertEquals(12, position.y)
    }

    @Test
    fun dragPositionCanMoveFreelyInsideScreen() {
        val position = WeChatTeachingDragBounds.clamp(
            x = 420,
            y = 700,
            screenWidth = 1080,
            screenHeight = 2400,
            viewWidth = 180,
            viewHeight = 300,
            margin = 12
        )

        assertEquals(420, position.x)
        assertEquals(700, position.y)
    }
}
