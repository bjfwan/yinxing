package com.yinxing.launcher.common.util

internal object EmergencyContactNumber {
    private val allowedInput = Regex("^[+0-9()\\s-]+$")
    private val publicEmergencyNumbers = setOf("110", "119", "120", "122")

    fun normalize(raw: String?): String? {
        val input = raw?.trim().orEmpty()
        if (input.isEmpty() || !allowedInput.matches(input)) return null

        val normalized = input.replace(Regex("[()\\s-]"), "")
        if (normalized.count { it == '+' } > 1 || ('+' in normalized && !normalized.startsWith('+'))) {
            return null
        }
        val digits = normalized.removePrefix("+")
        if (digits.length !in 5..20 || !digits.all(Char::isDigit)) return null
        if (digits in publicEmergencyNumbers) return null
        return normalized
    }
}
