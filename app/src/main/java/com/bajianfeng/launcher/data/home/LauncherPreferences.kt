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
            key != KEY_LOW_PERFORMANCE_MODE
    }

    fun isHomeAppConfigKey(key: String?): Boolean {
        return key == KEY_APP_ORDER || isSelectionKey(key)
    }
}
