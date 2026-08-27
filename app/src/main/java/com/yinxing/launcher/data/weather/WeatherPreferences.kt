package com.yinxing.launcher.data.weather

import android.content.Context
import android.content.SharedPreferences

data class SavedWeatherLocation(
    val cityName: String,
    val latitude: Double,
    val longitude: Double,
)

class WeatherPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)

    init {
        WeatherRepository.initialize(context)
    }

    companion object {
        private const val KEY_CITY = "city_name"
        private const val KEY_SAVED_CITIES = "saved_cities"
        private const val CITY_SEPARATOR = "\u001F"
        private const val DEFAULT_CITY = "北京"
        private const val KEY_LOCATION_CITY = "location_city"
        private const val KEY_LOCATION_LATITUDE = "location_latitude"
        private const val KEY_LOCATION_LONGITUDE = "location_longitude"
        private const val KEY_INITIAL_LOCATION_PERMISSION_REQUESTED =
            "initial_location_permission_requested"

        @Volatile
        private var instance: WeatherPreferences? = null

        fun getInstance(context: Context): WeatherPreferences =
            instance ?: synchronized(this) {
                instance ?: WeatherPreferences(context.applicationContext).also { instance = it }
            }

        internal fun resetForTest() {
            instance = null
        }
    }

    fun getCityName(): String {
        return prefs.getString(KEY_CITY, DEFAULT_CITY)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_CITY
    }

    fun setCityName(city: String) {
        val normalized = normalizeCity(city)
        val cities = getSavedCities().filterNot { it == normalized }.toMutableList()
        cities.add(0, normalized)
        prefs.edit()
            .putString(KEY_CITY, normalized)
            .putString(KEY_SAVED_CITIES, cities.joinToString(CITY_SEPARATOR))
            .apply()
    }

    fun getSavedCities(): List<String> {
        val current = getCityName()
        val stored = prefs.getString(KEY_SAVED_CITIES, null)
            ?.split(CITY_SEPARATOR)
            ?.map(::normalizeCity)
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()
        return buildList {
            add(current)
            addAll(stored.filterNot { it == current })
        }
    }

    fun addCity(city: String) {
        val normalized = normalizeCity(city)
        val cities = getSavedCities().toMutableList()
        if (normalized !in cities) cities.add(normalized)
        prefs.edit().putString(KEY_SAVED_CITIES, cities.joinToString(CITY_SEPARATOR)).apply()
    }

    fun setCurrentLocation(city: String, latitude: Double, longitude: Double) {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
        val normalized = normalizeCity(city)
        setCityName(normalized)
        prefs.edit()
            .putString(KEY_LOCATION_CITY, normalized)
            .putString(KEY_LOCATION_LATITUDE, latitude.toString())
            .putString(KEY_LOCATION_LONGITUDE, longitude.toString())
            .apply()
    }

    fun getSelectedLocation(): SavedWeatherLocation? {
        val locationCity = prefs.getString(KEY_LOCATION_CITY, null) ?: return null
        if (locationCity != getCityName()) return null
        val latitude = prefs.getString(KEY_LOCATION_LATITUDE, null)?.toDoubleOrNull() ?: return null
        val longitude = prefs.getString(KEY_LOCATION_LONGITUDE, null)?.toDoubleOrNull() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return SavedWeatherLocation(locationCity, latitude, longitude)
    }

    fun removeCity(city: String) {
        val normalized = normalizeCity(city)
        val current = getCityName()
        val remaining = getSavedCities().filterNot { it == normalized }.toMutableList()
        if (remaining.isEmpty()) remaining.add(DEFAULT_CITY)
        val nextCurrent = if (current == normalized) remaining.first() else current
        remaining.remove(nextCurrent)
        remaining.add(0, nextCurrent)
        val editor = prefs.edit()
            .putString(KEY_CITY, nextCurrent)
            .putString(KEY_SAVED_CITIES, remaining.joinToString(CITY_SEPARATOR))
        if (prefs.getString(KEY_LOCATION_CITY, null) == normalized) {
            editor
                .remove(KEY_LOCATION_CITY)
                .remove(KEY_LOCATION_LATITUDE)
                .remove(KEY_LOCATION_LONGITUDE)
        }
        editor.apply()
    }

    fun hasCity(): Boolean = prefs.contains(KEY_CITY)

    fun wasInitialLocationPermissionRequested(): Boolean =
        prefs.getBoolean(KEY_INITIAL_LOCATION_PERMISSION_REQUESTED, false)

    fun markInitialLocationPermissionRequested() {
        prefs.edit().putBoolean(KEY_INITIAL_LOCATION_PERMISSION_REQUESTED, true).apply()
    }

    private fun normalizeCity(city: String): String = city
        .replace(CITY_SEPARATOR, "")
        .trim()
        .ifEmpty { DEFAULT_CITY }
}
