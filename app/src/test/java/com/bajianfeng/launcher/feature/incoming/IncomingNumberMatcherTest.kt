package com.bajianfeng.launcher.feature.incoming

import com.bajianfeng.launcher.data.contact.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingNumberMatcherTest {

    @Test
    fun exactMatchBeatsSuffixMatch() {
        val exact = contact(id = "1", name = "张阿姨", phoneNumber = "13812345678")
        val suffixOnly = contact(id = "2", name = "李阿姨", phoneNumber = "9913812345678")

        val matched = IncomingNumberMatcher.findBestMatch(
            contacts = listOf(suffixOnly, exact),
            incomingNumber = "13812345678"
        )

        assertEquals(exact, matched)
    }

    @Test
    fun chinaCountryCodeMatchesLocalMobileNumber() {
        val contact = contact(id = "1", name = "王叔叔", phoneNumber = "13812345678")

        val matched = IncomingNumberMatcher.findBestMatch(
            contacts = listOf(contact),
            incomingNumber = "+86 138 1234 5678"
        )

        assertEquals(contact, matched)
    }

    @Test
    fun blankIncomingNumberReturnsNull() {
        val matched = IncomingNumberMatcher.findBestMatch(
            contacts = listOf(contact(id = "1", name = "赵大爷", phoneNumber = "13812345678")),
            incomingNumber = "   "
        )

        assertNull(matched)
    }

    private fun contact(id: String, name: String, phoneNumber: String) = Contact(
        id = id,
        name = name,
        phoneNumber = phoneNumber
    )
}
