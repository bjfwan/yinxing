package com.yinxing.launcher.feature.weather

import androidx.annotation.DrawableRes
import com.yinxing.launcher.R

object WeatherIconResolver {
    @DrawableRes
    fun resolve(condition: String): Int = when {
        condition.contains("雷") -> R.drawable.weather_storm
        condition.contains("雪") || condition.contains("冰雹") -> R.drawable.weather_snow
        condition.contains("雾") || condition.contains("霾") || condition.contains("沙尘") -> R.drawable.weather_fog
        condition.contains("雨") -> R.drawable.weather_rain
        condition.contains("多云") -> R.drawable.weather_partly_cloudy
        condition.contains("阴") -> R.drawable.weather_cloud
        else -> R.drawable.weather_sun
    }
}
