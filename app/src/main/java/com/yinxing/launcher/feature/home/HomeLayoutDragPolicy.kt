package com.yinxing.launcher.feature.home

import com.yinxing.launcher.data.home.LauncherPreferences

internal object HomeLayoutDragPolicy {
    fun canDrag(type: HomeAppItem.Type, layoutLocked: Boolean): Boolean {
        return !layoutLocked && type == HomeAppItem.Type.APP
    }
}

internal object HomeLongPressPolicy {
    const val EXTENDED_DELAY_MILLIS = 1_000L

    fun usesExtendedDelay(response: String): Boolean {
        return response == LauncherPreferences.HOME_LONG_PRESS_LONG
    }
}
