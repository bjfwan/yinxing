package com.google.android.accessibility.selecttospeak

import com.yinxing.launcher.common.lobster.LobsterEventType
import com.yinxing.launcher.common.lobster.LobsterFailureSample
import com.yinxing.launcher.common.lobster.LobsterLogCategory
import com.yinxing.launcher.common.lobster.LobsterReportDetails
import com.yinxing.launcher.common.lobster.LobsterReportStatus
import com.yinxing.launcher.common.lobster.LobsterTraceStep
import com.yinxing.launcher.common.lobster.LobsterUsageEvent

internal object WeChatFailureReportFactory {
    const val REPORT_TYPE = "wechat_failure_sample_v1"
    private val safeStepCode = Regex("^[A-Za-z0-9_.-]{1,80}$")
    private val safeAction = Regex("^[a-z][a-z0-9_.-]{0,119}$")
    private val safeTimestamp = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?Z$")

    fun create(
        sample: LobsterFailureSample,
        traceId: String,
        contactName: String,
        steps: List<LobsterTraceStep>,
    ): LobsterUsageEvent = LobsterUsageEvent(
        scene = "微信视频失败样本",
        status = LobsterReportStatus.ERROR,
        summary = "$REPORT_TYPE ${sample.failureCode}",
        logLine = buildString {
            append("[微信失败样本] report_type=").append(REPORT_TYPE)
            append(", fingerprint=").append(sample.fingerprint)
            append(", error=").append(sample.failureCode)
            sample.failedStep?.let { append(", step=").append(it) }
            sample.capability?.let { append(", capability=").append(it) }
            sample.capabilityFailure?.let { append(", failure=").append(it) }
            sample.uiState.semanticPage?.let { append(", page=").append(it) }
            sample.uiState.route?.let { append(", route=").append(it) }
        },
        details = LobsterReportDetails(
            traceId = traceId,
            errorCode = sample.failureCode,
            failedStep = sample.failedStep,
            reportType = REPORT_TYPE,
            steps = steps.takeLast(100).mapNotNull { step ->
                val stepCode = step.stepCode.trim().lowercase()
                val action = step.action.trim().lowercase()
                if (!safeStepCode.matches(stepCode) || !safeAction.matches(action)) {
                    return@mapNotNull null
                }
                step.copy(
                    stepCode = stepCode,
                    stepName = "",
                    action = action,
                    detail = null,
                    durationMs = step.durationMs?.coerceIn(0L, 86_400_000L),
                    occurredAt = step.occurredAt.takeIf(safeTimestamp::matches).orEmpty(),
                )
            },
            failureSample = sample,
            sensitiveValues = listOf(contactName),
        ),
        category = LobsterLogCategory.WECHAT_VIDEO,
        eventType = LobsterEventType.ERROR,
        action = "upload_wechat_failure_sample",
    )
}
