package com.yinxing.launcher.automation.wechat.teaching

import com.yinxing.launcher.common.lobster.LobsterEventType
import com.yinxing.launcher.common.lobster.LobsterLogCategory
import com.yinxing.launcher.common.lobster.LobsterReportDetails
import com.yinxing.launcher.common.lobster.LobsterReportStatus
import com.yinxing.launcher.common.lobster.LobsterStepOutcome
import com.yinxing.launcher.common.lobster.LobsterTraceStep
import com.yinxing.launcher.common.lobster.LobsterUsageEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class WeChatTeachingUploadOutcome {
    SUCCEEDED,
    FAILED,
    INCOMPLETE,
    UNKNOWN_ROUTE
}

enum class WeChatTeachingUploadFailureReason {
    NONE,
    STEP_TIMEOUT,
    VOICE_CALL,
    VIDEO_NOT_CONFIRMED,
    LOW_RELIABILITY,
    USER_CANCELLED,
    UNKNOWN
}

object WeChatTeachingRouteUploadFactory {
    const val REPORT_TYPE = "wechat_teaching_route_v1"
    private val safeId = Regex("^[A-Za-z0-9_-]{1,80}$")
    private val safeResourceId = Regex("^com\\.tencent\\.mm:id/[A-Za-z0-9_]+$")
    private val safeClassName = Regex("^(?:android|androidx|com\\.tencent\\.mm)\\.[A-Za-z0-9_.$]+$")

    fun create(
        route: WeChatTeachingRoute,
        sessionId: String,
        outcome: WeChatTeachingUploadOutcome,
        replayResult: WeChatTeachingReplayResult,
        failureReason: WeChatTeachingUploadFailureReason,
        missingEventCount: Int
    ): LobsterUsageEvent {
        val safeRouteId = route.routeId.takeIf(safeId::matches) ?: "invalid-route"
        val safeSessionId = sessionId.takeIf(safeId::matches) ?: "invalid-session"
        return LobsterUsageEvent(
            scene = "微信视频示教",
            status = LobsterReportStatus.REPORTED,
            summary = "$REPORT_TYPE outcome=${outcome.name} replay=${replayResult.name}",
            logLine = buildString {
                append("[微信示教路线] report_type=").append(REPORT_TYPE)
                append(", route_id=").append(safeRouteId)
                append(", session_id=").append(safeSessionId)
                append(", outcome=").append(outcome.name)
                append(", replay=").append(replayResult.name)
                append(", failure=").append(failureReason.name)
                route.lastFailureStep?.let { append(", failed_step=").append(it.coerceAtLeast(0)) }
                append(", missing_events=").append(missingEventCount.coerceIn(0, 999))
                append(", lifecycle=").append(route.lifecycle.name)
                append(", reliability=").append(route.reliabilityScore.coerceIn(0, 100))
                append(", device=").append(route.fingerprint.manufacturer.take(40))
                append('/').append(route.fingerprint.model.take(40))
                append(", android_sdk=").append(route.fingerprint.androidSdk)
                append(", wechat=").append(route.fingerprint.weChatVersionName.take(40))
                append('/').append(route.fingerprint.weChatVersionCode)
            },
            details = LobsterReportDetails(
                reportType = REPORT_TYPE,
                steps = buildList {
                    add(
                        LobsterTraceStep(
                            stepCode = "ROUTE_START",
                            stepName = "ROUTE_START",
                            action = "state",
                            outcome = LobsterStepOutcome.REPORTED,
                            detail = "route=$safeRouteId;session=$safeSessionId;${safeState(route.startState)}",
                            occurredAt = isoTimestamp(route.createdAtEpochMs)
                        )
                    )
                    route.steps.forEachIndexed { index, step ->
                        add(
                            LobsterTraceStep(
                                stepCode = "STEP_${index}_${step.type.name}",
                                stepName = step.type.name,
                                action = "route_step",
                                outcome = LobsterStepOutcome.REPORTED,
                                detail = safeStep(step),
                                occurredAt = isoTimestamp(route.createdAtEpochMs + index)
                            )
                        )
                    }
                    add(
                        LobsterTraceStep(
                            stepCode = "ROUTE_RESULT",
                            stepName = route.endEvidence.name,
                            action = "route_result",
                            outcome = if (outcome == WeChatTeachingUploadOutcome.SUCCEEDED) {
                                LobsterStepOutcome.SUCCESS
                            } else {
                                LobsterStepOutcome.ERROR
                            },
                            detail = "outcome=${outcome.name};replay=${replayResult.name};" +
                                "failure=${failureReason.name};" +
                                (route.lastFailureStep?.let { "failed_step=${it.coerceAtLeast(0)};" } ?: "") +
                                "missing_events=${missingEventCount.coerceIn(0, 999)}",
                            occurredAt = isoTimestamp(route.lastVerifiedAtEpochMs ?: route.createdAtEpochMs)
                        )
                    )
                }
            ),
            category = LobsterLogCategory.WECHAT_VIDEO,
            eventType = LobsterEventType.OPERATION,
            action = "upload_wechat_teaching_route"
        )
    }

