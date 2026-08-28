package com.yinxing.launcher.common.lobster

object LobsterAnrEventFactory {
    private const val MAX_APP_FRAMES = 30
    private const val MAX_FALLBACK_FRAMES = 15
    private const val APP_PACKAGE_PREFIX = "com.yinxing.launcher"

    fun from(frames: List<StackTraceElement>): LobsterUsageEvent {
        val appFrames = frames.filter { it.className.startsWith(APP_PACKAGE_PREFIX) }
        val selectedFrames = if (appFrames.isNotEmpty()) {
            appFrames.take(MAX_APP_FRAMES)
        } else {
            frames.take(MAX_FALLBACK_FRAMES)
        }
        val logLine = buildString {
            append("[ANR] 主线程超过 8 秒未响应")
            selectedFrames.forEach { frame ->
                append("\nat ").append(frame)
            }
        }

        return LobsterUsageEvent(
            scene = "主线程卡死",
            status = LobsterReportStatus.ERROR,
            summary = "主线程超过 8 秒未响应",
            logLine = logLine,
            details = LobsterReportDetails(
                errorCode = "MAIN_THREAD_STALLED",
                failedStep = "main_thread"
            ),
            category = LobsterLogCategory.SYSTEM,
            eventType = LobsterEventType.ERROR,
            action = "detect_main_thread_stall"
        )
    }
}
