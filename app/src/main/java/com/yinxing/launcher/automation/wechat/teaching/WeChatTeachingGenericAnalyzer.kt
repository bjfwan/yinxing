package com.yinxing.launcher.automation.wechat.teaching

import kotlin.math.abs

enum class WeChatTeachingGenericFailure {
    VIDEO_NOT_CONFIRMED,
    NO_REUSABLE_STEPS,
    LOW_RELIABILITY
}

sealed interface WeChatTeachingGenericResult {
    data class Complete(val route: WeChatTeachingRoute) : WeChatTeachingGenericResult
    data class Incomplete(
        val reason: WeChatTeachingGenericFailure,
        val route: WeChatTeachingRoute? = null
    ) : WeChatTeachingGenericResult
}

object WeChatTeachingBackInference {
    fun shouldInfer(
        history: List<WeChatTeachingObservation>,
        previousWindowClass: String?,
        currentWindowClass: String?
    ): Boolean {
        if (
            previousWindowClass == null ||
            currentWindowClass == null ||
            previousWindowClass == currentWindowClass
        ) return false
        if (history.none {
                it.kind == WeChatTeachingObservationKind.WINDOW &&
                    it.windowClass == currentWindowClass
            }
        ) return false
        val previousWindowIndex = history.indexOfLast {
            it.kind == WeChatTeachingObservationKind.WINDOW &&
                it.windowClass == previousWindowClass
        }
        if (previousWindowIndex < 0) return false
        return history.drop(previousWindowIndex + 1).none {
            it.kind == WeChatTeachingObservationKind.CLICK
        }
    }
}

object WeChatTeachingGenericAnalyzer {
    private const val VIDEO_ACTIVITY = "com.tencent.mm.plugin.voip.ui.VideoActivity"
    private const val MIN_CAPTURE_RELIABILITY = 55

