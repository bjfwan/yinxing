package com.yinxing.launcher.feature.weather

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.yinxing.launcher.R
import com.yinxing.launcher.data.weather.WeatherPreferences
import com.yinxing.launcher.data.weather.WeatherRepository
import com.yinxing.launcher.data.weather.WeatherState
import com.yinxing.launcher.databinding.ActivityWeatherDetailBinding
import com.yinxing.launcher.databinding.ItemWeatherDayBinding
import com.yinxing.launcher.databinding.ItemWeatherHourBinding
import kotlinx.coroutines.launch

class WeatherDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWeatherDetailBinding
    private val preferences by lazy { WeatherPreferences.getInstance(this) }
    private val cityName: String get() = preferences.getCityName()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWeatherDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemInsets()
        binding.btnBack.setOnClickListener { finish() }
        binding.tvCity.setOnClickListener {
            startActivityForResult(
                Intent(this, WeatherCityManagerActivity::class.java),
                REQUEST_MANAGE_CITIES,
            )
        }
        loadWeather()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MANAGE_CITIES && resultCode == Activity.RESULT_OK) loadWeather()
    }

    private fun loadWeather() {
        renderLoading()
        WeatherRepository.getCached()
            ?.takeIf { it.cityName == cityName && it.now != null }
            ?.let(::renderWeather)
        lifecycleScope.launch {
            val location = preferences.getSelectedLocation()
            val state = if (location != null) {
                WeatherRepository.fetchWeatherAtLocation(
                    cityName,
                    location.latitude,
                    location.longitude,
                    this@WeatherDetailActivity,
                )
            } else {
                WeatherRepository.fetchWeather(cityName, this@WeatherDetailActivity)
            }
            renderWeather(state)
        }
    }

    private fun applySystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.weatherDetailScroll) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top, bottom = systemBars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.weatherDetailScroll)
    }

    private fun renderLoading() {
        binding.tvCity.text = cityName
        binding.tvDate.text = ""
        binding.tvTemperature.text = "--°"
        binding.tvCondition.setText(R.string.weather_detail_loading)
        binding.tvHighLow.text = ""
        binding.hourlySection.visibility = View.GONE
        binding.futureSection.visibility = View.GONE
        binding.tvWindHumidity.visibility = View.GONE
        binding.tvUpdate.visibility = View.GONE
        binding.errorGroup.visibility = View.GONE
    }

    private fun renderWeather(state: WeatherState) {
        if (state.now == null) {
            renderFailure(state)
            return
        }
        val ui = WeatherDetailPresenter.present(state)
        binding.errorGroup.visibility = View.GONE
        binding.tvCity.text = ui.city
        binding.tvDate.text = ui.date
        binding.tvTemperature.text = ui.temperature
        binding.tvCondition.text = ui.condition
        binding.tvHighLow.text = ui.highLow
        binding.ivWeatherHeroIcon.setImageResource(
            if (ui.condition.contains("晴")) R.drawable.weather_hero_sun else WeatherIconResolver.resolve(ui.condition),
        )
        binding.ivWeatherHeroIcon.contentDescription = ui.condition
        binding.ivWeatherHeroIcon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES

        renderHours(ui.hours)
        renderDays(ui.days)

        binding.tvWindHumidity.visibility = View.VISIBLE
        binding.tvWindHumidity.text = ui.windAndHumidity
        binding.tvUpdate.visibility = View.VISIBLE
        binding.tvUpdate.text = ui.updateAt
    }

    private fun renderHours(hours: List<WeatherHourUi>) {
        val slots = listOf(binding.hourNow, binding.hourSecond, binding.hourThird, binding.hourFourth)
        binding.hourlySection.visibility = if (hours.isEmpty()) View.GONE else View.VISIBLE
        slots.forEachIndexed { index, slot ->
            val hour = hours.getOrNull(index)
            slot.root.visibility = if (hour == null) View.GONE else View.VISIBLE
            if (hour != null) bindHour(slot, hour, index == 0)
        }
    }

    private fun bindHour(binding: ItemWeatherHourBinding, hour: WeatherHourUi, current: Boolean) {
        binding.root.background = if (current) getDrawable(R.drawable.bg_weather_hour_current) else null
        binding.tvHourLabel.text = hour.label
        binding.tvHourTime.text = hour.time
        binding.tvHourTemperature.text = hour.temperature
        binding.ivHourWeather.setImageResource(WeatherIconResolver.resolve(hour.condition))
        binding.ivHourWeather.contentDescription = hour.condition
    }

    private fun renderDays(days: List<WeatherDayUi>) {
        val rows = listOf(binding.dayTomorrow, binding.dayAfterTomorrow, binding.dayThird)
        binding.futureSection.visibility = if (days.isEmpty()) View.GONE else View.VISIBLE
        rows.forEachIndexed { index, row ->
            val day = days.getOrNull(index)
            row.root.visibility = if (day == null) View.GONE else View.VISIBLE
            if (day != null) bindDay(row, day)
        }
        binding.dayDividerOne.visibility = if (days.size > 1) View.VISIBLE else View.GONE
        binding.dayDividerTwo.visibility = if (days.size > 2) View.VISIBLE else View.GONE
    }

    private fun bindDay(binding: ItemWeatherDayBinding, day: WeatherDayUi) {
        binding.tvDayRelative.text = day.relativeDay
        binding.tvDayWeekday.text = day.weekday
        binding.tvDayCondition.text = day.condition
        binding.tvDayHigh.text = day.high
        binding.tvDayLow.text = day.low
        binding.ivDayWeather.setImageResource(WeatherIconResolver.resolve(day.condition))
        binding.ivDayWeather.contentDescription = day.condition
    }

    private fun renderFailure(state: WeatherState) {
        binding.tvCity.text = state.cityName.ifBlank { cityName }
        binding.tvDate.text = ""
        binding.tvTemperature.text = "--°"
        binding.tvCondition.setText(R.string.weather_detail_failed)
        binding.tvHighLow.text = ""
        binding.hourlySection.visibility = View.GONE
        binding.futureSection.visibility = View.GONE
        binding.tvWindHumidity.visibility = View.GONE
        binding.tvUpdate.visibility = View.GONE
        binding.errorGroup.visibility = View.VISIBLE
    }

    companion object {
        private const val REQUEST_MANAGE_CITIES = 2101
    }
}
