package com.yinxing.launcher.common.lobster

enum class LobsterSetting(val action: String, val label: String) {
    AUTO_ANSWER("change_auto_answer", "自动接听"),
    RETURN_HOME_AFTER_CALL("change_return_home_after_call", "通话结束返回首页"),
    FALL_DETECTION("change_fall_detection", "跌倒检测"),
    LOW_PERFORMANCE_MODE("change_low_performance_mode", "低性能模式"),
    FULL_CARD_TAP("change_full_card_tap", "整卡点击")
}

enum class LobsterContactChannel(val actionPart: String, val label: String) {
    PHONE("phone", "电话联系人"),
    WECHAT("wechat", "微信视频联系人")
}

enum class LobsterContactChange(val actionPart: String, val label: String) {
    ADDED("add", "已新增"),
    UPDATED("update", "已修改"),
    DELETED("delete", "已删除")
}

enum class LobsterPermissionTarget(val actionPart: String, val label: String) {
    PHONE("phone", "电话权限"),
    NOTIFICATION("notification", "通知权限"),
    LOCATION("location", "定位权限"),
    CONTACTS("contacts", "通讯录权限")
}

object LobsterSettingEventFactory {
    private val screenActions = mapOf(
        "StandardOverview" to "open_settings_standard",
        "ElderOverview" to "open_settings_elder",
        "Contacts" to "open_settings_contacts",
        "Calls" to "open_settings_calls",
        "Safety" to "open_settings_safety",
        "Permissions" to "open_settings_permissions",
        "Device" to "open_settings_device",
        "System" to "open_settings_system",
        "About" to "open_settings_about"
    )

    fun screenOpened(screenName: String): LobsterUsageEvent = LobsterUsageEvent(
        scene = "设置操作",
        status = LobsterReportStatus.REPORTED,
        summary = "打开设置页面",
        logLine = "[设置] 打开设置页面",
        category = LobsterLogCategory.SETTINGS,
        eventType = LobsterEventType.OPERATION,
        action = screenActions[screenName] ?: "open_settings"
    )

    fun toggleChanged(setting: LobsterSetting, enabled: Boolean): LobsterUsageEvent {
        val state = if (enabled) "开启" else "关闭"
        return LobsterUsageEvent(
            scene = "设置操作",
            status = LobsterReportStatus.SUCCESS,
            summary = "${setting.label}已$state",
            logLine = "[设置] ${setting.label}已$state",
            category = LobsterLogCategory.SETTINGS,
            eventType = LobsterEventType.OPERATION,
            action = setting.action
        )
    }

    fun contactChanged(
        channel: LobsterContactChannel,
        change: LobsterContactChange
    ) = operation(
        summary = "${channel.label}${change.label}",
        action = "${change.actionPart}_${channel.actionPart}_contact",
        category = if (channel == LobsterContactChannel.WECHAT) {
            LobsterLogCategory.WECHAT_VIDEO
        } else {
            LobsterLogCategory.PHONE
        }
    )

    fun contactChangeFailed(
        channel: LobsterContactChannel,
        change: LobsterContactChange
    ) = operation(
        summary = "${channel.label}${change.label}失败",
        action = "${change.actionPart}_${channel.actionPart}_contact",
        category = if (channel == LobsterContactChannel.WECHAT) {
            LobsterLogCategory.WECHAT_VIDEO
        } else {
            LobsterLogCategory.PHONE
        },
        status = LobsterReportStatus.ERROR,
        eventType = LobsterEventType.ERROR,
        errorCode = "CONTACT_${change.actionPart.uppercase()}_FAILED",
        failedStep = "${change.actionPart}_contact"
    )

    fun homeAppSelectionChanged(selected: Boolean) = operation(
        summary = if (selected) "首页应用已添加" else "首页应用已移除",
        action = if (selected) "add_home_app" else "remove_home_app",
        category = LobsterLogCategory.NAVIGATION
    )

    fun homeAppsReordered() = operation(
        summary = "首页应用顺序已调整",
        action = "reorder_home_apps",
        category = LobsterLogCategory.NAVIGATION
    )

    fun permissionResult(
        target: LobsterPermissionTarget,
        granted: Boolean
    ): LobsterUsageEvent {
        val result = if (granted) "已授予" else "未授予"
        return LobsterUsageEvent(
            scene = "权限申请",
            status = if (granted) LobsterReportStatus.SUCCESS else LobsterReportStatus.ERROR,
            summary = "${target.label}$result",
            logLine = "[权限] ${target.label}$result",
            details = if (granted) LobsterReportDetails() else LobsterReportDetails(
                errorCode = "PERMISSION_DENIED",
                failedStep = "request_permission"
            ),
            category = LobsterLogCategory.SETTINGS,
            eventType = if (granted) LobsterEventType.OPERATION else LobsterEventType.ERROR,
            action = "request_${target.actionPart}_permission"
        )
    }

    fun permissionRequested(target: LobsterPermissionTarget) = LobsterUsageEvent(
        scene = "权限申请",
        status = LobsterReportStatus.REPORTED,
        summary = "开始申请${target.label}",
        logLine = "[权限] 开始申请${target.label}",
        category = LobsterLogCategory.SETTINGS,
        eventType = LobsterEventType.OPERATION,
        action = "request_${target.actionPart}_permission"
    )

    fun defaultLauncherResult(enabled: Boolean) = operation(
        summary = if (enabled) "默认桌面设置成功" else "默认桌面未设置成功",
        action = "set_default_launcher",
        status = if (enabled) LobsterReportStatus.SUCCESS else LobsterReportStatus.ERROR,
        eventType = if (enabled) LobsterEventType.OPERATION else LobsterEventType.ERROR,
        errorCode = if (enabled) null else "DEFAULT_LAUNCHER_NOT_SET",
        failedStep = if (enabled) null else "request_default_launcher"
    )

    fun autoAnswerDelayChanged() = operation(
        summary = "自动接听延迟已修改",
        action = "change_auto_answer_delay"
    )

    fun darkModeChanged() = operation(
        summary = "深色模式已修改",
        action = "change_dark_mode"
    )

    private fun operation(
        summary: String,
        action: String,
        category: LobsterLogCategory = LobsterLogCategory.SETTINGS,
        status: LobsterReportStatus = LobsterReportStatus.SUCCESS,
        eventType: LobsterEventType = LobsterEventType.OPERATION,
        errorCode: String? = null,
        failedStep: String? = null
    ) = LobsterUsageEvent(
        scene = "细操作",
        status = status,
        summary = summary,
        logLine = "[操作] $summary",
        details = LobsterReportDetails(errorCode = errorCode, failedStep = failedStep),
        category = category,
        eventType = eventType,
        action = action
    )
}
