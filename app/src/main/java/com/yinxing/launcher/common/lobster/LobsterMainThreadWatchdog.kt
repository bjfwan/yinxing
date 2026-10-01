package com.yinxing.launcher.common.lobster

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
                    if (deviceAsleep || durationMs > MAX_ACTIONABLE_STALL_MS) {
                        LobsterClient.reportMetrics(
                            appContext,
                            listOf("main_thread_stall_recovered_sample" to durationMs)
                        )
                    } else {
                        val frames = mainThread.stackTrace.toList()
                        when (MainThreadStallSampleClassifier.classify(frames)) {
                            MainThreadStallSample.ACTIONABLE -> LobsterClient.reportUsage(
                                appContext,
                                LobsterAnrEventFactory.from(frames, durationMs)
                            )
                            MainThreadStallSample.RECOVERED_IDLE,
                            MainThreadStallSample.WATCHDOG_SELF -> LobsterClient.reportMetrics(
                                appContext,
                                listOf("main_thread_stall_recovered_sample" to durationMs)
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
}
