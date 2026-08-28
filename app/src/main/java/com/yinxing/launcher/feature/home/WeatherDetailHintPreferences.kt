package com.yinxing.launcher.feature.home

import android.content.Context
import androidx.core.content.edit

class WeatherDetailHintPreferences(context: Context) {
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
        const val PREFS_NAME = "home_guidance"
        private const val KEY_SHOWN = "weather_detail_hint_shown_v1"
    }
}

internal fun shouldRevealWeatherDetailHint(
    weatherAvailable: Boolean,
    hostResumed: Boolean,
    familySetupPending: Boolean,
    alreadyShown: Boolean,
): Boolean = weatherAvailable && hostResumed && !familySetupPending && !alreadyShown
