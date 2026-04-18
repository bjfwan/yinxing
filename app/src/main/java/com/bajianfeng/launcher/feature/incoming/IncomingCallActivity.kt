package com.bajianfeng.launcher.feature.incoming

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build

import android.os.Bundle
import android.os.CountDownTimer
import android.telecom.TelecomManager
import android.util.Log
import android.view.KeyEvent
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible

import androidx.cardview.widget.CardView
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.data.home.LauncherPreferences

class IncomingCallActivity : AppCompatActivity() {

    private lateinit var tvCaller: TextView
    private lateinit var tvCountdown: TextView
    private lateinit var btnAccept: CardView
    private lateinit var btnDecline: CardView

    private var countDownTimer: CountDownTimer? = null
    private var handled = false

    companion object {
        private const val TAG = "IncomingCallActivity"

        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_AUTO_ANSWER = "extra_auto_answer"
        const val EXTRA_TRIGGER_ACTION = "extra_trigger_action"

        const val TRIGGER_ACTION_ACCEPT = "trigger_accept"
        const val TRIGGER_ACTION_DECLINE = "trigger_decline"

        @Deprecated("Use buildLaunchIntent(context, callerName, autoAnswer)")
        const val EXTRA_NOTIFICATION_KEY = "extra_notification_key"
        @Deprecated("Use buildLaunchIntent(context, callerName, autoAnswer)")
        const val EXTRA_ACCEPT_ACTION_INDEX = "extra_accept_action_index"
        @Deprecated("Use buildLaunchIntent(context, callerName, autoAnswer)")
        const val EXTRA_DECLINE_ACTION_INDEX = "extra_decline_action_index"

        fun buildLaunchIntent(
            context: Context,
            callerName: String?,
            autoAnswer: Boolean = false,
            notificationKey: String? = null,
            acceptActionIndex: Int = -1,
            declineActionIndex: Int = -1,
            triggerAction: String? = null
        ): Intent = Intent(context, IncomingCallActivity::class.java).apply {
            callerName?.let { putExtra(EXTRA_CALLER_NAME, it) }
            putExtra(EXTRA_AUTO_ANSWER, autoAnswer)
            notificationKey?.let { putExtra(EXTRA_NOTIFICATION_KEY, it) }
            putExtra(EXTRA_ACCEPT_ACTION_INDEX, acceptActionIndex)
            putExtra(EXTRA_DECLINE_ACTION_INDEX, declineActionIndex)
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
        countDownTimer?.cancel()
        handled = false
        applyIntent(intent)
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        super.onDestroy()
    }

    private fun applyIntent(intent: Intent) {
        val rawName = intent.getStringExtra(EXTRA_CALLER_NAME)
        val callerName = rawName?.trim()?.takeIf { it.isNotEmpty() }
            ?: getString(R.string.incoming_call_unknown_caller)
        tvCaller.text = callerName

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

        val prefs = LauncherPreferences.getInstance(this)
        val autoAnswer = intent.getBooleanExtra(EXTRA_AUTO_ANSWER, false) && prefs.isAutoAnswerEnabled()

        if (autoAnswer) {
            val delaySec = prefs.getAutoAnswerDelaySeconds()
            startCountdown(delaySec)
        } else {
            tvCountdown.text = ""
        }
    }

    private fun startCountdown(seconds: Int) {
        tvCountdown.isVisible = true
        tvCountdown.text = getString(R.string.incoming_call_auto_answer_countdown, seconds)
        countDownTimer = object : CountDownTimer(seconds * 1000L, 1000L) {

            override fun onTick(millisUntilFinished: Long) {
                val remaining = ((millisUntilFinished + 999) / 1000).toInt()
                tvCountdown.text = getString(R.string.incoming_call_auto_answer_countdown, remaining)
            }

            override fun onFinish() {
                tvCountdown.text = ""
                tvCountdown.isVisible = false
                acceptCall()
            }

        }.start()
    }

    private fun acceptCall() {
        if (handled) return
        handled = true
        countDownTimer?.cancel()
        answerRingingCall()
        IncomingCallForegroundService.stop(this)
        finish()
    }

    private fun declineCall() {
        if (handled) return
        handled = true
        countDownTimer?.cancel()
        endRingingCall()
        IncomingCallForegroundService.stop(this)
        finish()
    }

    private fun answerRingingCall() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "answerRingingCall: ANSWER_PHONE_CALLS permission missing")
                    return@runCatching
                }
                val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                telecomManager.acceptRingingCall()
                Log.i(TAG, "answerRingingCall: acceptRingingCall() called (API ${Build.VERSION.SDK_INT})")
            } else {
                @Suppress("DEPRECATION")
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.isWiredHeadsetOn
                sendOrderedBroadcast(Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK))
                }, null)
                sendOrderedBroadcast(Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK))
                }, null)
                Log.i(TAG, "answerRingingCall: HEADSETHOOK broadcast sent (API ${Build.VERSION.SDK_INT})")
            }
        }.onFailure { e ->
            Log.e(TAG, "answerRingingCall: FAILED - ${e::class.simpleName}: ${e.message}")
        }
    }

    private fun endRingingCall() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "endRingingCall: ANSWER_PHONE_CALLS permission missing")
                    return@runCatching
                }
                val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                telecomManager.endCall()
            } else {
                @Suppress("DEPRECATION")
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.isWiredHeadsetOn
            }
        }
    }

}
