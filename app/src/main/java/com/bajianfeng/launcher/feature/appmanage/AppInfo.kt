package com.bajianfeng.launcher.feature.appmanage

data class AppInfo(
    val packageName: String,
    val appName: String,
    val isSelected: Boolean = false
) {
    val stableId: Long
        get() = packageName.hashCode().toLong()
}
