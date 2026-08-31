package com.yinxing.launcher.feature.fall

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.widget.TextView
import com.yinxing.launcher.common.ui.FontScaleActivity
import com.yinxing.launcher.R
import com.yinxing.launcher.common.service.TTSService
import com.yinxing.launcher.data.home.LauncherPreferences

class FallAlertActivity : FontScaleActivity() {
    private val stateMachine = FallAlertStateMachine()
    private lateinit var countdownView: TextView
    private var countDownTimer: CountDownTimer? = null
    private var ttsService: TTSService? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenWindow()
        setContentView(R.layout.activity_fall_alert)

        countdownView = findViewById(R.id.fall_alert_countdown)
        renderCountdown(FallAlertStateMachine.DEFAULT_COUNTDOWN_SECONDS)
        val contact = LauncherPreferences.getInstance(this).getFallEmergencyContact()
        findViewById<TextView>(R.id.fall_alert_contact).text = getString(
            R.string.fall_alert_contact,
            maskNumber(contact)
        )
        findViewById<android.view.View>(R.id.fall_alert_cancel).setOnClickListener {
            if (stateMachine.cancel() is FallAlertState.Cancelled) {
                FallDetectionService.resolveFalseAlarm(this)
                finish()
            }
        }
        findViewById<android.view.View>(R.id.fall_alert_call_now).setOnClickListener {
            callFamilyNow()
        }

        startVoiceAndVibration()
        startCountdown()
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        ttsService?.shutdown()
        vibrator?.cancel()
        super.onDestroy()
    }

    private fun startCountdown() {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(
            FallAlertStateMachine.DEFAULT_COUNTDOWN_SECONDS * 1_000L,
            1_000L
        ) {
            override fun onTick(millisUntilFinished: Long) {
                val remaining = ((millisUntilFinished + 999L) / 1_000L).toInt()
                renderCountdown(remaining)
            }

            override fun onFinish() {
                callFamilyNow()
            }
        }.start()
    }

    private fun callFamilyNow() {
        if (stateMachine.callNow() !is FallAlertState.CallingFamily) return
        countDownTimer?.cancel()
        FallDetectionService.callFamilyNow(this)
        finish()
    }

    private fun renderCountdown(seconds: Int) {
        countdownView.text = getString(R.string.fall_alert_countdown, seconds)
    }

    private fun startVoiceAndVibration() {
        ttsService = TTSService(this).also { service ->
            service.initialize {
                service.speak(getString(R.string.fall_alert_message))
            }
        }
        vibrator = getSystemService(Vibrator::class.java)?.also { vibration ->
            runCatching {
                val pattern = longArrayOf(0L, 700L, 350L, 700L, 350L, 1_000L)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibration.vibrate(
                        VibrationEffect.createWaveform(pattern, 1)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibration.vibrate(pattern, 1)
                }
            }
        }
    }

    private fun configureLockScreenWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {}
            )
        }
    }

    private fun maskNumber(number: String): String {
        if (number.length <= 7) return number
        return number.take(3) + "****" + number.takeLast(4)
    }

    companion object {
        fun buildLaunchIntent(context: Context): Intent {
            return Intent(context, FallAlertActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
    }
}
