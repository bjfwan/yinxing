package com.yinxing.launcher.common.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

object FirebaseTelemetry {
    fun analyticsOrNull(context: Context): FirebaseAnalytics? {
        return runCatching {
            if (FirebaseApp.getApps(context.applicationContext).isEmpty()) {
                null
            } else {
                FirebaseAnalytics.getInstance(context.applicationContext)
            }
        }.getOrNull()
    }

    fun crashlyticsOrNull(): FirebaseCrashlytics? {
        return runCatching { FirebaseCrashlytics.getInstance() }.getOrNull()
    }

    inline fun withCrashlytics(block: FirebaseCrashlytics.() -> Unit) {
        crashlyticsOrNull()?.block()
    }
}
