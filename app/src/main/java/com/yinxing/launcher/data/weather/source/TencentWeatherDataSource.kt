package com.yinxing.launcher.data.weather.source

import com.yinxing.launcher.BuildConfig
import com.yinxing.launcher.data.weather.WeatherApiClient
import com.yinxing.launcher.data.weather.WeatherNow
import com.yinxing.launcher.data.weather.parser.TencentWeatherParser
import java.net.URLEncoder

interface TencentWeatherSource {
    suspend fun searchAdcode(cityName: String): String?
    suspend fun fetchNow(adcode: String, cityName: String): WeatherNow?
}

class TencentWeatherDataSource(
    private val apiClient: WeatherApiClient,
    private val parser: TencentWeatherParser = TencentWeatherParser,
    private val apiKey: String = BuildConfig.TENCENT_KEY
) : TencentWeatherSource {
    override suspend fun searchAdcode(cityName: String): String? {
        val encoded = URLEncoder.encode(cityName, "UTF-8")
        val url = "https://apis.map.qq.com/ws/district/v1/search?keyword=$encoded&key=$apiKey"
        return parser.parseAdcode(apiClient.get(url))
    }

    override suspend fun fetchNow(adcode: String, cityName: String): WeatherNow? {
        val url = "https://apis.map.qq.com/ws/weather/v1/?adcode=$adcode&key=$apiKey"
        return parser.parseNow(apiClient.get(url), cityName)
    }
}
