package com.yinxing.launcher.feature.incoming

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.yinxing.launcher.R

object IncomingCallDiagnostics {

    private const val PREFS_NAME = "incoming_call_diagnostics"
    private const val KEY_CHAIN = "chain"
    private const val KEY_CALLER_LABEL = "caller_label"
    private const val KEY_DETAIL = "detail"
    private const val TAG = "IncomingCallTrace"
    private const val SEPARATOR = "|"

    private enum class Step(val code: String, val labelResId: Int) {
        BroadcastReceived("broadcast_received", R.string.incoming_call_trace_broadcast),
        ForegroundServiceStarted("foreground_service_started", R.string.incoming_call_trace_service),
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
                )
            )
        )
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
                )
            )
        )
    }

    fun recordActivityShown(context: Context, callerLabel: String?) {
        val resolvedCallerLabel = callerLabel?.trim()?.takeIf { it.isNotEmpty() }
            ?: storedCallerLabel(context)
        append(
            context = context,
            step = Step.ActivityShown,
            callerLabel = resolvedCallerLabel,
            detail = resolvedCallerLabel
        )
    }

    fun recordAcceptSuccess(context: Context, detail: String) {
        append(
            context = context,
            step = Step.AcceptSucceeded,
            detail = joinDetail(storedCallerLabel(context), detail)
        )
    }

    fun recordAcceptFailure(context: Context, detail: String) {
        append(
            context = context,
            step = Step.AcceptFailed,
            detail = joinDetail(storedCallerLabel(context), detail)
        )
    }

    fun recordDeclineSuccess(context: Context, detail: String) {
        append(
            context = context,
            step = Step.DeclineSucceeded,
            detail = joinDetail(storedCallerLabel(context), detail)
        )
    }

    fun recordDeclineFailure(context: Context, detail: String) {
        append(
            context = context,
            step = Step.DeclineFailed,
            detail = joinDetail(storedCallerLabel(context), detail)
        )
    }

    fun recordSpeakerEnabled(context: Context) {
        append(
            context = context,
            step = Step.SpeakerEnabled,
            detail = joinDetail(
                storedCallerLabel(context),
                context.getString(R.string.incoming_call_status_speaker_on)
            )
        )
    }

    fun recordSpeakerKeptPrivate(context: Context) {
        append(
            context = context,
            step = Step.SpeakerKeptPrivate,
            detail = joinDetail(
                storedCallerLabel(context),
                context.getString(R.string.incoming_call_status_speaker_kept_private)
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
        if (summary == context.getString(R.string.settings_incoming_trace_empty)) {
            return summary
        }
        return if (detail.isEmpty()) summary else "$summary\n$detail"
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
        detail: String
    ) {
        write(context, steps, callerLabel, detail)
        log(steps.last(), detail)
    }

    private fun append(
        context: Context,
        step: Step,
        callerLabel: String? = null,
        detail: String
    ) {
        val existingSteps = readSteps(context)
        val nextSteps = buildList {
            addAll(existingSteps)
            if (!contains(step)) {
                add(step)
            }
        }
        write(context, nextSteps, callerLabel ?: storedCallerLabel(context), detail)
        log(step, detail)
    }

    private fun write(
        context: Context,
        steps: List<Step>,
        callerLabel: String?,
        detail: String
    ) {
        prefs(context).edit {
            putString(KEY_CHAIN, steps.joinToString(SEPARATOR) { it.code })
            putString(KEY_CALLER_LABEL, callerLabel?.trim().orEmpty())
            putString(KEY_DETAIL, detail.trim())
        }
    }

    private fun readSteps(context: Context): List<Step> {
        return prefs(context)
            .getString(KEY_CHAIN, null)
            .orEmpty()
            .split(SEPARATOR)
            .mapNotNull(Step::fromCode)
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

    private fun joinDetail(vararg values: String?): String {
        return values
            .mapNotNull { it?.trim()?.takeIf { value -> value.isNotEmpty() } }
            .distinct()
            .joinToString(" · ")
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun log(step: Step, detail: String) {
        Log.i(TAG, "${step.code}: $detail")
    }
}
