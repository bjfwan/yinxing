package com.yinxing.launcher.feature.weather

import androidx.annotation.ColorRes
import com.yinxing.launcher.R

enum class WeatherThemeKind {
    SUNNY,
    CLOUDY,
    RAIN,
    STORM,
    SNOW,
    FOG,
}

data class WeatherVisualTheme(
    val kind: WeatherThemeKind,
    @field:ColorRes val pageStart: Int,
    @field:ColorRes val pageEnd: Int,
    @field:ColorRes val heroStart: Int,
    @field:ColorRes val heroMiddle: Int,
    @field:ColorRes val heroEnd: Int,
    @field:ColorRes val surface: Int,
    @field:ColorRes val currentStart: Int,
    @field:ColorRes val currentEnd: Int,
    @field:ColorRes val accent: Int,
    @field:ColorRes val secondaryText: Int,
    @field:ColorRes val outline: Int,
)

object WeatherThemeResolver {
    fun resolve(condition: String): WeatherVisualTheme = when {
        condition.contains("雷") -> storm
        condition.contains("雪") || condition.contains("冰雹") -> snow
        condition.contains("雾") || condition.contains("霾") || condition.contains("沙尘") -> fog
        condition.contains("雨") -> rain
        condition.contains("多云") || condition.contains("阴") -> cloudy
        condition.contains("晴") -> sunny
        else -> cloudy
    }

    private val sunny = WeatherVisualTheme(
        kind = WeatherThemeKind.SUNNY,
        pageStart = R.color.weather_sunny_page_start,
        pageEnd = R.color.weather_sunny_page_end,
        heroStart = R.color.weather_sunny_hero_start,
        heroMiddle = R.color.weather_sunny_hero_middle,
        heroEnd = R.color.weather_sunny_hero_end,
        surface = R.color.weather_sunny_surface,
        currentStart = R.color.weather_sunny_current_start,
        currentEnd = R.color.weather_sunny_current_end,
        accent = R.color.weather_sunny_accent,
        secondaryText = R.color.weather_sunny_secondary_text,
        outline = R.color.weather_sunny_outline,
    )

    private val cloudy = WeatherVisualTheme(
        kind = WeatherThemeKind.CLOUDY,
        pageStart = R.color.weather_cloudy_page_start,
        pageEnd = R.color.weather_cloudy_page_end,
        heroStart = R.color.weather_cloudy_hero_start,
        heroMiddle = R.color.weather_cloudy_hero_middle,
        heroEnd = R.color.weather_cloudy_hero_end,
        surface = R.color.weather_cloudy_surface,
        currentStart = R.color.weather_cloudy_current_start,
        currentEnd = R.color.weather_cloudy_current_end,
        accent = R.color.weather_cloudy_accent,
        secondaryText = R.color.weather_cloudy_secondary_text,
        outline = R.color.weather_cloudy_outline,
    )

    private val rain = WeatherVisualTheme(
        kind = WeatherThemeKind.RAIN,
        pageStart = R.color.weather_rain_page_start,
        pageEnd = R.color.weather_rain_page_end,
        heroStart = R.color.weather_rain_hero_start,
        heroMiddle = R.color.weather_rain_hero_middle,
        heroEnd = R.color.weather_rain_hero_end,
        surface = R.color.weather_rain_surface,
        currentStart = R.color.weather_rain_current_start,
        currentEnd = R.color.weather_rain_current_end,
        accent = R.color.weather_rain_accent,
        secondaryText = R.color.weather_rain_secondary_text,
        outline = R.color.weather_rain_outline,
    )

    private val storm = WeatherVisualTheme(
        kind = WeatherThemeKind.STORM,
        pageStart = R.color.weather_storm_page_start,
        pageEnd = R.color.weather_storm_page_end,
        heroStart = R.color.weather_storm_hero_start,
        heroMiddle = R.color.weather_storm_hero_middle,
        heroEnd = R.color.weather_storm_hero_end,
        surface = R.color.weather_storm_surface,
        currentStart = R.color.weather_storm_current_start,
        currentEnd = R.color.weather_storm_current_end,
        accent = R.color.weather_storm_accent,
        secondaryText = R.color.weather_storm_secondary_text,
        outline = R.color.weather_storm_outline,
    )

    private val snow = WeatherVisualTheme(
        kind = WeatherThemeKind.SNOW,
        pageStart = R.color.weather_snow_page_start,
        pageEnd = R.color.weather_snow_page_end,
        heroStart = R.color.weather_snow_hero_start,
        heroMiddle = R.color.weather_snow_hero_middle,
        heroEnd = R.color.weather_snow_hero_end,
        surface = R.color.weather_snow_surface,
        currentStart = R.color.weather_snow_current_start,
        currentEnd = R.color.weather_snow_current_end,
        accent = R.color.weather_snow_accent,
        secondaryText = R.color.weather_snow_secondary_text,
        outline = R.color.weather_snow_outline,
    )

    private val fog = WeatherVisualTheme(
        kind = WeatherThemeKind.FOG,
        pageStart = R.color.weather_fog_page_start,
        pageEnd = R.color.weather_fog_page_end,
        heroStart = R.color.weather_fog_hero_start,
        heroMiddle = R.color.weather_fog_hero_middle,
        heroEnd = R.color.weather_fog_hero_end,
        surface = R.color.weather_fog_surface,
        currentStart = R.color.weather_fog_current_start,
        currentEnd = R.color.weather_fog_current_end,
        accent = R.color.weather_fog_accent,
        secondaryText = R.color.weather_fog_secondary_text,
        outline = R.color.weather_fog_outline,
    )
}
