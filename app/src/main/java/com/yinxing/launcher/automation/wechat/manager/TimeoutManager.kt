package com.bajianfeng.launcher.automation.wechat.manager

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.bajianfeng.launcher.automation.wechat.model.PerformanceMetrics

/**
 * 超时管理器（升级版）。
 *
 * 改进点：
 * 1. 设备性能分级（HIGH / MID / LOW），基于 CPU 核心数 + 可用 RAM 自动分档
 * 2. 每档有独立的默认超时基线，低端设备默认超时更宽松，避免首次就超时失败
 * 3. PerformanceMetrics 的加权自适应算法在历史数据积累后逐渐接管
 * 4. SharedPreferences 写入使用 apply()（异步），不阻塞主线程
 */
class TimeoutManager private constructor(context: Context) {

    enum class DeviceTier { HIGH, MID, LOW }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("timeout_config", Context.MODE_PRIVATE)
    private val metrics = loadMetrics()
    private val tier = detectDeviceTier(context)

    companion object {
        @Volatile
        private var instance: TimeoutManager? = null

        fun getInstance(context: Context): TimeoutManager {
            return instance ?: synchronized(this) {
                instance ?: TimeoutManager(context.applicationContext).also { instance = it }
            }
        }

        // ── HIGH 档基线（8+ 核，≥ 3GB RAM）──
        private const val HIGH_LAUNCH = 12_000L
        private const val HIGH_HOME   = 15_000L
        private const val HIGH_SEARCH = 12_000L
        private const val HIGH_CHAT   = 12_000L
        private const val HIGH_TOTAL  = 55_000L

        // ── MID 档基线（4~7 核，1.5~3GB RAM）──
        const val DEFAULT_LAUNCH_WECHAT  = 15_000L
        const val DEFAULT_LOAD_HOME      = 20_000L
        const val DEFAULT_SEARCH_CONTACT = 15_000L
        const val DEFAULT_LOAD_CHAT      = 15_000L
        const val DEFAULT_TOTAL          = 65_000L

        // ── LOW 档基线（≤ 3 核 或 < 1.5GB RAM）──
        private const val LOW_LAUNCH = 22_000L
        private const val LOW_HOME   = 30_000L
        private const val LOW_SEARCH = 22_000L
        private const val LOW_CHAT   = 22_000L
        private const val LOW_TOTAL  = 100_000L

        /** 探测设备性能档位（一次性调用，结果缓存在实例里） */
        fun detectDeviceTier(context: Context): DeviceTier {
            val cores = Runtime.getRuntime().availableProcessors()
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
            val totalRamMb = memInfo.totalMem / (1024 * 1024)

            return when {
                cores >= 8 && totalRamMb >= 3000 -> DeviceTier.HIGH
                cores <= 3 || totalRamMb < 1500   -> DeviceTier.LOW
                else                               -> DeviceTier.MID
            }
        }
    }

    fun getDeviceTier(): DeviceTier = tier

    fun getTimeout(step: String): Long {
        val default = defaultFor(step)
        return when (step) {
            "launch" -> metrics.getAdaptiveTimeout("launch", default)
            "home"   -> metrics.getAdaptiveTimeout("home",   default)
            "search" -> metrics.getAdaptiveTimeout("search", default)
            "chat"   -> metrics.getAdaptiveTimeout("chat",   default)
            "total"  -> totalFor()
            else     -> 10_000L
        }
    }

    fun recordSuccess(step: String, duration: Long) {
        metrics.recordTime(step, duration)
        saveMetrics()
    }

    // ── 内部 ──────────────────────────────────────────────────────────────────

    private fun defaultFor(step: String): Long = when (tier) {
        DeviceTier.HIGH -> when (step) {
            "launch" -> HIGH_LAUNCH
            "home"   -> HIGH_HOME
            "search" -> HIGH_SEARCH
            "chat"   -> HIGH_CHAT
            else     -> 10_000L
        }
        DeviceTier.MID -> when (step) {
            "launch" -> DEFAULT_LAUNCH_WECHAT
            "home"   -> DEFAULT_LOAD_HOME
            "search" -> DEFAULT_SEARCH_CONTACT
            "chat"   -> DEFAULT_LOAD_CHAT
            else     -> 10_000L
        }
        DeviceTier.LOW -> when (step) {
            "launch" -> LOW_LAUNCH
            "home"   -> LOW_HOME
            "search" -> LOW_SEARCH
            "chat"   -> LOW_CHAT
            else     -> 15_000L
        }
    }

    private fun totalFor(): Long = when (tier) {
        DeviceTier.HIGH -> HIGH_TOTAL
        DeviceTier.MID  -> DEFAULT_TOTAL
        DeviceTier.LOW  -> LOW_TOTAL
    }

    private fun loadMetrics(): PerformanceMetrics {
        fun loadList(key: String) = prefs.getString(key, "")
            ?.split(",")?.mapNotNull { it.toLongOrNull() }?.toMutableList()
            ?: mutableListOf()
        return PerformanceMetrics(
            loadList("launch_times"),
            loadList("home_times"),
            loadList("search_times"),
            loadList("chat_times")
        )
    }

    private fun saveMetrics() {
        prefs.edit()
            .putString("launch_times", metrics.launchWeChatTimes.joinToString(","))
            .putString("home_times",   metrics.loadHomeTimes.joinToString(","))
            .putString("search_times", metrics.searchContactTimes.joinToString(","))
            .putString("chat_times",   metrics.loadChatTimes.joinToString(","))
            .apply() // 异步写入，不阻塞
    }
}
