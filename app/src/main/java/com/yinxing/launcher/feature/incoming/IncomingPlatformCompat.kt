package com.yinxing.launcher.feature.incoming

import android.os.Build

class IncomingPlatformCompat(
    val sdkInt: Int = Build.VERSION.SDK_INT
) {
    val supportsModernLockScreenApi: Boolean
        get() = sdkInt >= Build.VERSION_CODES.O_MR1

    val supportsNotificationChannels: Boolean
        get() = sdkInt >= Build.VERSION_CODES.O

    val supportsStopForegroundRemoveFlag: Boolean
        get() = sdkInt >= Build.VERSION_CODES.N

    val supportsAcceptRingingCall: Boolean
        get() = sdkInt >= Build.VERSION_CODES.O

    val supportsEndCall: Boolean
        get() = sdkInt >= Build.VERSION_CODES.P

    val requiresNotificationPermission: Boolean
        get() = sdkInt >= Build.VERSION_CODES.TIRAMISU
}
