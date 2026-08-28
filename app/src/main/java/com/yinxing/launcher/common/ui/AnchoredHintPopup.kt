package com.yinxing.launcher.common.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.StringRes
import com.yinxing.launcher.R

enum class AnchoredHintAlignment {
    Start,
    End,
}

class AnchoredHintPopup(
    private val activity: Activity,
    private val anchor: View,
    @param:StringRes private val textRes: Int,
    private val alignment: AnchoredHintAlignment,
    private val onClick: () -> Unit,
) {
    private var popupWindow: PopupWindow? = null

    val isShowing: Boolean
        get() = popupWindow?.isShowing == true

    fun show(lowPerformanceMode: Boolean) {
        if (isShowing || !anchor.isAttachedToWindow || anchor.width == 0) return

        val content = LayoutInflater.from(activity)
            .inflate(R.layout.popup_weather_detail_hint, null, false)
        content.findViewById<TextView>(R.id.tv_hint_text).setText(textRes)
        alignTail(content.findViewById(R.id.iv_hint_tail))
        content.setOnClickListener {
            dismiss()
            onClick()
        }

        val popup = PopupWindow(
            content,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false,
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isTouchable = true
            isOutsideTouchable = false
            elevation = activity.dpToPx(4).toFloat()
            setOnDismissListener {
                content.removeCallbacks(autoDismissRunnable)
                content.animate().cancel()
                if (popupWindow === this) popupWindow = null
            }
        }
        popupWindow = popup

        content.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val horizontalOffset = when (alignment) {
            AnchoredHintAlignment.Start -> 0
            AnchoredHintAlignment.End -> anchor.width - content.measuredWidth - activity.dpToPx(10)
        }
        popup.showAsDropDown(anchor, horizontalOffset, activity.dpToPx(4))

        if (!lowPerformanceMode) {
            content.alpha = 0f
            content.translationY = -activity.dpToPx(4).toFloat()
            content.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
        content.postDelayed(autoDismissRunnable, AUTO_DISMISS_DELAY_MS)
    }

    fun dismiss() {
        popupWindow?.dismiss()
    }

    private fun alignTail(tail: ImageView) {
        val layoutParams = tail.layoutParams as LinearLayout.LayoutParams
        layoutParams.gravity = when (alignment) {
            AnchoredHintAlignment.Start -> Gravity.START
            AnchoredHintAlignment.End -> Gravity.END
        }
        layoutParams.marginStart = if (alignment == AnchoredHintAlignment.Start) activity.dpToPx(24) else 0
        layoutParams.marginEnd = if (alignment == AnchoredHintAlignment.End) activity.dpToPx(24) else 0
        tail.layoutParams = layoutParams
    }

    private val autoDismissRunnable = Runnable { dismiss() }

    private fun Activity.dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    companion object {
        private const val AUTO_DISMISS_DELAY_MS = 8_000L
    }
}
