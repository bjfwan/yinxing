package com.yinxing.launcher.data.weather.parser

import com.yinxing.launcher.data.weather.WeatherForecastDay
import org.json.JSONObject

object SeniverseWeatherParser {
    fun parseForecast(json: String): List<WeatherForecastDay> {
        val root = JSONObject(json)
        val results = root.getJSONArray("results")
        if (results.length() == 0) return emptyList()
        val daily = results.getJSONObject(0).getJSONArray("daily")
        val list = mutableListOf<WeatherForecastDay>()
        for (i in 0 until daily.length()) {
            val item = daily.getJSONObject(i)
            list.add(
                WeatherForecastDay(
                    date = item.optString("date", ""),
                    textDay = item.optString("text_day", ""),
                    textNight = item.optString("text_night", ""),
                    high = item.optString("high", "0").toIntOrNull() ?: 0,
                    low = item.optString("low", "0").toIntOrNull() ?: 0,
                    weatherCode = item.optString("code_day", "0")
                )
            )
        }
        return list
    }
}
