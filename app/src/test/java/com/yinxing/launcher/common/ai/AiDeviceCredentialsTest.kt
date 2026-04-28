package com.yinxing.launcher.common.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiDeviceCredentialsTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("ai_device_credentials", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun snapshotCreatesStableDeviceCredentials() {
        val credentials = AiDeviceCredentials.getInstance(context)
        credentials.clearForTesting()
        val first = credentials.snapshot()
        val second = credentials.snapshot()
        assertEquals(first.deviceId, second.deviceId)
        assertEquals(first.deviceToken, second.deviceToken)
        assertTrue(first.deviceId.startsWith("ol-"))
        assertTrue(first.deviceToken.length >= 32)
        assertFalse(first.activationCode.contains(" "))
    }
}
