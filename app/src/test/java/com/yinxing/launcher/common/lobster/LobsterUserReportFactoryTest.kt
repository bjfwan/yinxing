package com.yinxing.launcher.common.lobster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LobsterUserReportFactoryTest {
    @Test
    fun `creates a reported feedback event with sanitized bounded text`() {
        val event = LobsterUserReportFactory.create(
            type = LobsterUserReportType.PERFORMANCE,
            description = "  打开天气后卡住，联系电话 13800138000，邮箱 user@example.com  ",
            reproductionSteps = "  1. 打开天气\n2. 点击刷新  ",
            traceId = "trace-report-1"
        )!!

        assertEquals(LobsterReportStatus.REPORTED, event.status)
        assertEquals(LobsterLogCategory.FEEDBACK, event.category)
        assertEquals(LobsterEventType.DIAGNOSTIC, event.eventType)
        assertEquals("submit_user_report", event.action)
        assertEquals("用户反馈：卡顿或速度慢", event.summary)
        assertEquals("trace-report-1", event.details.traceId)
        assertEquals("performance", event.details.reportType)
        assertTrue(event.details.userDescription!!.contains("138****8000"))
        assertFalse(event.details.userDescription!!.contains("13800138000"))
        assertFalse(event.details.userDescription!!.contains("user@example.com"))
        assertEquals("1. 打开天气\n2. 点击刷新", event.details.reproductionSteps)
        assertNull(event.details.errorCode)

        val json = event.details.toJson()
        assertEquals("performance", json.getString("report_type"))
        assertEquals("trace-report-1", json.getString("trace_id"))
    }

    @Test
    fun `rejects a blank description and caps free text`() {
        assertNull(LobsterUserReportFactory.create(LobsterUserReportType.OTHER, "   "))

        val event = LobsterUserReportFactory.create(
            LobsterUserReportType.FUNCTION,
            "x".repeat(900),
            "y".repeat(900),
            "trace-report-2"
        )!!
        assertEquals(800, event.details.userDescription!!.length)
        assertEquals(800, event.details.reproductionSteps!!.length)
    }
}
