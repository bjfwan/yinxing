package com.yinxing.launcher.data.weather.parser

import com.yinxing.launcher.data.weather.WeatherNow
import org.json.JSONObject

object TencentWeatherParser {
    fun parseAdcode(json: String): String? {
        val root = JSONObject(json)
        if (root.getInt("status") != 0) return null
        val resultArr = root.getJSONArray("result")
        if (resultArr.length() == 0) return null
        val firstGroup = resultArr.getJSONArray(0)
        if (firstGroup.length() == 0) return null
        for (i in 0 until firstGroup.length()) {
            val item = firstGroup.getJSONObject(i)
            if (item.optInt("level", 99) <= 2) {
                return item.getString("id")
            }
        }
        return firstGroup.getJSONObject(0).getString("id")
    }

    fun parseNow(json: String, cityName: String): WeatherNow? {
        val root = JSONObject(json)
        if (root.getInt("status") != 0) return null
        val realtimeArr = root.getJSONObject("result").getJSONArray("realtime")
        if (realtimeArr.length() == 0) return null
        val item = realtimeArr.getJSONObject(0)
        val infos = item.getJSONObject("infos")
        val updateTime = item.optString("update_time", "")
            .let { if (it.length >= 16) it.substring(11, 16) else it }
        return WeatherNow(
            cityName = cityName,
            weather = infos.optString("weather", ""),
            temperature = infos.optInt("temperature", 0),
            windDirection = infos.optString("wind_direction", ""),
            windPower = infos.optString("wind_power", ""),
            humidity = infos.optInt("humidity", 0),
            updateTime = updateTime
        )
    }
}
