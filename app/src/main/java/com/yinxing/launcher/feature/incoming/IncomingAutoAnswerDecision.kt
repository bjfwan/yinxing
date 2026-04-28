package com.yinxing.launcher.feature.incoming

import com.yinxing.launcher.data.contact.Contact

data class IncomingAutoAnswerDecision(
    val callerLabel: String?,
    val matchedContact: Contact?,
    val autoAnswer: Boolean,
    val delaySeconds: Int
)

object IncomingAutoAnswerDecisionMaker {
    fun decide(
        contacts: List<Contact>,
        incomingNumber: String,
        delaySeconds: Int,
        globalAutoAnswer: Boolean = false
    ): IncomingAutoAnswerDecision {
        val matchedContact = IncomingNumberMatcher.findBestMatch(
            contacts = contacts,
            incomingNumber = incomingNumber
        )
        return IncomingAutoAnswerDecision(
            callerLabel = matchedContact?.name ?: incomingNumber.trim().takeIf { it.isNotEmpty() },
            matchedContact = matchedContact,
            autoAnswer = globalAutoAnswer || matchedContact?.autoAnswer == true,
            delaySeconds = delaySeconds.coerceIn(1, 30)
        )
    }
}
