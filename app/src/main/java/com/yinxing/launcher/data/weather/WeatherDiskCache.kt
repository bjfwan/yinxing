package com.yinxing.launcher.data.weather

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class WeatherDiskCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(cityName: String? = null): WeatherState.Success? {
        val json = prefs.getString(KEY_PAYLOAD, null) ?: return null
        return runCatching { parse(json) }.getOrNull()
            ?.takeIf { cityName == null || it.cityName == cityName }
            ?.copy(fromCache = true)
    }

    fun write(state: WeatherState.Success) {
        prefs.edit().putString(KEY_PAYLOAD, serialize(state)).commit()
    }

    fun clear() {
        prefs.edit().clear().commit()
    }

    private fun serialize(state: WeatherState.Success): String {
        val forecast = JSONArray()
        state.forecast.forEach { day ->
            forecast.put(
                JSONObject()
                    .put("date", day.date)
                    .put("textDay", day.textDay)
                    .put("textNight", day.textNight)
                    .put("high", day.high)
                    .put("low", day.low)
                    .put("weatherCode", day.weatherCode)
            )
        }
        return JSONObject()
            .put("cityName", state.cityName)
            .put("adcode", state.adcode)
            .put("lastFetchTime", state.lastFetchTime)
            .put(
                "now",
                JSONObject()
                    .put("cityName", state.now.cityName)
                    .put("weather", state.now.weather)
                    .put("temperature", state.now.temperature)
                    .put("windDirection", state.now.windDirection)
                    .put("windPower", state.now.windPower)
                    .put("humidity", state.now.humidity)
                    .put("updateTime", state.now.updateTime)
            )
            .put("forecast", forecast)
            .toString()
    }

    private fun parse(json: String): WeatherState.Success {
        val root = JSONObject(json)
        val nowJson = root.getJSONObject("now")
        val forecastJson = root.optJSONArray("forecast") ?: JSONArray()
        val forecast = mutableListOf<WeatherForecastDay>()
        for (i in 0 until forecastJson.length()) {
            val day = forecastJson.getJSONObject(i)
            forecast.add(
                WeatherForecastDay(
                    date = day.optString("date", ""),
                    textDay = day.optString("textDay", ""),
                    textNight = day.optString("textNight", ""),
                    high = day.optInt("high", 0),
                    low = day.optInt("low", 0),
                    weatherCode = day.optString("weatherCode", "0")
                )
            )
        }
        return WeatherState.Success(
            cityName = root.optString("cityName", ""),
            adcode = root.optString("adcode", ""),
            now = WeatherNow(
                cityName = nowJson.optString("cityName", ""),
                weather = nowJson.optString("weather", ""),
                temperature = nowJson.optInt("temperature", 0),
                windDirection = nowJson.optString("windDirection", ""),
                windPower = nowJson.optString("windPower", ""),
                humidity = nowJson.optInt("humidity", 0),
                updateTime = nowJson.optString("updateTime", "")
            ),
            forecast = forecast,
            lastFetchTime = root.optLong("lastFetchTime", 0L),
            fromCache = true
        )
    }

    companion object {
        private const val PREFS_NAME = "weather_disk_cache"
        private const val KEY_PAYLOAD = "payload"
    }
}
