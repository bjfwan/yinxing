package com.bajianfeng.launcher.automation.wechat.model

data class PerformanceMetrics(
    val launchWeChatTimes: MutableList<Long> = mutableListOf(),
    val loadHomeTimes: MutableList<Long> = mutableListOf(),
    val searchContactTimes: MutableList<Long> = mutableListOf(),
    val loadChatTimes: MutableList<Long> = mutableListOf()
) {
    fun getAdaptiveTimeout(step: String, defaultTimeout: Long): Long {
        val times = when(step) {
            "launch" -> launchWeChatTimes
            "home" -> loadHomeTimes
            "search" -> searchContactTimes
            "chat" -> loadChatTimes
            else -> return defaultTimeout
        }
        
        if (times.isEmpty()) return defaultTimeout
        
        val maxTime = times.maxOrNull() ?: defaultTimeout
        return maxOf((maxTime * 1.5).toLong(), defaultTimeout)
    }
    
    fun recordTime(step: String, time: Long) {
        val times = when(step) {
            "launch" -> launchWeChatTimes
            "home" -> loadHomeTimes
            "search" -> searchContactTimes
            "chat" -> loadChatTimes
            else -> return
        }
        
        times.add(time)
        if (times.size > 10) {
            times.removeAt(0)
        }
    }
}
