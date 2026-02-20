package com.bajianfeng.launcher.util

import android.content.Context
import android.content.Intent
import android.provider.Settings

object PermissionUtil {
    fun isAccessibilityServiceEnabled(context: Context, serviceName: String): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        
        return enabledServices.contains(serviceName)
    }
    
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
    
    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }
    
    fun openOverlaySettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}
