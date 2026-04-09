package com.bajianfeng.launcher.common.ui

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.bajianfeng.launcher.R

class FloatingStatusView(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var floatingView: android.view.View? = null
    private var isShowing = false

    fun show(message: String) {
        mainHandler.post {
            if (isShowing) {
                updateMessage(message)
                return@post
            }

            try {
                floatingView = LayoutInflater.from(context).inflate(
                    R.layout.floating_status,
                    FrameLayout(context),
                    false
                )

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_PHONE
                    },
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = 100
                }

                floatingView?.findViewById<TextView>(R.id.tv_status)?.text = message
                windowManager.addView(floatingView, params)
                isShowing = true
            } catch (_: Exception) {
                isShowing = false
                floatingView = null
            }
        }
    }

    fun updateMessage(message: String) {
        mainHandler.post {
            floatingView?.findViewById<TextView>(R.id.tv_status)?.text = message
        }
    }

    fun hide() {
        mainHandler.post {
            try {
                if (floatingView != null) {
                    windowManager.removeView(floatingView)
                }
            } catch (_: Exception) {
            } finally {
                floatingView = null
                isShowing = false
            }
        }
    }
}
