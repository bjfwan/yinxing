package com.yinxing.launcher.common.lobster

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LobsterRuntimePolicyTest {
    @Test
    fun `blocks Robolectric runtimes`() {
        assertFalse(LobsterRuntimePolicy.shouldUpload("Robolectric", "Pixel", "device"))
        assertFalse(LobsterRuntimePolicy.shouldUpload("Google", "robolectric", "device"))
        assertFalse(LobsterRuntimePolicy.shouldUpload("Google", "Pixel", "robolectric-fingerprint"))
    }

    @Test
    fun `allows physical Android runtimes`() {
        assertTrue(
            LobsterRuntimePolicy.shouldUpload(
                manufacturer = "Xiaomi",
                model = "23127PN0CC",
                fingerprint = "Xiaomi/shennong/shennong:15/AP3A/user/release-keys"
            )
        )
    }
}
