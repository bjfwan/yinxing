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
        fun from(threadName: String, throwable: Throwable): LobsterCrashSnapshot {
            val appFrames = throwable.stackTrace
                .asSequence()
                .filter { it.className.startsWith("com.yinxing.launcher") }
                .take(20)
                .map(StackTraceElement::toString)
                .toList()
            val fallbackFrames = throwable.stackTrace
                .asSequence()
                .take(10)
                .map(StackTraceElement::toString)
                .toList()
            return LobsterCrashSnapshot(
                exceptionType = throwable.javaClass.simpleName.take(120),
                threadKind = if (threadName == "main") "main_thread" else "background_thread",
                stackFrames = appFrames.ifEmpty { fallbackFrames }
            )
        }
    }
}

object LobsterCrashReporter {
    @Volatile
    private var installed = false

    @Synchronized
    fun install(context: Context) {
        if (installed || !LobsterRuntimePolicy.shouldUpload(
                Build.MANUFACTURER,
                Build.MODEL,
                Build.FINGERPRINT
            )
        ) return

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
