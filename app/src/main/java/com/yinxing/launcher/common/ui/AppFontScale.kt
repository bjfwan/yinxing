package com.yinxing.launcher.common.ui

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import com.yinxing.launcher.data.home.LauncherPreferences
import kotlin.math.abs

internal object AppFontScale {
    const val SYSTEM = LauncherPreferences.FONT_SCALE_SYSTEM
    const val STANDARD = LauncherPreferences.FONT_SCALE_STANDARD
    const val LARGE = LauncherPreferences.FONT_SCALE_LARGE
    const val EXTRA_LARGE = LauncherPreferences.FONT_SCALE_EXTRA_LARGE

    fun resolve(mode: String, systemScale: Float): Float = when (mode) {
        STANDARD -> 1.0f
        LARGE -> 1.15f
        EXTRA_LARGE -> 1.30f
        else -> systemScale
    }

    fun wrap(base: Context, mode: String): Context {
        val systemScale = base.resources.configuration.fontScale
        val targetScale = resolve(mode, systemScale)
        if (abs(targetScale - systemScale) < 0.001f) return base

        val configuration = Configuration(base.resources.configuration).apply {
            fontScale = targetScale
        }
        return base.createConfigurationContext(configuration)
    }
}

abstract class FontScaleActivity : AppCompatActivity() {
    private var appliedFontScaleMode = LauncherPreferences.FONT_SCALE_SYSTEM

    override fun attachBaseContext(newBase: Context) {
        appliedFontScaleMode = LauncherPreferences.getInstance(newBase).getFontScaleMode()
        super.attachBaseContext(AppFontScale.wrap(newBase, appliedFontScaleMode))
    }

    override fun onResume() {
        super.onResume()
        val currentMode = LauncherPreferences.getInstance(this).getFontScaleMode()
        if (currentMode != appliedFontScaleMode && !isFinishing && !isDestroyed) {
            recreate()
        }
    }
}
