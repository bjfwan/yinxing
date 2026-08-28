package com.yinxing.launcher.automation.wechat.teaching

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.yinxing.launcher.automation.wechat.WeChatPackage
import kotlin.math.roundToInt

object WeChatTeachingFingerprintFactory {
    fun capture(context: Context): WeChatTeachingFingerprint? {
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    WeChatPackage.NAME,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(WeChatPackage.NAME, 0)
            }
        }.getOrNull() ?: return null
        val metrics = context.resources.displayMetrics
        val configuration = context.resources.configuration
        val localeTag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.locales.get(0)?.toLanguageTag().orEmpty()
        } else {
            @Suppress("DEPRECATION")
            configuration.locale?.toLanguageTag().orEmpty()
        }
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        return WeChatTeachingFingerprint(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            androidSdk = Build.VERSION.SDK_INT,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            densityDpi = metrics.densityDpi,
            fontScalePermille = (configuration.fontScale * 1_000).roundToInt(),
            localeTag = localeTag,
            weChatVersionName = packageInfo.versionName.orEmpty(),
            weChatVersionCode = versionCode
        )
    }
}
