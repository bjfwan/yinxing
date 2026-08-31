package com.yinxing.launcher.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppUpdateEndpointTest {
    @Test
    fun updateCheckUsesTheNumericOfficialDomain() {
        assertEquals(
            "https://yinxing.722688.xyz/update.json",
            DEFAULT_APP_UPDATE_ENDPOINT
        )
        assertFalse(DEFAULT_APP_UPDATE_ENDPOINT.contains("likeyou.qzz.io"))
    }
}
