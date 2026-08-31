package com.yinxing.launcher.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

data class LauncherSettings(
    val lowPerformanceModeEnabled: Boolean = false,
    val homeLayoutLocked: Boolean = false,
    val homeLongPressResponse: String = LauncherSettingsDataStore.DEFAULT_HOME_LONG_PRESS_RESPONSE,
    val autoAnswerEnabled: Boolean = true,
    val autoAnswerDelaySeconds: Int = LauncherSettingsDataStore.DEFAULT_AUTO_ANSWER_DELAY_SECONDS,
    val returnHomeAfterCallEnabled: Boolean = true,
    val fullCardTapEnabled: Boolean = false,
    val darkMode: String = LauncherSettingsDataStore.DARK_MODE_SYSTEM,
    val fontScaleMode: String = LauncherSettingsDataStore.FONT_SCALE_SYSTEM,
    val autoStartConfirmed: Boolean = false,
    val backgroundStartConfirmed: Boolean = false,
    val iconScale: Int = LauncherSettingsDataStore.DEFAULT_ICON_SCALE,
    val fallDetectionEnabled: Boolean = false,
    val fallEmergencyContact: String = ""
)

data class LauncherSettingsMigration(
    val lowPerformanceModeEnabled: Boolean? = null,
    val homeLayoutLocked: Boolean? = null,
    val homeLongPressResponse: String? = null,
    val autoAnswerEnabled: Boolean? = null,
    val autoAnswerDelaySeconds: Int? = null,
    val fullCardTapEnabled: Boolean? = null,
    val darkMode: String? = null,
    val fontScaleMode: String? = null,
    val autoStartConfirmed: Boolean? = null,
    val backgroundStartConfirmed: Boolean? = null,
    val iconScale: Int? = null,
    val fallDetectionEnabled: Boolean? = null,
    val fallEmergencyContact: String? = null
) {
    val hasValues: Boolean
        get() = listOf(
            lowPerformanceModeEnabled,
            homeLayoutLocked,
            homeLongPressResponse,
            autoAnswerEnabled,
            autoAnswerDelaySeconds,
            fullCardTapEnabled,
            darkMode,
            fontScaleMode,
            autoStartConfirmed,
            backgroundStartConfirmed,
            iconScale,
            fallDetectionEnabled,
            fallEmergencyContact
        ).any { it != null }
}

private val Context.launcherSettingsDataStore by preferencesDataStore(name = "launcher_settings")

class LauncherSettingsDataStore private constructor(context: Context) {
    private val dataStore = context.applicationContext.launcherSettingsDataStore
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()

    companion object {
        const val DEFAULT_AUTO_ANSWER_DELAY_SECONDS = 5
        const val DARK_MODE_SYSTEM = "system"
        const val DARK_MODE_LIGHT = "light"
        const val DARK_MODE_DARK = "dark"
        const val FONT_SCALE_SYSTEM = "system"
        const val FONT_SCALE_STANDARD = "standard"
        const val FONT_SCALE_LARGE = "large"
        const val FONT_SCALE_EXTRA_LARGE = "extra_large"
        const val DEFAULT_ICON_SCALE = 100
        const val MIN_ICON_SCALE = 60
        const val MAX_ICON_SCALE = 120
        const val DEFAULT_HOME_LONG_PRESS_RESPONSE = "standard"
        const val HOME_LONG_PRESS_RESPONSE_LONG = "long"

        private val KEY_LOW_PERFORMANCE_MODE = booleanPreferencesKey("low_performance_mode")
        private val KEY_HOME_LAYOUT_LOCKED = booleanPreferencesKey("home_layout_locked")
        private val KEY_HOME_LONG_PRESS_RESPONSE = stringPreferencesKey("home_long_press_response")
        private val KEY_AUTO_ANSWER_ENABLED = booleanPreferencesKey("auto_answer_enabled")
        private val KEY_AUTO_ANSWER_DELAY_SECONDS = intPreferencesKey("auto_answer_delay_seconds")
        private val KEY_RETURN_HOME_AFTER_CALL_ENABLED = booleanPreferencesKey("return_home_after_call_enabled")
        private val KEY_FULL_CARD_TAP_ENABLED = booleanPreferencesKey("full_card_tap_enabled")
        private val KEY_DARK_MODE = stringPreferencesKey("dark_mode")
        private val KEY_FONT_SCALE_MODE = stringPreferencesKey("font_scale_mode")
        private val KEY_AUTOSTART_CONFIRMED = booleanPreferencesKey("autostart_confirmed")
        private val KEY_BACKGROUND_START_CONFIRMED = booleanPreferencesKey("background_start_confirmed")
        private val KEY_ICON_SCALE = intPreferencesKey("icon_scale")
        private val KEY_FALL_DETECTION_ENABLED = booleanPreferencesKey("fall_detection_enabled")
        private val KEY_FALL_EMERGENCY_CONTACT = stringPreferencesKey("fall_emergency_contact")

        @Volatile
        private var instance: LauncherSettingsDataStore? = null

        fun getInstance(context: Context): LauncherSettingsDataStore {
            return instance ?: synchronized(this) {
                instance ?: LauncherSettingsDataStore(context.applicationContext).also { instance = it }
            }
        }
    }

