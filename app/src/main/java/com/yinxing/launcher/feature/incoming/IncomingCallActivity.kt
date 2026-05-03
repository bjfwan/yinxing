package com.yinxing.launcher.feature.incoming

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.yinxing.launcher.R
import com.yinxing.launcher.common.lobster.LobsterClient
import com.yinxing.launcher.common.perf.LauncherTraceNames
import com.yinxing.launcher.common.perf.traceAndReport
import com.yinxing.launcher.common.util.CallAudioStrategy
import com.yinxing.launcher.common.util.DebugLog
import com.yinxing.launcher.data.home.LauncherPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class IncomingCallActivity : AppCompatActivity() {

    private lateinit var tvCaller: TextView
    private lateinit var tvRisk: TextView
    private lateinit var tvCountdown: TextView
    private lateinit var btnAccept: CardView
    private lateinit var btnDecline: CardView

    private var countDownTimer: CountDownTimer? = null
    private var riskJob: Job? = null
    private var actionInProgress = false
    private val platformCompat = IncomingPlatformCompat()
    private lateinit var actions: IncomingCallActions
    private var announcer: IncomingCallAnnouncer? = null

    companion object {
        private const val TAG = "IncomingCallActivity"

        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_AUTO_ANSWER = "extra_auto_answer"
        const val EXTRA_INCOMING_NUMBER = "extra_incoming_number"
        const val EXTRA_KNOWN_CONTACT = "extra_known_contact"
        const val EXTRA_TRIGGER_ACTION = "extra_trigger_action"

        const val TRIGGER_ACTION_ACCEPT = "trigger_accept"
        const val TRIGGER_ACTION_DECLINE = "trigger_decline"

        fun buildLaunchIntent(
            context: Context,
            callerName: String?,
            autoAnswer: Boolean = false,
            incomingNumber: String? = null,
            knownContact: Boolean = false,
            triggerAction: String? = null
        ): Intent = Intent(context, IncomingCallActivity::class.java).apply {
            callerName?.let { putExtra(EXTRA_CALLER_NAME, it) }
            putExtra(EXTRA_AUTO_ANSWER, autoAnswer)
            incomingNumber?.let { putExtra(EXTRA_INCOMING_NUMBER, it) }
            putExtra(EXTRA_KNOWN_CONTACT, knownContact)
            triggerAction?.let { putExtra(EXTRA_TRIGGER_ACTION, it) }
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWindowForIncomingCall()
        setContentView(R.layout.activity_incoming_call)
        actions = IncomingCallActions(this, platformCompat)
        announcer = IncomingCallAnnouncer(this)

        tvCaller = findViewById(R.id.tv_incoming_caller)
        tvRisk = findViewById(R.id.tv_incoming_risk)
        tvCountdown = findViewById(R.id.tv_incoming_countdown)
        btnAccept = findViewById(R.id.btn_incoming_accept)
        btnDecline = findViewById(R.id.btn_incoming_decline)

        btnAccept.setOnClickListener { acceptCall() }
        btnDecline.setOnClickListener { declineCall() }

        applyIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resetUiState()
        announcer?.reset()
        applyIntent(intent)
    }

    override fun onDestroy() {
        riskJob?.cancel()
        hideCountdown()
        announcer?.shutdown()
        announcer = null
        super.onDestroy()
    }

    private fun applyIntent(intent: Intent) {
        val callerName = resolveCallerName(intent.getStringExtra(EXTRA_CALLER_NAME))
        val incomingNumber = intent.getStringExtra(EXTRA_INCOMING_NUMBER).orEmpty()
        val knownContact = intent.getBooleanExtra(EXTRA_KNOWN_CONTACT, false)
        val preferences = LauncherPreferences.getInstance(this)
        val triggerAction = intent.getStringExtra(EXTRA_TRIGGER_ACTION)
        
        val intentAutoAnswer = intent.getBooleanExtra(EXTRA_AUTO_ANSWER, false)
        val prefAutoAnswerEnabled = preferences.isAutoAnswerEnabled()
        val autoAnswer = intentAutoAnswer && prefAutoAnswerEnabled
        
        DebugLog.banner(
            "INCOMING_FLOW",
            listOf(
                "[来电处理] 执行 applyIntent",
                "├─ 来电者: $callerName",
                "├─ 最终决定: ${if (autoAnswer) "开启自动接听" else "关闭自动接听"}",
                "└─ 判定逻辑: 意图参数=$intentAutoAnswer && 设置开关=$prefAutoAnswerEnabled"
            )
        )

        LobsterClient.log("[来电处理] applyIntent | 来电者: $callerName | 决定: $autoAnswer (意图: $intentAutoAnswer, 设置: $prefAutoAnswerEnabled)")

        val state = IncomingCallSessionState.uiShown(
            callerLabel = callerName,
            autoAnswer = autoAnswer,
            autoAnswerDelaySeconds = preferences.getAutoAnswerDelaySeconds()
        )

        tvCaller.text = callerName
        renderRisk(null)
        IncomingCallDiagnostics.recordActivityShown(this, callerName)
        announceCallerIfNeeded(callerName, triggerAction)

        when (triggerAction) {
            TRIGGER_ACTION_ACCEPT -> {
                acceptCall()
                return
            }
            TRIGGER_ACTION_DECLINE -> {
                declineCall()
                return
            }
        }

        maybeAssessIncomingRisk(incomingNumber, knownContact)

        when (state) {
            is IncomingCallState.WaitingAutoAnswer -> startCountdown(state.delaySeconds)
            else -> hideCountdown()
        }
    }

    private fun resetUiState() {
        actionInProgress = false
        riskJob?.cancel()
        setActionButtonsEnabled(true)
        hideCountdown()
        renderRisk(null)
    }

    private fun startCountdown(seconds: Int) {
        hideCountdown()
        tvCountdown.isVisible = true
        tvCountdown.text = getString(R.string.incoming_call_auto_answer_countdown, seconds)
        countDownTimer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val remaining = ((millisUntilFinished + 999) / 1000).toInt()
                tvCountdown.text = getString(R.string.incoming_call_auto_answer_countdown, remaining)
            }

            override fun onFinish() {
                hideCountdown()
                acceptCall()
            }
        }.start()
    }

    private fun hideCountdown() {
        countDownTimer?.cancel()
        countDownTimer = null
        tvCountdown.text = ""
        tvCountdown.isVisible = false
    }

    private fun acceptCall() {
        if (actionInProgress) return
        actionInProgress = true
        setActionButtonsEnabled(false)
        hideCountdown()
        announcer?.stop()

        val result = answerRingingCall()
        if (result.success) {
            IncomingCallSessionState.answered()
            IncomingCallDiagnostics.recordAcceptSuccess(this, result.detail)
            applyAnsweredCallAudioStrategy()
            IncomingCallForegroundService.stop(this)
            LobsterClient.report(this, "来电已接听")
            traceAndReport(this, LauncherTraceNames.INCOMING_CALL_RESPONSE)
            finish()
            return
        }

        IncomingCallDiagnostics.recordAcceptFailure(this, result.detail, result.failureReason)
        Toast.makeText(this, result.detail, Toast.LENGTH_SHORT).show()
        actionInProgress = false
        setActionButtonsEnabled(true)
    }

    private fun declineCall() {
        if (actionInProgress) return
        actionInProgress = true
        setActionButtonsEnabled(false)
        hideCountdown()
        announcer?.stop()

        val result = endRingingCall()
        if (result.success) {
            IncomingCallSessionState.rejected()
            IncomingCallDiagnostics.recordDeclineSuccess(this, result.detail)
            IncomingCallForegroundService.stop(this)
            LobsterClient.report(this, "来电已挂断")
            traceAndReport(this, LauncherTraceNames.INCOMING_CALL_RESPONSE)
            finish()
            return
        }

        IncomingCallDiagnostics.recordDeclineFailure(this, result.detail, result.failureReason)
        Toast.makeText(this, result.detail, Toast.LENGTH_SHORT).show()
        actionInProgress = false
        setActionButtonsEnabled(true)
    }

    private fun configureWindowForIncomingCall() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (platformCompat.supportsModernLockScreenApi) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissError() {}
                    override fun onDismissSucceeded() {}
                    override fun onDismissCancelled() {}
                }
            )
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    private fun answerRingingCall(): IncomingCallActions.Result =
        actions.acceptRingingCall(
            permissionMissingDetail = getString(R.string.incoming_call_status_permission_missing),
            successDetail = getString(R.string.incoming_call_status_accept_sent),
            actionFailureFormat = { action, message ->
                getString(R.string.incoming_call_status_action_failed, action, message)
            },
            actionLabel = getString(R.string.incoming_call_accept),
            unknownErrorLabel = getString(R.string.incoming_call_status_unknown_error)
        )

    private fun endRingingCall(): IncomingCallActions.Result =
        actions.endRingingCall(
            unsupportedDetail = getString(R.string.incoming_call_status_decline_unsupported),
            permissionMissingDetail = getString(R.string.incoming_call_status_permission_missing),
            successDetail = getString(R.string.incoming_call_status_decline_sent),
            actionFailureFormat = { action, message ->
                getString(R.string.incoming_call_status_action_failed, action, message)
            },
            actionLabel = getString(R.string.incoming_call_decline),
            unknownErrorLabel = getString(R.string.incoming_call_status_unknown_error)
        )

    private fun applyAnsweredCallAudioStrategy() {
        val result = CallAudioStrategy.prepareSystemCall(this)
        if (result.keptPrivateOutput) {
            IncomingCallDiagnostics.recordSpeakerKeptPrivate(this)
            return
        }
        if (result.speakerEnabled) {
            IncomingCallDiagnostics.recordSpeakerEnabled(this)
            DebugLog.i(TAG) { "applyAnsweredCallAudioStrategy: call volume=max speakerphone enabled" }
        }
    }

    private fun setActionButtonsEnabled(enabled: Boolean) {
        btnAccept.isEnabled = enabled
        btnDecline.isEnabled = enabled
        btnAccept.alpha = if (enabled) 1f else 0.72f
        btnDecline.alpha = if (enabled) 1f else 0.72f
    }

    private fun announceCallerIfNeeded(callerName: String, triggerAction: String?) {
        if (triggerAction != null) return
        val voiceText = if (callerName == getString(R.string.incoming_call_unknown_caller)) {
            getString(R.string.incoming_call_voice_unknown)
        } else {
            getString(R.string.incoming_call_voice_announcement, callerName)
        }
        announcer?.announce(callerName, voiceText)
    }

    private fun resolveCallerName(rawCallerName: String?): String {
        return rawCallerName?.trim()?.takeIf { it.isNotEmpty() }
            ?: getString(R.string.incoming_call_unknown_caller)
    }

    private fun maybeAssessIncomingRisk(incomingNumber: String, knownContact: Boolean) {
        riskJob?.cancel()
        val assessor = IncomingCallRiskAssessor(this)
        if (knownContact || incomingNumber.isBlank()) {
            return
        }
        riskJob = lifecycleScope.launch {
            val assessment = assessor.assess(incomingNumber, knownContact) ?: return@launch
            if (isFinishing || isDestroyed) {
                return@launch
            }
            renderRisk(assessment)
        }
    }

    private fun renderRisk(assessment: IncomingCallRiskAssessment?) {
        if (assessment == null || !assessment.shouldShowWarning) {
            tvRisk.isVisible = false
            tvRisk.text = ""
            return
        }
        tvRisk.isVisible = true
        tvRisk.text = assessment.label
        val colorRes = if (assessment.level == IncomingCallRiskLevel.High) {
            R.color.launcher_danger
        } else {
            R.color.launcher_warning
        }
        tvRisk.setTextColor(ContextCompat.getColor(this, colorRes))
    }

}
