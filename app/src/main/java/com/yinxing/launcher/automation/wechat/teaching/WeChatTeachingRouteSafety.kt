package com.yinxing.launcher.automation.wechat.teaching

object WeChatTeachingCapturePolicy {
    fun shouldCapture(videoCallConfirmed: Boolean): Boolean = !videoCallConfirmed

    data class Decision(
        val shouldCapture: Boolean,
        val currentWindowClass: String?
    )

    fun decide(
        videoCallConfirmed: Boolean,
        currentWindowClass: String?,
        observedWindowClass: String?
    ): Decision = Decision(
        shouldCapture = shouldCapture(videoCallConfirmed),
        currentWindowClass = observedWindowClass ?: currentWindowClass
    )
}

object WeChatTeachingRouteTerminalPolicy {
    const val VIDEO_ACTIVITY = "com.tencent.mm.plugin.voip.ui.VideoActivity"

    fun isTerminal(step: WeChatTeachingRouteStep): Boolean =
        step.expectedState?.windowClass == VIDEO_ACTIVITY
}

object WeChatTeachingRouteSafetyPolicy {
    private const val WECHAT_HOME = "com.tencent.mm.ui.LauncherUI"

    fun prepareForReplay(route: WeChatTeachingRoute): WeChatTeachingRoute? {
        val semanticTerminalIndex = route.steps.indexOfLast {
            it.selector?.semanticLabel == WeChatTeachingSemanticLabel.VIDEO_CALL
        }
        if (
            semanticTerminalIndex < 0 &&
            route.steps.any { it.selector?.semanticLabel != null }
        ) return null
        val terminalIndex = semanticTerminalIndex.takeIf { it >= 0 }
            ?: route.steps.indexOfLast(WeChatTeachingRouteTerminalPolicy::isTerminal)
        if (terminalIndex < 0) return null

        val semanticWorkflow = route.steps.any { it.selector?.semanticLabel != null }
        var inputKept = false
        val trimmed = removeRedundantOpenMenuStep(
            startState = route.startState,
            steps = removeRepeatedVideoMenuDetour(
                route.steps
                    .take(terminalIndex + 1)
                    .filterNot {
                        semanticWorkflow && it.type == WeChatTeachingRouteStepType.SCROLL_FORWARD
                    }
                    .filter { step ->
                        if (
                            !semanticWorkflow ||
                            step.type != WeChatTeachingRouteStepType.INPUT_CONTACT_PLACEHOLDER
                        ) {
                            true
                        } else if (!inputKept) {
                            inputKept = true
                            true
                        } else {
                            false
                        }
                    }
            )
        ).toMutableList()
        if (trimmed.isEmpty()) return null
        for (index in 0 until trimmed.lastIndex) {
            val nextLabel = trimmed[index + 1].selector?.semanticLabel
            if (nextLabel != null) {
                trimmed[index] = trimmed[index].copy(
                    expectedState = WeChatTeachingStateFingerprint(
                        windowClass = null,
                        semanticLabels = setOf(nextLabel),
                        resourceIds = emptySet()
                    )
                )
            } else if (
                trimmed[index].expectedState?.windowClass ==
                WeChatTeachingRouteTerminalPolicy.VIDEO_ACTIVITY
            ) {
                trimmed[index] = trimmed[index].copy(expectedState = null)
            }
        }
        val terminal = trimmed.last().copy(
            expectedState = WeChatTeachingStateFingerprint(
                windowClass = WeChatTeachingRouteTerminalPolicy.VIDEO_ACTIVITY,
                semanticLabels = emptySet(),
                resourceIds = emptySet()
            )
        )
        trimmed[trimmed.lastIndex] = terminal

        if (
            route.startState.windowClass == WECHAT_HOME &&
            trimmed.none { it.type == WeChatTeachingRouteStepType.INPUT_CONTACT_PLACEHOLDER }
        ) return null

        val labels = trimmed.mapNotNull { it.selector?.semanticLabel }
        val startsBeforeVideoSheet = route.startState.windowClass in setOf(
            "com.tencent.mm.ui.chatting.ChattingUI",
            "com.tencent.mm.plugin.profile.ui.ContactInfoUI"
        )
        if (
            startsBeforeVideoSheet &&
            WeChatTeachingSemanticLabel.AUDIO_VIDEO_MENU !in labels
        ) return null
        if (
            route.startState.windowClass == WECHAT_HOME &&
            WeChatTeachingSemanticLabel.SEARCH in labels &&
            (
                WeChatTeachingSemanticLabel.MORE !in labels ||
                    WeChatTeachingSemanticLabel.AUDIO_VIDEO_MENU !in labels ||
                    WeChatTeachingSemanticLabel.VIDEO_CALL !in labels
                )
        ) return null

        val firstAction = trimmed.firstOrNull {
            it.type !in setOf(
                WeChatTeachingRouteStepType.LAUNCH_WECHAT,
                WeChatTeachingRouteStepType.WAIT_FOR_STATE
            )
        }
        if (
            route.startState.windowClass == WECHAT_HOME &&
            firstAction?.selector?.semanticLabel in setOf(
                WeChatTeachingSemanticLabel.MORE,
                WeChatTeachingSemanticLabel.VIDEO_CALL
            )
        ) return null

        val safeSteps = trimmed.toList()
        return route.copy(
            routeId = WeChatTeachingRouteIdentity.create(
                route.fingerprint,
                route.startState,
                safeSteps
            ),
            steps = safeSteps,
            lastFailureStep = route.lastFailureStep?.takeIf { it in safeSteps.indices }
        )
    }

    private fun removeRepeatedVideoMenuDetour(
        steps: List<WeChatTeachingRouteStep>
    ): List<WeChatTeachingRouteStep> {
        if (steps.size < 4) return steps
        val terminalIndex = steps.lastIndex
        if (
            steps[terminalIndex].selector?.semanticLabel !=
            WeChatTeachingSemanticLabel.VIDEO_CALL
        ) return steps

        val firstMore = steps.indexOfFirst {
            it.selector?.semanticLabel == WeChatTeachingSemanticLabel.MORE
        }
        if (firstMore < 0) return steps
        val firstVideoMenu = (firstMore + 1 until terminalIndex).firstOrNull { index ->
            steps[index].selector?.semanticLabel == WeChatTeachingSemanticLabel.AUDIO_VIDEO_MENU
        } ?: return steps
        val repeatedMore = (firstVideoMenu + 1 until terminalIndex).any { index ->
            steps[index].selector?.semanticLabel == WeChatTeachingSemanticLabel.MORE
        }
        if (!repeatedMore) return steps

        return buildList {
            addAll(steps.take(firstMore + 1))
            add(steps[firstVideoMenu])
            add(steps[terminalIndex])
        }
    }

    private fun removeRedundantOpenMenuStep(
        startState: WeChatTeachingStateFingerprint,
        steps: List<WeChatTeachingRouteStep>
    ): List<WeChatTeachingRouteStep> {
        if (WeChatTeachingSemanticLabel.AUDIO_VIDEO_MENU !in startState.semanticLabels) return steps
        val firstActionIndex = steps.indexOfFirst {
            it.type !in setOf(
                WeChatTeachingRouteStepType.LAUNCH_WECHAT,
                WeChatTeachingRouteStepType.WAIT_FOR_STATE
            )
        }
        if (
            firstActionIndex < 0 ||
            steps[firstActionIndex].selector?.semanticLabel != WeChatTeachingSemanticLabel.MORE
        ) return steps
        return steps.filterIndexed { index, _ -> index != firstActionIndex }
    }
}
