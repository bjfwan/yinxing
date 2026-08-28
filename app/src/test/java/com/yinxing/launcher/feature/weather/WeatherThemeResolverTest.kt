package com.yinxing.launcher.feature.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WeatherThemeResolverTest {
    @Test
    fun `maps every weather family to its own page atmosphere`() {
        assertEquals(WeatherThemeKind.SUNNY, WeatherThemeResolver.resolve("晴").kind)
        assertEquals(WeatherThemeKind.CLOUDY, WeatherThemeResolver.resolve("多云").kind)
        assertEquals(WeatherThemeKind.CLOUDY, WeatherThemeResolver.resolve("阴").kind)
        assertEquals(WeatherThemeKind.RAIN, WeatherThemeResolver.resolve("小雨").kind)
        assertEquals(WeatherThemeKind.STORM, WeatherThemeResolver.resolve("雷阵雨").kind)
        assertEquals(WeatherThemeKind.SNOW, WeatherThemeResolver.resolve("小雪").kind)
        assertEquals(WeatherThemeKind.FOG, WeatherThemeResolver.resolve("雾").kind)
    }

    @Test
    fun `severe weather takes priority over generic precipitation words`() {
        assertEquals(WeatherThemeKind.STORM, WeatherThemeResolver.resolve("雷雨").kind)
        assertEquals(WeatherThemeKind.SNOW, WeatherThemeResolver.resolve("雨夹雪").kind)
    }

    @Test
    fun `clear rain and storm use visibly different surfaces and accents`() {
        val sunny = WeatherThemeResolver.resolve("晴")
        val rain = WeatherThemeResolver.resolve("小雨")
        val storm = WeatherThemeResolver.resolve("雷阵雨")

        assertNotEquals(sunny.pageStart, rain.pageStart)
        assertNotEquals(sunny.heroStart, rain.heroStart)
        assertNotEquals(sunny.surface, rain.surface)
        assertNotEquals(sunny.accent, rain.accent)
        assertNotEquals(rain.pageStart, storm.pageStart)
        assertNotEquals(rain.accent, storm.accent)
    }
}
