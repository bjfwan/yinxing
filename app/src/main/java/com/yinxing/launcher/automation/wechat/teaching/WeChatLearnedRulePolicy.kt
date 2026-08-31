package com.yinxing.launcher.automation.wechat.teaching

object WeChatLearnedRulePolicy {
    fun compatibleProfile(
        profile: WeChatTeachingProfile?,
        currentFingerprint: WeChatTeachingFingerprint?
    ): WeChatTeachingProfile? = profile?.takeIf {
        currentFingerprint != null &&
            it.fingerprint == currentFingerprint &&
            it.reliability != WeChatTeachingReliability.LOW &&
            it.reliabilityScore >= 55
    }

    fun selectorForFallback(
        profile: WeChatTeachingProfile?,
        currentFingerprint: WeChatTeachingFingerprint?,
        action: WeChatTeachingAction,
        builtInSucceeded: Boolean
    ): WeChatTeachingSelector? {
        if (builtInSucceeded) return null
        return compatibleProfile(profile, currentFingerprint)?.selectorFor(action)
    }

    fun selectorForWindowFallback(
        profile: WeChatTeachingProfile?,
        action: WeChatTeachingAction,
        currentWindowClass: String?
    ): WeChatTeachingSelector? = stepForWindowFallback(
        profile = profile,
        action = action,
        currentWindowClass = currentWindowClass
    )?.selector

    fun stepForWindowFallback(
        profile: WeChatTeachingProfile?,
        action: WeChatTeachingAction,
        currentWindowClass: String?
    ): WeChatTeachingStep? {
        if (currentWindowClass == null) return null
        return profile?.steps
            ?.firstOrNull { it.action == action }
            ?.takeIf { it.windowClass == currentWindowClass }
    }
}