    fun createUnknown(
        fingerprint: WeChatTeachingFingerprint,
        sessionId: String,
        outcome: WeChatTeachingUploadOutcome,
        replayResult: WeChatTeachingReplayResult,
        failureReason: WeChatTeachingUploadFailureReason,
        missingEventCount: Int,
        createdAtEpochMs: Long
    ): LobsterUsageEvent = create(
        route = WeChatTeachingRoute(
            routeId = sessionId.takeIf(safeId::matches) ?: "unknown-route",
            fingerprint = fingerprint,
            startState = WeChatTeachingStateFingerprint(null, emptySet(), emptySet()),
            steps = emptyList(),
            endEvidence = WeChatTeachingRouteEndEvidence.VIDEO_CALL_CONFIRMED,
            source = WeChatTeachingRouteSource.DEMONSTRATION,
            priority = 0,
            reliabilityScore = 0,
            lifecycle = WeChatTeachingRouteLifecycle.CANDIDATE,
            createdAtEpochMs = createdAtEpochMs
        ),
        sessionId = sessionId,
        outcome = outcome,
        replayResult = replayResult,
        failureReason = failureReason,
        missingEventCount = missingEventCount
    )

    private fun safeStep(step: WeChatTeachingRouteStep): String = buildList {
        add("type=${step.type.name}")
        step.selector?.resourceId?.takeIf(safeResourceId::matches)?.let { add("id=$it") }
        step.selector?.nodeClass?.takeIf(safeClassName::matches)?.let { add("class=$it") }
        step.selector?.semanticLabel?.let { add("label=${it.name}") }
        step.selector?.centerXRatio?.takeIf { it in 0f..1f }?.let { add("x=${ratio(it)}") }
        step.selector?.centerYRatio?.takeIf { it in 0f..1f }?.let { add("y=${ratio(it)}") }
        step.expectedState?.let { add(safeState(it)) }
        add("wait_ms=${step.maxWaitMs.coerceIn(500L, 30_000L)}")
    }.joinToString(";")

    private fun safeState(state: WeChatTeachingStateFingerprint): String = buildList {
        state.windowClass?.takeIf(safeClassName::matches)?.let { add("window=$it") }
        if (state.semanticLabels.isNotEmpty()) {
            add("labels=${state.semanticLabels.map { it.name }.sorted().joinToString(",")}")
        }
        val safeIds = state.resourceIds.filter(safeResourceId::matches).sorted()
        if (safeIds.isNotEmpty()) add("ids=${safeIds.joinToString(",")}")
    }.joinToString(";")

    private fun ratio(value: Float): String = String.format(Locale.US, "%.3f", value)

    private fun isoTimestamp(epochMs: Long): String = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        Locale.US
    ).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(epochMs.coerceAtLeast(0L)))
}
