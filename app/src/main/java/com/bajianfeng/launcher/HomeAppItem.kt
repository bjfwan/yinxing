package com.bajianfeng.launcher

import android.graphics.drawable.Drawable

data class HomeAppItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable,
    val type: Type
) {
    enum class Type {
        APP, SETTINGS, ADD, WEATHER
    }
}
