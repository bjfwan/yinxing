package com.yinxing.launcher.common.lobster

import android.content.Context
import com.yinxing.launcher.data.home.LauncherPreferences

object LobsterRuntimePolicy {
    fun shouldUpload(
        manufacturer: String,
        model: String,
        fingerprint: String,
        diagnosticsSharingEnabled: Boolean = true
    ): Boolean {
        return diagnosticsSharingEnabled && sequenceOf(manufacturer, model, fingerprint)
            .none { it.contains("robolectric", ignoreCase = true) }
    }

    fun shouldUpload(context: Context): Boolean {
        val sharingEnabled = runCatching {
            LauncherPreferences.getInstance(context).isDiagnosticsSharingEnabled()
        }.getOrDefault(true)
        return shouldUpload(
            android.os.Build.MANUFACTURER,
            android.os.Build.MODEL,
            android.os.Build.FINGERPRINT,
            sharingEnabled
        )
    }
}
