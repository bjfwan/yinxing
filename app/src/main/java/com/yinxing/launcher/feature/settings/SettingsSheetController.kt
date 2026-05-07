package com.yinxing.launcher.feature.settings

import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.yinxing.launcher.R

internal class SettingsSheetController(
    private val activity: SettingsActivity
) {
    fun showIncomingGuardSheet() = activity.showIncomingGuardSheet()

    fun showContactsSheet() = activity.showContactsSheet()

    fun showAutoAnswerSheet() = activity.showAutoAnswerSheet()

    fun showPermissionGroupsSheet() = activity.showPermissionGroupsSheet()

    fun showDeviceSettingsSheet() = activity.showDeviceSettingsSheet()

    fun showSystemSheet() = activity.showSystemSheet()

    fun playEntryAnimation() = activity.playEntryAnimation()
}

internal fun SettingsActivity.playEntryAnimation() {
    val root = findViewById<View>(R.id.scroll_settings_root) ?: return
    root.alpha = 0f
    root.translationY = 24f
    root.post {
        root.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(260)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }
}

internal fun SettingsActivity.createListSheet(title: String, message: String): ListSheetContext {
    val dialog = BottomSheetDialog(this)
    val contentView = layoutInflater.inflate(R.layout.dialog_permission_group, FrameLayout(this), false)
    contentView.findViewById<TextView>(R.id.tv_dialog_title).text = title
    contentView.findViewById<TextView>(R.id.tv_dialog_message).text = message
    contentView.findViewById<View>(R.id.btn_close).setOnClickListener { dialog.dismiss() }
    dialog.setContentView(contentView)
    return ListSheetContext(
        dialog = dialog,
        contentView = contentView,
        container = contentView.findViewById(R.id.layout_permission_items)
    )
}

internal fun SettingsActivity.addSheetEntry(
    context: ListSheetContext,
    title: String,
    summary: String,
    badge: BadgeStyle,
    onClick: () -> Unit
) {
    val itemView = layoutInflater.inflate(R.layout.item_settings_permission_entry, context.container, false)
    itemView.findViewById<TextView>(R.id.tv_permission_item_title).text = title
    itemView.findViewById<TextView>(R.id.tv_permission_item_summary).text = summary
    overviewController.applyInfoBadge(
        tv = itemView.findViewById(R.id.tv_permission_item_status),
        text = badge.text,
        textColorResId = badge.textColorResId,
        backgroundColorResId = badge.backgroundColorResId
    )
    itemView.setOnClickListener { onClick() }
    context.container.addView(itemView)
}

internal fun SettingsActivity.addSheetTip(
    context: ListSheetContext,
    title: String,
    lines: List<String>
) {
    val tipView = layoutInflater.inflate(R.layout.item_settings_tip, context.container, false)
    tipView.findViewById<TextView>(R.id.tv_tip_title).text = title
    val linesContainer = tipView.findViewById<LinearLayout>(R.id.layout_tip_lines)
    lines.forEachIndexed { index, text ->
        val lineView = layoutInflater.inflate(R.layout.item_settings_tip_line, linesContainer, false)
        lineView.findViewById<TextView>(R.id.tv_tip_line_index).text = "${index + 1}."
        lineView.findViewById<TextView>(R.id.tv_tip_line_text).text = text
        linesContainer.addView(lineView)
    }
    context.container.addView(tipView)
}

internal fun SettingsActivity.actionBadge(text: String): BadgeStyle {
    return BadgeStyle(
        text = text,
        textColorResId = R.color.launcher_action,
        backgroundColorResId = R.color.launcher_surface_muted
    )
}
