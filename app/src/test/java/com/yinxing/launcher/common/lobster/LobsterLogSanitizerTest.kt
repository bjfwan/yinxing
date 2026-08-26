package com.yinxing.launcher.common.lobster

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LobsterLogSanitizerTest {
    @Test
    fun `masks phone numbers before upload`() {
        val sanitized = LobsterLogSanitizer.sanitize("Caller=15381151420, Number=05617043475")

        assertFalse(sanitized.contains("15381151420"))
        assertFalse(sanitized.contains("05617043475"))
        assertTrue(sanitized.contains("153****1420"))
    }

    @Test
    fun `masks bearer tokens before upload`() {
        val sanitized = LobsterLogSanitizer.sanitize("Authorization: Bearer secret-token-value")

        assertFalse(sanitized.contains("secret-token-value"))
        assertTrue(sanitized.contains("Bearer ***"))
    }
}
