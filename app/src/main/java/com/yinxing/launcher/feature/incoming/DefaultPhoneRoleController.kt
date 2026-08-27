package com.yinxing.launcher.feature.incoming

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager

/**
 * 默认电话角色入口。Android 10+ 使用 RoleManager，Android 7-9 使用旧版 Telecom intent。
 *
 * Source: https://developer.android.com/develop/connectivity/telecom/dialer-app#becoming-the-default-phone-app
 */
internal object DefaultPhoneRoleController {

    fun isAvailable(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)
                ?.isRoleAvailable(RoleManager.ROLE_DIALER) == true
        } else {
            context.getSystemService(Context.TELECOM_SERVICE) is TelecomManager
        }
    }

    fun isHeld(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)
                ?.isRoleHeld(RoleManager.ROLE_DIALER) == true
        } else {
            @Suppress("DEPRECATION")
            val defaultPackage = (context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)
                ?.defaultDialerPackage
            defaultPackage == context.packageName
        }
    }

    fun createRequestIntent(context: Context): Intent? {
        if (!isAvailable(context) || isHeld(context)) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)
                ?.createRequestRoleIntent(RoleManager.ROLE_DIALER)
        } else {
            @Suppress("DEPRECATION")
            Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).putExtra(
                TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                context.packageName
            )
        }
    }
}
