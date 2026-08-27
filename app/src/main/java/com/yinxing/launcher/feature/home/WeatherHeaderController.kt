package com.yinxing.launcher.feature.home

import android.view.View
import android.view.animation.DecelerateInterpolator
import com.yinxing.launcher.R
import com.yinxing.launcher.data.weather.WeatherState
import com.yinxing.launcher.databinding.ActivityMainBinding
import com.yinxing.launcher.feature.weather.WeatherIconResolver

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
        binding.tvTime.textSize = (56f * ratio).coerceAtLeast(40f)
        binding.tvDate.textSize = (20f * ratio).coerceAtLeast(17f)
        binding.tvLunar.textSize = (17f * ratio).coerceAtLeast(15f)
        weatherBinding.tvWeatherTemp.textSize = (46f * ratio).coerceAtLeast(38f)
        weatherBinding.tvWeatherCity.textSize = (22f * ratio).coerceAtLeast(18f)
        weatherBinding.tvWeatherDesc.textSize = (24f * ratio).coerceAtLeast(20f)
        weatherBinding.tvWeatherUpdate.textSize = (18f * ratio).coerceAtLeast(16f)
    }

    fun renderWeather(state: WeatherState) {
        val now = state.now
        val today = state.forecast.firstOrNull()
        if (now != null) {
            weatherBinding.tvWeatherTemp.visibility = View.VISIBLE
            weatherBinding.ivWeatherIcon.setImageResource(WeatherIconResolver.resolve(now.weather))
            weatherBinding.ivWeatherIcon.visibility = View.VISIBLE
            weatherBinding.tvWeatherCity.text = now.cityName
            weatherBinding.tvWeatherDesc.text = now.weather
            weatherBinding.tvWeatherTemp.text = context.getString(R.string.weather_temperature_format, now.temperature)
            weatherBinding.tvWeatherUpdate.text = buildList {
                if (today != null) {
                    if (today.textDay.isNotEmpty() && today.textDay != now.weather) {
                        add(context.getString(R.string.weather_today_short, today.textDay))
                    }
                    add(context.getString(R.string.weather_range_short, today.high, today.low))
                }
                if (now.updateTime.isNotEmpty()) {
                    add(context.getString(R.string.weather_updated_short, now.updateTime))
                }
            }.joinToString(" · ")
        } else {
            weatherBinding.tvWeatherTemp.visibility = View.GONE
            weatherBinding.ivWeatherIcon.visibility = View.GONE
            weatherBinding.tvWeatherCity.text = state.cityName.ifEmpty { context.getString(R.string.home_weather_placeholder_city) }
            weatherBinding.tvWeatherDesc.text = if (state.error != null) {
                context.getString(R.string.weather_load_failed_short)
            } else {
                context.getString(R.string.weather_loading_short)
            }
            weatherBinding.tvWeatherTemp.text = context.getString(R.string.weather_temperature_placeholder)
            weatherBinding.tvWeatherUpdate.text = ""
        }
    }
}
