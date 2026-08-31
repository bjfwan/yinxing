package com.yinxing.launcher.automation.wechat.teaching

object WeChatTeachingAnalyzer {
    private const val WECHAT_HOME = "com.tencent.mm.ui.LauncherUI"
    private const val WECHAT_SEARCH = "com.tencent.mm.plugin.fts.ui.FTSMainUI"
    private const val WECHAT_CHAT = "com.tencent.mm.ui.chatting.ChattingUI"
    private const val WECHAT_VIDEO_DIALOG_PREFIX = "com.tencent.mm.ui.widget.dialog."
    private const val WECHAT_VIDEO_ACTIVITY = "com.tencent.mm.plugin.voip.ui.VideoActivity"

    private val orderedActions = listOf(
        WeChatTeachingAction.OPEN_SEARCH,
        WeChatTeachingAction.OPEN_CONTACT,
        WeChatTeachingAction.OPEN_MORE,
        WeChatTeachingAction.OPEN_VIDEO_MENU,
        WeChatTeachingAction.START_VIDEO_CALL
    )

    fun analyze(
        observations: List<WeChatTeachingObservation>,
        fingerprint: WeChatTeachingFingerprint,
        createdAtEpochMs: Long
    ): WeChatTeachingResult {
        val matched = mutableListOf<Pair<WeChatTeachingAction, WeChatTeachingObservation>>()
        var cursor = 0
        orderedActions.forEach { action ->
            val matchIndex = (cursor until observations.size).firstOrNull { index ->
                matches(action, observations, index)
            }
            if (matchIndex != null) {
                matched += action to observations[matchIndex]
                cursor = matchIndex + 1
            }
        }

        val reachedCallPage = observations.any(::isCallPage)
        val missing = buildSet {
            orderedActions.forEach { action ->
                if (matched.none { it.first == action }) add(action.toRequirement())
            }
            if (!reachedCallPage) add(WeChatTeachingRequirement.CALL_PAGE_REACHED)
        }
        val matchedSteps = matched.map { (action, observation) ->
            val selector = requireNotNull(observation.selector).let { observedSelector ->
                if (action == WeChatTeachingAction.OPEN_CONTACT) {
                    observedSelector.copy(semanticLabel = null)
                } else {
                    observedSelector
                }
            }
            WeChatTeachingStep(
                action = action,
                windowClass = observation.windowClass.orEmpty(),
                expectedWindowClass = expectedWindowClass(action),
                selector = selector
            )
        }
        val hasWeakMatchedSelector = matchedSteps.any { selectorScore(it.selector) < MIN_SELECTOR_SCORE }
        val steps = matchedSteps.filter { step ->
            selectorScore(step.selector) >= MIN_SELECTOR_SCORE || isBuiltInVerification(step)
        }
        val effectiveMissing = if (steps.size < matched.size || hasWeakMatchedSelector) {
            missing + WeChatTeachingRequirement.SELECTOR_QUALITY
        } else {
            missing
        }
        if (steps.isEmpty()) {
            return WeChatTeachingResult.Incomplete(
                effectiveMissing + WeChatTeachingRequirement.SELECTOR_QUALITY
            )
        }
        val reliabilityScore = steps.map { selectorScore(it.selector) }.average().toInt()
        val reliability = when {
            reliabilityScore >= 80 -> WeChatTeachingReliability.RELIABLE
            reliabilityScore >= 55 -> WeChatTeachingReliability.USABLE_WITH_POSITION_FALLBACK
            else -> WeChatTeachingReliability.LOW
        }
        val profile = WeChatTeachingProfile(
            fingerprint = fingerprint,
            steps = steps,
            reliabilityScore = reliabilityScore,
            reliability = reliability,
            createdAtEpochMs = createdAtEpochMs
        )
        return if (effectiveMissing.isEmpty() && steps.size == orderedActions.size) {
            WeChatTeachingResult.Complete(profile)
        } else {
            WeChatTeachingResult.Incomplete(effectiveMissing, profile)
        }
    }

