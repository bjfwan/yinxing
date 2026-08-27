package com.yinxing.launcher.feature.weather

import com.yinxing.launcher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WeatherIconResolverTest {
    @Test
    fun `uses distinct source-matched icons for common weather conditions`() {
        assertEquals(R.drawable.weather_sun, WeatherIconResolver.resolve("晴"))
        assertEquals(R.drawable.weather_partly_cloudy, WeatherIconResolver.resolve("多云"))
        assertEquals(R.drawable.weather_cloud, WeatherIconResolver.resolve("阴"))
        assertEquals(R.drawable.weather_rain, WeatherIconResolver.resolve("阵雨"))
    }

    @Test
    fun `keeps severe and low-visibility weather visually distinct`() {
        assertNotEquals(WeatherIconResolver.resolve("小雨"), WeatherIconResolver.resolve("雷阵雨"))
        assertNotEquals(WeatherIconResolver.resolve("晴"), WeatherIconResolver.resolve("小雪"))
        assertNotEquals(WeatherIconResolver.resolve("阴"), WeatherIconResolver.resolve("雾"))
        assertEquals(WeatherIconResolver.resolve("雾"), WeatherIconResolver.resolve("霾"))
    }
}
