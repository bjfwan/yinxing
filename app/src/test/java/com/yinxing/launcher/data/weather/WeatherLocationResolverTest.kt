package com.yinxing.launcher.data.weather

import android.location.Address
import android.location.Location
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class WeatherLocationResolverTest {
    @Test
    fun `accepts only recent cached locations for the fast path`() {
        val now = 10 * 60 * 60 * 1000L
        val recent = Location("network").apply { time = now - 5 * 60 * 1000L }
        val stale = Location("network").apply { time = now - 7 * 60 * 60 * 1000L }

        assertEquals(true, WeatherLocationResolver.isRecent(recent, now))
        assertEquals(false, WeatherLocationResolver.isRecent(stale, now))
    }

    @Test
    fun `uses locality before broader administrative areas`() {
        val address = Address(Locale.SIMPLIFIED_CHINESE).apply {
            locality = "杭州市"
            subAdminArea = "浙江省"
            adminArea = "浙江省"
        }

        assertEquals("杭州市", WeatherLocationResolver.cityName(address))
    }

    @Test
    fun `falls back to province when a city is unavailable`() {
        val address = Address(Locale.SIMPLIFIED_CHINESE).apply { adminArea = "北京市" }

        assertEquals("北京市", WeatherLocationResolver.cityName(address))
    }
}
