package com.yinxing.launcher.common.lobster

data class LobsterUsageEvent(
    val scene: String,
    val status: LobsterReportStatus,
    val summary: String,
    val logLine: String,
    val details: LobsterReportDetails = LobsterReportDetails()
)

object LobsterUsageEvents {
    val APP_STARTED = event("应用启动", LobsterReportStatus.SUCCESS, "客户端启动完成")
    val HOME_PHONE_OPENED = event("首页入口", LobsterReportStatus.REPORTED, "打开电话入口")
    val HOME_WEATHER_OPENED = event("首页入口", LobsterReportStatus.REPORTED, "打开天气入口")
    val HOME_APP_MANAGER_OPENED = event("首页入口", LobsterReportStatus.REPORTED, "打开应用管理")
    val CAREGIVER_SETTINGS_OPENED = event("首页入口", LobsterReportStatus.REPORTED, "打开关怀设置")
    val APP_OPENED = event("应用启动", LobsterReportStatus.SUCCESS, "第三方应用启动成功")
    val APP_OPEN_FAILED = event(
        "应用启动",
        LobsterReportStatus.ERROR,
        "第三方应用启动失败",
        errorCode = "APP_LAUNCH_FAILED",
        failedStep = "start_activity"
    )
    val OUTGOING_CALL_STARTED = event("电话拨出", LobsterReportStatus.SUCCESS, "系统已受理拨号")
    val OUTGOING_CALL_FAILED = event(
        "电话拨出",
        LobsterReportStatus.ERROR,
        "系统拨号失败",
        errorCode = "PHONE_DIAL_FAILED",
        failedStep = "place_call"
    )
    val CALL_PERMISSION_DENIED = event(
        "电话拨出",
        LobsterReportStatus.ERROR,
        "拨号权限未授予",
        errorCode = "CALL_PERMISSION_DENIED",
        failedStep = "request_permission"
    )

    val all = listOf(
        APP_STARTED,
        HOME_PHONE_OPENED,
        HOME_WEATHER_OPENED,
        HOME_APP_MANAGER_OPENED,
        CAREGIVER_SETTINGS_OPENED,
        APP_OPENED,
        APP_OPEN_FAILED,
        OUTGOING_CALL_STARTED,
        OUTGOING_CALL_FAILED,
        CALL_PERMISSION_DENIED
    )

    private fun event(
        scene: String,
        status: LobsterReportStatus,
        summary: String,
        errorCode: String? = null,
        failedStep: String? = null
    ) = LobsterUsageEvent(
        scene = scene,
        status = status,
        summary = summary,
        logLine = "[使用] $summary",
        details = LobsterReportDetails(errorCode = errorCode, failedStep = failedStep)
    )
}
