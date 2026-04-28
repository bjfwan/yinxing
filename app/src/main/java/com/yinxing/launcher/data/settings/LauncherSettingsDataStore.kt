package com.yinxing.launcher.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

data class LauncherSettings(
    val lowPerformanceModeEnabled: Boolean = false,
    val autoAnswerEnabled: Boolean = true,
    val autoAnswerDelaySeconds: Int = LauncherSettingsDataStore.DEFAULT_AUTO_ANSWER_DELAY_SECONDS,
    val fullCardTapEnabled: Boolean = false,
    val darkMode: String = LauncherSettingsDataStore.DARK_MODE_SYSTEM,
    val kioskModeEnabled: Boolean = false,
    val autoStartConfirmed: Boolean = false,
    val backgroundStartConfirmed: Boolean = false,
    val iconScale: Int = LauncherSettingsDataStore.DEFAULT_ICON_SCALE
)

data class LauncherSettingsMigration(
    val lowPerformanceModeEnabled: Boolean? = null,
    val autoAnswerEnabled: Boolean? = null,
    val autoAnswerDelaySeconds: Int? = null,
    val fullCardTapEnabled: Boolean? = null,
    val darkMode: String? = null,
    val kioskModeEnabled: Boolean? = null,
    val autoStartConfirmed: Boolean? = null,
    val backgroundStartConfirmed: Boolean? = null,
    val iconScale: Int? = null
) {
    val hasValues: Boolean
        get() = listOf(
            lowPerformanceModeEnabled,
            autoAnswerEnabled,
            autoAnswerDelaySeconds,
            fullCardTapEnabled,
            darkMode,
            kioskModeEnabled,
            autoStartConfirmed,
            backgroundStartConfirmed,
            iconScale
        ).any { it != null }
}

private val Context.launcherSettingsDataStore by preferencesDataStore(name = "launcher_settings")

class LauncherSettingsDataStore private constructor(context: Context) {
    private val dataStore = context.applicationContext.launcherSettingsDataStore

    @Volatile
    private var cachedSettings = readSettings()

    companion object {
        const val DEFAULT_AUTO_ANSWER_DELAY_SECONDS = 5
        const val DARK_MODE_SYSTEM = "system"
        const val DARK_MODE_LIGHT = "light"
        const val DARK_MODE_DARK = "dark"
        const val DEFAULT_ICON_SCALE = 100
        const val MIN_ICON_SCALE = 60
        const val MAX_ICON_SCALE = 120

        private val KEY_LOW_PERFORMANCE_MODE = booleanPreferencesKey("low_performance_mode")
        private val KEY_AUTO_ANSWER_ENABLED = booleanPreferencesKey("auto_answer_enabled")
        private val KEY_AUTO_ANSWER_DELAY_SECONDS = intPreferencesKey("auto_answer_delay_seconds")
        private val KEY_FULL_CARD_TAP_ENABLED = booleanPreferencesKey("full_card_tap_enabled")
        private val KEY_DARK_MODE = stringPreferencesKey("dark_mode")
        private val KEY_KIOSK_MODE_ENABLED = booleanPreferencesKey("kiosk_mode_enabled")
        private val KEY_AUTOSTART_CONFIRMED = booleanPreferencesKey("autostart_confirmed")
        private val KEY_BACKGROUND_START_CONFIRMED = booleanPreferencesKey("background_start_confirmed")
        private val KEY_ICON_SCALE = intPreferencesKey("icon_scale")

        @Volatile
        private var instance: LauncherSettingsDataStore? = null

        fun getInstance(context: Context): LauncherSettingsDataStore {
            return instance ?: synchronized(this) {
                instance ?: LauncherSettingsDataStore(context.applicationContext).also { instance = it }
            }
        }
    }

    fun snapshot(): LauncherSettings = cachedSettings

    fun setLowPerformanceModeEnabled(enabled: Boolean) {
        update { it[KEY_LOW_PERFORMANCE_MODE] = enabled }
    }

    fun setAutoAnswerEnabled(enabled: Boolean) {
        update { it[KEY_AUTO_ANSWER_ENABLED] = enabled }
    }

    fun setAutoAnswerDelaySeconds(seconds: Int) {
        update { it[KEY_AUTO_ANSWER_DELAY_SECONDS] = seconds.coerceIn(1, 30) }
    }

