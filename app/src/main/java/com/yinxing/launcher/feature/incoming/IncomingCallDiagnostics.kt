package com.yinxing.launcher.feature.incoming

import android.content.Context
import androidx.core.content.edit
import com.yinxing.launcher.R
import com.yinxing.launcher.common.util.DebugLog
import com.yinxing.launcher.common.util.PermissionUtil
import com.yinxing.launcher.data.home.LauncherPreferences

object IncomingCallDiagnostics {

    private const val PREFS_NAME = "incoming_call_diagnostics"
    private const val KEY_CHAIN = "chain"
    private const val KEY_CALLER_LABEL = "caller_label"
    private const val KEY_DETAIL = "detail"
    private const val KEY_FAILURE_CATEGORY = "failure_category"
    private const val KEY_FAILURE_DETAIL = "failure_detail"
    private const val TAG = "IncomingCallTrace"
    private const val SEPARATOR = "|"

    private enum class Step(val code: String, val labelResId: Int) {
        BroadcastReceived("broadcast_received", R.string.incoming_call_trace_broadcast),
        BroadcastFailed("broadcast_failed", R.string.incoming_call_trace_broadcast_failed),
        ForegroundServiceStarted("foreground_service_started", R.string.incoming_call_trace_service),
        ForegroundServiceFailed("foreground_service_failed", R.string.incoming_call_trace_service_failed),
        ActivityShown("activity_shown", R.string.incoming_call_trace_activity),
        AcceptSucceeded("accept_succeeded", R.string.incoming_call_trace_accept_success),
        AcceptFailed("accept_failed", R.string.incoming_call_trace_accept_failed),
        DeclineSucceeded("decline_succeeded", R.string.incoming_call_trace_decline_success),
        DeclineFailed("decline_failed", R.string.incoming_call_trace_decline_failed),
        SpeakerEnabled("speaker_enabled", R.string.incoming_call_trace_speaker_on),
        SpeakerKeptPrivate("speaker_kept_private", R.string.incoming_call_trace_speaker_kept_private);

        companion object {
            fun fromCode(code: String): Step? = entries.firstOrNull { it.code == code }
        }
    }

    fun clear(context: Context) {
        prefs(context).edit { clear() }
        IncomingCallSessionState.idle()
    }

    fun recordBroadcastReceived(
        context: Context,
        callerLabel: String?,
        incomingNumber: String?,
        autoAnswer: Boolean
    ) {
        val resolvedCallerLabel = resolveCallerLabel(context, callerLabel, incomingNumber)
        replace(
            context = context,
            steps = listOf(Step.BroadcastReceived),
            callerLabel = resolvedCallerLabel,
            detail = joinDetail(
                resolvedCallerLabel,
                context.getString(
                    if (autoAnswer) R.string.incoming_call_trace_detail_auto_answer
                    else R.string.incoming_call_trace_detail_manual_answer
                ),
                guardSnapshot(context).displayText()
            )
        )
    }

    fun recordBroadcastFailure(
        context: Context,
        callerLabel: String?,
        incomingNumber: String?,
        throwable: Throwable
    ) {
        val resolvedCallerLabel = resolveCallerLabel(context, callerLabel, incomingNumber)
        val reason = IncomingCallFailureReason(
            category = IncomingCallFailureCategory.Broadcast,
            detail = throwable.message ?: throwable.javaClass.simpleName
        )
        replace(
            context = context,
            steps = listOf(Step.BroadcastFailed),
            callerLabel = resolvedCallerLabel,
            detail = joinDetail(
                resolvedCallerLabel,
                throwable.javaClass.simpleName,
                throwable.message,
                guardSnapshot(context).displayText()
            ),
            throwable = throwable,
            failureReason = reason
        )
        IncomingCallSessionState.failed(reason)
    }

    fun recordServiceStarted(context: Context, callerLabel: String?, autoAnswer: Boolean) {
        val resolvedCallerLabel = callerLabel?.trim()?.takeIf { it.isNotEmpty() }
            ?: storedCallerLabel(context)
        append(
            context = context,
            step = Step.ForegroundServiceStarted,
            callerLabel = resolvedCallerLabel,
            detail = joinDetail(
                resolvedCallerLabel,
                context.getString(
                    if (autoAnswer) R.string.incoming_call_trace_detail_auto_answer
                    else R.string.incoming_call_trace_detail_manual_answer
                ),
                guardSnapshot(context).displayText()
            )
        )
    }

    fun recordForegroundServiceStartResult(
        context: Context,
        callerLabel: String?,
        started: Boolean,
        throwable: Throwable? = null
    ) {
        val resolvedCallerLabel = callerLabel?.trim()?.takeIf { it.isNotEmpty() }
            ?: storedCallerLabel(context)
        val reason = if (started) null else IncomingCallFailureReason(
            category = IncomingCallFailureCategory.ForegroundService,
            detail = throwable?.message ?: throwable?.javaClass?.simpleName
        )
        append(
            context = context,
            step = if (started) Step.ForegroundServiceStarted else Step.ForegroundServiceFailed,
            callerLabel = resolvedCallerLabel,
            detail = joinDetail(
                resolvedCallerLabel,
                guardSnapshot(context, foregroundServiceStarted = started).displayText(),
                throwable?.javaClass?.simpleName,
                throwable?.message
            ),
            throwable = throwable,
            failureReason = reason
        )
        if (reason != null) {
            IncomingCallSessionState.failed(reason)
        }
    }