    fun snapshot(): LauncherSettings = _settings.value

    fun setLowPerformanceModeEnabled(enabled: Boolean) {
        mutate(
            update = { it.copy(lowPerformanceModeEnabled = enabled) },
            persist = { it[KEY_LOW_PERFORMANCE_MODE] = enabled }
        )
    }

    fun setAutoAnswerEnabled(enabled: Boolean) {
        mutate(
            update = { it.copy(autoAnswerEnabled = enabled) },
            persist = { it[KEY_AUTO_ANSWER_ENABLED] = enabled }
        )
    }

    fun setAutoAnswerDelaySeconds(seconds: Int) {
        val normalized = seconds.coerceIn(1, 30)
        mutate(
            update = { it.copy(autoAnswerDelaySeconds = normalized) },
            persist = { it[KEY_AUTO_ANSWER_DELAY_SECONDS] = normalized }
        )
    }

    fun setFullCardTapEnabled(enabled: Boolean) {
        mutate(
            update = { it.copy(fullCardTapEnabled = enabled) },
            persist = { it[KEY_FULL_CARD_TAP_ENABLED] = enabled }
        )
    }

    fun setDarkMode(value: String) {
        val normalized = normalizeDarkMode(value)
        mutate(
            update = { it.copy(darkMode = normalized) },
            persist = { it[KEY_DARK_MODE] = normalized }
        )
    }

    fun setAutoStartConfirmed(confirmed: Boolean) {
        mutate(
            update = { it.copy(autoStartConfirmed = confirmed) },
            persist = { it[KEY_AUTOSTART_CONFIRMED] = confirmed }
        )
    }

    fun setBackgroundStartConfirmed(confirmed: Boolean) {
        mutate(
            update = { it.copy(backgroundStartConfirmed = confirmed) },
            persist = { it[KEY_BACKGROUND_START_CONFIRMED] = confirmed }
        )
    }

    fun setIconScale(scale: Int) {
        val normalized = scale.coerceIn(MIN_ICON_SCALE, MAX_ICON_SCALE)
        mutate(
            update = { it.copy(iconScale = normalized) },
            persist = { it[KEY_ICON_SCALE] = normalized }
        )
    }

    fun setReturnHomeAfterCallEnabled(enabled: Boolean) {
        mutate(
            update = { it.copy(returnHomeAfterCallEnabled = enabled) },
            persist = { it[KEY_RETURN_HOME_AFTER_CALL_ENABLED] = enabled }
        )
    }

    fun setFontScaleMode(value: String) {
        val normalized = normalizeFontScaleMode(value)
        mutate(
            update = { it.copy(fontScaleMode = normalized) },
            persist = { it[KEY_FONT_SCALE_MODE] = normalized }
        )
    }

