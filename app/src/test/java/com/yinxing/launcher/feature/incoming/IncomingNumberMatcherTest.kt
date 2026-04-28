package com.yinxing.launcher.feature.incoming

import com.yinxing.launcher.data.contact.Contact
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
    fun zeroZeroEightSixCountryCodeMatchesLocalMobileNumber() {
        val contact = contact(id = "1", name = "王叔叔", phoneNumber = "13812345678")

        val matched = IncomingNumberMatcher.findBestMatch(
            contacts = listOf(contact),
            incomingNumber = "0086 138 1234 5678"
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

    @Test
    fun spacedIncomingNumberMatchesStoredCompactNumber() {
        val contact = contact(id = "1", name = "孙阿姨", phoneNumber = "13812345678")

        val matched = IncomingNumberMatcher.findBestMatch(
            contacts = listOf(contact),
            incomingNumber = "138 1234 5678"
        )

        assertEquals(contact, matched)
    }

    @Test
    fun landlineWithAreaCodeAndSeparatorsMatchesStoredDigits() {
        val contact = contact(id = "1", name = "家里", phoneNumber = "01088889999")

        val matched = IncomingNumberMatcher.findBestMatch(
            contacts = listOf(contact),
            incomingNumber = "010-8888-9999"
        )

        assertEquals(contact, matched)
    }

    @Test
    fun areaCodeOnlyDoesNotMatchMobileContact() {
        val mobile = contact(id = "1", name = "张阿姨", phoneNumber = "13812345678")

        val matched = IncomingNumberMatcher.findBestMatch(
            contacts = listOf(mobile),
            incomingNumber = "010"
        )

        assertNull("3 位区号不应误匹配 11 位手机号", matched)
    }

    @Test
    fun longerSuffixMatchBeatsShorterSuffixMatch() {
        val shortSuffix = contact(id = "1", name = "短号", phoneNumber = "45678")
        val longSuffix = contact(id = "2", name = "长号", phoneNumber = "12345678")

        val matched = IncomingNumberMatcher.findBestMatch(
            contacts = listOf(shortSuffix, longSuffix),
            incomingNumber = "13812345678"
        )

        assertEquals(longSuffix, matched)
    }

    @Test
    fun contactsWithoutPhoneNumberAreIgnored() {
        val matched = IncomingNumberMatcher.findBestMatch(
            contacts = listOf(
                contact(id = "1", name = "空号码", phoneNumber = null),
                contact(id = "2", name = "有效号码", phoneNumber = "13812345678")
            ),
            incomingNumber = "13812345678"
        )

        assertEquals("2", matched?.id)
    }

    @Test
    fun emptyContactListReturnsNull() {
        val matched = IncomingNumberMatcher.findBestMatch(
            contacts = emptyList(),
            incomingNumber = "13812345678"
        )

        assertNull(matched)
    }

    private fun contact(id: String, name: String, phoneNumber: String?) = Contact(
        id = id,
        name = name,
        phoneNumber = phoneNumber
    )
}
