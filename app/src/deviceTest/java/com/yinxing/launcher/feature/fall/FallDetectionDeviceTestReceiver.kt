package com.yinxing.launcher.feature.fall

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** ADB-only harness shipped exclusively in the deviceTest build type. */
class FallDetectionDeviceTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        var supported = true
        val result = when (intent.action) {
            ACTION_RESET_CALL_CONTEXT -> {
                FallCallTransitionContext.resetForTest()
                "callTransition=false"
            }
            ACTION_SIMULATE_CALL_TRANSITION -> {
                FallCallTransitionContext.updateCallState(active = true)
                "callActive=true"
            }
            ACTION_RUN_FALL_SEQUENCE -> {
                val callTransition = FallCallTransitionContext.isActive()
                val impactG = intent.getFloatExtra(EXTRA_IMPACT_G, 3.5f)
                val event = runSequence(
                    impactG = impactG,
                    detectionContext = if (callTransition) {
                        FallDetectionContext.CallTransition
                    } else {
                        FallDetectionContext.Normal
                    }
                )
                "callTransition=$callTransition;impactG=$impactG;event=$event"
            }
            else -> {
                supported = false
                "unsupportedAction=${intent.action}"
            }
        }
        Log.i(TAG, result)
        resultData = result
        resultCode = if (supported) Activity.RESULT_OK else Activity.RESULT_CANCELED
    }

    private fun runSequence(
        impactG: Float,
        detectionContext: FallDetectionContext
    ): FallDetectionEvent {
        val engine = FallDetectionEngine()
        var result = FallDetectionEvent.None

        for (timeMs in 0L..2_000L step 20L) {
            result = maxEvent(
                result,
                engine.acceptG(timeMs, 0f, 1f, 0f, detectionContext)
            )
        }
        for (timeMs in 2_020L..2_120L step 20L) {
            result = maxEvent(
                result,
                engine.acceptG(timeMs, impactG, 0f, 0f, detectionContext)
            )
        }
        for (timeMs in 2_140L..4_300L step 20L) {
            result = maxEvent(
                result,
                engine.acceptG(timeMs, 1f, 0f, 0f, detectionContext)
            )
        }
        return result
    }

    private fun maxEvent(
        first: FallDetectionEvent,
        second: FallDetectionEvent
    ): FallDetectionEvent {
        return if (first == FallDetectionEvent.PossibleFall ||
            second == FallDetectionEvent.PossibleFall
        ) {
            FallDetectionEvent.PossibleFall
        } else {
            FallDetectionEvent.None
        }
    }

    companion object {
        private const val TAG = "FallDeviceTest"
        const val ACTION_RESET_CALL_CONTEXT =
            "com.yinxing.launcher.devicetest.RESET_CALL_CONTEXT"
        const val ACTION_SIMULATE_CALL_TRANSITION =
            "com.yinxing.launcher.devicetest.SIMULATE_CALL_TRANSITION"
        const val ACTION_RUN_FALL_SEQUENCE =
            "com.yinxing.launcher.devicetest.RUN_FALL_SEQUENCE"
        const val EXTRA_IMPACT_G = "impact_g"
    }
}
