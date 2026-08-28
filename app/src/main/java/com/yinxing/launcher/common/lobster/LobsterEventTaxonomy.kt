package com.yinxing.launcher.common.lobster

enum class LobsterLogCategory(val wireValue: String) {
    STARTUP("startup"), NAVIGATION("navigation"), PHONE("phone"),
    INCOMING_CALL("incoming_call"), WEATHER("weather"), WECHAT_VIDEO("wechat_video"),
    SETTINGS("settings"), SYSTEM("system"), CRASH("crash"), TELEMETRY("telemetry"),
    FEEDBACK("feedback"), OTHER("other")
}

enum class LobsterEventType(val wireValue: String) {
    OPERATION("operation"), LIFECYCLE("lifecycle"), DIAGNOSTIC("diagnostic"),
    ERROR("error"), HEALTH("health")
}

data class LobsterEventTaxonomy(
    val category: LobsterLogCategory,
    val eventType: LobsterEventType,
    val action: String?
) {
    companion object {
        fun infer(
            scene: String,
            status: LobsterReportStatus,
            summary: String?,
            errorCode: String? = null
        ): LobsterEventTaxonomy {
            val text = listOfNotNull(scene, summary, errorCode).joinToString(" ")
            val category = when {
                text.contains("用户反馈") || text.contains("user report", true) -> LobsterLogCategory.FEEDBACK
                text.contains("崩溃") || text.contains("crash", true) || text.contains("uncaught", true) -> LobsterLogCategory.CRASH
                text.contains("微信") || text.contains("wechat", true) -> LobsterLogCategory.WECHAT_VIDEO
                text.contains("来电") || text.contains("接听") || text.contains("incoming", true) -> LobsterLogCategory.INCOMING_CALL
                text.contains("天气") || text.contains("weather", true) -> LobsterLogCategory.WEATHER
                text.contains("电话") || text.contains("拨号") || text.contains("phone", true) -> LobsterLogCategory.PHONE
                text.contains("设置") || text.contains("权限") -> LobsterLogCategory.SETTINGS
                text.contains("客户端启动") -> LobsterLogCategory.STARTUP
                text.contains("首页") || text.contains("入口") || text.contains("应用启动") -> LobsterLogCategory.NAVIGATION
                text.contains("上报") || text.contains("补传") -> LobsterLogCategory.TELEMETRY
                text.contains("系统") -> LobsterLogCategory.SYSTEM
                else -> LobsterLogCategory.OTHER
            }
            val action = when {
                category == LobsterLogCategory.FEEDBACK -> "submit_user_report"
                text.contains("关闭") && category == LobsterLogCategory.WECHAT_VIDEO -> "close_video_page"
                text.contains("接听") -> "answer_call"
                text.contains("挂断") -> "hang_up_call"
                text.contains("天气") && text.contains("打开") -> "open_weather"
                text.contains("拨号") -> "place_call"
                text.contains("微信") && text.contains("打开") -> "open_video_page"
                text.contains("客户端启动") -> "start_client"
                else -> errorCode?.lowercase()?.replace(Regex("[^a-z0-9]+"), "_")?.trim('_')?.take(80)
            }
            val eventType = when {
                status == LobsterReportStatus.ERROR || errorCode != null -> LobsterEventType.ERROR
                action != null && action != "start_client" && !action.startsWith("close_") -> LobsterEventType.OPERATION
                text.contains("启动") || text.contains("关闭") || text.contains("页面") -> LobsterEventType.LIFECYCLE
                category == LobsterLogCategory.TELEMETRY -> LobsterEventType.HEALTH
                else -> LobsterEventType.DIAGNOSTIC
            }
            return LobsterEventTaxonomy(category, eventType, action)
        }
    }
}
