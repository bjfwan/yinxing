package com.yinxing.launcher.feature.weather

import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.addCallback
import com.yinxing.launcher.common.ui.FontScaleActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.yinxing.launcher.R
import com.yinxing.launcher.common.ui.LauncherDialogFactory
import com.yinxing.launcher.data.weather.WeatherPreferences
import com.yinxing.launcher.data.weather.WeatherLocationResolver
import com.yinxing.launcher.data.weather.WeatherRepository
import com.yinxing.launcher.data.weather.WeatherState
import com.yinxing.launcher.databinding.ActivityWeatherCityManagerBinding
import com.yinxing.launcher.databinding.DialogWeatherCitySearchBinding
import com.yinxing.launcher.databinding.ItemWeatherCityBinding
import kotlinx.coroutines.launch

class WeatherCityManagerActivity : FontScaleActivity() {
    private lateinit var binding: ActivityWeatherCityManagerBinding
    private val preferences by lazy { WeatherPreferences.getInstance(this) }
    private val weatherStates = mutableMapOf<String, WeatherState>()
    private var managementMode = false
    private var selectionChanged = false
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) locateCurrentCity() else showLocationFailure(R.string.weather_location_denied)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWeatherCityManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemInsets()
        binding.btnBack.setOnClickListener { finishWithResult() }
        binding.btnManage.setOnClickListener {
            managementMode = !managementMode
            renderCities()
        }
        binding.btnSearchCity.setOnClickListener { showCitySearchDialog() }
        binding.btnUseCurrentLocation.setOnClickListener { requestOrLocateCurrentCity() }
        onBackPressedDispatcher.addCallback(this) { finishWithResult() }
        renderCities()
        loadSavedCities()
    }

    private fun applySystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.weatherCityRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.weatherCityRoot)
    }

    private fun loadSavedCities() {
        preferences.getSavedCities().forEach { city ->
            lifecycleScope.launch {
                val location = preferences.getSelectedLocation()?.takeIf { it.cityName == city }
                weatherStates[city] = if (location != null) {
                    WeatherRepository.fetchWeatherAtLocation(
                        city,
                        location.latitude,
                        location.longitude,
                        this@WeatherCityManagerActivity,
                    )
                } else {
                    WeatherRepository.fetchWeather(city, this@WeatherCityManagerActivity)
                }
                renderCities()
            }
        }
    }

    private fun requestOrLocateCurrentCity() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            locateCurrentCity()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private fun locateCurrentCity() {
        binding.btnUseCurrentLocation.isEnabled = false
        binding.tvUseCurrentLocation.setText(R.string.weather_city_locating)
        lifecycleScope.launch {
            val location = WeatherLocationResolver.resolve(this@WeatherCityManagerActivity)
            if (location == null) {
                showLocationFailure(R.string.weather_location_failed)
                return@launch
            }
            val state = WeatherRepository.fetchWeatherAtLocation(
                location.cityName,
                location.latitude,
                location.longitude,
                this@WeatherCityManagerActivity,
            )
            if (state.now == null) {
                showLocationFailure(R.string.weather_location_failed)
                return@launch
            }
            preferences.setCurrentLocation(location.cityName, location.latitude, location.longitude)
            weatherStates[location.cityName] = state
            selectionChanged = true
            Toast.makeText(
                this@WeatherCityManagerActivity,
                getString(R.string.weather_location_success, location.cityName),
                Toast.LENGTH_SHORT,
            ).show()
            finishWithResult()
        }
    }

    private fun showLocationFailure(message: Int) {
        binding.btnUseCurrentLocation.isEnabled = true
        binding.tvUseCurrentLocation.setText(R.string.weather_city_use_current_location)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun renderCities() {
        binding.btnManage.setText(if (managementMode) R.string.weather_city_done else R.string.weather_city_manage)
        binding.cityListContainer.removeAllViews()
        val currentCity = preferences.getCityName()
        preferences.getSavedCities().forEach { city ->
            val card = ItemWeatherCityBinding.inflate(layoutInflater, binding.cityListContainer, false)
            val ui = WeatherCityCardPresenter.present(
                weatherStates[city] ?: WeatherState.Loading(city),
                city == currentCity,
            )
            card.tvCityName.text = ui.city
            card.tvTemperature.text = ui.temperature
            card.tvCondition.text = ui.condition
            card.tvHighLow.text = ui.highLow
            card.tvUpdateTime.text = ui.updateTime
            card.tvCurrentBadge.visibility = if (ui.isCurrent) View.VISIBLE else View.GONE
            card.btnDelete.visibility = if (managementMode) View.VISIBLE else View.GONE
            card.tvHighLow.visibility = if (managementMode) View.GONE else View.VISIBLE
            card.cityCard.contentDescription = if (ui.isCurrent) {
                getString(R.string.weather_city_card_current_description, ui.city)
            } else {
                getString(R.string.weather_city_card_switch_description, ui.city)
            }
            card.cityCard.setOnClickListener {
                if (!managementMode) selectCity(city)
            }
            card.btnDelete.setOnClickListener {
                preferences.removeCity(city)
                weatherStates.remove(city)
                selectionChanged = true
                renderCities()
            }
            card.root.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.weather_city_card_height),
            ).apply {
                bottomMargin = resources.getDimensionPixelSize(R.dimen.weather_city_card_gap)
            }
            binding.cityListContainer.addView(card.root)
        }
    }

    private fun selectCity(city: String) {
        preferences.setCityName(city)
        selectionChanged = true
        Toast.makeText(this, getString(R.string.weather_city_selected, city), Toast.LENGTH_SHORT).show()
        finishWithResult()
    }

    private fun showCitySearchDialog() {
        val dialogBinding = DialogWeatherCitySearchBinding.inflate(layoutInflater)
        val dialog = LauncherDialogFactory.create(
            context = this,
            contentView = dialogBinding.root,
            dismissOnTouchOutside = false
        ) {
            dialogBinding.etCitySearch.requestFocus()
            it.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }

        fun search() {
            val city = dialogBinding.etCitySearch.text.toString().trim()
            if (city.isBlank()) {
                dialogBinding.tvSearchStatus.setText(R.string.weather_city_not_found)
                return
            }
            if (city !in preferences.getSavedCities() && preferences.getSavedCities().size >= MAX_CITIES) {
                dialogBinding.tvSearchStatus.setText(R.string.weather_city_limit)
                return
            }
            dialogBinding.btnSearch.isEnabled = false
            dialogBinding.tvSearchStatus.setText(R.string.weather_city_searching)
            lifecycleScope.launch {
                val state = WeatherRepository.fetchWeather(city, this@WeatherCityManagerActivity)
                if (state.now != null) {
                    preferences.addCity(city)
                    weatherStates[city] = state
                    selectionChanged = true
                    renderCities()
                    dialog.dismiss()
                    Toast.makeText(
                        this@WeatherCityManagerActivity,
                        getString(R.string.weather_city_added, city),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    dialogBinding.tvSearchStatus.setText(R.string.weather_city_not_found)
                    dialogBinding.btnSearch.isEnabled = true
                }
            }
        }

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnSearch.setOnClickListener { search() }
        dialogBinding.etCitySearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search()
                true
            } else {
                false
            }
        }
        dialog.show()
    }

    private fun finishWithResult() {
        if (selectionChanged) setResult(Activity.RESULT_OK)
        finish()
    }

    companion object {
        private const val MAX_CITIES = 6
    }
}
