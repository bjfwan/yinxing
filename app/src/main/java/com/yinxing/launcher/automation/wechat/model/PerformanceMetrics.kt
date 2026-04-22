package com.yinxing.launcher.automation.wechat.model

/**
 * 各步骤耗时历史记录，支持自适应超时计算。
 *
 * 算法升级：
 * - 旧：max(times) × 1.5（偶发一次慢操作会永久拉高超时）
 * - 新：加权移动平均（近期数据权重高）× 安全系数，同时设置上界防止无限膨胀
 *
 * 权重分布：最新一条权重最高，越旧权重越低（指数衰减）
 * 安全系数：1.6（在均值基础上留 60% 余量，比 max×1.5 更平滑）
 * 上界：defaultTimeout × 2.5（防止设备偶发性极慢导致超时无限膨胀）
 */
data class PerformanceMetrics(
    val launchWeChatTimes: MutableList<Long> = mutableListOf(),
    val loadHomeTimes: MutableList<Long> = mutableListOf(),
    val searchContactTimes: MutableList<Long> = mutableListOf(),
    val loadChatTimes: MutableList<Long> = mutableListOf()
) {
    companion object {
        /** 安全系数：加权均值 × SAFETY_FACTOR 作为超时 */
        private const val SAFETY_FACTOR = 1.6f
        /** 超时上界倍数：不超过默认超时的 2.5 倍 */
        private const val MAX_FACTOR = 2.5f
        /** 指数衰减底数（0~1），越小表示越重视近期数据 */
        private const val DECAY_BASE = 0.75f
        /** 最少需要多少条数据才启用自适应（不足时退回默认值） */
        private const val MIN_SAMPLES = 2
    }

    fun getAdaptiveTimeout(step: String, defaultTimeout: Long): Long {
        val times = timesFor(step) ?: return defaultTimeout
        if (times.size < MIN_SAMPLES) return defaultTimeout

        // 加权移动平均：最新一条权重 = 1，往前每步乘以 DECAY_BASE
        var weightedSum = 0.0
        var weightTotal = 0.0
        var weight = 1.0
        for (i in times.indices.reversed()) {
            weightedSum += times[i] * weight
            weightTotal += weight
            weight *= DECAY_BASE
        }
        val weightedAvg = weightedSum / weightTotal

        val adaptive = (weightedAvg * SAFETY_FACTOR).toLong()
        val upper = (defaultTimeout * MAX_FACTOR).toLong()
        return adaptive.coerceIn(defaultTimeout, upper)
    }

    fun recordTime(step: String, time: Long) {
        val times = timesFor(step) ?: return
        times.add(time)
        if (times.size > 10) {
            times.removeAt(0)
        }
    }

    /** 返回指定步骤的耗时列表，未知步骤返回 null */
    private fun timesFor(step: String): MutableList<Long>? = when (step) {
        "launch" -> launchWeChatTimes
        "home" -> loadHomeTimes
        "search" -> searchContactTimes
        "chat" -> loadChatTimes
        else -> null
    }
}
