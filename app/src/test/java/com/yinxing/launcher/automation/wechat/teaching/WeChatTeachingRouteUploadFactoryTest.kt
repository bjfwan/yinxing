package com.yinxing.launcher.automation.wechat.teaching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatTeachingRouteUploadFactoryTest {

    @Test
    fun routeUploadUsesV1ReportAndStrictAnonymousWhitelist() {
        val report = WeChatTeachingRouteUploadFactory.create(
            route = route(),
            sessionId = "session-safe",
            outcome = WeChatTeachingUploadOutcome.FAILED,
            replayResult = WeChatTeachingReplayResult.TIMEOUT,
            failureReason = WeChatTeachingUploadFailureReason.STEP_TIMEOUT,
            missingEventCount = 2
        )
        val rendered = listOf(
            report.summary,
            report.logLine,
            report.details.toJson().toString()
        ).joinToString("\n")

        assertEquals("upload_wechat_teaching_route", report.action)
        assertTrue(rendered.contains("wechat_teaching_route_v1"))
        assertTrue(rendered.contains("route-safe"))
        assertTrue(rendered.contains("session-safe"))
        assertTrue(rendered.contains("STEP_TIMEOUT"))
        assertTrue(rendered.contains("failed_step=1"))
        assertTrue(rendered.contains("missing_events=2"))
        listOf("张三", "今晚视频", "contact_name", "chat_text", "screenshot", "image", "audio", "video_file")
            .forEach { forbidden -> assertFalse(rendered.contains(forbidden, ignoreCase = true)) }
    }

    private fun route() = WeChatTeachingRoute(
        routeId = "route-safe",
        fingerprint = WeChatTeachingFingerprint(
            "vivo", "V2285A", 36, 1080, 2400, 440, 1000, "zh-CN", "8.0.76", 3141
        ),
        startState = WeChatTeachingStateFingerprint(
            "com.tencent.mm.ui.LauncherUI",
            setOf(WeChatTeachingSemanticLabel.SEARCH),
            setOf("com.tencent.mm:id/jha")
        ),
        steps = listOf(
            WeChatTeachingRouteStep(
                type = WeChatTeachingRouteStepType.INPUT_CONTACT_PLACEHOLDER,
                expectedState = WeChatTeachingStateFingerprint(
                    "com.tencent.mm.plugin.fts.ui.FTSMainUI",
                    emptySet(),
                    emptySet()
                )
            )
        ),
        endEvidence = WeChatTeachingRouteEndEvidence.VIDEO_CALL_CONFIRMED,
        source = WeChatTeachingRouteSource.DEMONSTRATION,
        priority = 0,
        reliabilityScore = 70,
        lifecycle = WeChatTeachingRouteLifecycle.CANDIDATE,
        createdAtEpochMs = 1_000L,
        lastFailureStep = 1
    )
}
