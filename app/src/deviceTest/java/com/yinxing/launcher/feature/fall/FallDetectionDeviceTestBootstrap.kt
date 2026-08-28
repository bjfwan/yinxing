package com.yinxing.launcher.feature.fall

import android.app.Activity
import android.app.Application
import android.os.Bundle

class FallDetectionDeviceTestApplication : Application()

/** Unstops the freshly installed test package without running production initialization. */
class FallDetectionDeviceTestBootstrapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
