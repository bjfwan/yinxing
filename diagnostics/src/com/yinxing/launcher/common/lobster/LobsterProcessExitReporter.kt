package com.yinxing.launcher.common.lobster

import android.app.ActivityManager
import android.content.Context
import android.os.Build

object LobsterProcessExitReasons {
    const val EXIT_SELF = 1
    const val SIGNALED = 2
    const val LOW_MEMORY = 3
    const val CRASH = 4
    const val CRASH_NATIVE = 5
    const val ANR = 6
    const val INITIALIZATION_FAILURE = 7
    const val EXCESSIVE_RESOURCE_USAGE = 9
    const val USER_REQUESTED = 10
    const val USER_STOPPED = 11
    const val DEPENDENCY_DIED = 12
}

object LobsterProcessExitClassifier {
    fun classify(reason: Int): LobsterUsageEvent? {
        val code = when (reason) {
            LobsterProcessExitReasons.SIGNALED -> "PROCESS_EXIT_SIGNALED"
            LobsterProcessExitReasons.LOW_MEMORY -> "PROCESS_EXIT_LOW_MEMORY"
            LobsterProcessExitReasons.CRASH -> "PROCESS_EXIT_CRASH"
            LobsterProcessExitReasons.CRASH_NATIVE -> "PROCESS_EXIT_NATIVE_CRASH"
            LobsterProcessExitReasons.ANR -> "PROCESS_EXIT_ANR"
            LobsterProcessExitReasons.INITIALIZATION_FAILURE -> "PROCESS_EXIT_INITIALIZATION_FAILURE"
            LobsterProcessExitReasons.EXCESSIVE_RESOURCE_USAGE -> "PROCESS_EXIT_EXCESSIVE_RESOURCE"
            LobsterProcessExitReasons.DEPENDENCY_DIED -> "PROCESS_EXIT_DEPENDENCY_DIED"
            else -> return null
        }
        return LobsterUsageEvent(
            scene = "上次进程退出",
            status = LobsterReportStatus.ERROR,
            summary = "检测到上次进程异常退出",
            logLine = "[系统] 检测到上次进程异常退出",
            details = LobsterReportDetails(errorCode = code, failedStep = "previous_process"),
            category = LobsterLogCategory.SYSTEM,
            eventType = LobsterEventType.ERROR,
            action = "previous_process_exit"
        )
    }
}

object LobsterProcessExitReporter {
    private const val PREFS_NAME = "lobster_process_exit"
    private const val KEY_LAST_SEEN_TIMESTAMP = "last_seen_timestamp"

    fun reportPreviousAbnormalExit(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSeen = prefs.getLong(KEY_LAST_SEEN_TIMESTAMP, 0L)
        val exits = runCatching {
            manager.getHistoricalProcessExitReasons(appContext.packageName, 0, 5)
        }.getOrNull().orEmpty()
        val newestTimestamp = exits.maxOfOrNull { it.timestamp } ?: return
        exits.asSequence()
            .filter { it.timestamp > lastSeen }
            .sortedByDescending { it.timestamp }
            .mapNotNull { LobsterProcessExitClassifier.classify(it.reason) }
            .firstOrNull()
            ?.let { LobsterClient.reportUsage(appContext, it) }
        prefs.edit().putLong(KEY_LAST_SEEN_TIMESTAMP, newestTimestamp).apply()
    }
}
