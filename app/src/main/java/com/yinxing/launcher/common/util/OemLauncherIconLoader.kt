package com.yinxing.launcher.common.util

import android.content.Context
import android.graphics.drawable.Drawable

object OemLauncherIconLoader {
    fun load(context: Context, profile: OemLauncherProfile): Drawable {
        (profile.iconPackages + "com.android.settings").distinct().forEach { packageName ->
            runCatching { context.packageManager.getApplicationIcon(packageName) }
                .getOrNull()
                ?.let { return it }
        }
        return context.packageManager.defaultActivityIcon
    }
}