    fun analyze(
        observations: List<WeChatTeachingObservation>,
        fingerprint: WeChatTeachingFingerprint,
        videoCallConfirmed: Boolean,
        createdAtEpochMs: Long,
        initialState: WeChatTeachingStateFingerprint? = null
    ): WeChatTeachingGenericResult {
        val safe = observations.filter { observation ->
            observation.windowClass == null || isSafeWeChatClass(observation.windowClass)
        }
        val startState = WeChatTeachingStateFingerprint(
            windowClass = initialState?.windowClass?.takeIf(::isSafeWeChatClass)
                ?: safe.firstOrNull()?.windowClass,
            semanticLabels = initialState?.semanticLabels.orEmpty(),
            resourceIds = emptySet()
        )
        val steps = buildList {
            safe.forEachIndexed { index, observation ->
                val expectedState = safe.drop(index + 1)
                    .firstOrNull { it.kind == WeChatTeachingObservationKind.WINDOW }
                    ?.let(::stateFromObservation)
                when (observation.kind) {
                    WeChatTeachingObservationKind.LAUNCH -> add(
                        WeChatTeachingRouteStep(
                            type = WeChatTeachingRouteStepType.LAUNCH_WECHAT,
                            expectedState = stateFromObservation(observation)
                        )
                    )
                    WeChatTeachingObservationKind.CLICK -> {
                        if (
                            observation.source == WeChatTeachingObservationSource.VISIBLE_CONTROL &&
                            !isValidatedVisibleSample(safe, index)
                        ) return@forEachIndexed
                        val selector = selectorWithVisibleSemantic(
                            observations = safe,
                            index = index,
                            observation = observation
                        )?.sanitize() ?: return@forEachIndexed
                        val type = if (
                            selector.resourceId != null || selector.semanticLabel != null ||
                            selector.nodeClass != null
                        ) {
                            WeChatTeachingRouteStepType.CLICK_CONTROL
                        } else {
                            WeChatTeachingRouteStepType.CLICK_RELATIVE_POSITION
                        }
                        add(WeChatTeachingRouteStep(type, selector, expectedState))
                    }
                    WeChatTeachingObservationKind.INPUT_CONTACT -> {
                        if (lastOrNull()?.type != WeChatTeachingRouteStepType.INPUT_CONTACT_PLACEHOLDER) {
                            add(
                                WeChatTeachingRouteStep(
                                    type = WeChatTeachingRouteStepType.INPUT_CONTACT_PLACEHOLDER,
                                    selector = observation.selector?.sanitize(),
                                    expectedState = expectedState
                                )
                            )
                        }
                    }
                    WeChatTeachingObservationKind.SCROLL -> add(
                        WeChatTeachingRouteStep(
                            type = WeChatTeachingRouteStepType.SCROLL_FORWARD,
                            selector = observation.selector?.sanitize(),
                            expectedState = expectedState
                        )
                    )
                    WeChatTeachingObservationKind.BACK -> add(
                        WeChatTeachingRouteStep(
                            type = WeChatTeachingRouteStepType.GO_BACK,
                            expectedState = expectedState
                        )
                    )
                    WeChatTeachingObservationKind.WINDOW -> if (index == safe.lastIndex) {
                        add(
                            WeChatTeachingRouteStep(
                                type = WeChatTeachingRouteStepType.WAIT_FOR_STATE,
                                expectedState = stateFromObservation(observation)
                            )
                        )
                    }
                }
            }
        }
        val hasReusableAction = steps.any {
            it.type !in setOf(
                WeChatTeachingRouteStepType.LAUNCH_WECHAT,
                WeChatTeachingRouteStepType.WAIT_FOR_STATE
            )
        }
        if (!hasReusableAction) {
            return WeChatTeachingGenericResult.Incomplete(
                WeChatTeachingGenericFailure.NO_REUSABLE_STEPS
            )
        }
        val score = steps.map(::score).average().toInt().coerceIn(0, 100)
        val rawRoute = WeChatTeachingRoute(
            routeId = WeChatTeachingRouteIdentity.create(fingerprint, startState, steps),
            fingerprint = fingerprint,
            startState = startState,
            steps = steps,
            endEvidence = WeChatTeachingRouteEndEvidence.VIDEO_CALL_CONFIRMED,
            source = WeChatTeachingRouteSource.DEMONSTRATION,
            priority = 0,
            reliabilityScore = score,
            lifecycle = WeChatTeachingRouteLifecycle.CANDIDATE,
            createdAtEpochMs = createdAtEpochMs
        )
        val route = WeChatTeachingRouteSafetyPolicy.prepareForReplay(rawRoute)
            ?: return WeChatTeachingGenericResult.Incomplete(
                WeChatTeachingGenericFailure.NO_REUSABLE_STEPS
            )
        if (!videoCallConfirmed || safe.none { it.windowClass == VIDEO_ACTIVITY }) {
            return WeChatTeachingGenericResult.Incomplete(
                WeChatTeachingGenericFailure.VIDEO_NOT_CONFIRMED,
                route
            )
        }
        if (score < MIN_CAPTURE_RELIABILITY) {
            return WeChatTeachingGenericResult.Incomplete(
                WeChatTeachingGenericFailure.LOW_RELIABILITY,
                route
            )
        }
        return WeChatTeachingGenericResult.Complete(route)
    }

    private fun stateFromObservation(observation: WeChatTeachingObservation) =
        WeChatTeachingStateFingerprint(
            windowClass = observation.windowClass,
            semanticLabels = observation.selector?.semanticLabel?.let(::setOf).orEmpty(),
            resourceIds = observation.selector?.resourceId?.takeIf(::isSafeResourceId)
                ?.let(::setOf)
                .orEmpty()
        )

    private fun score(step: WeChatTeachingRouteStep): Int = when (step.type) {
        WeChatTeachingRouteStepType.CLICK_CONTROL -> selectorScore(step.selector)
        WeChatTeachingRouteStepType.CLICK_RELATIVE_POSITION -> 55
        WeChatTeachingRouteStepType.LAUNCH_WECHAT,
        WeChatTeachingRouteStepType.INPUT_CONTACT_PLACEHOLDER,
        WeChatTeachingRouteStepType.SCROLL_FORWARD,
        WeChatTeachingRouteStepType.GO_BACK,
        WeChatTeachingRouteStepType.WAIT_FOR_STATE -> 75
    }

