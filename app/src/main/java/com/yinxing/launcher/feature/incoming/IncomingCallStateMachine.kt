package com.yinxing.launcher.feature.incoming

import android.util.Log
import com.yinxing.launcher.common.firebase.FirebaseTelemetry

sealed class IncomingCallState {
    object Idle : IncomingCallState()
    data class Ringing(
        val callerLabel: String?,
        val incomingNumber: String?,
        val autoAnswer: Boolean
    ) : IncomingCallState()
    data class ShowingUi(
        val callerLabel: String,
        val autoAnswer: Boolean
    ) : IncomingCallState()
    data class WaitingAutoAnswer(
        val callerLabel: String,
        val delaySeconds: Int
    ) : IncomingCallState()
    object Answered : IncomingCallState()
    object Rejected : IncomingCallState()
    data class Failed(val reason: IncomingCallFailureReason) : IncomingCallState()
}

enum class IncomingCallFailureCategory(val code: String, val label: String) {
    PhonePermission("phone_permission", "电话权限"),
    NotificationPermission("notification_permission", "通知权限"),
    Overlay("overlay", "悬浮窗"),
    BackgroundStart("background_start", "后台启动"),
    Accessibility("accessibility", "无障碍"),
    BatteryOptimization("battery_optimization", "省电限制"),
    ForegroundService("foreground_service", "前台服务"),
    CallAction("call_action", "接听指令"),
    Broadcast("broadcast", "来电广播"),
    UnsupportedPlatform("unsupported_platform", "系统版本"),
    Unknown("unknown", "未知原因");

    companion object {
        fun fromCode(code: String?): IncomingCallFailureCategory? {
            return entries.firstOrNull { it.code == code }
        }
    }
}

data class IncomingCallFailureReason(
    val category: IncomingCallFailureCategory,
    val detail: String? = null
) {
    fun displayText(): String {
        val cleanDetail = detail?.trim()?.takeIf { it.isNotEmpty() }
        return if (cleanDetail == null) category.label else "${category.label}：$cleanDetail"
    }
}

class IncomingCallStateMachine(
    initialState: IncomingCallState = IncomingCallState.Idle
) {
    private var currentState: IncomingCallState = initialState

    val state: IncomingCallState
        get() = currentState

    fun ringing(
        callerLabel: String?,
        incomingNumber: String?,
        autoAnswer: Boolean
    ): IncomingCallState {
        currentState = IncomingCallState.Ringing(
            callerLabel = callerLabel?.trim()?.takeIf { it.isNotEmpty() },
            incomingNumber = incomingNumber?.trim()?.takeIf { it.isNotEmpty() },
            autoAnswer = autoAnswer
        )
        return currentState
    }

    fun foregroundServiceStarted(callerLabel: String, autoAnswer: Boolean): IncomingCallState {
        currentState = IncomingCallState.ShowingUi(
            callerLabel = callerLabel,
            autoAnswer = autoAnswer
        )
        return currentState
    }

    fun uiShown(
        callerLabel: String,
        autoAnswer: Boolean,
        autoAnswerDelaySeconds: Int
    ): IncomingCallState {
        Log.i("INCOMING_STATE", "╔══════════════════════════════════════════════════════")
        Log.i("INCOMING_STATE", "║ [来电状态机] 计算 UI 状态")
        Log.i("INCOMING_STATE", "║ ├─ 自动接听开关: $autoAnswer")
        Log.i("INCOMING_STATE", "║ ├─ 延迟秒数: $autoAnswerDelaySeconds")
        
        currentState = if (autoAnswer) {
            Log.i("INCOMING_STATE", "║ └─ 最终状态: WaitingAutoAnswer (等待自动接听)")
            IncomingCallState.WaitingAutoAnswer(
                callerLabel = callerLabel,
                delaySeconds = autoAnswerDelaySeconds.coerceIn(1, 30)
            )
        } else {
            Log.i("INCOMING_STATE", "║ └─ 最终状态: ShowingUi (仅显示界面)")
            IncomingCallState.ShowingUi(
                callerLabel = callerLabel,
                autoAnswer = false
            )
        }
        Log.i("INCOMING_STATE", "╚══════════════════════════════════════════════════════")
        
        FirebaseTelemetry.withCrashlytics {
            log("[来电状态] uiShown: Auto=$autoAnswer, Delay=$autoAnswerDelaySeconds, State=${currentState.javaClass.simpleName}")
        }
        
        return currentState
    }

    fun answered(): IncomingCallState {
        currentState = IncomingCallState.Answered
        return currentState
    }

    fun rejected(): IncomingCallState {
        currentState = IncomingCallState.Rejected
        return currentState
    }

    fun failed(reason: IncomingCallFailureReason): IncomingCallState {
        currentState = IncomingCallState.Failed(reason)
        return currentState
    }

    fun idle(): IncomingCallState {
        currentState = IncomingCallState.Idle
        return currentState
    }
}

object IncomingCallSessionState {
    private val machine = IncomingCallStateMachine()

    @Synchronized
    fun current(): IncomingCallState = machine.state

    @Synchronized
    fun ringing(
        callerLabel: String?,
        incomingNumber: String?,
        autoAnswer: Boolean
    ): IncomingCallState {
        return machine.ringing(callerLabel, incomingNumber, autoAnswer)
    }

    @Synchronized
    fun foregroundServiceStarted(callerLabel: String, autoAnswer: Boolean): IncomingCallState {
        return machine.foregroundServiceStarted(callerLabel, autoAnswer)
    }

    @Synchronized
    fun uiShown(
        callerLabel: String,
        autoAnswer: Boolean,
        autoAnswerDelaySeconds: Int
    ): IncomingCallState {
        return machine.uiShown(callerLabel, autoAnswer, autoAnswerDelaySeconds)
    }

    @Synchronized
    fun answered(): IncomingCallState = machine.answered()

    @Synchronized
    fun rejected(): IncomingCallState = machine.rejected()

    @Synchronized
    fun failed(reason: IncomingCallFailureReason): IncomingCallState {
        return machine.failed(reason)
    }

    @Synchronized
    fun idle(): IncomingCallState = machine.idle()
}
