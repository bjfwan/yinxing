package com.yinxing.launcher.common.lobster

object LobsterLogSanitizer {
    private val bearerToken = Regex("(?i)(Bearer\\s+)[^\\s,;]+")
    private val mobileNumber = Regex("(?<!\\d)(1\\d{2})\\d{4}(\\d{4})(?!\\d)")
    private val landlineNumber = Regex("(?<!\\d)(0\\d{2,3})\\d{3,4}(\\d{4})(?!\\d)")
    private val labeledContact = Regex(
        "(?i)((?:联系人|来电者|搜索名|caller|contact)\\s*[=:：]\\s*)([^,，|\\n)）]+)"
    )

    fun sanitize(value: String, sensitiveValues: Collection<String> = emptyList()): String {
        var sanitized = value
            .replace(bearerToken, "$1***")
            .replace(mobileNumber, "$1****$2")
            .replace(landlineNumber, "$1****$2")
            .replace(labeledContact) { match ->
                val valuePart = match.groupValues[2]
                if ('*' in valuePart && valuePart.any(Char::isDigit)) match.value else "${match.groupValues[1]}***"
            }
        sensitiveValues
            .asSequence()
            .map(String::trim)
            .filter { it.length >= 2 }
            .distinct()
            .sortedByDescending(String::length)
            .forEach { sensitive -> sanitized = sanitized.replace(sensitive, "***", ignoreCase = true) }
        return sanitized
    }
}