    fun recordServiceStartFailure(
        context: Context,
        callerLabel: String?,
        throwable: Throwable
    ) {
        val resolvedCallerLabel = callerLabel?.trim()?.takeIf { it.isNotEmpty() }
            ?: storedCallerLabel(context)
        val reason = IncomingCallFailureReason(
            category = IncomingCallFailureCategory.ForegroundService,
            detail = throwable.message ?: throwable.javaClass.simpleName
        )
        append(
            context = context,
            step = Step.ForegroundServiceFailed,
            callerLabel = resolvedCallerLabel,
            detail = joinDetail(
                resolvedCallerLabel,
                throwable.javaClass.simpleName,
                throwable.message,
                guardSnapshot(context, foregroundServiceStarted = false).displayText()
            ),
            throwable = throwable,
            failureReason = reason
        )
        IncomingCallSessionState.failed(reason)
    }

    fun recordActivityShown(context: Context, callerLabel: String?) {
        val resolvedCallerLabel = callerLabel?.trim()?.takeIf { it.isNotEmpty() }
            ?: storedCallerLabel(context)
        append(
            context = context,
            step = Step.ActivityShown,
            callerLabel = resolvedCallerLabel,
            detail = joinDetail(resolvedCallerLabel, guardSnapshot(context).displayText())
        )
    }

    fun recordActivityLaunchFailure(context: Context, throwable: Throwable) {
        val reason = IncomingCallFailureReason(
            category = IncomingCallFailureCategory.BackgroundStart,
            detail = throwable.message ?: throwable.javaClass.simpleName
        )
        append(
            context = context,
            step = Step.ForegroundServiceFailed,
            detail = joinDetail(
                storedCallerLabel(context),
                throwable.javaClass.simpleName,
                throwable.message,
                guardSnapshot(context).displayText()
            ),
            throwable = throwable,
            failureReason = reason
        )
        IncomingCallSessionState.failed(reason)
    }

    fun recordAcceptSuccess(context: Context, detail: String) {
        append(
            context = context,
            step = Step.AcceptSucceeded,
            detail = joinDetail(storedCallerLabel(context), detail, guardSnapshot(context).displayText())
        )
    }

    fun recordAcceptFailure(
        context: Context,
        detail: String,
        failureReason: IncomingCallFailureReason? = null
    ) {
        append(
            context = context,
            step = Step.AcceptFailed,
            detail = joinDetail(storedCallerLabel(context), detail, guardSnapshot(context).displayText()),
            failureReason = failureReason
        )
        failureReason?.let(IncomingCallSessionState::failed)
    }

    fun recordDeclineSuccess(context: Context, detail: String) {
        append(
            context = context,
            step = Step.DeclineSucceeded,
            detail = joinDetail(storedCallerLabel(context), detail, guardSnapshot(context).displayText())
        )
    }

    fun recordDeclineFailure(
        context: Context,
        detail: String,
        failureReason: IncomingCallFailureReason? = null
    ) {
        append(
            context = context,
            step = Step.DeclineFailed,
            detail = joinDetail(storedCallerLabel(context), detail, guardSnapshot(context).displayText()),
            failureReason = failureReason
        )
        failureReason?.let(IncomingCallSessionState::failed)
    }

    fun recordSpeakerEnabled(context: Context) {
        append(
            context = context,
            step = Step.SpeakerEnabled,
            detail = joinDetail(
                storedCallerLabel(context),
                context.getString(R.string.incoming_call_status_speaker_on),
                guardSnapshot(context).displayText()
            )
        )
    }

    fun recordSpeakerKeptPrivate(context: Context) {
        append(
            context = context,
            step = Step.SpeakerKeptPrivate,
            detail = joinDetail(
                storedCallerLabel(context),
                context.getString(R.string.incoming_call_status_speaker_kept_private),
                guardSnapshot(context).displayText()
            )
        )
    }

    fun getSummaryText(context: Context): String {
        val labels = readSteps(context).map { context.getString(it.labelResId) }
        return labels.joinToString(" · ").ifBlank {
            context.getString(R.string.settings_incoming_trace_empty)
        }
    }

    fun getDisplayText(context: Context): String {
        val summary = getSummaryText(context)
        val detail = prefs(context).getString(KEY_DETAIL, null).orEmpty().trim()
        val failure = readFailureReason(context)?.let { "失败分类：${it.displayText()}" }
        if (summary == context.getString(R.string.settings_incoming_trace_empty)) {
            return summary
        }
        return listOf(summary, detail, failure)
            .mapNotNull { it?.trim()?.takeIf { value -> value.isNotEmpty() } }
            .joinToString("\n")
    }