    private fun matches(
        action: WeChatTeachingAction,
        observations: List<WeChatTeachingObservation>,
        index: Int
    ): Boolean {
        val observation = observations[index]
        if (observation.kind != WeChatTeachingObservationKind.CLICK || observation.selector == null) {
            return false
        }
        val windowClass = observation.windowClass.orEmpty()
        return when (action) {
            WeChatTeachingAction.OPEN_SEARCH ->
                windowClass == WECHAT_HOME &&
                    if (observation.source == WeChatTeachingObservationSource.VISIBLE_CONTROL) {
                        transitionsTo(observations, index, WECHAT_SEARCH)
                    } else {
                        observation.selector.semanticLabel == WeChatTeachingSemanticLabel.SEARCH ||
                            transitionsTo(observations, index, WECHAT_SEARCH)
                    }
            WeChatTeachingAction.OPEN_CONTACT ->
                windowClass == WECHAT_SEARCH &&
                    if (observation.source == WeChatTeachingObservationSource.VISIBLE_CONTROL) {
                        transitionsTo(observations, index, WECHAT_CHAT)
                    } else {
                        observation.selector.resourceId in CONTACT_RESULT_IDS ||
                            transitionsTo(observations, index, WECHAT_CHAT)
                    }
            WeChatTeachingAction.OPEN_MORE ->
                windowClass == WECHAT_CHAT &&
                    if (observation.source == WeChatTeachingObservationSource.VISIBLE_CONTROL) {
                        nextEvidenceIsVisibleVideoMenu(observations, index)
                    } else {
                        observation.selector.semanticLabel == WeChatTeachingSemanticLabel.MORE
                    }
            WeChatTeachingAction.OPEN_VIDEO_MENU ->
                windowClass in setOf(WECHAT_CHAT, WECHAT_CONTACT_INFO) &&
                    if (observation.source == WeChatTeachingObservationSource.VISIBLE_CONTROL) {
                        reachesWindowBeforeAnotherRealClick(observations, index) { window ->
                            window.startsWith(WECHAT_VIDEO_DIALOG_PREFIX)
                        }
                    } else {
                        observation.selector.semanticLabel in setOf(
                            WeChatTeachingSemanticLabel.AUDIO_VIDEO_MENU,
                            WeChatTeachingSemanticLabel.VIDEO_CALL
                        ) ||
                            transitionsToPrefix(observations, index, WECHAT_VIDEO_DIALOG_PREFIX)
                    }
            WeChatTeachingAction.START_VIDEO_CALL ->
                windowClass.startsWith(WECHAT_VIDEO_DIALOG_PREFIX) &&
                    if (observation.source == WeChatTeachingObservationSource.VISIBLE_CONTROL) {
                        reachesWindowBeforeAnotherRealClick(observations, index) { window ->
                            window == WECHAT_VIDEO_ACTIVITY
                        }
                    } else {
                        observation.selector.semanticLabel == WeChatTeachingSemanticLabel.VIDEO_CALL ||
                            transitionsTo(observations, index, WECHAT_VIDEO_ACTIVITY)
                    }
        }
    }

    private fun nextEvidenceIsVisibleVideoMenu(
        observations: List<WeChatTeachingObservation>,
        index: Int
    ): Boolean = observations
        .drop(index + 1)
        .firstOrNull { observation ->
            observation.kind == WeChatTeachingObservationKind.WINDOW ||
                observation.source == WeChatTeachingObservationSource.ACCESSIBILITY_EVENT ||
                (
                    observation.kind == WeChatTeachingObservationKind.CLICK &&
                        observation.windowClass == WECHAT_CHAT &&
                        observation.selector?.semanticLabel == WeChatTeachingSemanticLabel.AUDIO_VIDEO_MENU
                    )
        }
        ?.let { evidence ->
            evidence.kind == WeChatTeachingObservationKind.CLICK &&
                evidence.source == WeChatTeachingObservationSource.VISIBLE_CONTROL &&
                evidence.windowClass == WECHAT_CHAT &&
                evidence.selector?.semanticLabel == WeChatTeachingSemanticLabel.AUDIO_VIDEO_MENU
        } == true

    private fun reachesWindowBeforeAnotherRealClick(
        observations: List<WeChatTeachingObservation>,
        index: Int,
        expected: (String) -> Boolean
    ): Boolean {
        val startedAt = observations[index].elapsedMs
        var adjacentRealClickSeen = false
        observations.drop(index + 1).forEach { observation ->
            if (observation.elapsedMs - startedAt > MAX_VISIBLE_TRANSITION_MS) return false
            if (
                observation.kind == WeChatTeachingObservationKind.CLICK &&
                observation.source == WeChatTeachingObservationSource.ACCESSIBILITY_EVENT
            ) {
                if (adjacentRealClickSeen) return false
                adjacentRealClickSeen = true
                return@forEach
            }
            if (
                observation.kind == WeChatTeachingObservationKind.WINDOW &&
                expected(observation.windowClass.orEmpty())
            ) {
                return true
            }
        }
        return false
    }

