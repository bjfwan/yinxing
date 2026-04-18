package com.bajianfeng.launcher.feature.incoming

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.telecom.TelecomManager
import android.util.Log
import android.view.KeyEvent
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.common.util.CallAudioStrategy
import com.bajianfeng.launcher.data.home.LauncherPreferences

class IncomingCallActivity : AppCompatActivity() {

    private lateinit var tvCaller: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvCountdown: TextView
    private lateinit var btnAccept: CardView
    private lateinit var btnDecline: CardView

    private var countDownTimer: CountDownTimer? = null
    private var actionInProgress = false

    companion object {
        private const val TAG = "IncomingCallActivity"

        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_AUTO_ANSWER = "extra_auto_answer"
        const val EXTRA_TRIGGER_ACTION = "extra_trigger_action"

        const val TRIGGER_ACTION_ACCEPT = "trigger_accept"
        const val TRIGGER_ACTION_DECLINE = "trigger_decline"

        fun buildLaunchIntent(
            context: Context,
            callerName: String?,
            autoAnswer: Boolean = false,
            triggerAction: String? = null
        ): Intent = Intent(context, IncomingCallActivity::class.java).apply {
            callerName?.let { putExtra(EXTRA_CALLER_NAME, it) }
            putExtra(EXTRA_AUTO_ANSWER, autoAnswer)
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
        setContentView(R.layout.activity_incoming_call)

        tvCaller = findViewById(R.id.tv_incoming_caller)
        tvStatus = findViewById(R.id.tv_incoming_status)
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
        applyIntent(intent)
    }

    override fun onDestroy() {
        hideCountdown()
        super.onDestroy()
    }

    private fun applyIntent(intent: Intent) {
        val callerName = resolveCallerName(intent.getStringExtra(EXTRA_CALLER_NAME))
        tvCaller.text = callerName
        IncomingCallDiagnostics.recordActivityShown(this, callerName)
        renderStatus()

        when (intent.getStringExtra(EXTRA_TRIGGER_ACTION)) {
            TRIGGER_ACTION_ACCEPT -> {
                acceptCall()
                return
            }
            TRIGGER_ACTION_DECLINE -> {
                declineCall()
                return
            }
        }

        val preferences = LauncherPreferences.getInstance(this)
        val autoAnswer =
            intent.getBooleanExtra(EXTRA_AUTO_ANSWER, false) && preferences.isAutoAnswerEnabled()

        if (autoAnswer) {
            startCountdown(preferences.getAutoAnswerDelaySeconds())
        } else {
            hideCountdown()
        }
    }

    private fun resetUiState() {
        actionInProgress = false
        setActionButtonsEnabled(true)
        hideCountdown()
    }

    private fun renderStatus() {
        tvStatus.text = IncomingCallDiagnostics.getDisplayText(this)
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
        tvStatus.text = getString(R.string.incoming_call_status_answering)

        val result = answerRingingCall()
        if (result.success) {
            IncomingCallDiagnostics.recordAcceptSuccess(this, result.detail)
            applyAnsweredCallAudioStrategy()
            IncomingCallForegroundService.stop(this)
        } else {
            IncomingCallDiagnostics.recordAcceptFailure(this, result.detail)
        }
        renderStatus()
        finish()
    }

    private fun declineCall() {
        if (actionInProgress) return
        actionInProgress = true
        setActionButtonsEnabled(false)
        hideCountdown()
        tvStatus.text = getString(R.string.incoming_call_status_declining)

        val result = endRingingCall()
        if (result.success) {
            IncomingCallDiagnostics.recordDeclineSuccess(this, result.detail)
            IncomingCallForegroundService.stop(this)
        } else {
            IncomingCallDiagnostics.recordDeclineFailure(this, result.detail)
        }
        renderStatus()
        finish()
    }

    private fun answerRingingCall(): CallActionResult {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "answerRingingCall: ANSWER_PHONE_CALLS permission missing")
                    return CallActionResult(
                        success = false,
                        detail = getString(R.string.incoming_call_status_permission_missing)
                    )
                }
                val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                telecomManager.acceptRingingCall()
                Log.i(TAG, "answerRingingCall: acceptRingingCall() called")
            } else {
                sendHookBroadcast()
                Log.i(TAG, "answerRingingCall: HEADSETHOOK broadcast sent")
            }
            CallActionResult(
                success = true,
                detail = getString(R.string.incoming_call_status_accept_sent)
            )
        } catch (throwable: Throwable) {
            Log.e(
                TAG,
                "answerRingingCall: FAILED - ${throwable::class.simpleName}: ${throwable.message}"
            )
            CallActionResult(
                success = false,
                detail = getString(
                    R.string.incoming_call_status_action_failed,
                    getString(R.string.incoming_call_accept),
                    throwable.message ?: getString(R.string.incoming_call_status_unknown_error)
                )
            )
        }
    }

    private fun endRingingCall(): CallActionResult {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                return CallActionResult(
                    success = false,
                    detail = getString(R.string.incoming_call_status_decline_unsupported)
                )
            }
            if (checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "endRingingCall: ANSWER_PHONE_CALLS permission missing")
                return CallActionResult(
                    success = false,
                    detail = getString(R.string.incoming_call_status_permission_missing)
                )
            }
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecomManager.endCall()
            CallActionResult(
                success = true,
                detail = getString(R.string.incoming_call_status_decline_sent)
            )
        } catch (throwable: Throwable) {
            Log.e(TAG, "endRingingCall: FAILED - ${throwable::class.simpleName}: ${throwable.message}")
            CallActionResult(
                success = false,
                detail = getString(
                    R.string.incoming_call_status_action_failed,
                    getString(R.string.incoming_call_decline),
                    throwable.message ?: getString(R.string.incoming_call_status_unknown_error)
                )
            )
        }
    }

    private fun applyAnsweredCallAudioStrategy() {
        val result = CallAudioStrategy.prepareSystemCall(this)
        if (result.keptPrivateOutput) {
            IncomingCallDiagnostics.recordSpeakerKeptPrivate(this)
            return
        }
        if (result.speakerEnabled) {
            IncomingCallDiagnostics.recordSpeakerEnabled(this)
            Log.i(TAG, "applyAnsweredCallAudioStrategy: call volume=max speakerphone enabled")
        }
    }

    private fun sendHookBroadcast() {
        sendOrderedBroadcast(Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            putExtra(
                Intent.EXTRA_KEY_EVENT,
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK)
            )
        }, null)
        sendOrderedBroadcast(Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            putExtra(
                Intent.EXTRA_KEY_EVENT,
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK)
            )
        }, null)
    }

    private fun setActionButtonsEnabled(enabled: Boolean) {
        btnAccept.isEnabled = enabled
        btnDecline.isEnabled = enabled
        btnAccept.alpha = if (enabled) 1f else 0.72f
        btnDecline.alpha = if (enabled) 1f else 0.72f
    }

    private fun resolveCallerName(rawCallerName: String?): String {
        return rawCallerName?.trim()?.takeIf { it.isNotEmpty() }
            ?: getString(R.string.incoming_call_unknown_caller)
    }

    private data class CallActionResult(
        val success: Boolean,
        val detail: String
    )
}