    fun getNotificationStatusText(context: Context): String {
        return readSteps(context)
            .lastOrNull()
            ?.let { context.getString(it.labelResId) }
            ?: context.getString(R.string.incoming_call_trace_service)
    }

    private fun replace(
        context: Context,
        steps: List<Step>,
        callerLabel: String?,
        detail: String,
        throwable: Throwable? = null,
        failureReason: IncomingCallFailureReason? = null
    ) {
        write(context, steps, callerLabel, detail, failureReason)
        log(steps.last(), detail, throwable, failureReason)
    }

    private fun append(
        context: Context,
        step: Step,
        callerLabel: String? = null,
        detail: String,
        throwable: Throwable? = null,
        failureReason: IncomingCallFailureReason? = null
    ) {
        val existingSteps = readSteps(context)
        val nextSteps = buildList {
            addAll(existingSteps)
            if (!contains(step)) {
                add(step)
            }
        }
        val storedFailure = failureReason ?: readFailureReason(context)
        write(context, nextSteps, callerLabel ?: storedCallerLabel(context), detail, storedFailure)
        log(step, detail, throwable, storedFailure)
    }

    private fun write(
        context: Context,
        steps: List<Step>,
        callerLabel: String?,
        detail: String,
        failureReason: IncomingCallFailureReason?
    ) {
        prefs(context).edit {
            putString(KEY_CHAIN, steps.joinToString(SEPARATOR) { it.code })
            putString(KEY_CALLER_LABEL, callerLabel?.trim().orEmpty())
            putString(KEY_DETAIL, detail.trim())
            if (failureReason == null) {
                remove(KEY_FAILURE_CATEGORY)
                remove(KEY_FAILURE_DETAIL)
            } else {
                putString(KEY_FAILURE_CATEGORY, failureReason.category.code)
                putString(KEY_FAILURE_DETAIL, failureReason.detail.orEmpty())
            }
        }
    }

    private fun readSteps(context: Context): List<Step> {
        return prefs(context)
            .getString(KEY_CHAIN, null)
            .orEmpty()
            .split(SEPARATOR)
            .mapNotNull(Step::fromCode)
    }

    private fun readFailureReason(context: Context): IncomingCallFailureReason? {
        val category = IncomingCallFailureCategory.fromCode(
            prefs(context).getString(KEY_FAILURE_CATEGORY, null)
        ) ?: return null
        val detail = prefs(context)
            .getString(KEY_FAILURE_DETAIL, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        return IncomingCallFailureReason(category, detail)
    }

    private fun storedCallerLabel(context: Context): String {
        return prefs(context)
            .getString(KEY_CALLER_LABEL, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: context.getString(R.string.incoming_call_unknown_caller)
    }

    private fun resolveCallerLabel(
        context: Context,
        callerLabel: String?,
        incomingNumber: String?
    ): String {
        return callerLabel?.trim()?.takeIf { it.isNotEmpty() }
            ?: incomingNumber?.trim()?.takeIf { it.isNotEmpty() }
            ?: context.getString(R.string.incoming_call_unknown_caller)
    }

    private fun guardSnapshot(
        context: Context,
        foregroundServiceStarted: Boolean? = null
    ): IncomingGuardDiagnosticSnapshot {
        val appContext = context.applicationContext
        val preferences = runCatching { LauncherPreferences.getInstance(appContext) }.getOrNull()
        return IncomingGuardDiagnosticEvaluator.evaluate(
            hasNotificationPermission = safeReady { PermissionUtil.hasNotificationPermission(appContext) },
            canDrawOverlays = safeReady { PermissionUtil.canDrawOverlays(appContext) },
            backgroundStartConfirmed = preferences?.isBackgroundStartConfirmed() == true,
            hasAccessibilityService = safeReady { PermissionUtil.isAnyAccessibilityServiceEnabled(appContext) },
            ignoresBatteryOptimizations = safeReady { PermissionUtil.isIgnoringBatteryOptimizations(appContext) },
            foregroundServiceStarted = foregroundServiceStarted
        )
    }

    private fun safeReady(block: () -> Boolean): Boolean {
        return runCatching(block).getOrDefault(false)
    }

    private fun joinDetail(vararg values: String?): String {
        return values
            .mapNotNull { it?.trim()?.takeIf { value -> value.isNotEmpty() } }
            .distinct()
            .joinToString(" · ")
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun log(
        step: Step,
        detail: String,
        throwable: Throwable? = null,
        failureReason: IncomingCallFailureReason? = null
    ) {
        val fullDetail = joinDetail(detail, failureReason?.displayText())
        if (throwable != null) {
            DebugLog.e(TAG, "${step.code}: $fullDetail", throwable)
        } else {
            DebugLog.i(TAG) { "${step.code}: $fullDetail" }
        }
    }
}
