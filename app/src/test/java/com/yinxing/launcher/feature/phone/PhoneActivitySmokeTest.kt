package com.yinxing.launcher.feature.phone

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhoneActivitySmokeTest {
    @Test
    fun launchImmediatelyForwardsToUnifiedContactActivity() {
        val activity = Robolectric.buildActivity(PhoneActivity::class.java).setup().get()
        assertTrue(activity.isFinishing)
    }
}
