package com.bajianfeng.launcher.data.home

import android.content.Context
import android.content.SharedPreferences

class LauncherPreferences private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "launcher_prefs"
        private const val KEY_APP_ORDER = "app_order"

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
            .filter { (key, value) -> key != KEY_APP_ORDER && value == true }
            .keys
    }

    fun isPackageSelected(packageName: String): Boolean {
        return prefs.getBoolean(packageName, false)
    }

    fun setPackageSelected(packageName: String, isSelected: Boolean) {
        prefs.edit().putBoolean(packageName, isSelected).apply()
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
        prefs.edit()
            .putString(
                KEY_APP_ORDER,
                HomeAppOrderPolicy.normalizeSavedOrder(packageNames).joinToString(",")
            )
            .apply()
    }

    fun syncAppOrder(selectedPackages: Collection<String>) {
        saveAppOrder(
            HomeAppOrderPolicy.retainSelectedPackages(
                getAppOrder(),
                selectedPackages
            )
        )
    }
}
