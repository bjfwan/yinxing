package com.bajianfeng.launcher.feature.incoming

import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.bajianfeng.launcher.R

/**
 * 微信来电全屏页。
 *
 * 由 WeChatIncomingCallService 通过 LocalBroadcast 触发启动。
 * 页面覆盖锁屏（FLAG_SHOW_WHEN_LOCKED + FLAG_TURN_SCREEN_ON），
 * 提供大字体接听/拒绝按钮，适合老年用户。
 *
 * 接听：触发通知 Action PendingIntent → 微信自动接通；若无 PendingIntent 则直接启动微信。
 * 拒绝：触发拒绝 PendingIntent（若有），并 cancelNotification，然后 finish()。
 * 来电取消：WeChatIncomingCallService 广播 ACTION_DISMISS 后自动关闭页面。
 */
class IncomingCallActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "IncomingCallActivity"

        const val EXTRA_CALLER_NAME = IncomingCallBroadcast.EXTRA_CALLER_NAME
        const val EXTRA_NOTIFICATION_KEY = IncomingCallBroadcast.EXTRA_NOTIFICATION_KEY
        const val EXTRA_ACCEPT_INTENT = IncomingCallBroadcast.EXTRA_ACCEPT_INTENT
        const val EXTRA_DECLINE_INTENT = IncomingCallBroadcast.EXTRA_DECLINE_INTENT

        fun buildLaunchIntent(
            context: Context,
            callerName: String?,
            notificationKey: String,
            acceptIntent: PendingIntent?,
            declineIntent: PendingIntent?
        ): Intent {
            return Intent(context, IncomingCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_CALLER_NAME, callerName)
                putExtra(EXTRA_NOTIFICATION_KEY, notificationKey)
                putExtra(EXTRA_ACCEPT_INTENT, acceptIntent)
                putExtra(EXTRA_DECLINE_INTENT, declineIntent)
            }
        }
    }

    private var notificationKey: String? = null
    private var acceptPendingIntent: PendingIntent? = null
    private var declinePendingIntent: PendingIntent? = null

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val key = intent?.getStringExtra(IncomingCallBroadcast.EXTRA_NOTIFICATION_KEY)
            if (key == notificationKey) {
                Log.d(TAG, "收到 ACTION_DISMISS，来电已取消，关闭页面")
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 覆盖锁屏 & 唤醒屏幕
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(KeyguardManager::class.java)
            km?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        setContentView(R.layout.activity_incoming_call)

        notificationKey = intent.getStringExtra(EXTRA_NOTIFICATION_KEY)
        acceptPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_ACCEPT_INTENT, PendingIntent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_ACCEPT_INTENT)
        }
        declinePendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_DECLINE_INTENT, PendingIntent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_DECLINE_INTENT)
        }

        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME)
        Log.d(TAG, "来电页已打开: caller=$callerName key=$notificationKey")

        val tvCaller = findViewById<TextView>(R.id.tv_incoming_caller)
        tvCaller.text = callerName ?: getString(R.string.incoming_call_unknown_caller)

        val btnAccept = findViewById<CardView>(R.id.btn_incoming_accept)
        val btnDecline = findViewById<CardView>(R.id.btn_incoming_decline)

        btnAccept.setOnClickListener { handleAccept() }
        btnDecline.setOnClickListener { handleDecline() }

        // 监听来电取消广播（微信挂断或超时）
        LocalBroadcastManager.getInstance(this).registerReceiver(
            dismissReceiver,
            IntentFilter(IncomingCallBroadcast.ACTION_DISMISS)
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 多路来电刷新（singleTop）
        setIntent(intent)
        notificationKey = intent.getStringExtra(EXTRA_NOTIFICATION_KEY)
        acceptPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_ACCEPT_INTENT, PendingIntent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_ACCEPT_INTENT)
        }
        declinePendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_DECLINE_INTENT, PendingIntent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_DECLINE_INTENT)
        }
        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME)
        Log.d(TAG, "来电页刷新: caller=$callerName key=$notificationKey")
        findViewById<TextView>(R.id.tv_incoming_caller).text =
            callerName ?: getString(R.string.incoming_call_unknown_caller)
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(dismissReceiver)
        super.onDestroy()
    }

    private fun handleAccept() {
        Log.d(TAG, "用户点击接听")
        val pi = acceptPendingIntent
        if (pi != null) {
            runCatching { pi.send() }.onFailure { e ->
                Log.e(TAG, "接听 PendingIntent 发送失败，回退到启动微信", e)
                launchWeChatFallback()
            }
        } else {
            Log.d(TAG, "无接听 PendingIntent，直接启动微信")
            launchWeChatFallback()
        }
        finish()
    }

    private fun handleDecline() {
        Log.d(TAG, "用户点击拒绝")
        val pi = declinePendingIntent
        if (pi != null) {
            runCatching { pi.send() }.onFailure { e ->
                Log.e(TAG, "拒绝 PendingIntent 发送失败", e)
            }
        }
        // 撤销通知，防止通知栏残留
        notificationKey?.let { WeChatIncomingCallService.cancelNotification(it) }
        finish()
    }

    private fun launchWeChatFallback() {
        runCatching {
            val intent = packageManager.getLaunchIntentForPackage("com.tencent.mm")
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent != null) startActivity(intent)
        }.onFailure { e ->
            Log.e(TAG, "启动微信失败", e)
        }
    }
}
