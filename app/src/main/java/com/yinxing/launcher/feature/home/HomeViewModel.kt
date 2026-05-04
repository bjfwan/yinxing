package com.yinxing.launcher.feature.home

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yinxing.launcher.data.home.LauncherAppRepository
import com.yinxing.launcher.data.home.LauncherPreferences
import com.yinxing.launcher.data.weather.WeatherPreferences
import com.yinxing.launcher.data.weather.WeatherRepository
import com.yinxing.launcher.data.weather.WeatherState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val appSource: HomeAppSource,
    private val settingsSource: HomeSettingsSource,
    private val weatherSource: HomeWeatherSource,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val weatherRefreshIntervalMillis: Long = 8 * 60 * 60 * 1000L
) : ViewModel() {
    private val _homeUiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading(appSource.getStaticHomeItems()))
    val homeUiState: StateFlow<HomeUiState> = _homeUiState.asStateFlow()

    private val _settings = MutableStateFlow(settingsSource.current())
    val settings: StateFlow<HomeSettingsState> = _settings.asStateFlow()

    private val _weatherState = MutableStateFlow(weatherSource.getCached())
    val weatherState: StateFlow<WeatherState?> = _weatherState.asStateFlow()

    private var refreshAppsJob: Job? = null
    private var weatherJob: Job? = null
    private var weatherRefreshJob: Job? = null
    private var weatherLoadingCity: String? = null

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when {
                settingsSource.isLowPerformanceModeKey(key) || settingsSource.isIconScaleKey(key) -> {
                    _settings.value = settingsSource.current()
                }
                settingsSource.isHomeAppConfigKey(key) -> {
                    appSource.invalidateSelections()
                    refreshApps()
                }
            }
        }

    init {
        settingsSource.register(preferenceListener)
    }

    fun refreshApps() {
        refreshAppsJob?.cancel()
        val fallbackItems = _homeUiState.value.items.ifEmpty(appSource::getStaticHomeItems)
        _homeUiState.value = HomeUiState.Loading(fallbackItems)
        refreshAppsJob = viewModelScope.launch {
            runCatching { appSource.getHomeItems() }
                .onSuccess { items ->
                    val resolvedItems = items.ifEmpty { fallbackItems }
                    _homeUiState.value = if (resolvedItems.isEmpty()) {
                        HomeUiState.Empty(resolvedItems)
                    } else {
                        HomeUiState.Success(resolvedItems)
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) {
                        throw error
                    }
                    _homeUiState.value = HomeUiState.Error(
                        fallbackItems,
                        error.message ?: error.javaClass.simpleName
                    )
                }
        }
    }

    fun onPackageChanged() {
        appSource.invalidateInstalledApps()
        refreshApps()
    }

    fun saveAppOrder(items: List<HomeAppItem>) {
        appSource.saveAppOrder(
            items.filter { it.type == HomeAppItem.Type.APP }.map { it.packageName }
        )
    }

    fun maybeRefreshWeather() {
        val city = weatherSource.getCityName()
        val cached = weatherSource.getCached()
        if (cached != null && cached.cityName == city) {
            _weatherState.value = cached
        }
        if (weatherJob?.isActive == true && weatherLoadingCity == city) {
            return
        }
        val expired = cached == null ||
            cached.cityName != city ||
            nowMillis() - cached.lastFetchTime > weatherRefreshIntervalMillis
        if (expired) {
            weatherRefreshJob?.cancel()
            weatherRefreshJob = viewModelScope.launch {
                delay(if (cached == null) 1_200L else 250L)
                refreshWeatherNow()
            }
        }
    }

    fun cancelPendingWeatherRefresh() {
        weatherRefreshJob?.cancel()
        weatherRefreshJob = null
    }

    fun refreshWeatherNow() {
        val city = weatherSource.getCityName()
        if (weatherJob?.isActive == true && weatherLoadingCity == city) {
            return
        }
        weatherJob?.cancel()
        weatherLoadingCity = city
        weatherJob = viewModelScope.launch {
            try {
                _weatherState.value = weatherSource.fetchWeather(city)
            } finally {
                if (weatherLoadingCity == city) {
                    weatherLoadingCity = null
                }
            }
        }
    }

    override fun onCleared() {
        settingsSource.unregister(preferenceListener)
        super.onCleared()
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val preferences = LauncherPreferences.getInstance(appContext)
            val appRepository = LauncherAppRepository.getInstance(appContext)
            val weatherPreferences = WeatherPreferences.getInstance(appContext)
            return HomeViewModel(
                appSource = AndroidHomeAppSource(appRepository, preferences),
                settingsSource = AndroidHomeSettingsSource(preferences),
                weatherSource = AndroidHomeWeatherSource(weatherPreferences, appContext)
            ) as T
        }
    }
}

data class HomeSettingsState(
    val lowPerformanceMode: Boolean,
    val iconScale: Int
)

interface HomeAppSource {
    fun getStaticHomeItems(): List<HomeAppItem>
    suspend fun getHomeItems(): List<HomeAppItem>
    fun invalidateInstalledApps()
    fun invalidateSelections()
    fun saveAppOrder(packageNames: List<String>)
}

interface HomeSettingsSource {
    fun current(): HomeSettingsState
    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener)
    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener)
    fun isLowPerformanceModeKey(key: String?): Boolean
    fun isIconScaleKey(key: String?): Boolean
    fun isHomeAppConfigKey(key: String?): Boolean
}

interface HomeWeatherSource {
    fun getCityName(): String
    fun getCached(): WeatherState?
    suspend fun fetchWeather(cityName: String): WeatherState
}

private class AndroidHomeAppSource(
    private val repository: LauncherAppRepository,
    private val preferences: LauncherPreferences
) : HomeAppSource {
    override fun getStaticHomeItems() = repository.getStaticHomeItems()

    override suspend fun getHomeItems() = repository.getHomeItems(preferences)

    override fun invalidateInstalledApps() = repository.invalidateInstalledApps()

    override fun invalidateSelections() = repository.invalidateSelections()

    override fun saveAppOrder(packageNames: List<String>) = preferences.saveAppOrder(packageNames)
}

private class AndroidHomeSettingsSource(
    private val preferences: LauncherPreferences
) : HomeSettingsSource {
    override fun current() = HomeSettingsState(
        lowPerformanceMode = preferences.isLowPerformanceModeEnabled(),
        iconScale = preferences.getIconScale()
    )

    override fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.registerListener(listener)
    }

    override fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterListener(listener)
    }

    override fun isLowPerformanceModeKey(key: String?) = preferences.isLowPerformanceModeKey(key)

    override fun isIconScaleKey(key: String?) = preferences.isIconScaleKey(key)

    override fun isHomeAppConfigKey(key: String?) = preferences.isHomeAppConfigKey(key)
}

private class AndroidHomeWeatherSource(
    private val preferences: WeatherPreferences,
    private val appContext: Context
) : HomeWeatherSource {
    override fun getCityName() = preferences.getCityName()

    override fun getCached() = WeatherRepository.getCached()

    override suspend fun fetchWeather(cityName: String) = WeatherRepository.fetchWeather(cityName, appContext)
}
