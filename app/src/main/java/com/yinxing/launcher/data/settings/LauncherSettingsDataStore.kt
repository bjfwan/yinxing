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
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()

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

    fun setKioskModeEnabled(enabled: Boolean) {
        mutate(
            update = { it.copy(kioskModeEnabled = enabled) },
            persist = { it[KEY_KIOSK_MODE_ENABLED] = enabled }
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

    fun migrateFrom(migration: LauncherSettingsMigration) {
        if (!migration.hasValues) {
            return
        }
        mutate(
            update = { current ->
                current.copy(
                    lowPerformanceModeEnabled = migration.lowPerformanceModeEnabled
                        ?: current.lowPerformanceModeEnabled,
                    autoAnswerEnabled = migration.autoAnswerEnabled ?: current.autoAnswerEnabled,
                    autoAnswerDelaySeconds = (migration.autoAnswerDelaySeconds
                        ?: current.autoAnswerDelaySeconds).coerceIn(1, 30),
                    fullCardTapEnabled = migration.fullCardTapEnabled ?: current.fullCardTapEnabled,
                    darkMode = migration.darkMode?.let(::normalizeDarkMode) ?: current.darkMode,
                    kioskModeEnabled = migration.kioskModeEnabled ?: current.kioskModeEnabled,
                    autoStartConfirmed = migration.autoStartConfirmed ?: current.autoStartConfirmed,
                    backgroundStartConfirmed = migration.backgroundStartConfirmed
                        ?: current.backgroundStartConfirmed,
                    iconScale = (migration.iconScale ?: current.iconScale)
                        .coerceIn(MIN_ICON_SCALE, MAX_ICON_SCALE)
                )
            },
            persist = { preferences ->
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
                migration.iconScale?.let {
                    preferences[KEY_ICON_SCALE] = it.coerceIn(MIN_ICON_SCALE, MAX_ICON_SCALE)
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
