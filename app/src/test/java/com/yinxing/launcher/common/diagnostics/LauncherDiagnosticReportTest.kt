package com.yinxing.launcher.common.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherDiagnosticReportTest {
    @Test
    fun reportContainsUsefulStateAndPrivacyNotice() {
        val report = LauncherDiagnosticReportFormatter.format(
            LauncherDiagnosticSnapshot(
                generatedAt = "2026-08-28 12:30:00",
                appVersion = "2.0.0 (17)",
                deviceModel = "vivo S17e",
                androidVersion = "15（SDK 35）",
                selectedHomeAppCount = 3,
                homeLayoutLocked = true,
                homeLongPressMode = "较长",
                lowPerformanceMode = false,
                iconScale = 100,
                pendingReportCount = 2,
                deviceState = "电量=90%；省电模式=否"
            )
        )

        assertTrue(report.contains("银杏诊断信息"))
        assertTrue(report.contains("应用版本：2.0.0 (17)"))
        assertTrue(report.contains("锁定首页布局：是"))
        assertTrue(report.contains("长按响应时间：较长"))
        assertTrue(report.contains("待补传诊断：2 条"))
        assertTrue(report.contains("不包含联系人、电话号码和聊天内容"))
        assertFalse(report.contains("device_id"))
    }
}
