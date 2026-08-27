package com.yinxing.launcher.common.lobster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LobsterUsageEventTest {
    @Test
    fun `catalog covers non WeChat client usage`() {
        val events = LobsterUsageEvents.all

        assertEquals(10, events.size)
        assertEquals(
            setOf("应用启动", "首页入口", "应用启动", "电话拨出"),
            events.map { it.scene }.toSet()
        )
        assertEquals(LobsterReportStatus.ERROR, LobsterUsageEvents.APP_OPEN_FAILED.status)
        assertEquals(LobsterReportStatus.ERROR, LobsterUsageEvents.OUTGOING_CALL_FAILED.status)
        assertEquals(LobsterReportStatus.ERROR, LobsterUsageEvents.CALL_PERMISSION_DENIED.status)
        assertEquals("APP_LAUNCH_FAILED", LobsterUsageEvents.APP_OPEN_FAILED.details.errorCode)
        assertEquals("PHONE_DIAL_FAILED", LobsterUsageEvents.OUTGOING_CALL_FAILED.details.errorCode)
        assertEquals("CALL_PERMISSION_DENIED", LobsterUsageEvents.CALL_PERMISSION_DENIED.details.errorCode)
    }

    @Test
    fun `catalog contains no user or target identifiers`() {
        val serialized = LobsterUsageEvents.all.joinToString("|") {
            "${it.scene}|${it.status.wireValue}|${it.summary}|${it.logLine}"
        }

        assertFalse(serialized.contains("phone", ignoreCase = true))
        assertFalse(serialized.contains("package", ignoreCase = true))
        assertFalse(serialized.contains("contact", ignoreCase = true))
        assertFalse(serialized.contains("city", ignoreCase = true))
    }
}
