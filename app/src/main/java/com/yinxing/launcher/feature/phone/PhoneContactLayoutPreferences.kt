package com.yinxing.launcher.feature.phone

import android.content.Context

enum class PhoneContactLayoutStyle {
    LARGE,
    GRID
}

class PhoneContactLayoutPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun get(): PhoneContactLayoutStyle = runCatching {
        PhoneContactLayoutStyle.valueOf(
            preferences.getString(KEY_LAYOUT_STYLE, PhoneContactLayoutStyle.LARGE.name).orEmpty()
        )
    }.getOrDefault(PhoneContactLayoutStyle.LARGE)

    fun set(style: PhoneContactLayoutStyle) {
        preferences.edit().putString(KEY_LAYOUT_STYLE, style.name).apply()
    }

    companion object {
        private const val PREFS_NAME = "phone_contact_ui"
        private const val KEY_LAYOUT_STYLE = "layout_style"
    }
}
