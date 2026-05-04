package com.yinxing.launcher.data.home

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class HomeAppConfig(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "home_app_config"
        private const val KEY_SELECTED_PACKAGES = "selected_packages"
        private const val KEY_APP_ORDER = "app_order"
    }

    fun getSelectedPackages(): Set<String> {
        return prefs.getStringSet(KEY_SELECTED_PACKAGES, emptySet()).orEmpty()
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun isPackageSelected(packageName: String): Boolean {
        return packageName in getSelectedPackages()
    }

    fun setPackageSelected(packageName: String, isSelected: Boolean): Boolean {
        val current = getSelectedPackages()
        if ((packageName in current) == isSelected) {
            return false
        }
        val updated = current.toMutableSet()
        if (isSelected) {
            updated += packageName
        } else {
            updated -= packageName
        }
        prefs.edit {
            putStringSet(KEY_SELECTED_PACKAGES, updated)
        }
        saveAppOrder(
            HomeAppOrderPolicy.updateOrderForSelection(
                getAppOrder(),
                packageName,
                isSelected
            )
        )
        return true
    }

    fun getAppOrder(): List<String> {
        return HomeAppOrderPolicy.normalizeSavedOrder(
            prefs.getString(KEY_APP_ORDER, null)
                ?.split(",")
                ?: emptyList()
        )
    }

    fun saveAppOrder(packageNames: List<String>): Boolean {
        val normalizedOrder = HomeAppOrderPolicy.normalizeSavedOrder(packageNames)
        if (getAppOrder() == normalizedOrder) {
            return false
        }
        val normalized = normalizedOrder.joinToString(",")
        prefs.edit {
            if (normalized.isEmpty()) {
                remove(KEY_APP_ORDER)
            } else {
                putString(KEY_APP_ORDER, normalized)
            }
        }
        return true
    }

    fun syncAppOrder(selectedPackages: Collection<String>): Boolean {
        return saveAppOrder(
            HomeAppOrderPolicy.retainSelectedPackages(
                getAppOrder(),
                selectedPackages
            )
        )
    }

    fun migrateFrom(selectedPackages: Set<String>, appOrder: List<String>) {
        if (selectedPackages.isEmpty() && appOrder.isEmpty()) {
            return
        }
        val normalizedSelected = selectedPackages.filter { it.isNotBlank() }.toSet()
        val normalizedOrder = HomeAppOrderPolicy.retainSelectedPackages(
            HomeAppOrderPolicy.normalizeSavedOrder(appOrder),
            normalizedSelected
        ).joinToString(",")
        prefs.edit {
            if (!prefs.contains(KEY_SELECTED_PACKAGES)) {
                putStringSet(KEY_SELECTED_PACKAGES, normalizedSelected)
            }
            if (!prefs.contains(KEY_APP_ORDER)) {
                putString(KEY_APP_ORDER, normalizedOrder)
            }
        }
    }

    fun clear() {
        prefs.edit { clear() }
    }
}
