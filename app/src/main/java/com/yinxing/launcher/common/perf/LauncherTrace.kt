package com.yinxing.launcher.common.perf

import android.content.Context
import android.os.Trace
import com.yinxing.launcher.common.lobster.LobsterClient
import java.util.concurrent.ConcurrentHashMap

object LauncherTraceNames {
    const val APP_INIT = "oldlauncher.app.init"
    const val HOME_APP_LIST_LOAD = "oldlauncher.home.app_list.load"
    const val HOME_ICON_LOAD = "oldlauncher.home.icon.load"
    const val HOME_APP_LAUNCH = "oldlauncher.home.app_launch"
    const val HOME_WEATHER_REQUEST = "oldlauncher.home.weather.request"
    const val WECHAT_VIDEO_TOTAL = "oldlauncher.wechat.video.total"
    const val WECHAT_VIDEO_FAILURE_TOTAL = "oldlauncher.wechat.video.failure.total"
    const val INCOMING_CALL_RESPONSE = "oldlauncher.incoming.call.response"
    const val PHONE_PLACE_CALL = "oldlauncher.phone.place_call"
}

private val pendingTraces = ConcurrentHashMap<String, Long>()

fun traceBegin(name: String) {
    pendingTraces[name] = System.currentTimeMillis()
    try { Trace.beginSection(name) } catch (_: Throwable) {}
}

fun traceEnd(name: String): Long? {
    try { Trace.endSection() } catch (_: Throwable) {}
    val startMs = pendingTraces.remove(name) ?: return null
    return System.currentTimeMillis() - startMs
}

inline fun <T> traceSection(name: String, block: () -> T): T {
    try { Trace.beginSection(name) } catch (_: Throwable) {}
    return try {
        block()
    } finally {
        try { Trace.endSection() } catch (_: Throwable) {}
    }
}

fun traceAndReport(context: Context, name: String, traceId: String? = null) {
    val durationMs = traceEnd(name) ?: return
    LobsterClient.reportMetrics(context, listOf(name to durationMs), traceId)
}

fun reportCollectedMetrics(context: Context, names: List<String>, traceId: String? = null) {
    val metrics = mutableListOf<Pair<String, Long>>()
    for (name in names) {
        val durationMs = traceEnd(name) ?: continue
        metrics.add(name to durationMs)
    }
    LobsterClient.reportMetrics(context, metrics, traceId)
}
