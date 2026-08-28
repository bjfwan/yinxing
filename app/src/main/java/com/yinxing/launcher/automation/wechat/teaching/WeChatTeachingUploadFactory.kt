package com.yinxing.launcher.automation.wechat.teaching

import com.yinxing.launcher.common.lobster.LobsterEventType
import com.yinxing.launcher.common.lobster.LobsterLogCategory
import com.yinxing.launcher.common.lobster.LobsterReportDetails
import com.yinxing.launcher.common.lobster.LobsterReportStatus
import com.yinxing.launcher.common.lobster.LobsterStepOutcome
import com.yinxing.launcher.common.lobster.LobsterTraceStep
import com.yinxing.launcher.common.lobster.LobsterUsageEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object WeChatTeachingUploadFactory {
    private val resourceIdPattern = Regex("^com\\.tencent\\.mm:id/[A-Za-z0-9_]+$")
    private val classPattern = Regex("^(?:android|androidx|com\\.tencent\\.mm)\\.[A-Za-z0-9_.$]+$")

    fun create(profile: WeChatTeachingProfile): LobsterUsageEvent {
        val version = profile.fingerprint.weChatVersionName.take(40)
        val versionCode = profile.fingerprint.weChatVersionCode
        return LobsterUsageEvent(
            scene = "微信视频示教",
            status = LobsterReportStatus.REPORTED,
            summary = "用户同意上传匿名设备适配规则，微信 $version ($versionCode)，可靠度 ${profile.reliabilityScore}",
            logLine = buildSafeDeviceLog(profile),
            details = LobsterReportDetails(
                reportType = "wechat_teaching_profile",
                steps = profile.steps.map { step ->
                    LobsterTraceStep(
                        stepCode = step.action.name,
                        stepName = step.action.name,
                        action = "record_selector",
                        outcome = LobsterStepOutcome.SUCCESS,
                        detail = safeSelectorDetail(step),
                        occurredAt = isoTimestamp(profile.createdAtEpochMs)
                    )
                }
            ),
            category = LobsterLogCategory.WECHAT_VIDEO,
            eventType = LobsterEventType.OPERATION,
            action = "upload_wechat_teaching_rule"
        )
    }

    private fun safeSelectorDetail(step: WeChatTeachingStep): String {
        val selector = step.selector
        return buildList {
            safeClass(step.windowClass)?.let { add("window=$it") }
            safeClass(step.expectedWindowClass)?.let { add("expected=$it") }
            selector.resourceId?.takeIf(resourceIdPattern::matches)?.let { add("id=$it") }
            selector.nodeClass?.takeIf(classPattern::matches)?.let { add("class=$it") }
            selector.semanticLabel?.let { add("label=${it.name}") }
            add("ancestor=${selector.clickableAncestorDepth.coerceIn(-1, 6)}")
            selector.centerXRatio?.takeIf { it in 0f..1f }?.let { add("x=${ratio(it)}") }
            selector.centerYRatio?.takeIf { it in 0f..1f }?.let { add("y=${ratio(it)}") }
        }.joinToString(";")
    }

    private fun buildSafeDeviceLog(profile: WeChatTeachingProfile): String {
        val fingerprint = profile.fingerprint
        return "[微信示教] schema=${profile.schemaVersion}, " +
            "wechat_version=${fingerprint.weChatVersionName.take(40)}, " +
            "wechat_code=${fingerprint.weChatVersionCode}, " +
            "android_sdk=${fingerprint.androidSdk}, " +
            "screen=${fingerprint.screenWidth}x${fingerprint.screenHeight}, " +
            "density=${fingerprint.densityDpi}, " +
            "font_scale_permille=${fingerprint.fontScalePermille}, " +
            "locale=${fingerprint.localeTag.take(30)}, " +
            "reliability=${profile.reliabilityScore}"
    }

    private fun safeClass(value: String): String? =
        value.takeIf { classPattern.matches(it) }?.take(200)

    private fun ratio(value: Float): String = String.format(Locale.US, "%.3f", value)

    private fun isoTimestamp(epochMs: Long): String = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        Locale.US
    ).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(epochMs))
}
