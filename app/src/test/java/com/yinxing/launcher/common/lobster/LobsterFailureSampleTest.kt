package com.yinxing.launcher.common.lobster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LobsterFailureSampleTest {
    private val sample = LobsterFailureSample(
        fingerprint = "a".repeat(64),
        domain = "wechat_video",
        failureCode = "WECHAT_WAITING_CONTACT_RESULT_FAILED",
        failedStep = "WAITING_CONTACT_RESULT",
        capability = "OPEN_SEARCH_RESULT",
        capabilityFailure = "SEARCH_RESULT_NOT_FOUND",
        reason = "search_result_not_found",
        targetVersionName = "8.0.60",
        targetVersionCode = 2600L,
        uiState = LobsterFailureUiState(
            windowClass = "com.tencent.mm.ui.chatting.ChattingUI",
            semanticPage = "SEARCH",
            route = "SEARCH",
            resourceIds = listOf("com.tencent.mm:id/search_result"),
            nodeClasses = listOf("android.widget.TextView"),
            nodeCount = 12,
            clickableCount = 3,
            editableCount = 1,
            maxDepth = 4,
        ),
    )

    @Test
    fun `serializes a versioned structural failure sample`() {
        val json = LobsterReportDetails(failureSample = sample).toJson()
        val failure = json.getJSONObject("failure_sample")

        assertEquals(1, failure.getInt("sample_version"))
        assertEquals(sample.fingerprint, failure.getString("fingerprint"))
        assertEquals("OPEN_SEARCH_RESULT", failure.getString("capability"))
        assertEquals("8.0.60", failure.getString("target_version_name"))
        assertEquals(2600L, failure.getLong("target_version_code"))
        assertEquals(12, failure.getJSONObject("ui_state").getInt("node_count"))
        assertFalse(json.toString().contains("sensitiveValues"))
    }

    @Test
    fun `deduplicates local history and keeps a bounded recent set`() {
        val first = LobsterFailureSampleHistory.merge(
            existing = emptyList(),
            sample = sample,
            traceId = "trace-1",
            occurredAt = 100L,
            maxRecords = 2,
        )
        val repeated = LobsterFailureSampleHistory.merge(
            existing = first,
            sample = sample.copy(uiState = sample.uiState.copy(nodeCount = 15)),
            traceId = "trace-2",
            occurredAt = 200L,
            maxRecords = 2,
        )
        val secondSample = sample.copy(fingerprint = "b".repeat(64), failureCode = "WECHAT_WAITING_HOME_TIMEOUT")
        val second = LobsterFailureSampleHistory.merge(repeated, secondSample, "trace-3", 300L, 2)
        val thirdSample = sample.copy(fingerprint = "c".repeat(64), failureCode = "WECHAT_WAITING_VIDEO_OPTIONS_FAILED")
        val bounded = LobsterFailureSampleHistory.merge(second, thirdSample, "trace-4", 400L, 2)

        val merged = repeated.single()
        assertEquals(2, merged.occurrenceCount)
        assertEquals(100L, merged.firstSeenAt)
        assertEquals(200L, merged.lastSeenAt)
        assertEquals(listOf("trace-1", "trace-2"), merged.traceIds)
        assertEquals(15, merged.sample.uiState.nodeCount)
        assertEquals(listOf(thirdSample.fingerprint, secondSample.fingerprint), bounded.map { it.sample.fingerprint })
        assertTrue(LobsterFailureSampleHistory.decode(LobsterFailureSampleHistory.encode(bounded)).isNotEmpty())
    }

    @Test
    fun `keeps an absent target version code absent after local persistence`() {
        val withoutVersion = sample.copy(targetVersionName = null, targetVersionCode = null)

        val decoded = LobsterFailureSampleHistory.decode(
            LobsterFailureSampleHistory.encode(
                LobsterFailureSampleHistory.merge(emptyList(), withoutVersion, "trace-1", 100L)
            )
        ).single()

        assertEquals(null, decoded.sample.targetVersionCode)
    }
}
