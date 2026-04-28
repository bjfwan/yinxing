package com.yinxing.launcher.data.weather

enum class WeatherFailureReason {
    Network,
    Api,
    Parse,
    Backoff,
    Unknown
}

sealed class WeatherState {
    abstract val cityName: String
    abstract val adcode: String
    abstract val now: WeatherNow?
    abstract val forecast: List<WeatherForecastDay>
    abstract val lastFetchTime: Long
    abstract val error: String?

    open val isValid: Boolean
        get() = now != null && error == null

    data class Loading(
        override val cityName: String
    ) : WeatherState() {
        override val adcode: String = ""
        override val now: WeatherNow? = null
        override val forecast: List<WeatherForecastDay> = emptyList()
        override val lastFetchTime: Long = 0L
        override val error: String? = null
    }

    data class Success(
        override val cityName: String,
        override val adcode: String,
        override val now: WeatherNow,
        override val forecast: List<WeatherForecastDay>,
        override val lastFetchTime: Long,
        val fromCache: Boolean = false
    ) : WeatherState() {
        override val error: String? = null
    }

    data class Failure(
        override val cityName: String,
        val reason: WeatherFailureReason,
        override val error: String,
        override val lastFetchTime: Long = 0L
    ) : WeatherState() {
        override val adcode: String = ""
        override val now: WeatherNow? = null
        override val forecast: List<WeatherForecastDay> = emptyList()
    }

    data class CityNotFound(
        override val cityName: String,
        override val error: String = "未找到城市"
    ) : WeatherState() {
        override val adcode: String = ""
        override val now: WeatherNow? = null
        override val forecast: List<WeatherForecastDay> = emptyList()
        override val lastFetchTime: Long = 0L
    }

    data class UsingCache(
        val cached: Success,
        val reason: WeatherFailureReason,
        val message: String
    ) : WeatherState() {
        override val cityName: String = cached.cityName
        override val adcode: String = cached.adcode
        override val now: WeatherNow = cached.now
        override val forecast: List<WeatherForecastDay> = cached.forecast
        override val lastFetchTime: Long = cached.lastFetchTime
        override val error: String = message
        override val isValid: Boolean = true
    }

    companion object {
        fun empty(): WeatherState = Loading("")
    }
}
