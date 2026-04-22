package com.bajianfeng.launcher.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() {
        var pass = 0
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        logStep("开始采集 Baseline Profile")
        device.logDeviceState("Baseline Profile collect 前")

        ProgressWatchdog(
            stepName = "BaselineProfileRule.collect",
            onHeartbeat = {
                device.logDeviceState("等待进入/返回 BaselineProfileRule.collect 时")
            }
        ).start("准备调用 collect")
            .use { watchdog ->
                baselineProfileRule.collect(
                    packageName = PACKAGE_NAME,
                    includeInStartupProfile = true
                ) {
                    pass += 1
                    watchdog.mark("已进入 lambda，第 ${pass} 轮")
                    logStep("Baseline Profile 第 ${pass} 轮开始")
                    goHomeWithLog()
                    startLauncherAndWaitHome()

                    device.clickObjectOrThrow(
                        description = "首页电话入口",
                        selectors = arrayOf(By.desc("电话"), By.text("电话"))
                    )
                    device.waitForObjectOrThrow(
                        selector = By.res(PACKAGE_NAME, "recycler_contacts"),
                        description = "电话联系人列表 recycler_contacts"
                    )
                    device.pressBackWithLog("电话联系人页返回首页")
                    device.waitForObjectOrThrow(
                        selector = By.res(PACKAGE_NAME, "recycler_home"),
                        description = "电话页返回后的首页列表 recycler_home"
                    )

                    device.clickObjectOrThrow(
                        description = "首页微信视频入口",
                        selectors = arrayOf(By.desc("微信视频"), By.text("微信视频"))
                    )
                    device.waitForObjectOrThrow(
                        selector = By.res(PACKAGE_NAME, "recycler_video_contacts"),
                        description = "微信视频联系人列表 recycler_video_contacts"
                    )
                    device.pressBackWithLog("微信视频联系人页返回首页")
                    device.waitForObjectOrThrow(
                        selector = By.res(PACKAGE_NAME, "recycler_home"),
                        description = "微信视频页返回后的首页列表 recycler_home"
                    )
                    logStep("Baseline Profile 第 ${pass} 轮结束")
                    watchdog.mark("第 ${pass} 轮已完成，等待 collect 后续流程")
                }
                watchdog.mark("collect 已返回")
            }

        device.logDeviceState("Baseline Profile collect 后")
        logStep("Baseline Profile 采集流程结束")
    }
}
