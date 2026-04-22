package com.yinxing.launcher.common.util

class PermissionRequestHandler(
    private val hasPermission: (String) -> Boolean,
    private val requestPermission: (String) -> Unit
) {
    private val pendingActions = mutableMapOf<String, () -> Unit>()

    fun runOrRequest(permission: String, action: () -> Unit): Boolean {
        if (hasPermission(permission)) {
            action()
            return true
        }
        pendingActions[permission] = action
        requestPermission(permission)
        return false
    }

    fun handleResult(permission: String, granted: Boolean): Boolean {
        val action = pendingActions.remove(permission)
        if (granted) {
            action?.invoke()
            return action != null
        }
        return false
    }

    fun clear(permission: String) {
        pendingActions.remove(permission)
    }
}
