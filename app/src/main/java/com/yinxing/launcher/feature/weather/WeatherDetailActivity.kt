package com.yinxing.launcher.feature.weather

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import com.yinxing.launcher.common.ui.FontScaleActivity
import com.yinxing.launcher.common.ui.AnchoredHintAlignment
import com.yinxing.launcher.common.ui.AnchoredHintPopup
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.yinxing.launcher.R
import com.yinxing.launcher.data.weather.WeatherPreferences
import com.yinxing.launcher.data.weather.WeatherRepository
import com.yinxing.launcher.data.weather.WeatherState
import com.yinxing.launcher.data.home.LauncherPreferences
import com.yinxing.launcher.databinding.ActivityWeatherDetailBinding
import com.yinxing.launcher.databinding.ItemWeatherDayBinding
import com.yinxing.launcher.databinding.ItemWeatherHourBinding
import kotlinx.coroutines.launch

class WeatherDetailActivity : FontScaleActivity() {
    private lateinit var binding: ActivityWeatherDetailBinding
    private val preferences by lazy { WeatherPreferences.getInstance(this) }
    private val launcherPreferences by lazy { LauncherPreferences.getInstance(this) }
    private val cityHintPreferences by lazy { WeatherCityHintPreferences(this) }
    private val cityName: String get() = preferences.getCityName()
    private var visualTheme = WeatherThemeResolver.resolve("")
    private var hostResumed = false
    private var cityHintScheduled = false
    private lateinit var cityHintPopup: AnchoredHintPopup
    private val showCityHintRunnable = Runnable {
        cityHintScheduled = false
        showCityHintAfterLayout()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWeatherDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cityHintPopup = AnchoredHintPopup(
            activity = this,
            anchor = binding.tvCity,
            textRes = R.string.weather_city_detail_hint,
            alignment = AnchoredHintAlignment.Start,
            onClick = ::openCityManager,
        )
        applySystemInsets()
        binding.btnBack.setOnClickListener { finish() }
        binding.btnRetry.setOnClickListener { loadWeather() }
        binding.tvCity.setOnClickListener { openCityManager() }
        loadWeather()
    }

    override fun onResume() {
        super.onResume()
        hostResumed = true
        maybeShowCityHint()
    }

    override fun onPause() {
        hostResumed = false
        cityHintScheduled = false
        binding.tvCity.removeCallbacks(showCityHintRunnable)
        cityHintPopup.dismiss()
        super.onPause()
    }

    override fun onDestroy() {
        binding.tvCity.removeCallbacks(showCityHintRunnable)
        cityHintPopup.dismiss()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MANAGE_CITIES && resultCode == Activity.RESULT_OK) loadWeather()
    }

    private fun openCityManager() {
        cityHintPopup.dismiss()
        startActivityForResult(
            Intent(this, WeatherCityManagerActivity::class.java),
            REQUEST_MANAGE_CITIES,
        )
    }

    private fun maybeShowCityHint() {
        if (
            !shouldRevealWeatherCityHint(
                hostResumed = hostResumed,
                alreadyShown = cityHintPreferences.hasBeenShown(),
            ) || cityHintScheduled || cityHintPopup.isShowing
        ) {
            return
        }
        cityHintScheduled = true
        binding.tvCity.postDelayed(showCityHintRunnable, CITY_HINT_DELAY_MS)
    }

    private fun showCityHintAfterLayout() {
        if (
            !shouldRevealWeatherCityHint(
                hostResumed = hostResumed,
                alreadyShown = cityHintPreferences.hasBeenShown(),
            ) || !binding.tvCity.isAttachedToWindow || binding.tvCity.width == 0
        ) {
            return
        }
        if (cityHintPreferences.markShownIfFirstTime()) {
            cityHintPopup.show(launcherPreferences.isLowPerformanceModeEnabled())
        }
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
        applyWeatherTheme("")
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
        binding.ivWeatherHeroIcon.setImageResource(WeatherIconResolver.resolve(ui.condition))
        binding.ivWeatherHeroIcon.contentDescription = ui.condition
        binding.ivWeatherHeroIcon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES

        applyWeatherTheme(ui.condition)

        renderHours(ui.hours)
        renderDays(ui.days)

        binding.tvWindHumidity.visibility = View.VISIBLE
        binding.tvWindHumidity.text = ui.windAndHumidity
        binding.tvUpdate.visibility = View.VISIBLE
        binding.tvUpdate.text = ui.updateAt
    }

    private fun renderHours(hours: List<WeatherHourUi>) {
        val slots = listOf(
            binding.hourNow,
            binding.hourSecond,
            binding.hourThird,
            binding.hourFourth,
            binding.hourFifth,
            binding.hourSixth,
            binding.hourSeventh,
            binding.hourEighth,
        )
        binding.hourlySection.visibility = if (hours.isEmpty()) View.GONE else View.VISIBLE
        slots.forEachIndexed { index, slot ->
            val hour = hours.getOrNull(index)
            slot.root.visibility = if (hour == null) View.GONE else View.VISIBLE
            if (hour != null) bindHour(slot, hour, index == 0)
        }
    }

    private fun bindHour(binding: ItemWeatherHourBinding, hour: WeatherHourUi, current: Boolean) {
        binding.root.background = if (current) {
            gradient(
                colors = intArrayOf(color(visualTheme.currentStart), color(visualTheme.currentEnd)),
                cornerRadiusDp = 12f,
            )
        } else {
            null
        }
        binding.tvHourLabel.text = hour.label
        binding.tvHourLabel.setTextColor(
            color(if (current) visualTheme.accent else R.color.launcher_text_primary),
        )
        binding.tvHourTime.text = hour.time
        binding.tvHourTemperature.text = hour.temperature
        binding.tvHourCondition.text = hour.condition
        binding.tvHourPrecipitation.text = hour.precipitation
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
        applyWeatherTheme("")
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

    private fun applyWeatherTheme(condition: String) {
        visualTheme = WeatherThemeResolver.resolve(condition)
        binding.weatherDetailScroll.background = gradient(
            colors = intArrayOf(color(visualTheme.pageStart), color(visualTheme.pageEnd)),
        )
        binding.weatherHero.background = gradient(
            colors = intArrayOf(
                color(visualTheme.heroStart),
                color(visualTheme.heroMiddle),
                color(visualTheme.heroEnd),
            ),
            cornerRadiusDp = 14f,
            strokeColor = color(visualTheme.outline),
        )
        val surfaceColor = color(visualTheme.surface)
        binding.hourlyCard.setCardBackgroundColor(surfaceColor)
        binding.futureCard.setCardBackgroundColor(surfaceColor)
        binding.weatherMetaCard.setCardBackgroundColor(surfaceColor)
        binding.tvCondition.setTextColor(color(visualTheme.accent))
        binding.tvDate.setTextColor(color(visualTheme.secondaryText))
        binding.tvHighLow.setTextColor(color(visualTheme.secondaryText))
    }

    private fun gradient(
        colors: IntArray,
        cornerRadiusDp: Float = 0f,
        strokeColor: Int? = null,
    ): GradientDrawable = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
        cornerRadius = cornerRadiusDp * resources.displayMetrics.density
        strokeColor?.let { setStroke(resources.displayMetrics.density.toInt().coerceAtLeast(1), it) }
    }

    private fun color(@ColorRes colorRes: Int): Int = ContextCompat.getColor(this, colorRes)

    companion object {
        private const val REQUEST_MANAGE_CITIES = 2101
        private const val CITY_HINT_DELAY_MS = 350L
    }
}
