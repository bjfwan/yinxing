package com.bajianfeng.launcher.common.util

class PermissionRequestHandler(
    private val hasPermission: (String) -> Boolean,
    private val requestPermission: (String) -> Unit
) {
    private val pendingActions = mutableMapOf<String, () -> Unit>()
    private val deniedActions = mutableMapOf<String, () -> Unit>()

    fun runOrRequest(permission: String, onGranted: () -> Unit): Boolean {
        return runOrRequest(permission, onGranted, {})
    }

    fun runOrRequest(
        permission: String,
        onGranted: () -> Unit,
        onDenied: () -> Unit = {}
    ): Boolean {
        if (hasPermission(permission)) {
            onGranted()
            return true
        }
        pendingActions[permission] = onGranted
        deniedActions[permission] = onDenied
        requestPermission(permission)
        return false
    }

    fun handleResult(permission: String, granted: Boolean): Boolean {
        val grantedAction = pendingActions.remove(permission)
        val deniedAction = deniedActions.remove(permission)
        if (granted) {
            grantedAction?.invoke()
            return grantedAction != null
        }
        deniedAction?.invoke()
        return false
    }
}
