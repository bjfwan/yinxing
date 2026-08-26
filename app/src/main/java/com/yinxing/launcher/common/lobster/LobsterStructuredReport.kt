package com.yinxing.launcher.common.lobster

import org.json.JSONArray
import org.json.JSONObject

enum class LobsterStepOutcome(val wireValue: String) {
    REPORTED("reported"),
    SUCCESS("success"),
    ERROR("error")
}

data class LobsterTraceStep(
    val stepCode: String,
    val stepName: String,
    val action: String,
    val outcome: LobsterStepOutcome,
    val detail: String? = null,
    val durationMs: Long? = null,
    val occurredAt: String
)

data class LobsterReportDetails(
    val traceId: String? = null,
    val errorCode: String? = null,
    val failedStep: String? = null,
    val steps: List<LobsterTraceStep> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        traceId.clean(120)?.let { put("trace_id", it) }
        errorCode.clean(120)?.let { put("error_code", it) }
        failedStep.clean(120)?.let { put("failed_step", it) }
        if (steps.isNotEmpty()) {
            put("steps", JSONArray().apply {
                steps.take(100).forEach { step ->
                    put(JSONObject().apply {
                        put("step_code", step.stepCode.clean(80).orEmpty())
                        put("step_name", step.stepName.clean(120).orEmpty())
                        put("action", step.action.clean(120).orEmpty())
                        put("outcome", step.outcome.wireValue)
                        step.detail.clean(500)?.let { put("detail", LobsterLogSanitizer.sanitize(it)) }
                        step.durationMs?.takeIf { it >= 0 }?.let { put("duration_ms", it) }
                        put("occurred_at", step.occurredAt.clean(40).orEmpty())
                    })
                }
            })
        }
    }
}

private fun String?.clean(maxLength: Int): String? = this?.trim()?.take(maxLength)?.takeIf { it.isNotEmpty() }
