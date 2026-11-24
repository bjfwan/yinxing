package com.yinxing.launcher.common.lobster

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 应用启动时尝试发送的轻量心跳，不提供后台每日调度。
 * 只表示这次启动曾尝试上报，不能据此判断卸载或跨重装的物理设备。
 *
 * 规则：
 * - 同一 UTC 日最多尝试一次；应用未启动时不会补发每日心跳；
 * - 先记日期再检查开关，避免同日反复尝试；
 * - 允许上传时复用普通事件队列和补传策略，不单独补齐缺失日期。
 */
object LobsterHeartbeat {
    private const val PREFS_NAME = "lobster_client"
    private const val KEY_LAST_DAY = "last_heartbeat_day"

    fun shouldReport(storedDay: String?, currentDay: String): Boolean =
        storedDay?.trim() != currentDay

    fun maybeReport(
        context: Context,
        nowMs: Long = System.currentTimeMillis(),
        isReportingAllowed: (Context) -> Boolean = LobsterRuntimePolicy::shouldUpload,
        report: (Context) -> Unit = { LobsterClient.reportUsage(it, LobsterUsageEvents.HEARTBEAT) }
    ) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentDay = utcDay(nowMs)
        if (!shouldReport(prefs.getString(KEY_LAST_DAY, null), currentDay)) return
        prefs.edit().putString(KEY_LAST_DAY, currentDay).apply()
        if (!isReportingAllowed(appContext)) return
        report(appContext)
    }

    fun utcDay(epochMs: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(epochMs))
}
