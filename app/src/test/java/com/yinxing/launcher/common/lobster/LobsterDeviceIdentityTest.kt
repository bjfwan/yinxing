package com.yinxing.launcher.common.lobster

import org.junit.Assert.assertEquals
import org.junit.Test

class LobsterDeviceIdentityTest {
    @Test
    fun `keeps install id on the same physical device`() {
        val resolved = LobsterDeviceIdentity.resolve(
            storedId = "existing-id",
            storedDeviceSignature = "manufacturer|model",
            currentDeviceSignature = "manufacturer|model",
            createId = { "new-id" }
        )

        assertEquals("existing-id", resolved.id)
        assertEquals(false, resolved.changed)
    }

    @Test
    fun `keeps existing id while recording signature after an upgrade`() {
        val resolved = LobsterDeviceIdentity.resolve(
            storedId = "existing-id",
            storedDeviceSignature = null,
            currentDeviceSignature = "manufacturer|model",
            createId = { "new-id" }
        )

        assertEquals("existing-id", resolved.id)
        assertEquals(false, resolved.changed)
    }

    @Test
    fun `regenerates restored install id on a different physical device`() {
        val resolved = LobsterDeviceIdentity.resolve(
            storedId = "restored-id",
            storedDeviceSignature = "old|phone",
            currentDeviceSignature = "new|phone",
            createId = { "new-id" }
        )

        assertEquals("new-id", resolved.id)
        assertEquals(true, resolved.changed)
    }
}
