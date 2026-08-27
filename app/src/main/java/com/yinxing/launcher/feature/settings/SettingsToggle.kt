package com.yinxing.launcher.feature.settings

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatCheckBox
import com.yinxing.launcher.R

class SettingsToggle @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatCheckBox(context, attrs, defStyleAttr) {
    init {
        buttonDrawable = null
        setBackgroundResource(R.drawable.bg_settings_switch_track)
        foreground = context.getDrawable(R.drawable.bg_settings_switch_thumb_position)
        minWidth = 0
        minHeight = 0
        setPadding(0, 0, 0, 0)
    }
}
