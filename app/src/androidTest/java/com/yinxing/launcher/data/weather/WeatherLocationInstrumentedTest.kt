package com.yinxing.launcher.data.weather

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeatherLocationInstrumentedTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun deviceLocationResolvesToCity() = runBlocking {
        val result = WeatherLocationResolver.resolve(context)
        Log.i("WeatherIT", "resolve() -> city=${result?.cityName} lat=${result?.latitude} lng=${result?.longitude}")
        assertNotNull("定位应返回城市信息（权限已授予）", result)
        assertTrue(result!!.cityName.isNotBlank())
    }

    @Test
    fun regionIndexLoadsFromAssetsAndMatches() {
        val index = ChinaRegionIndex.get(context)
        assertEquals(110105, index.resolve("北京朝阳")?.entry?.adcode)
        assertEquals("朝阳市", index.resolve("朝阳")?.entry?.name)
        assertEquals("杭州市", index.resolve("杭洲")?.entry?.name)
        assertEquals(330106, index.resolve("杭州市西湖区")?.entry?.adcode)
        val nearest = index.nearestCity(31.2304, 121.4737)
        assertEquals("上海市", nearest?.name)
    }

    @Test
    fun cityWeatherUsesLocalIndexThenFetchesForecast() = runBlocking {
        WeatherRepository.initialize(context)
        val state = WeatherRepository.fetchWeather("北京朝阳", context)
        Log.i("WeatherIT", "fetchWeather(北京朝阳) -> ${state::class.simpleName}")
        assertTrue("应返回成功状态，实际=$state", state is WeatherState.Success)
        state as WeatherState.Success
        assertEquals("adcode:110105", state.adcode)
        assertTrue(state.now.temperature > -60 && state.now.temperature < 60)
    }
}
