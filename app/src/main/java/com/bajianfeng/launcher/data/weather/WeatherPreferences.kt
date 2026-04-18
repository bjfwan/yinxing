package com.bajianfeng.launcher.data.weather

import android.content.Context
import android.content.SharedPreferences

class WeatherPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CITY = "city_name"
        private const val DEFAULT_CITY = "北京"

        @Volatile
        private var instance: WeatherPreferences? = null

        fun getInstance(context: Context): WeatherPreferences =
            instance ?: synchronized(this) {
                instance ?: WeatherPreferences(context.applicationContext).also { instance = it }
            }
    }

    fun getCityName(): String = prefs.getString(KEY_CITY, DEFAULT_CITY) ?: DEFAULT_CITY

    fun setCityName(city: String) {
        prefs.edit().putString(KEY_CITY, city.trim()).apply()
    }

    fun hasCity(): Boolean = prefs.contains(KEY_CITY)
}
