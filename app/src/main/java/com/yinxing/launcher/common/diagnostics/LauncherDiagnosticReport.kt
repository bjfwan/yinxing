package com.yinxing.launcher.common.diagnostics

import android.content.Context
import android.os.Build
import com.yinxing.launcher.BuildConfig
import com.yinxing.launcher.common.lobster.LobsterDeviceStateCollector
import com.yinxing.launcher.common.lobster.LobsterPendingReportStore
import com.yinxing.launcher.data.home.LauncherPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LauncherDiagnosticSnapshot(
    val generatedAt: String,
    val appVersion: String,
    val deviceModel: String,
    val androidVersion: String,
    val selectedHomeAppCount: Int,
    val homeLayoutLocked: Boolean,
    val homeLongPressMode: String,
    val lowPerformanceMode: Boolean,
    val iconScale: Int,
    val pendingReportCount: Int,
    val deviceState: String
)

object LauncherDiagnosticReportFormatter {
    fun format(snapshot: LauncherDiagnosticSnapshot): String = buildString {
        appendLine("银杏诊断信息")
        appendLine("生成时间：${snapshot.generatedAt}")
        appendLine("应用版本：${snapshot.appVersion}")
        appendLine("设备型号：${snapshot.deviceModel}")
        appendLine("系统版本：${snapshot.androidVersion}")
        appendLine()
        appendLine("首页应用数量：${snapshot.selectedHomeAppCount}")
        appendLine("锁定首页布局：${snapshot.homeLayoutLocked.yesOrNo()}")
        appendLine("长按响应时间：${snapshot.homeLongPressMode}")
        appendLine("减少动态效果：${snapshot.lowPerformanceMode.onOrOff()}")
        appendLine("首页显示大小：${snapshot.iconScale}%")
        appendLine("待补传诊断：${snapshot.pendingReportCount} 条")
        appendLine(snapshot.deviceState)
        appendLine()
        append("隐私说明：不包含联系人、电话号码和聊天内容。")
    }

    private fun Boolean.yesOrNo(): String = if (this) "是" else "否"

    private fun Boolean.onOrOff(): String = if (this) "开启" else "关闭"
}

object LauncherDiagnosticReportCollector {
    fun collect(context: Context): LauncherDiagnosticSnapshot {
        val appContext = context.applicationContext
        val preferences = LauncherPreferences.getInstance(appContext)
        val deviceState = runCatching {
            LobsterDeviceStateCollector.capture(appContext).toLogLine()
        }.getOrElse {
            "[设备状态] 暂时无法读取"
        }
        val manufacturer = Build.MANUFACTURER.trim()
        val model = Build.MODEL.trim()
        val deviceModel = listOf(manufacturer, model)
            .filter(String::isNotBlank)
            .distinctBy(String::lowercase)
            .joinToString(" ")
            .ifBlank { "未知设备" }
        return LauncherDiagnosticSnapshot(
            generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date()),
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            deviceModel = deviceModel,
            androidVersion = "${Build.VERSION.RELEASE}（SDK ${Build.VERSION.SDK_INT}）",
            selectedHomeAppCount = preferences.getSelectedPackages().size,
            homeLayoutLocked = preferences.isHomeLayoutLocked(),
            homeLongPressMode = if (
                preferences.getHomeLongPressResponse() == LauncherPreferences.HOME_LONG_PRESS_LONG
            ) {
                "较长"
            } else {
                "标准"
            },
            lowPerformanceMode = preferences.isLowPerformanceModeEnabled(),
            iconScale = preferences.getIconScale(),
            pendingReportCount = LobsterPendingReportStore.read(appContext).size,
            deviceState = deviceState
        )
    }
}
