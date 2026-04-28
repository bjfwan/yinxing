package com.yinxing.launcher.feature.home

import android.os.Build
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TimeTicker(
    private val calendarProvider: () -> Calendar = { Calendar.getInstance() }
) {
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINA)
    private val dateFormat = SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.CHINA)

    suspend fun run(onTick: (TimeSnapshot) -> Unit) {
        while (currentCoroutineContext().isActive) {
            onTick(snapshot())
            delay(nextMinuteDelay())
        }
    }

    fun snapshot(): TimeSnapshot {
        val now = calendarProvider()
        return TimeSnapshot(
            timeText = timeFormat.format(now.time),
            dateText = dateFormat.format(now.time),
            lunarText = buildLunarDateString(now),
            dayKey = now.get(Calendar.YEAR) * 1000 + now.get(Calendar.DAY_OF_YEAR)
        )
    }

    private fun nextMinuteDelay(): Long {
        val now = System.currentTimeMillis()
        val delay = 60_000L - (now % 60_000L)
        return if (delay == 0L) 60_000L else delay
    }

    private fun buildLunarDateString(cal: Calendar): String {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                ""
            } else {
                val chinese = android.icu.util.ChineseCalendar().apply {
                    timeInMillis = cal.timeInMillis
                }
                val lunarMonth = chinese.get(android.icu.util.ChineseCalendar.MONTH) + 1
                val lunarDay = chinese.get(android.icu.util.ChineseCalendar.DAY_OF_MONTH)
                val monthText = (if (chinese.get(android.icu.util.ChineseCalendar.IS_LEAP_MONTH) == 1) "闰" else "") +
                    (lunarMonthNames.getOrNull(lunarMonth) ?: lunarMonth.toString()) + "月"
                val dayText = lunarDayNames.getOrNull(lunarDay) ?: lunarDay.toString()
                "农历 $monthText$dayText"
            }
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        private val lunarMonthNames = arrayOf("", "正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊")
        private val lunarDayNames = arrayOf(
            "", "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
            "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
            "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
        )
    }
}

data class TimeSnapshot(
    val timeText: String,
    val dateText: String,
    val lunarText: String,
    val dayKey: Int
)
