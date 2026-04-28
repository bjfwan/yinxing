package com.yinxing.launcher.data.home

data class LauncherAppRecord(
    val packageName: String,
    val appName: String
)

interface LauncherAppSource {
    suspend fun loadInstalledApps(): List<LauncherAppRecord>
    suspend fun loadSelectedApps(packageNames: Set<String>): List<LauncherAppRecord>
}
