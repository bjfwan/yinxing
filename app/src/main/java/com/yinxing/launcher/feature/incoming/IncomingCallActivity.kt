package com.bajianfeng.launcher.feature.incoming

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.tts.TextToSpeech
import android.telecom.TelecomManager
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import com.yinxing.launcher.R
import com.bajianfeng.launcher.common.util.CallAudioStrategy
import com.bajianfeng.launcher.data.home.LauncherPreferences
import java.util.Locale

class IncomingCallActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tvCaller: TextView
    private lateinit var tvCountdown: TextView
    private lateinit var btnAccept: CardView
    private lateinit var btnDecline: CardView

    private var countDownTimer: CountDownTimer? = null
    private var actionInProgress = false
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var pendingAnnouncement: String? = null
    private var lastAnnouncedCaller: String? = null

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
        configureWindowForIncomingCall()
        setContentView(R.layout.activity_incoming_call)
        initializeVoiceAnnouncement()

        tvCaller = findViewById(R.id.tv_incoming_caller)
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
        releaseVoiceAnnouncement()
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        val engine = textToSpeech ?: return
        if (status != TextToSpeech.SUCCESS) {
            ttsReady = false
            pendingAnnouncement = null
            return
        }
        val localeResult = engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
        ttsReady = localeResult != TextToSpeech.LANG_MISSING_DATA &&
            localeResult != TextToSpeech.LANG_NOT_SUPPORTED
        if (!ttsReady) {
            val fallbackResult = engine.setLanguage(Locale.CHINESE)
            ttsReady = fallbackResult != TextToSpeech.LANG_MISSING_DATA &&
                fallbackResult != TextToSpeech.LANG_NOT_SUPPORTED
        }
        if (!ttsReady) {
            pendingAnnouncement = null
            return
        }
        engine.setSpeechRate(0.92f)
        engine.setPitch(1.0f)
        speakPendingAnnouncement()
    }

    private fun applyIntent(intent: Intent) {
        val callerName = resolveCallerName(intent.getStringExtra(EXTRA_CALLER_NAME))
        val preferences = LauncherPreferences.getInstance(this)
        val triggerAction = intent.getStringExtra(EXTRA_TRIGGER_ACTION)
        val autoAnswer =
            intent.getBooleanExtra(EXTRA_AUTO_ANSWER, false) && preferences.isAutoAnswerEnabled()

        tvCaller.text = callerName
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
        stopVoiceAnnouncement()

        val result = answerRingingCall()
        if (result.success) {
            IncomingCallDiagnostics.recordAcceptSuccess(this, result.detail)
            applyAnsweredCallAudioStrategy()
            IncomingCallForegroundService.stop(this)
            finish()
            return
        }

        IncomingCallDiagnostics.recordAcceptFailure(this, result.detail)
        Toast.makeText(this, result.detail, Toast.LENGTH_SHORT).show()
        actionInProgress = false
        setActionButtonsEnabled(true)
    }

    private fun declineCall() {
        if (actionInProgress) return
        actionInProgress = true
        setActionButtonsEnabled(false)
        hideCountdown()
        stopVoiceAnnouncement()

        val result = endRingingCall()
        if (result.success) {
            IncomingCallDiagnostics.recordDeclineSuccess(this, result.detail)
            IncomingCallForegroundService.stop(this)
            finish()
            return
        }

        IncomingCallDiagnostics.recordDeclineFailure(this, result.detail)
        Toast.makeText(this, result.detail, Toast.LENGTH_SHORT).show()
        actionInProgress = false
        setActionButtonsEnabled(true)
    }

    private fun configureWindowForIncomingCall() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    @Suppress("DEPRECATION")
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

    @Suppress("DEPRECATION")
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

    private fun initializeVoiceAnnouncement() {
        if (textToSpeech != null) return
        textToSpeech = TextToSpeech(this, this)
    }

    private fun announceCallerIfNeeded(callerName: String, triggerAction: String?) {
        if (triggerAction != null || callerName == lastAnnouncedCaller) {
            return
        }
        lastAnnouncedCaller = callerName
        pendingAnnouncement = if (callerName == getString(R.string.incoming_call_unknown_caller)) {
            getString(R.string.incoming_call_voice_unknown)
        } else {
            getString(R.string.incoming_call_voice_announcement, callerName)
        }
        speakPendingAnnouncement()
    }

    private fun speakPendingAnnouncement() {
        if (!ttsReady) return
        val announcement = pendingAnnouncement ?: return
        val engine = textToSpeech ?: return
        pendingAnnouncement = null
        engine.stop()
        engine.speak(
            announcement,
            TextToSpeech.QUEUE_FLUSH,
            Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_RING)
            },
            "incoming_call_announcement"
        )
    }

    private fun stopVoiceAnnouncement() {
        pendingAnnouncement = null
        textToSpeech?.stop()
    }

    private fun releaseVoiceAnnouncement() {
        pendingAnnouncement = null
        ttsReady = false
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
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
