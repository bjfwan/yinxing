package com.yinxing.launcher.common.lobster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LobsterStructuredReportTest {
    @Test
    fun `serializes snake case fields and limits steps`() {
        val details = LobsterReportDetails(
            traceId = "request-1",
            errorCode = "WECHAT_SEARCH_FAILED",
            failedStep = "waiting_search",
            steps = (0 until 105).map { index ->
                LobsterTraceStep(
                    stepCode = "step_$index",
                    stepName = "步骤 $index",
                    action = "click",
                    outcome = LobsterStepOutcome.SUCCESS,
                    detail = null,
                    durationMs = 80,
                    occurredAt = "2026-08-26T00:00:00.000Z"
                )
            }
        )

        val json = details.toJson()

        assertEquals("request-1", json.getString("trace_id"))
        assertEquals("WECHAT_SEARCH_FAILED", json.getString("error_code"))
        assertEquals(100, json.getJSONArray("steps").length())
        val first = json.getJSONArray("steps").getJSONObject(0)
        assertEquals("step_0", first.getString("step_code"))
        assertEquals("success", first.getString("outcome"))
        assertFalse(first.has("detail"))
    }
}
