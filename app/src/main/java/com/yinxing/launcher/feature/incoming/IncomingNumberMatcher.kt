package com.yinxing.launcher.feature.incoming

import com.yinxing.launcher.data.contact.Contact
import kotlin.math.min

object IncomingNumberMatcher {

    fun findBestMatch(contacts: List<Contact>, incomingNumber: String): Contact? {
        val incomingVariants = variants(incomingNumber)
        if (incomingVariants.isEmpty()) {
            return null
        }
        var bestContact: Contact? = null
        var bestScore = Int.MIN_VALUE
        var bestIndex = Int.MAX_VALUE
        contacts.forEachIndexed { index, contact ->
            val score = score(contact.phoneNumber, incomingVariants) ?: return@forEachIndexed
            if (score > bestScore || (score == bestScore && index < bestIndex)) {
                bestContact = contact
                bestScore = score
                bestIndex = index
            }
        }
        return bestContact
    }

    private fun score(phoneNumber: String?, incomingVariants: Set<String>): Int? {
        val storedVariants = variants(phoneNumber)
        if (storedVariants.isEmpty()) {
            return null
        }
        var bestScore = 0
        storedVariants.forEach { stored ->
            incomingVariants.forEach { incoming ->
                if (stored == incoming) {
                    bestScore = maxOf(bestScore, 300 + stored.length)
                }
                val matchedSuffixLength = commonSuffixLength(stored, incoming)
                if (matchedSuffixLength >= minimumSuffixLength(stored.length, incoming.length)) {
                    bestScore = maxOf(bestScore, 100 + matchedSuffixLength)
                }
            }
        }
        return bestScore.takeIf { it > 0 }
    }

    private fun variants(phoneNumber: String?): Set<String> {
        val digits = phoneNumber?.filter { it.isDigit() }.orEmpty()
        if (digits.isBlank()) {
            return emptySet()
        }
        return linkedSetOf<String>().apply {
            add(digits)
            strippedChinaCountryCode(digits)?.let(::add)
        }
    }

    private fun strippedChinaCountryCode(digits: String): String? {
        return when {
            digits.startsWith("0086") && digits.length >= 15 -> digits.removePrefix("0086")
            digits.startsWith("86") && digits.length >= 13 -> digits.removePrefix("86")
            else -> null
        }
    }

    private fun commonSuffixLength(left: String, right: String): Int {
        var matched = 0
        while (
            matched < left.length &&
            matched < right.length &&
            left[left.lastIndex - matched] == right[right.lastIndex - matched]
        ) {
            matched++
        }
        return matched
    }

    private fun minimumSuffixLength(leftLength: Int, rightLength: Int): Int {
        val shorterLength = min(leftLength, rightLength)
        return when {
            shorterLength >= 11 -> 8
            shorterLength >= 7 -> 7
            else -> shorterLength
        }
    }
}
