package com.yinxing.launcher.common.lobster

enum class MainThreadStallSample {
    ACTIONABLE,
    RECOVERED_IDLE,
    WATCHDOG_SELF,
}

/**
 * 「本应用帧」判定的唯一入口：栈采集（[LobsterAnrEventFactory]）与样本分类
 * （[MainThreadStallSampleClassifier]）必须共用同一份判定，否则会出现
 * 「分类判 ACTIONABLE、日志里却只剩系统帧」的不一致（线上 2.5.0 的 MAIN_THREAD_STALLED
 * 误报正是这样来的：允许列表漏掉了 vendored 无障碍服务里的帧）。
 *
 * 判定采用**排除法**：平台帧一律不算本应用帧，其余全算。
 * 这样 com.yinxing.launcher.* 与 vendored 的 com.google.android.accessibility.*
 * （微信自动化执行层）天然命中，未来再有 vendored 包也不用记得改这里。
 */
object MainThreadStallFramePolicy {
    /** 平台帧前缀；命中任一前缀的帧不算本应用帧。 */
    private val PLATFORM_CLASS_PREFIXES = listOf(
        "android.",
        "java.",
        "kotlin.",
        "libcore.",
        "dalvik.",
        "com.android.",
        "sun.",
        "org.apache.",
    )

    const val APP_PACKAGE_PREFIX = "com.yinxing.launcher"
    const val ACCESSIBILITY_PACKAGE_PREFIX = "com.google.android.accessibility."
    const val WATCHDOG_CLASS = "com.yinxing.launcher.common.lobster.LobsterMainThreadWatchdog"

    /** 排除平台前缀后的「本应用帧」判定。 */
    fun isAppFrame(frame: StackTraceElement): Boolean =
        PLATFORM_CLASS_PREFIXES.none(frame.className::startsWith)

    fun isWatchdogFrame(frame: StackTraceElement): Boolean =
        frame.className == WATCHDOG_CLASS
}

object MainThreadStallSampleClassifier {
    fun classify(frames: List<StackTraceElement>): MainThreadStallSample {
        if (frames.isEmpty()) return MainThreadStallSample.RECOVERED_IDLE
        val appFrames = frames.filter(MainThreadStallFramePolicy::isAppFrame)
        if (appFrames.any { !MainThreadStallFramePolicy.isWatchdogFrame(it) }) {
            return MainThreadStallSample.ACTIONABLE
        }
        if (appFrames.isNotEmpty() ||
            frames.any(MainThreadStallFramePolicy::isWatchdogFrame)
        ) {
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
    private const val MAX_TOP_FRAMES = 15
    private const val MAX_APP_FRAME_CONTEXT = 10
    private const val MAX_TOTAL_FRAMES = 40

    fun from(
        frames: List<StackTraceElement>,
        stallDurationMs: Long = 8_000L,
        sample: MainThreadStallSample = MainThreadStallSample.ACTIONABLE,
        screenOn: Boolean? = null,
        appForeground: Boolean? = null
    ): LobsterUsageEvent {
        val safeDurationMs = stallDurationMs.coerceIn(0L, 86_400_000L)
        val selectedFrames = selectFrames(frames)
        val logLine = buildString {
            append("[ANR] 主线程未响应 duration_ms=").append(safeDurationMs)
            append(" sample=").append(sample.name)
            if (screenOn != null) append(" screen_on=").append(screenOn)
            if (appForeground != null) append(" app_foreground=").append(appForeground)
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

    /**
     * 栈帧选取：固定保留栈顶 15 帧（现场入口），另外**必保**第一个本应用帧及其下
     * [MAX_APP_FRAME_CONTEXT] 帧（谁发起的 Binder 等待、怎么走到系统层）。
     * 不再用「没匹配到允许列表就截掉调用方」的回退——那会把真正的责任人裁掉。
     */
    internal fun selectFrames(frames: List<StackTraceElement>): List<StackTraceElement> {
        if (frames.isEmpty()) return emptyList()
        val indices = sortedSetOf<Int>()
        indices.addAll(0 until minOf(MAX_TOP_FRAMES, frames.size))
        val firstAppIndex = frames.indexOfFirst(MainThreadStallFramePolicy::isAppFrame)
        if (firstAppIndex >= 0) {
            val lastAppContextIndex =
                minOf(firstAppIndex + MAX_APP_FRAME_CONTEXT, frames.size - 1)
            indices.addAll(firstAppIndex..lastAppContextIndex)
        }
        return indices.take(MAX_TOTAL_FRAMES).map(frames::get)
    }
}
