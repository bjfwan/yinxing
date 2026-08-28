package com.yinxing.launcher.feature.settings

import android.content.Context
import com.yinxing.launcher.R
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingAction
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingProfileStatus
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingSnapshot

internal object WeChatTeachingSummaryFormatter {
    fun format(context: Context, snapshot: WeChatTeachingSnapshot): String = when (snapshot.status) {
        WeChatTeachingProfileStatus.NOT_TAUGHT ->
            context.getString(R.string.settings_wechat_teaching_summary_not_taught)
        WeChatTeachingProfileStatus.VIDEO_CONFIRMED_NO_RULES -> if (
            snapshot.verifiedActions.isEmpty()
        ) {
            context.getString(R.string.settings_wechat_teaching_summary_no_rules)
        } else {
            context.getString(
                R.string.settings_wechat_teaching_summary_verified_no_new,
                snapshot.verifiedActions.size
            )
        }
        WeChatTeachingProfileStatus.PARTIAL,
        WeChatTeachingProfileStatus.READY -> if (
            snapshot.verifiedActions.isNotEmpty() || snapshot.addedActions.isNotEmpty()
        ) {
            context.getString(
                R.string.settings_wechat_teaching_summary_difference,
                snapshot.verifiedActions.size,
                snapshot.addedActions.size,
                snapshot.learnedActions.size
            )
        } else if (snapshot.status == WeChatTeachingProfileStatus.READY) {
            context.getString(
                R.string.settings_wechat_teaching_summary_complete,
                snapshot.learnedActions.size,
                WeChatTeachingAction.entries.size
            )
        } else {
            context.getString(
                R.string.settings_wechat_teaching_summary_partial,
                snapshot.learnedActions.size,
                WeChatTeachingAction.entries.size,
                snapshot.learnedActions
                    .sortedBy { WeChatTeachingAction.entries.indexOf(it) }
                    .joinToString("、") { context.getString(it.labelRes()) }
            )
        }
        WeChatTeachingProfileStatus.NEEDS_RETEACH ->
            context.getString(R.string.settings_wechat_teaching_summary_reteach)
    }

}

internal fun WeChatTeachingAction.labelRes(): Int = when (this) {
    WeChatTeachingAction.OPEN_SEARCH -> R.string.settings_wechat_teaching_rule_search
    WeChatTeachingAction.OPEN_CONTACT -> R.string.settings_wechat_teaching_rule_contact
    WeChatTeachingAction.OPEN_MORE -> R.string.settings_wechat_teaching_rule_more
    WeChatTeachingAction.OPEN_VIDEO_MENU -> R.string.settings_wechat_teaching_rule_video_entry
    WeChatTeachingAction.START_VIDEO_CALL -> R.string.settings_wechat_teaching_rule_start_video
}
