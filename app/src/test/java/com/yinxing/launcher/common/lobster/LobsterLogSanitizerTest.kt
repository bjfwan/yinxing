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

    @Test
    fun `masks contact names supplied by the video call session`() {
        val sanitized = LobsterLogSanitizer.sanitize(
            "failure=未找到联系人: 石延刚, contact=石延刚, nodeText=石延刚",
            sensitiveValues = listOf("石延刚")
        )

        assertFalse(sanitized.contains("石延刚"))
        assertTrue(sanitized.contains("***"))
    }

    @Test
    fun `masks labeled contact names even when a different event flushes the buffer`() {
        val sanitized = LobsterLogSanitizer.sanitize("[微信视频] 流程开始: 联系人=石延刚, requestId=1")

        assertFalse(sanitized.contains("石延刚"))
        assertTrue(sanitized.contains("联系人=***"))
    }
}