    private fun transitionsTo(
        observations: List<WeChatTeachingObservation>,
        index: Int,
        expectedWindow: String
    ): Boolean = nextWindowAfter(observations, index) == expectedWindow

    private fun transitionsToPrefix(
        observations: List<WeChatTeachingObservation>,
        index: Int,
        expectedPrefix: String
    ): Boolean = nextWindowAfter(observations, index).orEmpty().startsWith(expectedPrefix)

    private fun nextWindowAfter(
        observations: List<WeChatTeachingObservation>,
        index: Int
    ): String? = observations
        .drop(index + 1)
        .firstOrNull { observation ->
            observation.kind == WeChatTeachingObservationKind.WINDOW ||
                (
                    observation.kind == WeChatTeachingObservationKind.CLICK &&
                        observation.source == WeChatTeachingObservationSource.ACCESSIBILITY_EVENT
                    )
        }
        ?.takeIf { it.kind == WeChatTeachingObservationKind.WINDOW }
        ?.windowClass

    private fun isCallPage(observation: WeChatTeachingObservation): Boolean =
        observation.kind == WeChatTeachingObservationKind.WINDOW &&
            observation.windowClass == WECHAT_VIDEO_ACTIVITY

    private fun expectedWindowClass(action: WeChatTeachingAction): String = when (action) {
        WeChatTeachingAction.OPEN_SEARCH -> WECHAT_SEARCH
        WeChatTeachingAction.OPEN_CONTACT -> WECHAT_CHAT
        WeChatTeachingAction.OPEN_MORE -> WECHAT_CHAT
        WeChatTeachingAction.OPEN_VIDEO_MENU -> WECHAT_VIDEO_DIALOG_PREFIX
        WeChatTeachingAction.START_VIDEO_CALL -> WECHAT_VIDEO_ACTIVITY
    }

    private const val WECHAT_CONTACT_INFO = "com.tencent.mm.plugin.profile.ui.ContactInfoUI"
    private val CONTACT_RESULT_IDS = setOf(
        "com.tencent.mm:id/odf",
        "com.tencent.mm:id/kbq"
    )

    private fun selectorScore(selector: WeChatTeachingSelector): Int {
        var score = 0
        if (!selector.resourceId.isNullOrBlank()) score += 35
        if (selector.semanticLabel != null) score += 35
        if (!selector.nodeClass.isNullOrBlank()) score += 15
        if (selector.centerXRatio != null && selector.centerYRatio != null) score += 15
        return score.coerceIn(0, 100)
    }

    private fun isBuiltInVerification(step: WeChatTeachingStep): Boolean = when (step.action) {
        WeChatTeachingAction.OPEN_SEARCH ->
            step.selector.semanticLabel == WeChatTeachingSemanticLabel.SEARCH
        WeChatTeachingAction.OPEN_CONTACT ->
            step.selector.resourceId in CONTACT_RESULT_IDS
        WeChatTeachingAction.OPEN_MORE ->
            step.selector.semanticLabel == WeChatTeachingSemanticLabel.MORE
        WeChatTeachingAction.OPEN_VIDEO_MENU ->
            step.selector.semanticLabel in setOf(
                WeChatTeachingSemanticLabel.AUDIO_VIDEO_MENU,
                WeChatTeachingSemanticLabel.VIDEO_CALL
            )
        WeChatTeachingAction.START_VIDEO_CALL ->
            step.selector.semanticLabel == WeChatTeachingSemanticLabel.VIDEO_CALL
    }

    private const val MIN_SELECTOR_SCORE = 55
    private const val MAX_VISIBLE_TRANSITION_MS = 6_000L

    private fun WeChatTeachingAction.toRequirement(): WeChatTeachingRequirement = when (this) {
        WeChatTeachingAction.OPEN_SEARCH -> WeChatTeachingRequirement.OPEN_SEARCH
        WeChatTeachingAction.OPEN_CONTACT -> WeChatTeachingRequirement.OPEN_CONTACT
        WeChatTeachingAction.OPEN_MORE -> WeChatTeachingRequirement.OPEN_MORE
        WeChatTeachingAction.OPEN_VIDEO_MENU -> WeChatTeachingRequirement.OPEN_VIDEO_MENU
        WeChatTeachingAction.START_VIDEO_CALL -> WeChatTeachingRequirement.START_VIDEO_CALL
    }
}
