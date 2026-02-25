package com.bajianfeng.launcher.manager

import android.content.Context
import android.content.SharedPreferences
import com.bajianfeng.launcher.model.PerformanceMetrics

class TimeoutManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("timeout_config", Context.MODE_PRIVATE)
    private val metrics = loadMetrics()
    
    companion object {
        @Volatile
        private var instance: TimeoutManager? = null
        
        fun getInstance(context: Context): TimeoutManager {
            return instance ?: synchronized(this) {
                instance ?: TimeoutManager(context.applicationContext).also { instance = it }
            }
        }
        
        const val DEFAULT_LAUNCH_WECHAT = 15000L
        const val DEFAULT_LOAD_HOME = 20000L
        const val DEFAULT_SEARCH_CONTACT = 15000L
        const val DEFAULT_LOAD_CHAT = 15000L
        const val DEFAULT_TOTAL = 65000L
    }
    
    fun getTimeout(step: String): Long {
        return when(step) {
            "launch" -> metrics.getAdaptiveTimeout("launch", DEFAULT_LAUNCH_WECHAT)
            "home" -> metrics.getAdaptiveTimeout("home", DEFAULT_LOAD_HOME)
            "search" -> metrics.getAdaptiveTimeout("search", DEFAULT_SEARCH_CONTACT)
            "chat" -> metrics.getAdaptiveTimeout("chat", DEFAULT_LOAD_CHAT)
            "total" -> DEFAULT_TOTAL
            else -> 10000L
        }
    }
    
    fun recordSuccess(step: String, duration: Long) {
        metrics.recordTime(step, duration)
        saveMetrics()
    }
    
    private fun loadMetrics(): PerformanceMetrics {
        val launchTimes = prefs.getString("launch_times", "")?.split(",")?.mapNotNull { it.toLongOrNull() }?.toMutableList() ?: mutableListOf()
        val homeTimes = prefs.getString("home_times", "")?.split(",")?.mapNotNull { it.toLongOrNull() }?.toMutableList() ?: mutableListOf()
        val searchTimes = prefs.getString("search_times", "")?.split(",")?.mapNotNull { it.toLongOrNull() }?.toMutableList() ?: mutableListOf()
        val chatTimes = prefs.getString("chat_times", "")?.split(",")?.mapNotNull { it.toLongOrNull() }?.toMutableList() ?: mutableListOf()
        
        return PerformanceMetrics(launchTimes, homeTimes, searchTimes, chatTimes)
    }
    
    private fun saveMetrics() {
        prefs.edit().apply {
            putString("launch_times", metrics.launchWeChatTimes.joinToString(","))
            putString("home_times", metrics.loadHomeTimes.joinToString(","))
            putString("search_times", metrics.searchContactTimes.joinToString(","))
            putString("chat_times", metrics.loadChatTimes.joinToString(","))
            apply()
        }
    }
}
