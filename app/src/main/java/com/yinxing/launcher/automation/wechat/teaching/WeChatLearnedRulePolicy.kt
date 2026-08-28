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
    ): WeChatTeachingSelector? {
        val step = profile?.steps?.firstOrNull { it.action == action } ?: return null
        return step.selector.takeIf {
            currentWindowClass != null && currentWindowClass == step.windowClass
        }
    }
}
