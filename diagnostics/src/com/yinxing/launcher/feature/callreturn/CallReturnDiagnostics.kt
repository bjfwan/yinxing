package com.yinxing.launcher.feature.callreturn

import android.content.Context
import com.yinxing.launcher.common.lobster.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal enum class CallReturnStage(val code: String, val terminal: Boolean = false) {
    ARMED("return_session_armed"), CONFIRMED("return_session_confirmed"),
    CANCELLED("return_session_cancelled"), ATTEMPT("return_home_attempt"),
    COMPLETED("return_home_completed", true), BLOCKED("return_home_blocked", true),
    NOT_ATTEMPTED("return_home_not_attempted", true), EXPIRED("return_home_expired", true)
}

internal class CallReturnDiagnosticSession(
    val requestId: String,
    private val origin: CallReturnOrigin,
    private val report: (LobsterUsageEvent) -> Unit,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val startedAt = now()
    private val stages = linkedSetOf<CallReturnStage>()
    private val steps = mutableListOf<LobsterTraceStep>()
    var evidence = LobsterCallEvidence()
        private set
    var terminal = false
        private set

    @Synchronized
    fun evidence(value: LobsterCallEvidence) {
        if (!terminal) evidence = value.copy(
            completeCalled = evidence.completeCalled || value.completeCalled,
            startActivityCalled = evidence.startActivityCalled || value.startActivityCalled
        )
    }

    @Synchronized
    fun emit(stage: CallReturnStage, reason: LobsterCallReason = evidence.reason, foreground: LobsterForeground = evidence.foreground) {
        if (terminal || !stages.add(stage)) return
        if (stage.terminal) terminal = true
        evidence = evidence.copy(reason = reason, foreground = foreground,
            completeCalled = evidence.completeCalled || stage == CallReturnStage.ATTEMPT)
        val error = stage == CallReturnStage.BLOCKED
        val status = when { error -> LobsterReportStatus.ERROR; stage == CallReturnStage.COMPLETED -> LobsterReportStatus.SUCCESS; else -> LobsterReportStatus.REPORTED }
        val code = when (stage) {
            CallReturnStage.COMPLETED -> "CALL_RETURN_HOME_OK"
            CallReturnStage.BLOCKED -> "CALL_RETURN_HOME_BLOCKED"
            CallReturnStage.NOT_ATTEMPTED -> "CALL_RETURN_HOME_NOT_ATTEMPTED"
            CallReturnStage.EXPIRED -> "CALL_RETURN_HOME_EXPIRED"
            else -> null
        }
        steps += LobsterTraceStep(stage.code, stage.code, stage.code,
            when (status) { LobsterReportStatus.SUCCESS -> LobsterStepOutcome.SUCCESS; LobsterReportStatus.ERROR -> LobsterStepOutcome.ERROR; else -> LobsterStepOutcome.REPORTED },
            detail = evidence.safeLine(), durationMs = (now() - startedAt).coerceIn(0, 86_400_000),
            occurredAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(now())))
        report(LobsterUsageEvent(
            scene = "通话返回首页", status = status,
            summary = "${code ?: stage.code} · ${reason.name.lowercase()}",
            logLine = "${code ?: stage.code} origin=${origin.name.lowercase()} ${evidence.safeLine()}",
            details = LobsterReportDetails(traceId = requestId, reportType = "call_return", terminal = stage.terminal,
                resultCode = code, errorCode = code.takeIf { error }, failedStep = "home_verify".takeIf { error },
                steps = steps.takeLast(12), callEvidence = evidence),
            category = if (origin == CallReturnOrigin.SYSTEM_PHONE) LobsterLogCategory.PHONE else LobsterLogCategory.WECHAT_VIDEO,
            eventType = if (error) LobsterEventType.ERROR else LobsterEventType.DIAGNOSTIC, action = stage.code
        ))
    }
}

internal object CallReturnDiagnostics {
    private var app: Context? = null
    private val sessions = linkedMapOf<String, CallReturnDiagnosticSession>()

    @Synchronized
    fun begin(context: Context, origin: CallReturnOrigin, requestId: String): CallReturnDiagnosticSession {
        app = context.applicationContext
        return sessions.getOrPut(requestId) {
            CallReturnDiagnosticSession(requestId, origin, { event -> app?.let { LobsterClient.reportUsage(it, event) } })
        }.also {
            while (sessions.size > 32) sessions.remove(sessions.keys.first())
        }
    }

    @Synchronized fun session(requestId: String?): CallReturnDiagnosticSession? = sessions[requestId]
}
