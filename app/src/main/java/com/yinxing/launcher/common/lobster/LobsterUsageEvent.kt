package com.yinxing.launcher.common.lobster

data class LobsterUsageEvent(
    val scene: String,
    val status: LobsterReportStatus,
    val summary: String,
    val logLine: String,
    val details: LobsterReportDetails = LobsterReportDetails(),
    val category: LobsterLogCategory,
    val eventType: LobsterEventType,
    val action: String
)

object LobsterUsageEvents {
    val APP_STARTED = event("应用启动", LobsterReportStatus.SUCCESS, "客户端启动完成", LobsterLogCategory.STARTUP, LobsterEventType.LIFECYCLE, "start_client")
    val HOME_PHONE_OPENED = event("首页入口", LobsterReportStatus.REPORTED, "打开电话入口", LobsterLogCategory.PHONE, LobsterEventType.OPERATION, "open_phone")
    val HOME_WEATHER_OPENED = event("首页入口", LobsterReportStatus.REPORTED, "打开天气入口", LobsterLogCategory.WEATHER, LobsterEventType.OPERATION, "open_weather")
    val HOME_APP_MANAGER_OPENED = event("首页入口", LobsterReportStatus.REPORTED, "打开应用管理", LobsterLogCategory.NAVIGATION, LobsterEventType.OPERATION, "open_app_manager")
    val CAREGIVER_SETTINGS_OPENED = event("首页入口", LobsterReportStatus.REPORTED, "打开关怀设置", LobsterLogCategory.SETTINGS, LobsterEventType.OPERATION, "open_caregiver_settings")
    val APP_OPENED = event("应用启动", LobsterReportStatus.SUCCESS, "第三方应用启动成功", LobsterLogCategory.NAVIGATION, LobsterEventType.OPERATION, "open_app")
    val APP_OPEN_FAILED = event(
        "应用启动",
        LobsterReportStatus.ERROR,
        "第三方应用启动失败",
        LobsterLogCategory.NAVIGATION,
        LobsterEventType.ERROR,
        "open_app",
        errorCode = "APP_LAUNCH_FAILED",
        failedStep = "start_activity"
    )
    val OUTGOING_CALL_STARTED = event("电话拨出", LobsterReportStatus.SUCCESS, "系统已受理拨号", LobsterLogCategory.PHONE, LobsterEventType.OPERATION, "place_call")
    val OUTGOING_CALL_FAILED = event(
        "电话拨出",
        LobsterReportStatus.ERROR,
        "系统拨号失败",
        LobsterLogCategory.PHONE,
        LobsterEventType.ERROR,
        "place_call",
        errorCode = "PHONE_DIAL_FAILED",
        failedStep = "place_call"
    )
    val CALL_PERMISSION_DENIED = event(
        "电话拨出",
        LobsterReportStatus.ERROR,
        "拨号权限未授予",
        LobsterLogCategory.PHONE,
        LobsterEventType.ERROR,
        "request_call_permission",
        errorCode = "CALL_PERMISSION_DENIED",
        failedStep = "request_permission"
    )
    val FALL_DETECTION_STARTED = event(
        "跌倒检测", LobsterReportStatus.SUCCESS, "跌倒检测服务已启动",
        LobsterLogCategory.SYSTEM, LobsterEventType.HEALTH, "start_fall_detection"
    )
    val FALL_SENSOR_UNAVAILABLE = event(
        "跌倒检测",
        LobsterReportStatus.ERROR,
        "设备缺少加速度传感器",
        LobsterLogCategory.SYSTEM,
        LobsterEventType.ERROR,
        "start_fall_detection",
        errorCode = "FALL_SENSOR_UNAVAILABLE",
        failedStep = "register_sensor"
    )
    val FALL_POSSIBLE_DETECTED = event(
        "跌倒检测",
        LobsterReportStatus.REPORTED,
        "检测到疑似跌倒",
        LobsterLogCategory.SYSTEM,
        LobsterEventType.DIAGNOSTIC,
        "detect_possible_fall"
    )
    val FALL_ALERT_CANCELLED = event(
        "跌倒检测",
        LobsterReportStatus.SUCCESS,
        "用户确认没有跌倒",
        LobsterLogCategory.SYSTEM,
        LobsterEventType.OPERATION,
        "cancel_fall_alert"
    )
    val FALL_FAMILY_CALL_STARTED = event(
        "跌倒求助",
        LobsterReportStatus.SUCCESS,
        "系统已受理家属电话",
        LobsterLogCategory.PHONE,
        LobsterEventType.OPERATION,
        "place_emergency_call"
    )
    val FALL_FAMILY_CALL_FAILED = event(
        "跌倒求助",
        LobsterReportStatus.ERROR,
        "家属电话拨打失败",
        LobsterLogCategory.PHONE,
        LobsterEventType.ERROR,
        "place_emergency_call",
        errorCode = "FALL_FAMILY_CALL_FAILED",
        failedStep = "place_call"
    )
    val MAIN_THREAD_STALLED = event(
        "主线程卡死",
        LobsterReportStatus.ERROR,
        "主线程超过 8 秒未响应",
        LobsterLogCategory.SYSTEM,
        LobsterEventType.ERROR,
        "detect_main_thread_stall",
        errorCode = "MAIN_THREAD_STALLED",
        failedStep = "main_thread"
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
        CALL_PERMISSION_DENIED,
        FALL_DETECTION_STARTED,
        FALL_SENSOR_UNAVAILABLE,
        FALL_POSSIBLE_DETECTED,
        FALL_ALERT_CANCELLED,
        FALL_FAMILY_CALL_STARTED,
        FALL_FAMILY_CALL_FAILED,
        MAIN_THREAD_STALLED
    )

    private fun event(
        scene: String,
        status: LobsterReportStatus,
        summary: String,
        category: LobsterLogCategory,
        eventType: LobsterEventType,
        action: String,
        errorCode: String? = null,
        failedStep: String? = null
    ) = LobsterUsageEvent(
        scene = scene,
        status = status,
        summary = summary,
        logLine = "[使用] $summary",
        details = LobsterReportDetails(errorCode = errorCode, failedStep = failedStep),
        category = category,
        eventType = eventType,
        action = action
    )
}
