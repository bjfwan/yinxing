package com.yinxing.launcher.feature.callreturn

import android.os.Build
import android.telecom.Call

internal object SystemCallReturnEligibility {
    fun shouldConfirm(
        sdkInt: Int,
        callDirection: Int?,
        wasEverRinging: Boolean
    ): Boolean = if (sdkInt >= Build.VERSION_CODES.Q) {
        callDirection == Call.Details.DIRECTION_OUTGOING
    } else {
        !wasEverRinging
    }
}
