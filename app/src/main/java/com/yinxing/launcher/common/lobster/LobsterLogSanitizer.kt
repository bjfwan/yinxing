package com.yinxing.launcher.common.lobster

object LobsterLogSanitizer {
    private val bearerToken = Regex("(?i)(Bearer\\s+)[^\\s,;]+")
    private val mobileNumber = Regex("(?<!\\d)(1\\d{2})\\d{4}(\\d{4})(?!\\d)")
    private val landlineNumber = Regex("(?<!\\d)(0\\d{2,3})\\d{3,4}(\\d{4})(?!\\d)")

    fun sanitize(value: String): String {
        return value
            .replace(bearerToken, "$1***")
            .replace(mobileNumber, "$1****$2")
            .replace(landlineNumber, "$1****$2")
    }
}
