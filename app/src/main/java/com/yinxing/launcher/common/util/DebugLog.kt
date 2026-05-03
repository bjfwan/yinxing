package com.yinxing.launcher.common.util

import android.util.Log
import com.yinxing.launcher.BuildConfig

/**
 * 仅在 Debug 构建里输出装饰性诊断日志。
 *
 * Release 构建中所有 [banner] / [d] 调用都会被 JIT 短路，
 * 避免装饰框图、Unicode 排版和长字符串拼接污染线上 logcat 与 [com.yinxing.launcher.common.lobster.LobsterClient] 上报缓冲。
 */
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
        if (throwable == null) {
            Log.w(tag, message)
        } else {
            Log.w(tag, message, throwable)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.e(tag, message)
        } else {
            Log.e(tag, message, throwable)
        }
    }

    /**
     * 输出装饰框图日志，仅在 Debug 构建生效。Release 构建里整体短路，零运行时成本。
     */
    fun banner(tag: String, lines: List<String>) {
        if (!BuildConfig.DEBUG || lines.isEmpty()) {
            return
        }
        Log.i(tag, BANNER_TOP)
        lines.forEach { Log.i(tag, "$LINE_PREFIX$it") }
        Log.i(tag, BANNER_BOTTOM)
    }
}
