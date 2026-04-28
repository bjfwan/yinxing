package com.yinxing.launcher.data.home

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.yinxing.launcher.data.settings.LauncherSettingsDataStore
import com.yinxing.launcher.data.settings.LauncherSettingsMigration

class LauncherPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val legacyPrefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val homeAppConfig = HomeAppConfig(appContext)
    private val settingsStore = LauncherSettingsDataStore.getInstance(appContext)
    private val listeners = linkedSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    init {
        migrateLegacyPreferences()
    }

    companion object {
        private const val PREFS_NAME = "launcher_prefs"
        private const val KEY_APP_ORDER = "app_order"
        private const val KEY_LOW_PERFORMANCE_MODE = "low_performance_mode"
        private const val KEY_AUTO_ANSWER_ENABLED = "auto_answer_enabled"
        private const val KEY_AUTO_ANSWER_DELAY_SECONDS = "auto_answer_delay_seconds"
        const val DEFAULT_AUTO_ANSWER_DELAY_SECONDS = 5
        private const val KEY_FULL_CARD_TAP_ENABLED = "full_card_tap_enabled"
        private const val KEY_DARK_MODE = "dark_mode"
        const val DARK_MODE_SYSTEM = "system"
        const val DARK_MODE_LIGHT = "light"
        const val DARK_MODE_DARK = "dark"
        private const val KEY_KIOSK_MODE_ENABLED = "kiosk_mode_enabled"
        private const val KEY_AUTOSTART_CONFIRMED = "autostart_confirmed"
        private const val KEY_BACKGROUND_START_CONFIRMED = "background_start_confirmed"
        private const val KEY_ICON_SCALE = "icon_scale"
        const val DEFAULT_ICON_SCALE = 100
        const val MIN_ICON_SCALE = 60
        const val MAX_ICON_SCALE = 120
        private val RESERVED_KEYS = setOf(
            KEY_APP_ORDER,
            KEY_LOW_PERFORMANCE_MODE,
            KEY_AUTO_ANSWER_ENABLED,
            KEY_AUTO_ANSWER_DELAY_SECONDS,
            KEY_FULL_CARD_TAP_ENABLED,
            KEY_DARK_MODE,
            KEY_KIOSK_MODE_ENABLED,
            KEY_AUTOSTART_CONFIRMED,
            KEY_BACKGROUND_START_CONFIRMED,
            KEY_ICON_SCALE
        )

        @Volatile
        private var instance: LauncherPreferences? = null

        fun getInstance(context: Context): LauncherPreferences {
            return instance ?: synchronized(this) {
                instance ?: LauncherPreferences(context).also { instance = it }
            }
        }
    }

    fun getSelectedPackages(): Set<String> {
        return homeAppConfig.getSelectedPackages()
    }

    fun isPackageSelected(packageName: String): Boolean {
        return homeAppConfig.isPackageSelected(packageName)
    }

    fun setPackageSelected(packageName: String, isSelected: Boolean) {
        val orderBefore = getAppOrder()
        if (!homeAppConfig.setPackageSelected(packageName, isSelected)) return
        notifyPreferenceChanged(packageName)
        if (orderBefore != getAppOrder()) notifyPreferenceChanged(KEY_APP_ORDER)
    }

    fun getAppOrder(): List<String> {
        return homeAppConfig.getAppOrder()
    }

    fun saveAppOrder(packageNames: List<String>) {
        if (homeAppConfig.saveAppOrder(packageNames)) notifyPreferenceChanged(KEY_APP_ORDER)
    }

    fun syncAppOrder(selectedPackages: Collection<String>) {
        if (homeAppConfig.syncAppOrder(selectedPackages)) notifyPreferenceChanged(KEY_APP_ORDER)
    }

    fun isLowPerformanceModeEnabled(): Boolean {
        return settingsStore.snapshot().lowPerformanceModeEnabled
    }

    fun setLowPerformanceModeEnabled(enabled: Boolean) {
        if (settingsStore.snapshot().lowPerformanceModeEnabled == enabled) return
        settingsStore.setLowPerformanceModeEnabled(enabled)
        notifyPreferenceChanged(KEY_LOW_PERFORMANCE_MODE)
    }

    fun isAutoAnswerEnabled(): Boolean {
        return settingsStore.snapshot().autoAnswerEnabled
    }

    fun setAutoAnswerEnabled(enabled: Boolean) {
        if (settingsStore.snapshot().autoAnswerEnabled == enabled) return
        settingsStore.setAutoAnswerEnabled(enabled)
        notifyPreferenceChanged(KEY_AUTO_ANSWER_ENABLED)
    }

    fun getAutoAnswerDelaySeconds(): Int {
        return settingsStore.snapshot().autoAnswerDelaySeconds
    }

    fun setAutoAnswerDelaySeconds(seconds: Int) {
        val normalized = seconds.coerceIn(1, 30)
        if (settingsStore.snapshot().autoAnswerDelaySeconds == normalized) return
        settingsStore.setAutoAnswerDelaySeconds(normalized)
        notifyPreferenceChanged(KEY_AUTO_ANSWER_DELAY_SECONDS)
    }

    fun isFullCardTapEnabled(): Boolean {
        return settingsStore.snapshot().fullCardTapEnabled
    }

    fun setFullCardTapEnabled(enabled: Boolean) {
        if (settingsStore.snapshot().fullCardTapEnabled == enabled) return
        settingsStore.setFullCardTapEnabled(enabled)
        notifyPreferenceChanged(KEY_FULL_CARD_TAP_ENABLED)
    }

    fun isFullCardTapKey(key: String?): Boolean = key == KEY_FULL_CARD_TAP_ENABLED

    fun getDarkMode(): String {
        return settingsStore.snapshot().darkMode
    }

    fun setDarkMode(value: String) {
        val normalized = when (value) {
            DARK_MODE_LIGHT, DARK_MODE_DARK -> value
            else -> DARK_MODE_SYSTEM
        }
        if (getDarkMode() == normalized) return
        settingsStore.setDarkMode(normalized)
        notifyPreferenceChanged(KEY_DARK_MODE)
    }

    fun isDarkModeKey(key: String?): Boolean = key == KEY_DARK_MODE

    fun isKioskModeEnabled(): Boolean {
        return settingsStore.snapshot().kioskModeEnabled
    }

    fun setKioskModeEnabled(enabled: Boolean) {
        if (settingsStore.snapshot().kioskModeEnabled == enabled) return
        settingsStore.setKioskModeEnabled(enabled)
        notifyPreferenceChanged(KEY_KIOSK_MODE_ENABLED)
    }

    fun isAutoStartConfirmed(): Boolean {
        return settingsStore.snapshot().autoStartConfirmed
    }

    fun setAutoStartConfirmed(confirmed: Boolean) {
        if (settingsStore.snapshot().autoStartConfirmed == confirmed) return
        settingsStore.setAutoStartConfirmed(confirmed)
        notifyPreferenceChanged(KEY_AUTOSTART_CONFIRMED)
    }

    fun isBackgroundStartConfirmed(): Boolean {
        return settingsStore.snapshot().backgroundStartConfirmed
    }

    fun setBackgroundStartConfirmed(confirmed: Boolean) {
        if (settingsStore.snapshot().backgroundStartConfirmed == confirmed) return
        settingsStore.setBackgroundStartConfirmed(confirmed)
        notifyPreferenceChanged(KEY_BACKGROUND_START_CONFIRMED)
    }

    fun getIconScale(): Int {
        return settingsStore.snapshot().iconScale
    }

    fun setIconScale(scale: Int) {
        val normalized = scale.coerceIn(MIN_ICON_SCALE, MAX_ICON_SCALE)
        if (settingsStore.snapshot().iconScale == normalized) return
        settingsStore.setIconScale(normalized)
        notifyPreferenceChanged(KEY_ICON_SCALE)
    }

    fun isIconScaleKey(key: String?) = key == KEY_ICON_SCALE

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(listeners) {
            listeners += listener
        }
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(listeners) {
            listeners -= listener
        }
    }

    fun isLowPerformanceModeKey(key: String?): Boolean {
        return key == KEY_LOW_PERFORMANCE_MODE
    }

    fun isSelectionKey(key: String?): Boolean {
        return !key.isNullOrBlank() && key !in RESERVED_KEYS
    }

    fun isHomeAppConfigKey(key: String?): Boolean {
        return key == KEY_APP_ORDER || isSelectionKey(key)
    }

    private fun migrateLegacyPreferences() {
        val legacy = legacyPrefs.all
        if (legacy.isEmpty()) {
            return
        }
        val selectedPackages = legacy
            .filter { (key, value) -> isSelectionKey(key) && value == true }
            .keys
        val selectionKeys = legacy
            .filter { (key, value) -> isSelectionKey(key) && value is Boolean }
            .keys
        val appOrder = (legacy[KEY_APP_ORDER] as? String)
            ?.split(",")
            ?: emptyList()

        homeAppConfig.migrateFrom(selectedPackages, appOrder)
        settingsStore.migrateFrom(
            LauncherSettingsMigration(
                lowPerformanceModeEnabled = legacy[KEY_LOW_PERFORMANCE_MODE] as? Boolean,
                autoAnswerEnabled = legacy[KEY_AUTO_ANSWER_ENABLED] as? Boolean,
                autoAnswerDelaySeconds = legacy[KEY_AUTO_ANSWER_DELAY_SECONDS] as? Int,
                fullCardTapEnabled = legacy[KEY_FULL_CARD_TAP_ENABLED] as? Boolean,
                darkMode = legacy[KEY_DARK_MODE] as? String,
                kioskModeEnabled = legacy[KEY_KIOSK_MODE_ENABLED] as? Boolean,
                autoStartConfirmed = legacy[KEY_AUTOSTART_CONFIRMED] as? Boolean,
                backgroundStartConfirmed = legacy[KEY_BACKGROUND_START_CONFIRMED] as? Boolean,
                iconScale = legacy[KEY_ICON_SCALE] as? Int
            )
        )

        val keysToRemove = RESERVED_KEYS + selectionKeys
        legacyPrefs.edit {
            keysToRemove.forEach(::remove)
        }
    }

    private fun notifyPreferenceChanged(key: String) {
        val snapshot = synchronized(listeners) {
            listeners.toList()
        }
        snapshot.forEach { it.onSharedPreferenceChanged(legacyPrefs, key) }
    }
}
