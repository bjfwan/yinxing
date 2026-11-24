package com.yinxing.launcher.common.lobster

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class MainThreadStallDetector(private val thresholdMs: Long) {
    private var lastHeartbeatMs = 0L
    private var reportedForCurrentStall = false

    @Synchronized
    fun onHeartbeat(nowMs: Long) {
        lastHeartbeatMs = nowMs
        reportedForCurrentStall = false
    }

    @Synchronized
    fun shouldReport(nowMs: Long, debuggerConnected: Boolean): Boolean {
        return takeReportableStallDuration(nowMs, debuggerConnected) != null
    }

    @Synchronized
    fun takeReportableStallDuration(nowMs: Long, debuggerConnected: Boolean): Long? {
        if (debuggerConnected || lastHeartbeatMs <= 0L || reportedForCurrentStall) return null
        val durationMs = nowMs - lastHeartbeatMs
        if (durationMs <= thresholdMs) return null
        reportedForCurrentStall = true
        return durationMs
    }
}

object LobsterMainThreadWatchdog {
    private const val HEARTBEAT_INTERVAL_MS = 1_000L
    private const val STALL_THRESHOLD_MS = 8_000L
    private const val MAX_ACTIONABLE_STALL_MS = 120_000L

    // 一个指标名曾混装 4 种情况（休眠 / 超 120s / 空闲恢复 / watchdog 自身采样），
    // 线上 p90 被拉到 6 分钟以上、指标不可用；现在按原因拆成独立指标名。
    private const val STALL_ASLEEP_METRIC = "main_thread_stall_asleep_sample"
    private const val STALL_TOO_LONG_METRIC = "main_thread_stall_too_long_sample"
    private const val STALL_IDLE_METRIC = "main_thread_stall_idle_recovered_sample"
    private const val STALL_WATCHDOG_SELF_METRIC = "main_thread_stall_watchdog_self_sample"

    @Volatile
    private var started = false

    @Synchronized
    fun start(context: Context) {
        if (started || !LobsterRuntimePolicy.shouldUpload(context)) return
        started = true

        val appContext = context.applicationContext
        val powerManager = appContext.getSystemService(PowerManager::class.java)
        val detector = MainThreadStallDetector(STALL_THRESHOLD_MS)
        val handler = Handler(Looper.getMainLooper())
        val mainThread = Looper.getMainLooper().thread
        val heartbeat = object : Runnable {
            override fun run() {
                detector.onHeartbeat(SystemClock.uptimeMillis())
                handler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
            }
        }
        detector.onHeartbeat(SystemClock.uptimeMillis())
        handler.post(heartbeat)

        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "lobster-main-thread-watchdog").apply { isDaemon = true }
        }.scheduleAtFixedRate(
            {
                val durationMs = detector.takeReportableStallDuration(
                    SystemClock.uptimeMillis(),
                    Debug.isDebuggerConnected()
                )
                if (durationMs != null) {
                    val deviceAsleep = powerManager?.isInteractive == false
                    val appForeground = isAppForeground()
                    if (deviceAsleep) {
                        LobsterClient.reportMetrics(
                            appContext,
                            listOf(STALL_ASLEEP_METRIC to durationMs)
                        )
                    } else if (durationMs > MAX_ACTIONABLE_STALL_MS) {
                        LobsterClient.reportMetrics(
                            appContext,
                            listOf(STALL_TOO_LONG_METRIC to durationMs)
                        )
                    } else {
                        val frames = mainThread.stackTrace.toList()
                        when (MainThreadStallSampleClassifier.classify(frames)) {
                            MainThreadStallSample.ACTIONABLE -> LobsterClient.reportUsage(
                                appContext,
                                LobsterAnrEventFactory.from(
                                    frames,
                                    durationMs,
                                    sample = MainThreadStallSample.ACTIONABLE,
                                    screenOn = !deviceAsleep,
                                    appForeground = appForeground
                                )
                            )
                            MainThreadStallSample.RECOVERED_IDLE -> LobsterClient.reportMetrics(
                                appContext,
                                listOf(STALL_IDLE_METRIC to durationMs)
                            )
                            MainThreadStallSample.WATCHDOG_SELF -> LobsterClient.reportMetrics(
                                appContext,
                                listOf(STALL_WATCHDOG_SELF_METRIC to durationMs)
                            )
                        }
                    }
                }
            },
            STALL_THRESHOLD_MS,
            HEARTBEAT_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
    }

    /** 主线程卡顿时本应用是否在前台；取不到状态时按前台处理，宁可多报不漏报。 */
    private fun isAppForeground(): Boolean = runCatching {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }.getOrDefault(true)
}