    fun setFullCardTapEnabled(enabled: Boolean) {
        update { it[KEY_FULL_CARD_TAP_ENABLED] = enabled }
    }

    fun setDarkMode(value: String) {
        update { it[KEY_DARK_MODE] = normalizeDarkMode(value) }
    }

    fun setKioskModeEnabled(enabled: Boolean) {
        update { it[KEY_KIOSK_MODE_ENABLED] = enabled }
    }

    fun setAutoStartConfirmed(confirmed: Boolean) {
        update { it[KEY_AUTOSTART_CONFIRMED] = confirmed }
    }

    fun setBackgroundStartConfirmed(confirmed: Boolean) {
        update { it[KEY_BACKGROUND_START_CONFIRMED] = confirmed }
    }

    fun setIconScale(scale: Int) {
        update { it[KEY_ICON_SCALE] = scale.coerceIn(MIN_ICON_SCALE, MAX_ICON_SCALE) }
    }

    fun migrateFrom(migration: LauncherSettingsMigration) {
        if (!migration.hasValues) {
            return
        }
        update { preferences ->
            migration.lowPerformanceModeEnabled?.let { preferences[KEY_LOW_PERFORMANCE_MODE] = it }
            migration.autoAnswerEnabled?.let { preferences[KEY_AUTO_ANSWER_ENABLED] = it }
            migration.autoAnswerDelaySeconds?.let {
                preferences[KEY_AUTO_ANSWER_DELAY_SECONDS] = it.coerceIn(1, 30)
            }
            migration.fullCardTapEnabled?.let { preferences[KEY_FULL_CARD_TAP_ENABLED] = it }
            migration.darkMode?.let { preferences[KEY_DARK_MODE] = normalizeDarkMode(it) }
            migration.kioskModeEnabled?.let { preferences[KEY_KIOSK_MODE_ENABLED] = it }
            migration.autoStartConfirmed?.let { preferences[KEY_AUTOSTART_CONFIRMED] = it }
            migration.backgroundStartConfirmed?.let { preferences[KEY_BACKGROUND_START_CONFIRMED] = it }
            migration.iconScale?.let { preferences[KEY_ICON_SCALE] = it.coerceIn(MIN_ICON_SCALE, MAX_ICON_SCALE) }
        }
    }

    fun clear() {
        update { it.clear() }
    }

    private fun update(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        cachedSettings = runBlocking(Dispatchers.IO) {
            dataStore.edit { preferences -> block(preferences) }.toLauncherSettings()
        }
    }

    private fun readSettings(): LauncherSettings {
        return runBlocking(Dispatchers.IO) {
            dataStore.data
                .catch { emit(emptyPreferences()) }
                .first()
                .toLauncherSettings()
        }
    }

    private fun androidx.datastore.preferences.core.Preferences.toLauncherSettings(): LauncherSettings {
        return LauncherSettings(
            lowPerformanceModeEnabled = this[KEY_LOW_PERFORMANCE_MODE] ?: false,
            autoAnswerEnabled = this[KEY_AUTO_ANSWER_ENABLED] ?: true,
            autoAnswerDelaySeconds = (this[KEY_AUTO_ANSWER_DELAY_SECONDS] ?: DEFAULT_AUTO_ANSWER_DELAY_SECONDS)
                .coerceIn(1, 30),
            fullCardTapEnabled = this[KEY_FULL_CARD_TAP_ENABLED] ?: false,
            darkMode = normalizeDarkMode(this[KEY_DARK_MODE]),
            kioskModeEnabled = this[KEY_KIOSK_MODE_ENABLED] ?: false,
            autoStartConfirmed = this[KEY_AUTOSTART_CONFIRMED] ?: false,
            backgroundStartConfirmed = this[KEY_BACKGROUND_START_CONFIRMED] ?: false,
            iconScale = (this[KEY_ICON_SCALE] ?: DEFAULT_ICON_SCALE).coerceIn(MIN_ICON_SCALE, MAX_ICON_SCALE)
        )
    }

    private fun normalizeDarkMode(value: String?): String {
        return when (value) {
            DARK_MODE_LIGHT, DARK_MODE_DARK -> value
            else -> DARK_MODE_SYSTEM
        }
    }
}
