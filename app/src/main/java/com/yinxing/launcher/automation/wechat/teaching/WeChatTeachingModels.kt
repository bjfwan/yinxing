package com.yinxing.launcher.automation.wechat.teaching

enum class WeChatTeachingObservationKind {
    CLICK,
    WINDOW,
    LAUNCH,
    INPUT_CONTACT,
    SCROLL,
    BACK
}

enum class WeChatTeachingObservationSource {
    ACCESSIBILITY_EVENT,
    VISIBLE_CONTROL
}

enum class WeChatTeachingSemanticLabel {
    SEARCH,
    MORE,
    AUDIO_VIDEO_MENU,
    VIDEO_CALL,
    VOICE_CALL;

    companion object {
        fun fromVisibleValue(value: CharSequence?): WeChatTeachingSemanticLabel? {
            val normalized = value?.toString()?.trim().orEmpty()
            if (normalized.isEmpty()) return null
            return when {
                normalized == "搜索" || normalized == "Search" || normalized == "搜索联系人" -> SEARCH
                normalized.contains("更多功能按钮") || normalized == "更多" || normalized == "更多功能" -> MORE
                normalized == "音视频通话" -> AUDIO_VIDEO_MENU
                normalized == "视频通话" -> VIDEO_CALL
                normalized == "语音通话" -> VOICE_CALL
                else -> null
            }
        }
    }
}

enum class WeChatTeachingAction {
    OPEN_SEARCH,
    OPEN_CONTACT,
    OPEN_MORE,
    OPEN_VIDEO_MENU,
    START_VIDEO_CALL
}

enum class WeChatTeachingRequirement {
    OPEN_SEARCH,
    OPEN_CONTACT,
    OPEN_MORE,
    OPEN_VIDEO_MENU,
    START_VIDEO_CALL,
    CALL_PAGE_REACHED,
    SELECTOR_QUALITY
}

enum class WeChatTeachingReliability {
    RELIABLE,
    USABLE_WITH_POSITION_FALLBACK,
    LOW
}

data class WeChatTeachingSelector(
    val resourceId: String?,
    val nodeClass: String?,
    val semanticLabel: WeChatTeachingSemanticLabel?,
    val clickableAncestorDepth: Int,
    val centerXRatio: Float?,
    val centerYRatio: Float?
)

data class WeChatTeachingObservation(
    val kind: WeChatTeachingObservationKind,
    val windowClass: String?,
    val selector: WeChatTeachingSelector?,
    val elapsedMs: Long,
    val source: WeChatTeachingObservationSource = WeChatTeachingObservationSource.ACCESSIBILITY_EVENT
)

data class WeChatTeachingFingerprint(
    val manufacturer: String,
    val model: String,
    val androidSdk: Int,
    val screenWidth: Int,
    val screenHeight: Int,
    val densityDpi: Int,
    val fontScalePermille: Int,
    val localeTag: String,
    val weChatVersionName: String,
    val weChatVersionCode: Long
)

data class WeChatTeachingStep(
    val action: WeChatTeachingAction,
    val windowClass: String,
    val expectedWindowClass: String,
    val selector: WeChatTeachingSelector
)

data class WeChatTeachingProfile(
    val schemaVersion: Int = 1,
    val fingerprint: WeChatTeachingFingerprint,
    val steps: List<WeChatTeachingStep>,
    val reliabilityScore: Int,
    val reliability: WeChatTeachingReliability,
    val createdAtEpochMs: Long
) {
    fun selectorFor(action: WeChatTeachingAction): WeChatTeachingSelector? =
        steps.firstOrNull { it.action == action }?.selector
}

sealed interface WeChatTeachingResult {
    data class Complete(val profile: WeChatTeachingProfile) : WeChatTeachingResult
    data class Incomplete(
        val missing: Set<WeChatTeachingRequirement>,
        val profile: WeChatTeachingProfile? = null
    ) : WeChatTeachingResult
}

fun WeChatTeachingResult.learnedProfileOrNull(): WeChatTeachingProfile? = when (this) {
    is WeChatTeachingResult.Complete -> profile
    is WeChatTeachingResult.Incomplete -> profile
}

data class WeChatTeachingRecord(
    val fingerprint: WeChatTeachingFingerprint,
    val videoConfirmed: Boolean,
    val learnedActions: Set<WeChatTeachingAction>,
    val verifiedActions: Set<WeChatTeachingAction> = emptySet(),
    val addedActions: Set<WeChatTeachingAction> = emptySet(),
    val createdAtEpochMs: Long
)
