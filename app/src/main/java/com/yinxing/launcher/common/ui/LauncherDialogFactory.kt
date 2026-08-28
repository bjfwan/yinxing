package com.yinxing.launcher.common.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import com.yinxing.launcher.R

object LauncherDialogFactory {
    fun create(
        context: Context,
        contentView: View,
        dismissOnTouchOutside: Boolean = true,
        onShow: ((AlertDialog) -> Unit)? = null
    ): AlertDialog {
        val container = createContainer(context, contentView)
        val dialog = AlertDialog.Builder(context)
            .setView(container)
            .create()

        dialog.setCanceledOnTouchOutside(dismissOnTouchOutside)
        applyWindowChrome(dialog)
        dialog.setOnShowListener {
            applyWindowSize(dialog, context)
            onShow?.invoke(dialog)
        }
        return dialog
    }

    private fun createContainer(context: Context, contentView: View): FrameLayout {
        val surfaceMargin = context.resources.getDimensionPixelSize(R.dimen.launcher_dialog_surface_margin)
        contentView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            setPadding(surfaceMargin, surfaceMargin, surfaceMargin, surfaceMargin)
            addView(contentView)
        }
    }

    private fun applyWindowChrome(dialog: AlertDialog) {
        val window = dialog.window ?: return
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(DIALOG_DIM_AMOUNT)
        window.setGravity(Gravity.CENTER)
        window.setWindowAnimations(R.style.Animation_OldLauncher_Dialog)
    }

    private fun applyWindowSize(dialog: AlertDialog, context: Context) {
        val window = dialog.window ?: return
        val displayMetrics = context.resources.displayMetrics
        val maxWidth = context.resources.getDimensionPixelSize(R.dimen.launcher_dialog_window_max_width)
        val dialogWidth = displayMetrics.widthPixels.coerceAtMost(maxWidth)
        window.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private const val DIALOG_DIM_AMOUNT = 0.46f
}
