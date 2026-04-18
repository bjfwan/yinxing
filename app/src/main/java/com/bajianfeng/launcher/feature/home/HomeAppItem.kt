package com.bajianfeng.launcher.feature.home

data class HomeAppItem(
    val packageName: String,
    val appName: String,
    val type: Type,
    val iconResId: Int? = null
) {
    val stableId: Long
        get() = "${type.name}:$packageName".hashCode().toLong()

    enum class Type {
        APP,
        PHONE,
        WECHAT_VIDEO,
        ADD
    }
}
