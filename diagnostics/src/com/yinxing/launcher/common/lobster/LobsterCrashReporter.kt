package com.yinxing.launcher.common.lobster

import android.content.Context
import android.os.Build
import android.os.Process
import kotlin.system.exitProcess

data class LobsterCrashSnapshot(
    val exceptionType: String,
    val threadKind: String,
    val stackFrames: List<String>
) {
    fun toUsageEvent(): LobsterUsageEvent {
        val stack = stackFrames.joinToString(separator = "\n")
        return LobsterUsageEvent(
            scene = "客户端崩溃",
            status = LobsterReportStatus.ERROR,
            summary = "客户端发生未捕获异常",
            category = LobsterLogCategory.CRASH,
            eventType = LobsterEventType.ERROR,
            action = "uncaught_exception",
            logLine = buildString {
                append("[崩溃] 异常类型=").append(exceptionType)
                append("\n[崩溃] 线程=").append(threadKind)
                if (stack.isNotBlank()) append("\n[崩溃] 堆栈=").append(stack)
            },
            details = LobsterReportDetails(
                errorCode = "UNCAUGHT_EXCEPTION",
                failedStep = threadKind
            )
        )
    }

    companion object {
        private const val MAX_STACK_LINES = 60
        private const val MAX_CAUSE_DEPTH = 3
        private const val FRAMES_PER_THROWABLE = 25

        fun from(threadName: String, throwable: Throwable): LobsterCrashSnapshot {
            val lines = mutableListOf<String>()
            var current: Throwable? = throwable
            var depth = 0
            while (current != null && depth < MAX_CAUSE_DEPTH && lines.size < MAX_STACK_LINES) {
                if (depth > 0) {
                    val causeMessage = LobsterLogSanitizer.sanitize(current.message.orEmpty().take(200))
                    lines += "Caused by: ${current.javaClass.name}: $causeMessage"
                } else {
                    current.message?.takeIf { it.isNotBlank() }?.let {
                        lines += "message=${LobsterLogSanitizer.sanitize(it.take(300))}"
                    }
                }
                current.stackTrace
                    .asSequence()
                    .take(FRAMES_PER_THROWABLE)
                    .mapTo(lines, StackTraceElement::toString)
                current = current.cause?.takeIf { it !== current }
                depth += 1
            }
            return LobsterCrashSnapshot(
                exceptionType = throwable.javaClass.simpleName.take(120),
                threadKind = if (threadName == "main") "main_thread" else "background_thread",
                stackFrames = lines.take(MAX_STACK_LINES)
            )
        }
    }
}

object LobsterCrashReporter {
    @Volatile
    private var installed = false

    @Synchronized
    fun install(context: Context) {
        if (installed || !LobsterRuntimePolicy.shouldUpload(context)) return

        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                LobsterClient.recordCrash(
                    appContext,
                    LobsterCrashSnapshot.from(thread.name, throwable)
                )
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
        installed = true
    }
}
