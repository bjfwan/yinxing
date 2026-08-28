package com.yinxing.launcher.feature.fall

internal sealed interface FallAlertState {
    data class CountingDown(val remainingSeconds: Int) : FallAlertState
    data object Cancelled : FallAlertState
    data object CallingFamily : FallAlertState
}

internal class FallAlertStateMachine(countdownSeconds: Int = DEFAULT_COUNTDOWN_SECONDS) {
    var state: FallAlertState = FallAlertState.CountingDown(countdownSeconds.coerceAtLeast(1))
        private set

    fun tick(): FallAlertState {
        val current = state
        if (current is FallAlertState.CountingDown) {
            state = if (current.remainingSeconds <= 1) {
                FallAlertState.CallingFamily
            } else {
                FallAlertState.CountingDown(current.remainingSeconds - 1)
            }
        }
        return state
    }

    fun cancel(): FallAlertState {
        if (state is FallAlertState.CountingDown) state = FallAlertState.Cancelled
        return state
    }

    fun callNow(): FallAlertState {
        if (state is FallAlertState.CountingDown) state = FallAlertState.CallingFamily
        return state
    }

    companion object {
        const val DEFAULT_COUNTDOWN_SECONDS = 30
    }
}