    fun setHomeLayoutLocked(locked: Boolean) {
        mutate(
            update = { it.copy(homeLayoutLocked = locked) },
            persist = { it[KEY_HOME_LAYOUT_LOCKED] = locked }
        )
    }

    fun setHomeLongPressResponse(response: String) {
        val normalized = normalizeHomeLongPressResponse(response)
        mutate(
            update = { it.copy(homeLongPressResponse = normalized) },
            persist = { it[KEY_HOME_LONG_PRESS_RESPONSE] = normalized }
        )
    }

    fun setFallDetectionEnabled(enabled: Boolean) {
        mutate(
            update = { it.copy(fallDetectionEnabled = enabled) },
            persist = { it[KEY_FALL_DETECTION_ENABLED] = enabled }
        )
    }

    fun setFallEmergencyContact(number: String) {
        mutate(
            update = { it.copy(fallEmergencyContact = number) },
            persist = { it[KEY_FALL_EMERGENCY_CONTACT] = number }
        )
    }

    fun migrateFrom(migration: LauncherSettingsMigration) {
        if (!migration.hasValues) {
            return
        }
        mutate(
            update = { current ->
                current.copy(
                    lowPerformanceModeEnabled = migration.lowPerformanceModeEnabled
                        ?: current.lowPerformanceModeEnabled,
                    homeLayoutLocked = migration.homeLayoutLocked ?: current.homeLayoutLocked,
                    homeLongPressResponse = migration.homeLongPressResponse
                        ?.let(::normalizeHomeLongPressResponse)
                        ?: current.homeLongPressResponse,
                    autoAnswerEnabled = migration.autoAnswerEnabled ?: current.autoAnswerEnabled,
                    autoAnswerDelaySeconds = (migration.autoAnswerDelaySeconds
                        ?: current.autoAnswerDelaySeconds).coerceIn(1, 30),
                    fullCardTapEnabled = migration.fullCardTapEnabled ?: current.fullCardTapEnabled,
                    darkMode = migration.darkMode?.let(::normalizeDarkMode) ?: current.darkMode,
                    fontScaleMode = migration.fontScaleMode?.let(::normalizeFontScaleMode)
                        ?: current.fontScaleMode,
                    autoStartConfirmed = migration.autoStartConfirmed ?: current.autoStartConfirmed,
                    backgroundStartConfirmed = migration.backgroundStartConfirmed
                        ?: current.backgroundStartConfirmed,
                    iconScale = (migration.iconScale ?: current.iconScale)
                        .coerceIn(MIN_ICON_SCALE, MAX_ICON_SCALE),
                    fallDetectionEnabled = migration.fallDetectionEnabled
                        ?: current.fallDetectionEnabled,
                    fallEmergencyContact = migration.fallEmergencyContact
                        ?: current.fallEmergencyContact
                )
            },
            persist = { preferences ->
                migration.lowPerformanceModeEnabled?.let { preferences[KEY_LOW_PERFORMANCE_MODE] = it }
                migration.homeLayoutLocked?.let { preferences[KEY_HOME_LAYOUT_LOCKED] = it }
                migration.homeLongPressResponse?.let {
                    preferences[KEY_HOME_LONG_PRESS_RESPONSE] = normalizeHomeLongPressResponse(it)
                }
                migration.autoAnswerEnabled?.let { preferences[KEY_AUTO_ANSWER_ENABLED] = it }
                migration.autoAnswerDelaySeconds?.let {
                    preferences[KEY_AUTO_ANSWER_DELAY_SECONDS] = it.coerceIn(1, 30)
                }
                migration.fullCardTapEnabled?.let { preferences[KEY_FULL_CARD_TAP_ENABLED] = it }
                migration.darkMode?.let { preferences[KEY_DARK_MODE] = normalizeDarkMode(it) }
                migration.fontScaleMode?.let {
                    preferences[KEY_FONT_SCALE_MODE] = normalizeFontScaleMode(it)
                }
                migration.autoStartConfirmed?.let { preferences[KEY_AUTOSTART_CONFIRMED] = it }
                migration.backgroundStartConfirmed?.let { preferences[KEY_BACKGROUND_START_CONFIRMED] = it }
                migration.iconScale?.let {
                    preferences[KEY_ICON_SCALE] = it.coerceIn(MIN_ICON_SCALE, MAX_ICON_SCALE)
                }
                migration.fallDetectionEnabled?.let {
                    preferences[KEY_FALL_DETECTION_ENABLED] = it
                }
                migration.fallEmergencyContact?.let {
                    preferences[KEY_FALL_EMERGENCY_CONTACT] = it
                }
            }
        )
    }

