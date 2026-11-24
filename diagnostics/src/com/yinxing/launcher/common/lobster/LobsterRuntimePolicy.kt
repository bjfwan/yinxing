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
        return shouldUpload(
            android.os.Build.MANUFACTURER,
            android.os.Build.MODEL,
            android.os.Build.FINGERPRINT,
            shouldUploadByUserPreference(context)
        )
    }

    /**
     * 反馈弹窗用的“用户是否允许上传”口径：只看诊断总开关，不含测试运行时护栏。
     * robolectric 护栏只应该让实际上报静默跳过，不该让 UI 把测试环境当成“用户关闭了诊断”。
     */
    fun shouldUploadByUserPreference(context: Context): Boolean {
        return runCatching {
            LauncherPreferences.getInstance(context).isDiagnosticsSharingEnabled()
        }.getOrDefault(false)
    }
}
