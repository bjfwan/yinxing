package com.yinxing.launcher.common.lobster

enum class MainThreadStallSample {
    ACTIONABLE,
    RECOVERED_IDLE,
    WATCHDOG_SELF,
}

object MainThreadStallSampleClassifier {
    private const val APP_PACKAGE_PREFIX = "com.yinxing.launcher"
    private const val WATCHDOG_CLASS = "com.yinxing.launcher.common.lobster.LobsterMainThreadWatchdog"

    fun classify(frames: List<StackTraceElement>): MainThreadStallSample {
        if (frames.isEmpty()) return MainThreadStallSample.RECOVERED_IDLE
        val appFrames = frames.filter { it.className.startsWith(APP_PACKAGE_PREFIX) }
        if (appFrames.any { !it.className.startsWith(WATCHDOG_CLASS) }) {
            return MainThreadStallSample.ACTIONABLE
        }
        if (appFrames.isNotEmpty() || frames.any { it.className.startsWith(WATCHDOG_CLASS) }) {
            return MainThreadStallSample.WATCHDOG_SELF
        }
        val top = frames.first()
        if (top.className == "android.os.MessageQueue" &&
            (top.methodName == "nativePollOnce" || top.methodName == "next")
        ) {
            return MainThreadStallSample.RECOVERED_IDLE
        }
        return MainThreadStallSample.ACTIONABLE
    }
}

object LobsterAnrEventFactory {
    private const val MAX_APP_FRAMES = 30
    private const val MAX_FALLBACK_FRAMES = 15
    private const val APP_PACKAGE_PREFIX = "com.yinxing.launcher"

    fun from(frames: List<StackTraceElement>, stallDurationMs: Long = 8_000L): LobsterUsageEvent {
        val safeDurationMs = stallDurationMs.coerceIn(0L, 86_400_000L)
        val appFrames = frames.filter { it.className.startsWith(APP_PACKAGE_PREFIX) }
        val selectedFrames = if (appFrames.isNotEmpty()) {
            appFrames.take(MAX_APP_FRAMES)
        } else {
            frames.take(MAX_FALLBACK_FRAMES)
        }
        val logLine = buildString {
            append("[ANR] 主线程未响应 duration_ms=").append(safeDurationMs)
            selectedFrames.forEach { frame ->
                append("\nat ").append(frame)
            }
        }

        return LobsterUsageEvent(
            scene = "主线程卡死",
            status = LobsterReportStatus.ERROR,
            summary = "主线程持续 ${safeDurationMs}ms 未响应",
            logLine = logLine,
            details = LobsterReportDetails(
                errorCode = "MAIN_THREAD_STALLED",
                failedStep = "main_thread",
                stallDurationMs = safeDurationMs,
            ),
            category = LobsterLogCategory.SYSTEM,
            eventType = LobsterEventType.ERROR,
            action = "detect_main_thread_stall"
        )
    }
}
