package com.yinxing.launcher.feature.weather

import com.yinxing.launcher.data.weather.WeatherForecastDay
import com.yinxing.launcher.data.weather.WeatherHour
import com.yinxing.launcher.data.weather.WeatherState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class WeatherDetailUi(
    val city: String,
    val date: String,
    val temperature: String,
    val condition: String,
    val highLow: String,
    val hottestAdvice: String,
    val rainAdvice: String,
    val hours: List<WeatherHourUi>,
    val days: List<WeatherDayUi>,
    val windAndHumidity: String,
    val updateAt: String
)

data class WeatherHourUi(
    val label: String,
    val time: String,
    val condition: String,
    val temperature: String
)

data class WeatherDayUi(
    val relativeDay: String,
    val weekday: String,
    val condition: String,
    val high: String,
    val low: String
)

object WeatherDetailPresenter {
    private val relativeDays = listOf("明天", "后天", "大后天")

    fun present(state: WeatherState): WeatherDetailUi {
        val now = requireNotNull(state.now)
        val today = state.forecast.firstOrNull()
        val todayHours = today?.date?.let { date ->
            state.hourly.filter { it.time.startsWith(date) }
        }.orEmpty()
        val hours = selectHours(todayHours, now.updateTime)
        val hottest = todayHours.maxByOrNull { it.temperature }
        val hottestAdvice = hottest?.let {
            "${dayPart(hourOf(it.time))}${displayHour(hourOf(it.time))}点最热"
        }.orEmpty()
        val maxRain = todayHours.maxOfOrNull { it.precipitationProbability } ?: 0
        val rainAdvice = when {
            todayHours.isEmpty() -> ""
            maxRain >= 50 -> "今天建议带伞"
            maxRain >= 30 -> "今天可能下雨"
            else -> "今天不用带伞"
        }
        return WeatherDetailUi(
            city = state.cityName,
            date = today?.date?.let(::formatTodayDate).orEmpty(),
            temperature = "${now.temperature}°",
            condition = now.weather,
            highLow = today?.let { "最高${it.high}° · 最低${it.low}°" }.orEmpty(),
            hottestAdvice = hottestAdvice,
            rainAdvice = rainAdvice,
            hours = hours,
            days = state.forecast.drop(1).take(3).mapIndexed(::formatForecastDay),
            windAndHumidity = "${now.windDirection}${now.windPower} · 湿度${now.humidity}%",
            updateAt = "更新于${now.updateTime} · Open-Meteo"
        )
    }

    private fun selectHours(hours: List<WeatherHour>, updateTime: String): List<WeatherHourUi> {
        if (hours.isEmpty()) return emptyList()
        val currentHour = updateTime.substringBefore(':').toIntOrNull() ?: hourOf(hours.first().time)
        val requested = listOf(currentHour, 12, 15, 20).distinct()
        val selected = requested.mapNotNull { target ->
            hours.minByOrNull { kotlin.math.abs(hourOf(it.time) - target) }
        }.distinctBy { it.time }.toMutableList()
        hours.filter { hourOf(it.time) >= currentHour }.forEach { hour ->
            if (selected.size < 4 && selected.none { it.time == hour.time }) selected += hour
        }
        return selected.take(4).mapIndexed { index, hour ->
            WeatherHourUi(
                label = if (index == 0) "现在" else dayPart(hourOf(hour.time)),
                time = hour.time.substringAfter('T').take(5),
                condition = hour.weather,
                temperature = "${hour.temperature}°"
            )
        }
    }

    private fun formatForecastDay(index: Int, day: WeatherForecastDay) = WeatherDayUi(
        relativeDay = relativeDays[index],
        weekday = weekday(day.date),
        condition = day.textDay,
        high = "${day.high}°",
        low = "${day.low}°"
    )

    private fun formatTodayDate(date: String): String {
        val calendar = parseDate(date) ?: return "今天"
        return "今天 ${calendar.get(Calendar.MONTH) + 1}月${calendar.get(Calendar.DAY_OF_MONTH)}日 ${weekday(calendar)}"
    }

    private fun weekday(date: String): String = parseDate(date)?.let(::weekday).orEmpty()

    private fun weekday(calendar: Calendar): String = when (calendar.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "周一"
        Calendar.TUESDAY -> "周二"
        Calendar.WEDNESDAY -> "周三"
        Calendar.THURSDAY -> "周四"
        Calendar.FRIDAY -> "周五"
        Calendar.SATURDAY -> "周六"
        else -> "周日"
    }

    private fun parseDate(date: String): Calendar? = runCatching {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).apply { isLenient = false }.parse(date)
            ?: return null
        Calendar.getInstance().apply { time = parsed }
    }.getOrNull()

    private fun hourOf(time: String): Int = time.substringAfter('T').substringBefore(':').toIntOrNull() ?: 0

    private fun displayHour(hour: Int): Int = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }

    private fun dayPart(hour: Int): String = when (hour) {
        in 0..5 -> "凌晨"
        in 6..10 -> "上午"
        in 11..13 -> "中午"
        in 14..17 -> "下午"
        else -> "晚上"
    }
}
