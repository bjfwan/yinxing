package com.yinxing.launcher.feature.settings

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.yinxing.launcher.R

internal class SettingsDialogController(
    private val activity: SettingsActivity
) {
    fun showIncomingGuardDialog() = activity.showIncomingGuardDialog()

    fun showSystemDialog() = activity.showSystemDialog()

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

internal fun SettingsActivity.createListDialog(title: String, message: String): ListDialogContext {
    val contentView = layoutInflater.inflate(R.layout.dialog_permission_group, FrameLayout(this), false)
    contentView.findViewById<TextView>(R.id.tv_dialog_title).text = title
    contentView.findViewById<TextView>(R.id.tv_dialog_message).text = message
    val dialog = AlertDialog.Builder(this).setView(contentView).create()
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    dialog.setOnShowListener {
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.84f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    contentView.findViewById<View>(R.id.btn_close).setOnClickListener { dialog.dismiss() }
    return ListDialogContext(
        dialog = dialog,
        contentView = contentView,
        container = contentView.findViewById(R.id.layout_permission_items)
    )
}

internal fun SettingsActivity.addDialogEntry(
    context: ListDialogContext,
    title: String,
    summary: String,
    badge: BadgeStyle,
    iconResId: Int? = null,
    iconTintResId: Int = R.color.launcher_primary_dark,
    iconPlateResId: Int = R.color.launcher_surface,
    container: LinearLayout = context.container,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val layoutRes = if (compact) {
        R.layout.item_settings_permission_entry_compact
    } else {
        R.layout.item_settings_permission_entry
    }
    val itemView = layoutInflater.inflate(layoutRes, container, false)
    iconResId?.let { icon ->
        itemView.findViewById<ImageView>(R.id.iv_permission_item_icon).apply {
            visibility = View.VISIBLE
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this@addDialogEntry, iconTintResId))
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@addDialogEntry, iconPlateResId))
        }
    }
    itemView.findViewById<TextView>(R.id.tv_permission_item_title).text = title
    itemView.findViewById<TextView>(R.id.tv_permission_item_summary).text = summary
    overviewController.applyInfoBadge(
        tv = itemView.findViewById(R.id.tv_permission_item_status),
        text = badge.text,
        textColorResId = badge.textColorResId,
        backgroundColorResId = badge.backgroundColorResId
    )
    if (compact) itemView.findViewById<TextView>(R.id.tv_permission_item_status).background = null
    itemView.setOnClickListener { onClick() }
    container.addView(itemView)
}

internal fun SettingsActivity.addDialogSection(
    context: ListDialogContext,
    title: String
): LinearLayout {
    val section = layoutInflater.inflate(
        R.layout.item_settings_dialog_section,
        context.container,
        false
    )
    section.findViewById<TextView>(R.id.tv_section_title).text = title
    context.container.addView(section)
    return section.findViewById(R.id.layout_section_items)
}

internal fun SettingsActivity.actionBadge(text: String): BadgeStyle {
    return BadgeStyle(
        text = text,
        textColorResId = R.color.launcher_action,
        backgroundColorResId = R.color.launcher_surface_muted
    )
}
