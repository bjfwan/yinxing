package com.yinxing.launcher.common.lobster

import android.content.Context
import android.os.Debug
import android.os.Handler
import android.os.Looper
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
        if (debuggerConnected || lastHeartbeatMs <= 0L || reportedForCurrentStall) return false
        if (nowMs - lastHeartbeatMs <= thresholdMs) return false
        reportedForCurrentStall = true
        return true
    }
}

object LobsterMainThreadWatchdog {
    private const val HEARTBEAT_INTERVAL_MS = 1_000L
    private const val STALL_THRESHOLD_MS = 8_000L

    @Volatile
    private var started = false

    @Synchronized
    fun start(context: Context) {
        if (started || !LobsterRuntimePolicy.shouldUpload(
                android.os.Build.MANUFACTURER,
                android.os.Build.MODEL,
                android.os.Build.FINGERPRINT
            )
        ) return
        started = true

        val appContext = context.applicationContext
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
                if (detector.shouldReport(SystemClock.uptimeMillis(), Debug.isDebuggerConnected())) {
                    LobsterClient.reportUsage(
                        appContext,
                        LobsterAnrEventFactory.from(mainThread.stackTrace.toList())
                    )
                }
            },
            STALL_THRESHOLD_MS,
            HEARTBEAT_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
    }
}
