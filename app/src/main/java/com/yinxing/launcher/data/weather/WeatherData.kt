package com.bajianfeng.launcher.data.weather

data class WeatherNow(
    val cityName: String,
    val weather: String,       // 天气描述，如"晴"、"多云"
    val temperature: Int,      // 当前温度
    val windDirection: String, // 风向，如"北风"
    val windPower: String,     // 风力，如"1-2级"
    val humidity: Int,         // 湿度百分比
    val updateTime: String     // 更新时间，如"04:00"
)

data class WeatherForecastDay(
    val date: String,          // 日期，如"2026-04-18"
    val textDay: String,       // 白天天气
    val textNight: String,     // 夜间天气
    val high: Int,             // 最高温
    val low: Int,              // 最低温
    val weatherCode: String    // 心知天气code，用于映射图标
)

data class WeatherState(
    val cityName: String,
    val adcode: String,
    val now: WeatherNow?,
    val forecast: List<WeatherForecastDay>,
    val lastFetchTime: Long = 0L,
    val error: String? = null
) {
    val isValid: Boolean
        get() = now != null && error == null

    companion object {
        fun empty() = WeatherState(
            cityName = "",
            adcode = "",
            now = null,
            forecast = emptyList()
        )
    }
}
