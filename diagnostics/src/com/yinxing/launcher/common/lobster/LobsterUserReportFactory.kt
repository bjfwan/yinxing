package com.yinxing.launcher.common.lobster

enum class LobsterUserReportType(val wireValue: String, val label: String) {
    FUNCTION("function", "功能问题"),
    DISPLAY("display", "显示问题"),
    PERFORMANCE("performance", "卡顿或速度慢"),
    STABILITY("stability", "闪退或无响应"),
    OTHER("other", "其他问题")
}

object LobsterUserReportFactory {
    private const val MAX_TEXT_LENGTH = 800

    fun create(
        type: LobsterUserReportType,
        description: String,
        reproductionSteps: String? = null,
        traceId: String = LobsterTrace.newId()
    ): LobsterUsageEvent? {
        val cleanDescription = sanitize(description) ?: return null
        val cleanSteps = sanitize(reproductionSteps)
        return LobsterUsageEvent(
            scene = "用户反馈",
            status = LobsterReportStatus.REPORTED,
            summary = "用户反馈：${type.label}",
            logLine = "[用户反馈] 类型=${type.label}",
            details = LobsterReportDetails(
                traceId = traceId,
                reportType = type.wireValue,
                userDescription = cleanDescription,
                reproductionSteps = cleanSteps
            ),
            category = LobsterLogCategory.FEEDBACK,
            eventType = LobsterEventType.DIAGNOSTIC,
            action = "submit_user_report"
        )
    }

    private fun sanitize(value: String?): String? {
        val clean = value?.trim()?.take(MAX_TEXT_LENGTH)?.takeIf(String::isNotEmpty) ?: return null
        return LobsterLogSanitizer.sanitize(clean)
    }
}
