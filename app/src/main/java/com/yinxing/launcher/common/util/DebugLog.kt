package com.yinxing.launcher.common.util

import android.util.Log
import com.yinxing.launcher.BuildConfig

object DebugLog {
    private const val BANNER_TOP = "╔══════════════════════════════════════════════════════"
    private const val BANNER_BOTTOM = "╚══════════════════════════════════════════════════════"
    private const val LINE_PREFIX = "║ "

    inline fun d(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message())
        }
    }

    inline fun i(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message())
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (!BuildConfig.DEBUG) return
        if (throwable == null) {
            Log.w(tag, message)
        } else {
            Log.w(tag, message, throwable)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (!BuildConfig.DEBUG) return
        if (throwable == null) {
            Log.e(tag, message)
        } else {
            Log.e(tag, message, throwable)
        }
    }

    fun banner(tag: String, lines: List<String>) {
        if (!BuildConfig.DEBUG || lines.isEmpty()) {
            return
        }
        Log.i(tag, BANNER_TOP)
        lines.forEach { Log.i(tag, "$LINE_PREFIX$it") }
        Log.i(tag, BANNER_BOTTOM)
    }
}
