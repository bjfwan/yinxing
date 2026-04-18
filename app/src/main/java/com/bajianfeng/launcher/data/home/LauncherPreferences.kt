package com.bajianfeng.launcher.data.home

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class LauncherPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "launcher_prefs"
        private const val KEY_APP_ORDER = "app_order"
        private const val KEY_LOW_PERFORMANCE_MODE = "low_performance_mode"
        private const val KEY_AUTO_ANSWER_ENABLED = "auto_answer_enabled"
        private const val KEY_AUTO_ANSWER_DELAY_SECONDS = "auto_answer_delay_seconds"
        const val DEFAULT_AUTO_ANSWER_DELAY_SECONDS = 5
        private const val KEY_KIOSK_MODE_ENABLED = "kiosk_mode_enabled"

        @Volatile
        private var instance: LauncherPreferences? = null

        fun getInstance(context: Context): LauncherPreferences {
            return instance ?: synchronized(this) {
                instance ?: LauncherPreferences(context).also { instance = it }
            }
        }
    }

    fun getSelectedPackages(): Set<String> {
        return prefs.all
            .filter { (key, value) -> isSelectionKey(key) && value == true }
            .keys
    }

    fun isPackageSelected(packageName: String): Boolean {
        return prefs.getBoolean(packageName, false)
    }

    fun setPackageSelected(packageName: String, isSelected: Boolean) {
        if (prefs.getBoolean(packageName, false) == isSelected) {
            return
        }
        prefs.edit {
            putBoolean(packageName, isSelected)
        }
        saveAppOrder(
            HomeAppOrderPolicy.updateOrderForSelection(
                getAppOrder(),
                packageName,
                isSelected
            )
        )
    }

    fun getAppOrder(): List<String> {
        return HomeAppOrderPolicy.normalizeSavedOrder(
            prefs.getString(KEY_APP_ORDER, null)
                ?.split(",")
                ?: emptyList()
        )
    }

    fun saveAppOrder(packageNames: List<String>) {
        val normalized = HomeAppOrderPolicy.normalizeSavedOrder(packageNames).joinToString(",")
        if (prefs.getString(KEY_APP_ORDER, null) == normalized) {
            return
        }
        prefs.edit {
            putString(KEY_APP_ORDER, normalized)
        }
    }

    fun syncAppOrder(selectedPackages: Collection<String>) {
        saveAppOrder(
            HomeAppOrderPolicy.retainSelectedPackages(
                getAppOrder(),
                selectedPackages
            )
        )
    }

    fun isLowPerformanceModeEnabled(): Boolean {
        return prefs.getBoolean(KEY_LOW_PERFORMANCE_MODE, false)
    }

    fun setLowPerformanceModeEnabled(enabled: Boolean) {
        if (prefs.getBoolean(KEY_LOW_PERFORMANCE_MODE, false) == enabled) {
            return
        }
        prefs.edit {
            putBoolean(KEY_LOW_PERFORMANCE_MODE, enabled)
        }
    }

    fun isAutoAnswerEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_ANSWER_ENABLED, true)
    }

    fun setAutoAnswerEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_ANSWER_ENABLED, enabled) }
    }

    fun getAutoAnswerDelaySeconds(): Int {
        return prefs.getInt(KEY_AUTO_ANSWER_DELAY_SECONDS, DEFAULT_AUTO_ANSWER_DELAY_SECONDS)
    }

    fun setAutoAnswerDelaySeconds(seconds: Int) {
        prefs.edit { putInt(KEY_AUTO_ANSWER_DELAY_SECONDS, seconds.coerceIn(1, 30)) }
    }

    fun isKioskModeEnabled(): Boolean {
        return prefs.getBoolean(KEY_KIOSK_MODE_ENABLED, false)
    }

    fun setKioskModeEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_KIOSK_MODE_ENABLED, enabled) }
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun isLowPerformanceModeKey(key: String?): Boolean {
        return key == KEY_LOW_PERFORMANCE_MODE
    }

    fun isSelectionKey(key: String?): Boolean {
        return !key.isNullOrBlank() &&
            key != KEY_APP_ORDER &&
            key != KEY_LOW_PERFORMANCE_MODE &&
            key != KEY_AUTO_ANSWER_ENABLED &&
            key != KEY_AUTO_ANSWER_DELAY_SECONDS &&
            key != KEY_KIOSK_MODE_ENABLED
    }

    fun isHomeAppConfigKey(key: String?): Boolean {
        return key == KEY_APP_ORDER || isSelectionKey(key)
    }
}
