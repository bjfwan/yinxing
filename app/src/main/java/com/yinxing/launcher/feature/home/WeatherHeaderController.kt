package com.yinxing.launcher.feature.home

import android.view.animation.DecelerateInterpolator
import com.yinxing.launcher.R
import com.yinxing.launcher.data.weather.WeatherState
import com.yinxing.launcher.databinding.ActivityMainBinding

class WeatherHeaderController(
    private val binding: ActivityMainBinding
) {
    private val context = binding.root.context
    private val weatherBinding = binding.cardWeather
    private var lastHeaderDayKey = Int.MIN_VALUE
    private var lastTimeText: String? = null

    fun renderTime(snapshot: TimeSnapshot, lowPerformanceMode: Boolean) {
        if (binding.tvTime.text != snapshot.timeText) {
            val animateChange = lastTimeText != null && !lowPerformanceMode
            if (animateChange) {
                binding.tvTime.animate().cancel()
                binding.tvTime.animate()
                    .alpha(0.45f)
                    .setDuration(90)
                    .withEndAction {
                        binding.tvTime.text = snapshot.timeText
                        binding.tvTime.animate()
                            .alpha(1f)
                            .setDuration(160)
                            .setInterpolator(DecelerateInterpolator())
                            .start()
                    }
                    .start()
            } else {
                binding.tvTime.text = snapshot.timeText
            }
            lastTimeText = snapshot.timeText
        }
        if (snapshot.dayKey != lastHeaderDayKey) {
            lastHeaderDayKey = snapshot.dayKey
            binding.tvDate.text = snapshot.dateText
            binding.tvLunar.text = snapshot.lunarText
        }
    }

    fun applyScale(scale: Int) {
        val ratio = scale / 100f
        binding.tvTime.textSize = (46f * ratio).coerceAtLeast(32f)
        binding.tvDate.textSize = (20f * ratio).coerceAtLeast(16f)
        binding.tvLunar.textSize = (16f * ratio).coerceAtLeast(12f)
        weatherBinding.tvWeatherTemp.textSize = (52f * ratio).coerceAtLeast(36f)
        weatherBinding.tvWeatherCity.textSize = (22f * ratio).coerceAtLeast(16f)
        weatherBinding.tvWeatherDesc.textSize = (18f * ratio).coerceAtLeast(14f)
        weatherBinding.tvWeatherHighLow.textSize = (18f * ratio).coerceAtLeast(14f)
    }

    fun renderWeather(state: WeatherState) {
        val now = state.now
        val today = state.forecast.firstOrNull()
        if (now != null) {
            weatherBinding.tvWeatherCity.text = now.cityName
            val weatherText = if (today != null && today.textDay.isNotEmpty() && today.textDay != now.weather) {
                context.getString(R.string.weather_summary_with_today, now.weather, today.textDay)
            } else {
                now.weather
            }
            weatherBinding.tvWeatherDesc.text = listOf(weatherText, now.windDirection, now.windPower)
                .filter { it.isNotBlank() }
                .joinToString("  ")
            weatherBinding.tvWeatherTemp.text = context.getString(R.string.weather_temperature_format, now.temperature)
            weatherBinding.tvWeatherHighLow.text = if (today != null) {
                context.getString(R.string.weather_high_low_format, today.high, today.low)
            } else {
                ""
            }
            weatherBinding.tvWeatherUpdate.text = if (now.updateTime.isNotEmpty()) {
                context.getString(R.string.weather_update_at, now.updateTime)
            } else {
                ""
            }
        } else {
            weatherBinding.tvWeatherCity.text = state.cityName.ifEmpty { context.getString(R.string.home_weather_placeholder_city) }
            weatherBinding.tvWeatherDesc.text = if (state.error != null) {
                context.getString(R.string.weather_load_failed_short)
            } else {
                context.getString(R.string.weather_loading_short)
            }
            weatherBinding.tvWeatherTemp.text = context.getString(R.string.weather_temperature_placeholder)
            weatherBinding.tvWeatherHighLow.text = ""
            weatherBinding.tvWeatherUpdate.text = ""
        }
    }
}
