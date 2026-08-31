package com.yinxing.launcher.common.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.util.Base64InputStream
import androidx.core.content.FileProvider
import com.yinxing.launcher.R
import java.io.File
import java.io.FileOutputStream
import java.security.DigestInputStream
import java.security.MessageDigest

object OplusHomeCompatibility {
    const val PACKAGE_NAME = "com.android.cts.robot"
    const val EXPECTED_SHA256 = "105C3892022839C5D880006C63DBF0AD2EFE89832BFC58873ECA2CD6B6CF2DAA"

    fun isSupportedManufacturer(manufacturer: String): Boolean =
        OemLauncherPolicy.profile(manufacturer).vendorKey == "oplus"

    fun isInstalled(context: Context): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                PACKAGE_NAME,
                android.content.pm.PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
        }
    }.isSuccess

    fun launchInstall(context: Context): InstallLaunchResult {
        if (!isSupportedManufacturer(Build.MANUFACTURER)) return InstallLaunchResult.UNSUPPORTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            return runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                InstallLaunchResult.UNKNOWN_SOURCE_SETTINGS_OPENED
            }.getOrDefault(InstallLaunchResult.FAILED)
        }

        return runCatching {
            val apk = writeVerifiedApk(context)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk
            )
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, APK_MIME_TYPE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
            InstallLaunchResult.INSTALLER_OPENED
        }.getOrDefault(InstallLaunchResult.FAILED)
    }

    fun launchUninstall(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:$PACKAGE_NAME"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)

    private fun writeVerifiedApk(context: Context): File {
        val directory = File(context.cacheDir, CACHE_DIRECTORY).apply { mkdirs() }
        val destination = File(directory, APK_FILE_NAME)
        val digest = MessageDigest.getInstance("SHA-256")
        Base64InputStream(
            context.resources.openRawResource(R.raw.oplus_home_compat_apk),
            Base64.DEFAULT
        ).use { decoded ->
            DigestInputStream(decoded, digest).use { verified ->
                FileOutputStream(destination).use { output -> verified.copyTo(output) }
            }
        }
        val actualHash = digest.digest().joinToString("") { "%02X".format(it) }
        check(actualHash == EXPECTED_SHA256) { "OPPO compatibility component integrity check failed" }
        return destination
    }

    enum class InstallLaunchResult {
        INSTALLER_OPENED,
        UNKNOWN_SOURCE_SETTINGS_OPENED,
        UNSUPPORTED,
        FAILED
    }

    private const val CACHE_DIRECTORY = "oplus_home_compat"
    private const val APK_FILE_NAME = "yinxing-oppo-home-compat.apk"
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
}
