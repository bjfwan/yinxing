package com.yinxing.launcher.automation.wechat.teaching

import com.yinxing.launcher.automation.wechat.WeChatViewIds

data class WeChatTeachingRuleClassification(
    val verifiedActions: Set<WeChatTeachingAction>,
    val learnedSteps: List<WeChatTeachingStep>
)

object WeChatTeachingRuleClassifier {
    fun classify(profile: WeChatTeachingProfile?): WeChatTeachingRuleClassification {
        if (profile == null) {
            return WeChatTeachingRuleClassification(emptySet(), emptyList())
        }
        val verified = linkedSetOf<WeChatTeachingAction>()
        val learned = mutableListOf<WeChatTeachingStep>()
        profile.steps.forEach { step ->
            if (isCoveredByBuiltInRule(step)) {
                verified += step.action
            } else {
                learned += step
            }
        }
        return WeChatTeachingRuleClassification(verified, learned)
    }

    private fun isCoveredByBuiltInRule(step: WeChatTeachingStep): Boolean {
        val selector = step.selector
        return when (step.action) {
            WeChatTeachingAction.OPEN_SEARCH ->
                selector.semanticLabel == WeChatTeachingSemanticLabel.SEARCH ||
                    selector.resourceId in WeChatViewIds.TOP_SEARCH_BAR_IDS
            WeChatTeachingAction.OPEN_CONTACT ->
                selector.resourceId in WeChatViewIds.CONTACT_RESULT_TITLE_IDS
            WeChatTeachingAction.OPEN_MORE ->
                selector.semanticLabel == WeChatTeachingSemanticLabel.MORE ||
                    selector.resourceId in WeChatViewIds.MORE_BUTTON_FALLBACK_IDS
            WeChatTeachingAction.OPEN_VIDEO_MENU,
            WeChatTeachingAction.START_VIDEO_CALL ->
                selector.semanticLabel == WeChatTeachingSemanticLabel.VIDEO_CALL
        }
    }
}
