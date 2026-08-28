package com.google.android.accessibility.selecttospeak

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.yinxing.launcher.R
import com.yinxing.launcher.common.util.DebugLog

internal class WeChatTeachingOverlay(private val service: AccessibilityService) {
    companion object {
        private const val TAG = "WeChatTeachingOverlay"
    }

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var view: View? = null
    private var windowLayoutParams: WindowManager.LayoutParams? = null

    fun show(
        message: String,
        primaryText: String,
        @DrawableRes primaryBackgroundRes: Int,
        secondaryText: String?,
        onPrimary: () -> Unit,
        onSecondary: (() -> Unit)?,
        @ColorRes messageColorRes: Int = R.color.launcher_text_primary
    ): Boolean {
        return runCatching {
            val compact = secondaryText == null
            val target = view ?: LayoutInflater.from(service).inflate(
                    R.layout.floating_wechat_teaching,
                    FrameLayout(service),
                    false
                ).also { inflated ->
                    val params = layoutParams(compact)
                    windowManager.addView(inflated, params)
                    windowLayoutParams = params
                    WeChatTeachingDragTargets.collect(inflated).forEach { dragSurface ->
                        makeDraggable(dragSurface, inflated, params)
                    }
                    view = inflated
                }

            windowLayoutParams?.let { params ->
                val targetWidth = dp(if (compact) 112 else 176)
                if (params.width != targetWidth) {
                    params.width = targetWidth
                    windowManager.updateViewLayout(target, params)
                }
            }
            target.findViewById<TextView>(R.id.wechat_teaching_message).apply {
                text = message
                setTextColor(ContextCompat.getColor(service, messageColorRes))
            }
            target.findViewById<TextView>(R.id.wechat_teaching_primary).apply {
                text = primaryText
                setBackgroundResource(primaryBackgroundRes)
                setOnClickListener { onPrimary() }
            }
            target.findViewById<TextView>(R.id.wechat_teaching_secondary).apply {
                text = secondaryText.orEmpty()
                visibility = if (secondaryText == null || onSecondary == null) View.GONE else View.VISIBLE
                setOnClickListener { onSecondary?.invoke() }
            }
            target.post { keepInsideScreen(target) }
            true
        }.onFailure { error ->
            DebugLog.e(TAG, "微信示教悬浮按钮显示失败", error)
            hide()
        }.getOrDefault(false)
    }

    fun hide() {
        val current = view ?: return
        runCatching { windowManager.removeView(current) }
        view = null
        windowLayoutParams = null
    }

    private fun layoutParams(compact: Boolean) = WindowManager.LayoutParams(
        dp(if (compact) 112 else 176),
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        val metrics = service.resources.displayMetrics
        val margin = dp(6)
        gravity = Gravity.START or Gravity.TOP
        x = (metrics.widthPixels - width - margin).coerceAtLeast(margin)
        y = (metrics.heightPixels - dp(90)).coerceAtLeast(margin) / 2
    }

    private fun makeDraggable(
        dragSurface: View,
        target: View,
        params: WindowManager.LayoutParams
    ) {
        val touchSlop = ViewConfiguration.get(service).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragging = false

        dragSurface.isClickable = true
        if (dragSurface === target) {
            dragSurface.setOnClickListener { }
        }
        dragSurface.setOnTouchListener { touchedView, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    if (!dragging && (kotlin.math.abs(deltaX) > touchSlop || kotlin.math.abs(deltaY) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        moveTo(target, params, startX + deltaX.toInt(), startY + deltaY.toInt())
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) touchedView.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    private fun moveTo(target: View, params: WindowManager.LayoutParams, x: Int, y: Int) {
        val metrics = service.resources.displayMetrics
        val position = WeChatTeachingDragBounds.clamp(
            x = x,
            y = y,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            viewWidth = target.width.takeIf { it > 0 } ?: params.width,
            viewHeight = target.height.takeIf { it > 0 } ?: dp(90),
            margin = dp(6)
        )
        params.x = position.x
        params.y = position.y
        runCatching { windowManager.updateViewLayout(target, params) }
            .onFailure { DebugLog.w(TAG, "微信示教悬浮按钮移动失败", it) }
    }

    private fun keepInsideScreen(target: View) {
        val params = windowLayoutParams ?: return
        moveTo(target, params, params.x, params.y)
    }

    private fun dp(value: Int): Int =
        (value * service.resources.displayMetrics.density).toInt()
}

internal object WeChatTeachingDragTargets {
    fun collect(root: View): List<View> = listOf(
        root,
        root.findViewById<View>(R.id.wechat_teaching_primary),
        root.findViewById<View>(R.id.wechat_teaching_secondary)
    )
}
