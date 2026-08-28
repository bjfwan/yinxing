package com.yinxing.launcher.feature.weather

import android.content.Context
import androidx.core.content.edit

class WeatherCityHintPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun hasBeenShown(): Boolean = preferences.getBoolean(KEY_SHOWN, false)

    fun markShownIfFirstTime(): Boolean {
        if (hasBeenShown()) return false
        preferences.edit { putBoolean(KEY_SHOWN, true) }
        return true
    }

    companion object {
        const val PREFS_NAME = "weather_guidance"
        private const val KEY_SHOWN = "city_switch_hint_shown_v1"
    }
}

internal fun shouldRevealWeatherCityHint(
    hostResumed: Boolean,
    alreadyShown: Boolean,
): Boolean = hostResumed && !alreadyShown
