package com.yinxing.launcher.common.perf

import android.os.Trace

object LauncherTraceNames {
    const val APP_INIT = "oldlauncher.app.init"
    const val HOME_APP_LIST_LOAD = "oldlauncher.home.app_list.load"
    const val HOME_ICON_LOAD = "oldlauncher.home.icon.load"
    const val HOME_WEATHER_REQUEST = "oldlauncher.home.weather.request"
}

inline fun <T> traceSection(name: String, block: () -> T): T {
    Trace.beginSection(name)
    return try {
        block()
    } finally {
        Trace.endSection()
    }
}
