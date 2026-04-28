package com.yinxing.launcher.data.weather

data class WeatherNow(
    val cityName: String,
    val weather: String,
    val temperature: Int,
    val windDirection: String,
    val windPower: String,
    val humidity: Int,
    val updateTime: String
)

data class WeatherForecastDay(
    val date: String,
    val textDay: String,
    val textNight: String,
    val high: Int,
    val low: Int,
    val weatherCode: String
)