    fun clear() {
        mutate(
            update = { LauncherSettings() },
            persist = { it.clear() }
        )
    }

    /**
     * Write-through update: 指定内存中如何增量更新缓存，同时在 IO 协程里异步营造 DataStore。
     * 调用方会立即看到新值（不阻塞主线程）；磁盘写入在后台完成。
     */
    private fun mutate(
        update: (LauncherSettings) -> LauncherSettings,
        persist: (androidx.datastore.preferences.core.MutablePreferences) -> Unit
    ) {
        _settings.update(update)
        ioScope.launch {
            dataStore.edit(persist)
        }
    }

    /**
     * 启动期同步读一次，以保证后续同步 [snapshot] 语义与原实现一致。
     * 后续读写都走 [_settings] 内存缓存，不再阻塞主线。
     */
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
            homeLayoutLocked = this[KEY_HOME_LAYOUT_LOCKED] ?: false,
            homeLongPressResponse = normalizeHomeLongPressResponse(this[KEY_HOME_LONG_PRESS_RESPONSE]),
            autoAnswerEnabled = this[KEY_AUTO_ANSWER_ENABLED] ?: true,
            autoAnswerDelaySeconds = (this[KEY_AUTO_ANSWER_DELAY_SECONDS] ?: DEFAULT_AUTO_ANSWER_DELAY_SECONDS)
                .coerceIn(1, 30),
            returnHomeAfterCallEnabled = this[KEY_RETURN_HOME_AFTER_CALL_ENABLED] ?: true,
            fullCardTapEnabled = this[KEY_FULL_CARD_TAP_ENABLED] ?: false,
            darkMode = normalizeDarkMode(this[KEY_DARK_MODE]),
            fontScaleMode = normalizeFontScaleMode(this[KEY_FONT_SCALE_MODE]),
            autoStartConfirmed = this[KEY_AUTOSTART_CONFIRMED] ?: false,
            backgroundStartConfirmed = this[KEY_BACKGROUND_START_CONFIRMED] ?: false,
            iconScale = (this[KEY_ICON_SCALE] ?: DEFAULT_ICON_SCALE).coerceIn(MIN_ICON_SCALE, MAX_ICON_SCALE),
            fallDetectionEnabled = this[KEY_FALL_DETECTION_ENABLED] ?: false,
            fallEmergencyContact = this[KEY_FALL_EMERGENCY_CONTACT].orEmpty()
        )
    }

    private fun normalizeDarkMode(value: String?): String {
        return when (value) {
            DARK_MODE_LIGHT, DARK_MODE_DARK -> value
            else -> DARK_MODE_SYSTEM
        }
    }

    private fun normalizeFontScaleMode(value: String?): String {
        return when (value) {
            FONT_SCALE_STANDARD,
            FONT_SCALE_LARGE,
            FONT_SCALE_EXTRA_LARGE -> value
            else -> FONT_SCALE_SYSTEM
        }
    }

    private fun normalizeHomeLongPressResponse(value: String?): String {
        return if (value == HOME_LONG_PRESS_RESPONSE_LONG) {
            HOME_LONG_PRESS_RESPONSE_LONG
        } else {
            DEFAULT_HOME_LONG_PRESS_RESPONSE
        }
    }
}
