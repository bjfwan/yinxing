package com.bajianfeng.launcher.benchmark

import androidx.benchmark.macro.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() {
        baselineProfileRule.collect(
            packageName = PACKAGE_NAME,
            includeInStartupProfile = true
        ) {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.res(PACKAGE_NAME, "recycler_home")), 5_000)
            device.findObject(By.descContains("电话"))?.click()
            device.wait(Until.hasObject(By.res(PACKAGE_NAME, "recycler_contacts")), 5_000)
            device.pressBack()
            device.findObject(By.descContains("微信视频"))?.click()
            device.wait(Until.hasObject(By.res(PACKAGE_NAME, "recycler_video_contacts")), 5_000)
            device.pressBack()
        }
    }
}
