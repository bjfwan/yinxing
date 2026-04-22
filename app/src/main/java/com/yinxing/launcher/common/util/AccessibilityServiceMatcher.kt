package com.yinxing.launcher.common.util

object AccessibilityServiceMatcher {
    fun contains(enabledServices: String?, serviceName: String): Boolean {
        val normalizedServiceName = serviceName.trim()
        val serviceList = enabledServices
            ?.split(':')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: return false

        return serviceList.any { it.equals(normalizedServiceName, ignoreCase = true) }
    }
}
