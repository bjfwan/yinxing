package com.yinxing.launcher.automation.wechat.teaching

import java.security.MessageDigest

enum class WeChatTeachingRouteStepType {
    LAUNCH_WECHAT,
    CLICK_CONTROL,
    CLICK_RELATIVE_POSITION,
    INPUT_CONTACT_PLACEHOLDER,
    SCROLL_FORWARD,
    GO_BACK,
    WAIT_FOR_STATE
}

enum class WeChatTeachingRouteEndEvidence {
    VIDEO_CALL_CONFIRMED
}

enum class WeChatTeachingRouteSource {
    DEMONSTRATION,
    MIGRATED_LEGACY,
    BUILT_IN
}

enum class WeChatTeachingRouteLifecycle {
    CANDIDATE,
    VERIFIED,
    DEGRADED,
    DISABLED
}

data class WeChatTeachingStateFingerprint(
    val windowClass: String?,
    val semanticLabels: Set<WeChatTeachingSemanticLabel>,
    val resourceIds: Set<String>
) {
    fun matches(current: WeChatTeachingStateFingerprint): Boolean {
        if (windowClass != null && current.windowClass != windowClass) return false
        if (!current.semanticLabels.containsAll(semanticLabels)) return false
        return current.resourceIds.containsAll(resourceIds)
    }
}

data class WeChatTeachingRouteStep(
    val type: WeChatTeachingRouteStepType,
    val selector: WeChatTeachingSelector? = null,
    val expectedState: WeChatTeachingStateFingerprint? = null,
    val maxWaitMs: Long = DEFAULT_WAIT_MS
) {
    companion object {
        const val DEFAULT_WAIT_MS = 6_000L
    }
}

data class WeChatTeachingRoute(
    val routeId: String,
    val fingerprint: WeChatTeachingFingerprint,
    val startState: WeChatTeachingStateFingerprint,
    val steps: List<WeChatTeachingRouteStep>,
    val endEvidence: WeChatTeachingRouteEndEvidence,
    val source: WeChatTeachingRouteSource,
    val priority: Int,
    val reliabilityScore: Int,
    val lifecycle: WeChatTeachingRouteLifecycle,
    val createdAtEpochMs: Long,
    val lastVerifiedAtEpochMs: Long? = null,
    val consecutiveFailures: Int = 0,
    val lastFailureStep: Int? = null
)

data class WeChatTeachingRouteCollection(
    val schemaVersion: Int = 2,
    val fingerprint: WeChatTeachingFingerprint,
    val routes: List<WeChatTeachingRoute>
)

object WeChatTeachingRouteIdentity {
    fun create(
        fingerprint: WeChatTeachingFingerprint,
        startState: WeChatTeachingStateFingerprint,
        steps: List<WeChatTeachingRouteStep>
    ): String {
        val canonical = buildString {
            append(fingerprint.manufacturer).append('|')
            append(fingerprint.model).append('|')
            append(fingerprint.weChatVersionCode).append('|')
            append(startState.canonical()).append('|')
            steps.forEach { step ->
                append(step.type.name).append(':')
                append(step.selector?.resourceId.orEmpty()).append(':')
                append(step.selector?.nodeClass.orEmpty()).append(':')
                append(step.selector?.semanticLabel?.name.orEmpty()).append(':')
                append(step.selector?.centerXRatio ?: "").append(':')
                append(step.selector?.centerYRatio ?: "").append(':')
                append(step.expectedState?.canonical().orEmpty()).append(';')
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun WeChatTeachingStateFingerprint.canonical(): String = buildString {
        append(windowClass.orEmpty()).append(':')
        append(semanticLabels.map { it.name }.sorted().joinToString(",")).append(':')
        append(resourceIds.sorted().joinToString(","))
    }
}

object WeChatTeachingRouteMigration {
    fun fromLegacyProfile(profile: WeChatTeachingProfile): WeChatTeachingRoute {
        val startState = WeChatTeachingStateFingerprint(
            windowClass = profile.steps.firstOrNull()?.windowClass?.takeIf(::isSafeClassName),
            semanticLabels = emptySet(),
            resourceIds = emptySet()
        )
        val steps = profile.steps.map { legacy ->
            WeChatTeachingRouteStep(
                type = if (
                    legacy.selector.resourceId != null ||
                    legacy.selector.semanticLabel != null ||
                    legacy.selector.nodeClass != null
                ) {
                    WeChatTeachingRouteStepType.CLICK_CONTROL
                } else {
                    WeChatTeachingRouteStepType.CLICK_RELATIVE_POSITION
                },
                selector = legacy.selector,
                expectedState = WeChatTeachingStateFingerprint(
                    windowClass = legacy.expectedWindowClass
                        .takeUnless { it.endsWith(".") }
                        ?.takeIf(::isSafeClassName),
                    semanticLabels = emptySet(),
                    resourceIds = emptySet()
                )
            )
        }
        return WeChatTeachingRoute(
            routeId = WeChatTeachingRouteIdentity.create(profile.fingerprint, startState, steps),
            fingerprint = profile.fingerprint,
            startState = startState,
            steps = steps,
            endEvidence = WeChatTeachingRouteEndEvidence.VIDEO_CALL_CONFIRMED,
            source = WeChatTeachingRouteSource.MIGRATED_LEGACY,
            priority = 0,
            reliabilityScore = profile.reliabilityScore,
            lifecycle = WeChatTeachingRouteLifecycle.CANDIDATE,
            createdAtEpochMs = profile.createdAtEpochMs
        )
    }

    private fun isSafeClassName(value: String): Boolean =
        value.matches(Regex("^(?:android|androidx|com\\.tencent\\.mm)\\.[A-Za-z0-9_.$]+$"))
}
