package com.yinxing.launcher.common.ui

import android.view.Gravity
import android.view.View
import android.os.Looper
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.yinxing.launcher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherDialogFactoryTest {
    @Test
    fun `dialog applies the shared window geometry after it is shown`() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val contentId = View.generateViewId()
        val content = FrameLayout(activity).apply { id = contentId }
        val dialog = LauncherDialogFactory.create(activity, content)

        dialog.show()
        shadowOf(Looper.getMainLooper()).idle()

        val window = requireNotNull(dialog.window)
        val expectedWidth = activity.resources.displayMetrics.widthPixels.coerceAtMost(
            activity.resources.getDimensionPixelSize(R.dimen.launcher_dialog_window_max_width)
        )
        assertEquals(expectedWidth, window.attributes.width)
        assertEquals(0.46f, window.attributes.dimAmount, 0.001f)
        assertEquals(Gravity.CENTER, window.attributes.gravity)
        assertEquals(R.style.Animation_OldLauncher_Dialog, window.attributes.windowAnimations)
        assertNotNull(dialog.findViewById<View>(contentId))

        dialog.dismiss()
    }
}
