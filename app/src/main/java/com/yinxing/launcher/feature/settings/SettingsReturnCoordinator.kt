package com.yinxing.launcher.feature.settings

import android.content.Context

internal object SettingsReturnCoordinator {
    private const val PREFS_NAME = "settings_return"
    private const val KEY_RETURN_TO_DEVICE_SETTINGS = "return_to_device_settings"

    fun markDeviceSettingsReturn(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RETURN_TO_DEVICE_SETTINGS, true)
            .commit()
    }

    fun consumeDeviceSettingsReturn(context: Context): Boolean {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!preferences.getBoolean(KEY_RETURN_TO_DEVICE_SETTINGS, false)) return false
        preferences.edit().remove(KEY_RETURN_TO_DEVICE_SETTINGS).commit()
        return true
    }
}
