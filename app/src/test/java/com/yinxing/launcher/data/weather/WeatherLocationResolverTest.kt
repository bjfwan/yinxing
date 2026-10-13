package com.yinxing.launcher.data.weather

import android.location.Address
import android.location.Location
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `api city name prefers city then locality then subdivision`() {
        val full = JSONObject("""{"city":"杭州市","locality":"西湖区","principalSubdivision":"浙江省"}""")
        assertEquals("杭州市", WeatherLocationResolver.apiCityName(full))

        val noCity = JSONObject("""{"city":"","locality":"黄浦区","principalSubdivision":"上海市"}""")
        assertEquals("黄浦区", WeatherLocationResolver.apiCityName(noCity))

        val onlySubdivision = JSONObject("""{"principalSubdivision":"青海省"}""")
        assertEquals("青海省", WeatherLocationResolver.apiCityName(onlySubdivision))

        assertNull(WeatherLocationResolver.apiCityName(JSONObject("{}")))
    }

    @Test
    fun `ip location parses successful payload`() {
        val json = JSONObject(
            """{"success":true,"city":"上海市","region":"上海市","latitude":31.2,"longitude":121.5}"""
        )
        val location = WeatherLocationResolver.ipLocation(json)

        assertEquals("上海市", location?.cityName)
        assertEquals(31.2, location?.latitude ?: 0.0, 0.001)
        assertEquals(121.5, location?.longitude ?: 0.0, 0.001)
    }

    @Test
    fun `ip location falls back to region and placeholder name`() {
        val regionOnly = JSONObject(
            """{"success":true,"city":"","region":"四川省","latitude":30.6,"longitude":104.0}"""
        )
        assertEquals("四川省", WeatherLocationResolver.ipLocation(regionOnly)?.cityName)

        val noName = JSONObject(
            """{"success":true,"city":"","region":"","latitude":30.6,"longitude":104.0}"""
        )
        assertEquals(
            WeatherLocationResolver.FALLBACK_CITY_NAME,
            WeatherLocationResolver.ipLocation(noName)?.cityName
        )
    }

    @Test
    fun `ip location rejects failed or invalid payloads`() {
        assertNull(WeatherLocationResolver.ipLocation(JSONObject("""{"success":false}""")))
        assertNull(
            WeatherLocationResolver.ipLocation(
                JSONObject("""{"success":true,"city":"上海市"}""")
            )
        )
        assertNull(
            WeatherLocationResolver.ipLocation(
                JSONObject("""{"success":true,"latitude":120.0,"longitude":300.0}""")
            )
        )
    }
}
