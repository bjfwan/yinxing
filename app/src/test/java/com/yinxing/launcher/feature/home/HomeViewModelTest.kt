package com.yinxing.launcher.feature.home

import android.content.SharedPreferences
import com.yinxing.launcher.data.weather.WeatherForecastDay
import com.yinxing.launcher.data.weather.WeatherNow
import com.yinxing.launcher.data.weather.WeatherState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun refreshAppsEmitsSuccessAfterLoading() = runTest {
        val staticItems = builtInItems()
        val loadedItems = staticItems + homeItem(packageName = "pkg.camera", appName = "相机")
        val viewModel = createViewModel(
            appSource = FakeHomeAppSource(
                staticItems = staticItems,
                homeItemsResult = Result.success(loadedItems)
            )
        )

        viewModel.refreshApps()

        assertEquals(HomeUiState.Loading(staticItems), viewModel.homeUiState.value)
        dispatcher.scheduler.runCurrent()
        assertEquals(HomeUiState.Success(loadedItems), viewModel.homeUiState.value)
    }

    @Test
    fun refreshAppsEmitsSuccessStateWhenOnlyBuiltInItemsAreAvailable() = runTest {
        val staticItems = builtInItems()
        val viewModel = createViewModel(
            appSource = FakeHomeAppSource(
                staticItems = staticItems,
                homeItemsResult = Result.success(staticItems)
            )
        )

        viewModel.refreshApps()
        dispatcher.scheduler.runCurrent()

        assertEquals(HomeUiState.Success(staticItems), viewModel.homeUiState.value)
    }

    @Test
    fun refreshAppsEmitsErrorStateWithFallbackItems() = runTest {
        val staticItems = builtInItems()
        val viewModel = createViewModel(
            appSource = FakeHomeAppSource(
                staticItems = staticItems,
                homeItemsResult = Result.failure(IllegalStateException("boom"))
            )
        )

        viewModel.refreshApps()
        dispatcher.scheduler.runCurrent()

        val state = viewModel.homeUiState.value
        assertTrue(state is HomeUiState.Error)
        state as HomeUiState.Error
        assertEquals(staticItems, state.items)
        assertEquals("boom", state.message)
    }

    @Test
    fun refreshAppsUsesFallbackItemsWhenSourceReturnsEmptyList() = runTest {
        val staticItems = builtInItems()
        val viewModel = createViewModel(
            appSource = FakeHomeAppSource(
                staticItems = staticItems,
                homeItemsResult = Result.success(emptyList())
            )
        )

        viewModel.refreshApps()
        dispatcher.scheduler.runCurrent()

        assertEquals(HomeUiState.Success(staticItems), viewModel.homeUiState.value)
    }

    @Test
    fun saveAppOrderOnlyPersistsAppItems() {
        val appSource = FakeHomeAppSource(
            staticItems = builtInItems(),
            homeItemsResult = Result.success(emptyList())
        )
        val viewModel = createViewModel(appSource = appSource)

        viewModel.saveAppOrder(
            listOf(
                homeItem(packageName = "phone", appName = "电话", type = HomeAppItem.Type.PHONE),
                homeItem(packageName = "pkg.alpha", appName = "Alpha"),
                homeItem(packageName = "add", appName = "添加", type = HomeAppItem.Type.ADD),
                homeItem(packageName = "pkg.beta", appName = "Beta")
            )
        )

        assertEquals(listOf("pkg.alpha", "pkg.beta"), appSource.savedPackageNames)
    }

    @Test
    fun onPackageChangedInvalidatesInstalledAppsAndRefreshesApps() = runTest {
        val staticItems = builtInItems()
        val loadedItems = staticItems + homeItem(packageName = "pkg.camera", appName = "相机")
        val appSource = FakeHomeAppSource(
            staticItems = staticItems,
            homeItemsResult = Result.success(loadedItems)
        )
        val viewModel = createViewModel(appSource = appSource)

        viewModel.onPackageChanged()

        assertEquals(1, appSource.invalidateInstalledAppsCount)
        assertEquals(HomeUiState.Loading(staticItems), viewModel.homeUiState.value)
        dispatcher.scheduler.runCurrent()
        assertEquals(HomeUiState.Success(loadedItems), viewModel.homeUiState.value)
    }

    @Test
    fun maybeRefreshWeatherUsesCachedStateWithoutFetchWhenNotExpired() = runTest {
        val cached = weatherState(lastFetchTime = 900L)
        val weatherSource = FakeHomeWeatherSource(
            cached = cached,
            fetchResult = weatherState(lastFetchTime = 1000L, temperature = 32)
        )
        val viewModel = createViewModel(
            appSource = FakeHomeAppSource(
                staticItems = builtInItems(),
                homeItemsResult = Result.success(emptyList())
            ),
            weatherSource = weatherSource,
            nowMillis = { 1000L },
            weatherRefreshIntervalMillis = 200L
        )

        viewModel.maybeRefreshWeather()
        dispatcher.scheduler.runCurrent()

        assertEquals(cached, viewModel.weatherState.value)
        assertEquals(0, weatherSource.fetchCount)
    }

    @Test
    fun maybeRefreshWeatherFetchesExpiredCacheAfterDelay() = runTest {
        val cached = weatherState(lastFetchTime = 0L, temperature = 26)
        val fetched = weatherState(lastFetchTime = 1000L, temperature = 32)
        val weatherSource = FakeHomeWeatherSource(cached = cached, fetchResult = fetched)
        val viewModel = createViewModel(
            appSource = FakeHomeAppSource(
                staticItems = builtInItems(),
                homeItemsResult = Result.success(emptyList())
            ),
            weatherSource = weatherSource,
            nowMillis = { 1000L },
            weatherRefreshIntervalMillis = 200L
        )

        viewModel.maybeRefreshWeather()

        assertEquals(cached, viewModel.weatherState.value)
        dispatcher.scheduler.advanceTimeBy(249L)
        dispatcher.scheduler.runCurrent()
        assertEquals(0, weatherSource.fetchCount)

        dispatcher.scheduler.advanceTimeBy(1L)
        dispatcher.scheduler.runCurrent()

        assertEquals(1, weatherSource.fetchCount)
        assertEquals(fetched, viewModel.weatherState.value)
    }

    private fun createViewModel(
        appSource: HomeAppSource,
        settingsSource: HomeSettingsSource = FakeHomeSettingsSource(),
        weatherSource: HomeWeatherSource = FakeHomeWeatherSource(),
        nowMillis: () -> Long = { System.currentTimeMillis() },
        weatherRefreshIntervalMillis: Long = 8 * 60 * 60 * 1000L
    ): HomeViewModel {
        return HomeViewModel(
            appSource = appSource,
            settingsSource = settingsSource,
            weatherSource = weatherSource,
            nowMillis = nowMillis,
            weatherRefreshIntervalMillis = weatherRefreshIntervalMillis
        )
    }

    private fun builtInItems(): List<HomeAppItem> {
        return listOf(
            homeItem(packageName = "phone", appName = "电话", type = HomeAppItem.Type.PHONE),
            homeItem(packageName = "wechat_video", appName = "微信视频", type = HomeAppItem.Type.WECHAT_VIDEO),
            homeItem(packageName = "add", appName = "添加", type = HomeAppItem.Type.ADD)
        )
    }

    private fun homeItem(
        packageName: String,
        appName: String,
        type: HomeAppItem.Type = HomeAppItem.Type.APP
    ): HomeAppItem {
        return HomeAppItem(
            packageName = packageName,
            appName = appName,
            type = type
        )
    }

    private fun weatherState(
        cityName: String = "深圳",
        lastFetchTime: Long,
        temperature: Int = 28
    ) = WeatherState.Success(
        cityName = cityName,
        adcode = "440300",
        now = WeatherNow(
            cityName = cityName,
            weather = "晴",
            temperature = temperature,
            windDirection = "北风",
            windPower = "2级",
            humidity = 50,
            updateTime = "08:30"
        ),
        forecast = listOf(
            WeatherForecastDay(
                date = "2026-04-27",
                textDay = "多云",
                textNight = "晴",
                high = temperature + 2,
                low = temperature - 8,
                weatherCode = "4"
            )
        ),
        lastFetchTime = lastFetchTime
    )

    private class FakeHomeAppSource(
        private val staticItems: List<HomeAppItem>,
        private val homeItemsResult: Result<List<HomeAppItem>>
    ) : HomeAppSource {
        var invalidateInstalledAppsCount: Int = 0
        var savedPackageNames: List<String> = emptyList()

        override fun getStaticHomeItems(): List<HomeAppItem> = staticItems

        override suspend fun getHomeItems(): List<HomeAppItem> = homeItemsResult.getOrThrow()

        override fun invalidateInstalledApps() {
            invalidateInstalledAppsCount += 1
        }

        override fun invalidateSelections() {
        }

        override fun saveAppOrder(packageNames: List<String>) {
            savedPackageNames = packageNames
        }
    }

    private class FakeHomeSettingsSource : HomeSettingsSource {
        override fun current(): HomeSettingsState = HomeSettingsState(
            lowPerformanceMode = false,
            iconScale = 100
        )

        override fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        }

        override fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        }

        override fun isLowPerformanceModeKey(key: String?): Boolean = false

        override fun isIconScaleKey(key: String?): Boolean = false

        override fun isHomeAppConfigKey(key: String?): Boolean = false
    }

    private class FakeHomeWeatherSource(
        private val cityName: String = "深圳",
        private val cached: WeatherState? = null,
        private val fetchResult: WeatherState = WeatherState.Loading(cityName)
    ) : HomeWeatherSource {
        var fetchCount: Int = 0

        override fun getCityName(): String = cityName

        override fun getCached(): WeatherState? = cached

        override suspend fun fetchWeather(cityName: String): WeatherState {
            fetchCount += 1
            return fetchResult
        }
    }
}
