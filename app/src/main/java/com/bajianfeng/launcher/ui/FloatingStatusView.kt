package com.bajianfeng.launcher.ui

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.TextView
import com.bajianfeng.launcher.R

class FloatingStatusView(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var floatingView: android.view.View? = null
    private var isShowing = false
    
    fun show(message: String) {
        if (isShowing) {
            updateMessage(message)
            return
        }
        
        floatingView = LayoutInflater.from(context).inflate(R.layout.floating_status, null)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = 100
        
        floatingView?.findViewById<TextView>(R.id.tv_status)?.text = message
        
        try {
            windowManager.addView(floatingView, params)
            isShowing = true
        } catch (e: Exception) {
            isShowing = false
        }
    }
    
    fun updateMessage(message: String) {
        floatingView?.findViewById<TextView>(R.id.tv_status)?.text = message
    }
    
    fun hide() {
        if (isShowing && floatingView != null) {
            try {
                windowManager.removeView(floatingView)
            } catch (e: Exception) {
            }
            isShowing = false
            floatingView = null
        }
    }
}
