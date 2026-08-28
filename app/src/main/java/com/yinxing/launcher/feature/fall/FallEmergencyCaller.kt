package com.yinxing.launcher.feature.fall

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager

internal object FallEmergencyCaller {
    @SuppressLint("MissingPermission")
    fun placeFamilyCall(context: Context, number: String): Boolean {
        if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        val manager = context.getSystemService(TelecomManager::class.java) ?: return false
        return runCatching {
            manager.placeCall(Uri.fromParts("tel", number, null), Bundle())
            true
        }.getOrDefault(false)
    }
}
