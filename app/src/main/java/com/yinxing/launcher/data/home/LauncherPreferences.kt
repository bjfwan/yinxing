package com.yinxing.launcher.data.home

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.yinxing.launcher.data.settings.LauncherSettingsDataStore
import com.yinxing.launcher.data.settings.LauncherSettingsMigration
import com.yinxing.launcher.common.util.EmergencyContactNumber

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
        private const val KEY_HOME_LAYOUT_LOCKED = "home_layout_locked"
        private const val KEY_HOME_LONG_PRESS_RESPONSE = "home_long_press_response"
        private const val KEY_AUTO_ANSWER_ENABLED = "auto_answer_enabled"
        private const val KEY_AUTO_ANSWER_DELAY_SECONDS = "auto_answer_delay_seconds"
        private const val KEY_RETURN_HOME_AFTER_CALL_ENABLED = "return_home_after_call_enabled"
        const val DEFAULT_AUTO_ANSWER_DELAY_SECONDS = 5
        private const val KEY_FULL_CARD_TAP_ENABLED = "full_card_tap_enabled"
        private const val KEY_DARK_MODE = "dark_mode"
        const val DARK_MODE_SYSTEM = "system"
        const val DARK_MODE_LIGHT = "light"
        const val DARK_MODE_DARK = "dark"
        private const val KEY_FONT_SCALE_MODE = "font_scale_mode"
        const val FONT_SCALE_SYSTEM = "system"
        const val FONT_SCALE_STANDARD = "standard"
        const val FONT_SCALE_LARGE = "large"
        const val FONT_SCALE_EXTRA_LARGE = "extra_large"
        private const val REMOVED_KEY_KIOSK_MODE_ENABLED = "kiosk_mode_enabled"
        private const val KEY_AUTOSTART_CONFIRMED = "autostart_confirmed"
        private const val KEY_BACKGROUND_START_CONFIRMED = "background_start_confirmed"
        private const val KEY_ICON_SCALE = "icon_scale"
        private const val KEY_FALL_DETECTION_ENABLED = "fall_detection_enabled"
        private const val KEY_FALL_EMERGENCY_CONTACT = "fall_emergency_contact"
        const val DEFAULT_ICON_SCALE = 100
        const val MIN_ICON_SCALE = 60
        const val MAX_ICON_SCALE = 120
        const val HOME_LONG_PRESS_STANDARD = "standard"
        const val HOME_LONG_PRESS_LONG = "long"
        private val RESERVED_KEYS = setOf(
            KEY_APP_ORDER,
            KEY_LOW_PERFORMANCE_MODE,
            KEY_HOME_LAYOUT_LOCKED,
            KEY_HOME_LONG_PRESS_RESPONSE,
            KEY_AUTO_ANSWER_ENABLED,
            KEY_AUTO_ANSWER_DELAY_SECONDS,
            KEY_RETURN_HOME_AFTER_CALL_ENABLED,
            KEY_FULL_CARD_TAP_ENABLED,
            KEY_DARK_MODE,
            KEY_FONT_SCALE_MODE,
            REMOVED_KEY_KIOSK_MODE_ENABLED,
            KEY_AUTOSTART_CONFIRMED,
            KEY_BACKGROUND_START_CONFIRMED,
            KEY_ICON_SCALE,
            KEY_FALL_DETECTION_ENABLED,
            KEY_FALL_EMERGENCY_CONTACT
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

    fun resetHomeLayout(): Boolean {
        if (!homeAppConfig.resetToDefault()) return false
        notifyPreferenceChanged(KEY_APP_ORDER)
        return true
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

    fun getFontScaleMode(): String = settingsStore.snapshot().fontScaleMode

    fun setFontScaleMode(value: String) {
        val normalized = when (value) {
            FONT_SCALE_STANDARD,
            FONT_SCALE_LARGE,
            FONT_SCALE_EXTRA_LARGE -> value
            else -> FONT_SCALE_SYSTEM
        }
        if (getFontScaleMode() == normalized) return
        settingsStore.setFontScaleMode(normalized)
        notifyPreferenceChanged(KEY_FONT_SCALE_MODE)
    }

    fun isReturnHomeAfterCallEnabled(): Boolean =
        settingsStore.snapshot().returnHomeAfterCallEnabled

    fun setReturnHomeAfterCallEnabled(enabled: Boolean) {
        if (settingsStore.snapshot().returnHomeAfterCallEnabled == enabled) return
        settingsStore.setReturnHomeAfterCallEnabled(enabled)
        notifyPreferenceChanged(KEY_RETURN_HOME_AFTER_CALL_ENABLED)
    }

    fun isFontScaleModeKey(key: String?): Boolean = key == KEY_FONT_SCALE_MODE

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

    fun isFallDetectionEnabled(): Boolean = settingsStore.snapshot().fallDetectionEnabled

    fun setFallDetectionEnabled(enabled: Boolean) {
        val safeEnabled = enabled && getFallEmergencyContact().isNotEmpty()
        if (isFallDetectionEnabled() == safeEnabled) return
        settingsStore.setFallDetectionEnabled(safeEnabled)
        notifyPreferenceChanged(KEY_FALL_DETECTION_ENABLED)
    }

    fun isHomeLayoutLocked(): Boolean {
        return settingsStore.snapshot().homeLayoutLocked
    }

    fun setHomeLayoutLocked(locked: Boolean) {
        if (settingsStore.snapshot().homeLayoutLocked == locked) return
        settingsStore.setHomeLayoutLocked(locked)
        notifyPreferenceChanged(KEY_HOME_LAYOUT_LOCKED)
    }

    fun getHomeLongPressResponse(): String {
        return settingsStore.snapshot().homeLongPressResponse
    }

    fun setHomeLongPressResponse(response: String) {
        val normalized = if (response == HOME_LONG_PRESS_LONG) {
            HOME_LONG_PRESS_LONG
        } else {
            HOME_LONG_PRESS_STANDARD
        }
        if (getHomeLongPressResponse() == normalized) return
        settingsStore.setHomeLongPressResponse(normalized)
        notifyPreferenceChanged(KEY_HOME_LONG_PRESS_RESPONSE)
    }

    fun getFallEmergencyContact(): String = settingsStore.snapshot().fallEmergencyContact

    fun setFallEmergencyContact(rawNumber: String) {
        val normalized = EmergencyContactNumber.normalize(rawNumber).orEmpty()
        if (getFallEmergencyContact() != normalized) {
            settingsStore.setFallEmergencyContact(normalized)
            notifyPreferenceChanged(KEY_FALL_EMERGENCY_CONTACT)
        }
        if (normalized.isEmpty() && isFallDetectionEnabled()) {
            settingsStore.setFallDetectionEnabled(false)
            notifyPreferenceChanged(KEY_FALL_DETECTION_ENABLED)
        }
    }

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

    fun isHomeLayoutLockedKey(key: String?): Boolean {
        return key == KEY_HOME_LAYOUT_LOCKED
    }

    fun isHomeLongPressResponseKey(key: String?): Boolean {
        return key == KEY_HOME_LONG_PRESS_RESPONSE
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
                homeLayoutLocked = legacy[KEY_HOME_LAYOUT_LOCKED] as? Boolean,
                homeLongPressResponse = legacy[KEY_HOME_LONG_PRESS_RESPONSE] as? String,
                autoAnswerEnabled = legacy[KEY_AUTO_ANSWER_ENABLED] as? Boolean,
                autoAnswerDelaySeconds = legacy[KEY_AUTO_ANSWER_DELAY_SECONDS] as? Int,
                fullCardTapEnabled = legacy[KEY_FULL_CARD_TAP_ENABLED] as? Boolean,
                darkMode = legacy[KEY_DARK_MODE] as? String,
                fontScaleMode = legacy[KEY_FONT_SCALE_MODE] as? String,
                autoStartConfirmed = legacy[KEY_AUTOSTART_CONFIRMED] as? Boolean,
                backgroundStartConfirmed = legacy[KEY_BACKGROUND_START_CONFIRMED] as? Boolean,
                iconScale = legacy[KEY_ICON_SCALE] as? Int,
                fallDetectionEnabled = legacy[KEY_FALL_DETECTION_ENABLED] as? Boolean,
                fallEmergencyContact = legacy[KEY_FALL_EMERGENCY_CONTACT] as? String
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
