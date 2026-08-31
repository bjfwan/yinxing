package com.yinxing.launcher.common.util

import android.content.Context

class HomeRedirectPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        FILE_NAME,
        Context.MODE_PRIVATE
    )

    fun isEnabled(): Boolean = preferences.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    companion object {
        const val FILE_NAME = "home_redirect_preferences"
        private const val KEY_ENABLED = "enabled"
    }
}
