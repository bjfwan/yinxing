package com.yinxing.launcher.data.weather.source

import android.util.Base64
import com.yinxing.launcher.BuildConfig
import com.yinxing.launcher.data.weather.WeatherApiClient
import com.yinxing.launcher.data.weather.WeatherForecastDay
import com.yinxing.launcher.data.weather.parser.SeniverseWeatherParser
import java.net.URLEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

interface SeniverseWeatherSource {
    suspend fun fetchForecast(cityName: String): List<WeatherForecastDay>
}

class SeniverseWeatherDataSource(
    private val apiClient: WeatherApiClient,
    private val parser: SeniverseWeatherParser = SeniverseWeatherParser,
    private val uid: String = BuildConfig.SENIVERSE_UID,
    private val privateKey: String = BuildConfig.SENIVERSE_PK,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : SeniverseWeatherSource {
    override suspend fun fetchForecast(cityName: String): List<WeatherForecastDay> {
        val ts = (clock() / 1000).toString()
        val paramStr = "ts=$ts&uid=$uid"
        val sig = hmacSha1Base64(privateKey, paramStr)
        val sigEncoded = URLEncoder.encode(sig, "UTF-8")
        val locationEncoded = URLEncoder.encode(cityName, "UTF-8")
        val url = "https://api.seniverse.com/v3/weather/daily.json" +
            "?ts=$ts&uid=$uid&sig=$sigEncoded" +
            "&location=$locationEncoded&language=zh-Hans&unit=c&start=0&days=3"
        return parser.parseForecast(apiClient.get(url))
    }

    private fun hmacSha1Base64(key: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        val bytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
