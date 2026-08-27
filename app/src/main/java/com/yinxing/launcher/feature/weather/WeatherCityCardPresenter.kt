package com.yinxing.launcher.feature.weather

import com.yinxing.launcher.data.weather.WeatherState

data class WeatherCityCardUi(
    val city: String,
    val temperature: String,
    val condition: String,
    val highLow: String,
    val updateTime: String,
    val isCurrent: Boolean,
)

object WeatherCityCardPresenter {
    fun present(state: WeatherState, isCurrent: Boolean): WeatherCityCardUi {
        val now = state.now
        val today = state.forecast.firstOrNull()
        return WeatherCityCardUi(
            city = state.cityName,
            temperature = now?.let { "${it.temperature}°" } ?: "--°",
            condition = now?.weather ?: "天气暂时不可用",
            highLow = today?.let { "最高${it.high}° · 最低${it.low}°" } ?: "稍后再试",
            updateTime = now?.updateTime?.let { "更新于 $it" } ?: "",
            isCurrent = isCurrent,
        )
    }
}
