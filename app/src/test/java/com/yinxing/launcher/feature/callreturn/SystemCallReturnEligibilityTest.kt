package com.yinxing.launcher.feature.callreturn

import android.telecom.Call
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemCallReturnEligibilityTest {
    @Test
    fun modernAndroidOnlyConfirmsOutgoingCall() {
        assertTrue(
            SystemCallReturnEligibility.shouldConfirm(
                sdkInt = 34,
                callDirection = Call.Details.DIRECTION_OUTGOING,
                wasEverRinging = false
            )
        )
        assertFalse(
            SystemCallReturnEligibility.shouldConfirm(
                sdkInt = 34,
                callDirection = Call.Details.DIRECTION_INCOMING,
                wasEverRinging = true
            )
        )
    }

    @Test
    fun oldAndroidRejectsCallsThatWereObservedRinging() {
        assertTrue(
            SystemCallReturnEligibility.shouldConfirm(
                sdkInt = 28,
                callDirection = null,
                wasEverRinging = false
            )
        )
        assertFalse(
            SystemCallReturnEligibility.shouldConfirm(
                sdkInt = 28,
                callDirection = null,
                wasEverRinging = true
            )
        )
    }
}
