package com.yinxing.launcher.feature.incoming

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingPlatformCompatTest {

    @Test
    fun android10ProvidesAcceptRingingCallButLacksTiramisuNotificationPermission() {
        val compat = IncomingPlatformCompat(sdkInt = Build.VERSION_CODES.Q)

        assertEquals(Build.VERSION_CODES.Q, compat.sdkInt)
        assertTrue("Android 10 应支持 setShowWhenLocked", compat.supportsModernLockScreenApi)
        assertTrue("Android 10 应支持通知通道", compat.supportsNotificationChannels)
        assertTrue("Android 10 应支持 STOP_FOREGROUND_REMOVE", compat.supportsStopForegroundRemoveFlag)
        assertTrue("Android 10 应支持 acceptRingingCall", compat.supportsAcceptRingingCall)
        assertTrue("Android 10 应支持 endCall", compat.supportsEndCall)
        assertFalse("Android 10 不需要单独通知权限", compat.requiresNotificationPermission)
    }

    @Test
    fun android12StillBelowTiramisuNotificationPermission() {
        val compat = IncomingPlatformCompat(sdkInt = Build.VERSION_CODES.S)

        assertTrue(compat.supportsModernLockScreenApi)
        assertTrue(compat.supportsNotificationChannels)
        assertTrue(compat.supportsStopForegroundRemoveFlag)
        assertTrue(compat.supportsAcceptRingingCall)
        assertTrue(compat.supportsEndCall)
        assertFalse("Android 12 仍未引入通知运行时权限", compat.requiresNotificationPermission)
    }

    @Test
    fun android13RequiresNotificationPermission() {
        val compat = IncomingPlatformCompat(sdkInt = Build.VERSION_CODES.TIRAMISU)

        assertTrue(compat.supportsAcceptRingingCall)
        assertTrue(compat.supportsEndCall)
        assertTrue("Android 13 起需要 POST_NOTIFICATIONS 权限", compat.requiresNotificationPermission)
    }

    @Test
    fun android14EnablesEveryCapability() {
        val compat = IncomingPlatformCompat(sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)

        assertTrue(compat.supportsModernLockScreenApi)
        assertTrue(compat.supportsNotificationChannels)
        assertTrue(compat.supportsStopForegroundRemoveFlag)
        assertTrue(compat.supportsAcceptRingingCall)
        assertTrue(compat.supportsEndCall)
        assertTrue(compat.requiresNotificationPermission)
    }

    @Test
    fun legacyApi23DisablesNewerApis() {
        val compat = IncomingPlatformCompat(sdkInt = Build.VERSION_CODES.M)

        assertFalse("Android 6 早于 O_MR1，没有 setShowWhenLocked", compat.supportsModernLockScreenApi)
        assertFalse("Android 6 没有通知通道", compat.supportsNotificationChannels)
        assertFalse("Android 6 没有 acceptRingingCall", compat.supportsAcceptRingingCall)
        assertFalse("Android 6 没有 endCall", compat.supportsEndCall)
        assertFalse(compat.requiresNotificationPermission)
        assertTrue("Android 6 仍可用 STOP_FOREGROUND_REMOVE 标志（API 24+）", compat.sdkInt < Build.VERSION_CODES.N)
    }

    @Test
    fun stopForegroundRemoveFlagAvailableFromN() {
        assertFalse(IncomingPlatformCompat(sdkInt = Build.VERSION_CODES.M).supportsStopForegroundRemoveFlag)
        assertTrue(IncomingPlatformCompat(sdkInt = Build.VERSION_CODES.N).supportsStopForegroundRemoveFlag)
    }

    @Test
    fun endCallAvailableFromP() {
        assertFalse(IncomingPlatformCompat(sdkInt = Build.VERSION_CODES.O_MR1).supportsEndCall)
        assertTrue(IncomingPlatformCompat(sdkInt = Build.VERSION_CODES.P).supportsEndCall)
    }
}