    private fun selectorScore(selector: WeChatTeachingSelector?): Int {
        if (selector == null) return 0
        var score = 0
        if (selector.resourceId != null) score += 40
        if (selector.semanticLabel != null) score += 30
        if (selector.nodeClass != null) score += 15
        if (selector.centerXRatio != null && selector.centerYRatio != null) score += 15
        return score.coerceAtMost(100)
    }

    private fun WeChatTeachingSelector.sanitize() = copy(
        resourceId = resourceId?.takeIf(::isSafeResourceId),
        nodeClass = nodeClass?.takeIf(::isSafeClass),
        clickableAncestorDepth = clickableAncestorDepth.coerceIn(-1, 6),
        centerXRatio = centerXRatio?.takeIf { it in 0f..1f },
        centerYRatio = centerYRatio?.takeIf { it in 0f..1f }
    )

    private fun selectorWithVisibleSemantic(
        observations: List<WeChatTeachingObservation>,
        index: Int,
        observation: WeChatTeachingObservation
    ): WeChatTeachingSelector? {
        val selector = observation.selector ?: return null
        if (
            observation.source != WeChatTeachingObservationSource.ACCESSIBILITY_EVENT ||
            selector.semanticLabel != null
        ) return selector

        val visibleCandidates = observations
            .subList(0, index)
            .asReversed()
            .takeWhile { earlier ->
                earlier.kind != WeChatTeachingObservationKind.WINDOW &&
                    !(
                        earlier.kind == WeChatTeachingObservationKind.CLICK &&
                            earlier.source == WeChatTeachingObservationSource.ACCESSIBILITY_EVENT
                        )
            }
            .filter { earlier ->
                earlier.kind == WeChatTeachingObservationKind.CLICK &&
                    earlier.source == WeChatTeachingObservationSource.VISIBLE_CONTROL &&
                    earlier.windowClass == observation.windowClass &&
                    earlier.selector?.semanticLabel != null
            }
        val sameResource = selector.resourceId?.let { resourceId ->
            visibleCandidates.firstOrNull { it.selector?.resourceId == resourceId }
        }
        val nearest = visibleCandidates
            .mapNotNull { candidate ->
                val candidateSelector = candidate.selector ?: return@mapNotNull null
                val x = selector.centerXRatio ?: return@mapNotNull null
                val y = selector.centerYRatio ?: return@mapNotNull null
                val candidateX = candidateSelector.centerXRatio ?: return@mapNotNull null
                val candidateY = candidateSelector.centerYRatio ?: return@mapNotNull null
                candidate to (abs(x - candidateX) + abs(y - candidateY))
            }
            .filter { (_, distance) -> distance <= 0.2f }
            .minByOrNull { (_, distance) -> distance }
            ?.first
        val semanticLabel = (sameResource ?: nearest)?.selector?.semanticLabel ?: return selector
        return selector.copy(semanticLabel = semanticLabel)
    }

    private fun isSafeResourceId(value: String): Boolean =
        value.matches(Regex("^com\\.tencent\\.mm:id/[A-Za-z0-9_]+$"))

    private fun isSafeClass(value: String): Boolean =
        value.matches(Regex("^(?:android|androidx|com\\.tencent\\.mm)\\.[A-Za-z0-9_.$]+$"))

    private fun isSafeWeChatClass(value: String): Boolean =
        value.startsWith("com.tencent.mm.")

    private fun isValidatedVisibleSample(
        observations: List<WeChatTeachingObservation>,
        index: Int
    ): Boolean {
        val nextWindowIndex = (index + 1 until observations.size)
            .firstOrNull { observations[it].kind == WeChatTeachingObservationKind.WINDOW }
            ?: return false
        val between = observations.subList(index + 1, nextWindowIndex)
        if (between.any {
                it.kind == WeChatTeachingObservationKind.CLICK &&
                    it.source == WeChatTeachingObservationSource.ACCESSIBILITY_EVENT
            }
        ) return false
        return true
    }
}
