package com.bajianfeng.launcher.feature.incoming

import android.app.KeyguardManager
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
 * 接听：调用 WeChatIncomingCallService.performAction(key, ACCEPT_KEYWORDS)
 *       在服务进程内触发通知 Action，绕开微信对跨进程 PendingIntent 的保护。
 *       若服务未连接则 fallback 到直接打开微信。
 *
 * 拒绝：调用 WeChatIncomingCallService.performAction(key, DECLINE_KEYWORDS)
 *       并 cancelNotification 清除通知栏残留。
 *
 * 来电取消：监听 ACTION_DISMISS 广播，自动 finish()。
 */
class IncomingCallActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "IncomingCallActivity"

        const val EXTRA_CALLER_NAME          = IncomingCallBroadcast.EXTRA_CALLER_NAME
        const val EXTRA_NOTIFICATION_KEY     = IncomingCallBroadcast.EXTRA_NOTIFICATION_KEY
        const val EXTRA_ACCEPT_ACTION_INDEX  = IncomingCallBroadcast.EXTRA_ACCEPT_ACTION_INDEX
        const val EXTRA_DECLINE_ACTION_INDEX = IncomingCallBroadcast.EXTRA_DECLINE_ACTION_INDEX

        fun buildLaunchIntent(
            context: Context,
            callerName: String?,
            notificationKey: String,
            acceptActionIndex: Int,
            declineActionIndex: Int
        ): Intent {
            return Intent(context, IncomingCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_CALLER_NAME, callerName)
                putExtra(EXTRA_NOTIFICATION_KEY, notificationKey)
                putExtra(EXTRA_ACCEPT_ACTION_INDEX, acceptActionIndex)
                putExtra(EXTRA_DECLINE_ACTION_INDEX, declineActionIndex)
            }
        }
    }

    private var notificationKey: String? = null
    private var acceptActionIndex: Int = -1
    private var declineActionIndex: Int = -1

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

        loadExtras(intent)

        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME)
        Log.d(TAG, "来电页已打开: caller=$callerName key=$notificationKey acceptIdx=$acceptActionIndex declineIdx=$declineActionIndex")

        val tvCaller = findViewById<TextView>(R.id.tv_incoming_caller)
        tvCaller.text = callerName?.takeIf { it.isNotBlank() }
            ?: getString(R.string.incoming_call_unknown_caller)

        val btnAccept  = findViewById<CardView>(R.id.btn_incoming_accept)
        val btnDecline = findViewById<CardView>(R.id.btn_incoming_decline)
        btnAccept.setOnClickListener  { handleAccept() }
        btnDecline.setOnClickListener { handleDecline() }

        LocalBroadcastManager.getInstance(this).registerReceiver(
            dismissReceiver,
            IntentFilter(IncomingCallBroadcast.ACTION_DISMISS)
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        loadExtras(intent)
        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME)
        Log.d(TAG, "来电页刷新: caller=$callerName key=$notificationKey")
        findViewById<TextView>(R.id.tv_incoming_caller).text =
            callerName?.takeIf { it.isNotBlank() }
                ?: getString(R.string.incoming_call_unknown_caller)
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(dismissReceiver)
        super.onDestroy()
    }

    // ── 操作处理 ──────────────────────────────────────────────────────────────

    private fun handleAccept() {
        Log.d(TAG, "用户点击接听 key=$notificationKey acceptIdx=$acceptActionIndex")
        val key = notificationKey
        if (key != null && acceptActionIndex >= 0) {
            val ok = WeChatIncomingCallService.performAction(key, listOf("接受", "接听", "接通", "Accept"))
            Log.d(TAG, "performAction(accept) result=$ok")
            if (!ok) {
                // 服务未连接或 Action 触发失败，直接打开微信让用户手动接听
                launchWeChatFallback()
            }
        } else {
            Log.d(TAG, "无有效 acceptAction，直接打开微信")
            launchWeChatFallback()
        }
        finish()
    }

    private fun handleDecline() {
        Log.d(TAG, "用户点击拒绝 key=$notificationKey declineIdx=$declineActionIndex")
        val key = notificationKey
        if (key != null && declineActionIndex >= 0) {
            val ok = WeChatIncomingCallService.performAction(key, listOf("拒绝", "挂断", "拒接", "Decline", "忽略"))
            Log.d(TAG, "performAction(decline) result=$ok")
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

    // ── 工具 ──────────────────────────────────────────────────────────────────

    private fun loadExtras(intent: Intent) {
        notificationKey     = intent.getStringExtra(EXTRA_NOTIFICATION_KEY)
        acceptActionIndex   = intent.getIntExtra(EXTRA_ACCEPT_ACTION_INDEX, -1)
        declineActionIndex  = intent.getIntExtra(EXTRA_DECLINE_ACTION_INDEX, -1)
    }
}
