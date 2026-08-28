package com.yinxing.launcher.feature.home

import com.yinxing.launcher.data.home.LauncherPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutDragPolicyTest {
    @Test
    fun ordinaryAppsCanOnlyMoveWhenHomeLayoutIsUnlocked() {
        assertTrue(HomeLayoutDragPolicy.canDrag(HomeAppItem.Type.APP, layoutLocked = false))
        assertFalse(HomeLayoutDragPolicy.canDrag(HomeAppItem.Type.APP, layoutLocked = true))
    }

    @Test
    fun builtInItemsNeverMove() {
        assertFalse(HomeLayoutDragPolicy.canDrag(HomeAppItem.Type.PHONE, layoutLocked = false))
        assertFalse(HomeLayoutDragPolicy.canDrag(HomeAppItem.Type.WECHAT_VIDEO, layoutLocked = false))
        assertFalse(HomeLayoutDragPolicy.canDrag(HomeAppItem.Type.ADD, layoutLocked = false))
    }

    @Test
    fun longResponseRequiresAOneSecondHold() {
        assertFalse(HomeLongPressPolicy.usesExtendedDelay(LauncherPreferences.HOME_LONG_PRESS_STANDARD))
        assertTrue(HomeLongPressPolicy.usesExtendedDelay(LauncherPreferences.HOME_LONG_PRESS_LONG))
        assertEquals(1_000L, HomeLongPressPolicy.EXTENDED_DELAY_MILLIS)
    }
}
