package com.google.android.accessibility.selecttospeak

import com.yinxing.launcher.common.lobster.LobsterFailureSample
import com.yinxing.launcher.common.lobster.LobsterFailureUiState
import com.yinxing.launcher.common.lobster.LobsterReportStatus
import com.yinxing.launcher.common.lobster.LobsterStepOutcome
import com.yinxing.launcher.common.lobster.LobsterTraceStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WeChatFailureReportFactoryTest {
    @Test
    fun `creates a safe dedicated failure sample event`() {
        val sample = LobsterFailureSample(
            fingerprint = "a".repeat(64),
            domain = "wechat_video",
            failureCode = "WECHAT_WAITING_CONTACT_RESULT_FAILED",
            failedStep = "WAITING_CONTACT_RESULT",
            capability = "OPEN_SEARCH_RESULT",
            capabilityFailure = "SEARCH_RESULT_NOT_FOUND",
            reason = "search_result_not_found",
            uiState = LobsterFailureUiState(
                windowClass = "com.tencent.mm.ui.LauncherUI",
                semanticPage = "SEARCH",
                route = "SEARCH",
                resourceIds = emptyList(),
                nodeClasses = emptyList(),
                nodeCount = 1,
                clickableCount = 0,
                editableCount = 0,
                maxDepth = 0,
            ),
        )
        val event = WeChatFailureReportFactory.create(
            sample = sample,
            traceId = "trace-1",
            contactName = "张三",
            steps = listOf(
                LobsterTraceStep(
                    stepCode = "search",
                    stepName = "搜索联系人",
                    action = "click",
                    outcome = LobsterStepOutcome.ERROR,
                    detail = "联系人张三没有找到",
                    occurredAt = "2026-08-31T00:00:00.000Z",
                ),
            ),
        )

        assertEquals(LobsterReportStatus.ERROR, event.status)
        assertEquals("wechat_failure_sample_v1", event.details.reportType)
        assertEquals("upload_wechat_failure_sample", event.action)
        assertEquals(sample, event.details.failureSample)
        assertFalse(event.summary.contains("张三"))
        assertFalse(event.logLine.contains("张三"))
        assertFalse(event.details.toJson().toString().contains("张三"))
        assertEquals("search", event.details.steps.single().stepCode)
        assertEquals("", event.details.steps.single().stepName)
        assertEquals("click", event.details.steps.single().action)
        assertEquals(null, event.details.steps.single().detail)
        assertFalse(event.details.toJson().toString().contains("搜索联系人"))
        assertFalse(event.details.toJson().toString().contains("没有找到"))
    }
}
