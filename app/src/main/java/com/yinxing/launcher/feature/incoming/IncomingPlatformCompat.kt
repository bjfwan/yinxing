package com.yinxing.launcher.feature.incoming

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

class IncomingPlatformCompat(
    val sdkInt: Int = Build.VERSION.SDK_INT
) {
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O_MR1)
    val supportsModernLockScreenApi: Boolean
        get() = sdkInt >= Build.VERSION_CODES.O_MR1

    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    val supportsNotificationChannels: Boolean
        get() = sdkInt >= Build.VERSION_CODES.O

    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.N)
    val supportsStopForegroundRemoveFlag: Boolean
        get() = sdkInt >= Build.VERSION_CODES.N

    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    val supportsAcceptRingingCall: Boolean
        get() = sdkInt >= Build.VERSION_CODES.O

    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.P)
    val supportsEndCall: Boolean
        get() = sdkInt >= Build.VERSION_CODES.P

    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    val requiresNotificationPermission: Boolean
        get() = sdkInt >= Build.VERSION_CODES.TIRAMISU
}
