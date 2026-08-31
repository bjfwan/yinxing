package com.yinxing.launcher.feature.callreturn

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.yinxing.launcher.feature.home.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CallReturnHomeIntentFactoryTest {
    @Test
    fun returnIntentTargetsExistingLauncherTask() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val intent = CallReturnHomeIntentFactory.create(context)

        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED != 0)
    }
}
