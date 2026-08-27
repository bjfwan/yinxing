package com.yinxing.launcher.feature.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import com.yinxing.launcher.R

class SettingsProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {
    private val strokeWidth = 5f * resources.displayMetrics.density
    private val bounds = RectF()
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.launcher_divider)
        style = Paint.Style.STROKE
        strokeWidth = this@SettingsProgressView.strokeWidth
    }
    private val progressPaint = Paint(trackPaint).apply {
        color = ContextCompat.getColor(context, R.color.launcher_action)
        strokeCap = Paint.Cap.ROUND
    }
    private var fraction = 0f

    fun setProgress(completed: Int, total: Int) {
        fraction = if (total > 0) completed.toFloat().div(total).coerceIn(0f, 1f) else 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val inset = strokeWidth / 2f
        bounds.set(inset, inset, width - inset, height - inset)
        canvas.drawOval(bounds, trackPaint)
        if (fraction > 0f) canvas.drawArc(bounds, -90f, 360f * fraction, false, progressPaint)
        super.onDraw(canvas)
    }
}
