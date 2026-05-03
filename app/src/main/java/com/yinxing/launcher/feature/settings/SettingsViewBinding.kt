package com.yinxing.launcher.feature.settings

import android.app.Activity
import android.view.View
import android.widget.TextView
import com.yinxing.launcher.R

/**
 * 把 [SettingsActivity] 顶层 16 个 `internal lateinit` 视图字段聚合到一个 binding 容器里，
 * 一次性 [findViewById]，让 Activity 主体保持简短。
 *
 * 不引入 ViewBinding 的原因：项目同时混用 ViewBinding 与 [findViewById]，
 * 这里改成 ViewBinding 会触发对应 layout 的命名规范 + 全 Controller 的连锁修改，
 * 价值小于风险。手工聚合即可解决"字段噪音"低级问题。
 */
internal class SettingsViewBinding(activity: Activity) {
    val tvIncomingGuardStatus: TextView = activity.findViewById(R.id.tv_incoming_guard_status)
    val tvIncomingGuardProgress: TextView = activity.findViewById(R.id.tv_incoming_guard_progress)
    val tvIncomingGuardSummary: TextView = activity.findViewById(R.id.tv_incoming_guard_summary)
    val tvIncomingGuardAction: TextView = activity.findViewById(R.id.tv_incoming_guard_action)
    val btnIncomingGuardAction: View = activity.findViewById(R.id.btn_incoming_guard_action)

    val tvContactsHubSummary: TextView = activity.findViewById(R.id.tv_contacts_hub_summary)
    val tvAutoAnswerHubStatus: TextView = activity.findViewById(R.id.tv_auto_answer_hub_status)
    val tvAutoAnswerHubSummary: TextView = activity.findViewById(R.id.tv_auto_answer_hub_summary)
    val tvPermissionHubStatus: TextView = activity.findViewById(R.id.tv_permission_hub_status)
    val tvPermissionHubSummary: TextView = activity.findViewById(R.id.tv_permission_hub_summary)
    val tvDeviceHubStatus: TextView = activity.findViewById(R.id.tv_device_hub_status)
    val tvDeviceHubSummary: TextView = activity.findViewById(R.id.tv_device_hub_summary)
    val tvSystemHubSummary: TextView = activity.findViewById(R.id.tv_system_hub_summary)
}
