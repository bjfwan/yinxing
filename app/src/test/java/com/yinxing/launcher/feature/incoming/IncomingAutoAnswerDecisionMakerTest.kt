package com.yinxing.launcher.feature.incoming

import com.yinxing.launcher.data.contact.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingAutoAnswerDecisionMakerTest {

    // ── 号码空格 ─────────────────────────────────────────────────────────────

    @Test
    fun spacedIncomingNumberMatchesContactAndKeepsContactName() {
        val contact = contact(name = "张阿姨", phoneNumber = "13812345678", autoAnswer = true)

        val decision = IncomingAutoAnswerDecisionMaker.decide(
            contacts = listOf(contact),
            incomingNumber = "138 1234 5678",
            delaySeconds = 5
        )

        assertEquals(contact, decision.matchedContact)
        assertEquals("张阿姨", decision.callerLabel)
        assertTrue("匹配到允许自动接听的联系人时应自动接听", decision.autoAnswer)
    }

    // ── +86 ────────────────────────────────────────────────────────────────

    @Test
    fun plus86PrefixMatchesLocalMobileNumberAndAutoAnswers() {
        val contact = contact(name = "王叔叔", phoneNumber = "13812345678", autoAnswer = true)

        val decision = IncomingAutoAnswerDecisionMaker.decide(
            contacts = listOf(contact),
            incomingNumber = "+86 138 1234 5678",
            delaySeconds = 3
        )

        assertEquals(contact, decision.matchedContact)
        assertEquals("王叔叔", decision.callerLabel)
        assertTrue(decision.autoAnswer)
        assertEquals(3, decision.delaySeconds)
    }

    // ── 区号（固话） ─────────────────────────────────────────────────────────

    @Test
    fun areaCodePrefixedLandlineMatchesStoredFullNumber() {
        val contact = contact(name = "家里", phoneNumber = "01088889999", autoAnswer = true)

        val decision = IncomingAutoAnswerDecisionMaker.decide(
            contacts = listOf(contact),
            incomingNumber = "010-8888-9999",
            delaySeconds = 5
        )

        assertEquals(contact, decision.matchedContact)
        assertTrue(decision.autoAnswer)
    }

    @Test
    fun shortAreaCodeOnlyDoesNotFalseMatchMobileContact() {
        val mobile = contact(name = "张阿姨", phoneNumber = "13812345678", autoAnswer = true)

        val decision = IncomingAutoAnswerDecisionMaker.decide(
            contacts = listOf(mobile),
            incomingNumber = "010",
            delaySeconds = 5
        )

        assertNull("3 位区号不应误匹配 11 位手机号", decision.matchedContact)
        assertFalse(decision.autoAnswer)
        assertEquals("010", decision.callerLabel)
    }

    // ── 延迟 ───────────────────────────────────────────────────────────────

    @Test
    fun delayBelowMinimumIsClampedToOneSecond() {
        val contact = contact(name = "张阿姨", phoneNumber = "13812345678", autoAnswer = true)

        val decision = IncomingAutoAnswerDecisionMaker.decide(
            contacts = listOf(contact),
            incomingNumber = "13812345678",
            delaySeconds = 0
        )

        assertEquals(1, decision.delaySeconds)
    }

    @Test
    fun delayAboveMaximumIsClampedToThirtySeconds() {
        val contact = contact(name = "张阿姨", phoneNumber = "13812345678", autoAnswer = true)

        val decision = IncomingAutoAnswerDecisionMaker.decide(
            contacts = listOf(contact),
            incomingNumber = "13812345678",
            delaySeconds = 999
        )

        assertEquals(30, decision.delaySeconds)
    }

    @Test
    fun delayWithinRangeIsKept() {
        val contact = contact(name = "张阿姨", phoneNumber = "13812345678", autoAnswer = true)

        val decision = IncomingAutoAnswerDecisionMaker.decide(
            contacts = listOf(contact),
            incomingNumber = "13812345678",
            delaySeconds = 7
        )

        assertEquals(7, decision.delaySeconds)
    }

    // ── 失败兜底 ────────────────────────────────────────────────────────────

    @Test
    fun unknownIncomingNumberFallsBackToManualAnswer() {
        val contact = contact(name = "张阿姨", phoneNumber = "13812345678", autoAnswer = true)

        val decision = IncomingAutoAnswerDecisionMaker.decide(
            contacts = listOf(contact),
            incomingNumber = "13900000000",
            delaySeconds = 5
        )

        assertNull(decision.matchedContact)
        assertFalse("未匹配联系人时不应自动接听", decision.autoAnswer)
        assertEquals("13900000000", decision.callerLabel)
    }

    @Test
    fun matchedButContactDisabledAutoAnswerStaysManual() {
        val contact = contact(name = "李叔叔", phoneNumber = "13812345678", autoAnswer = false)

        val decision = IncomingAutoAnswerDecisionMaker.decide(
            contacts = listOf(contact),
            incomingNumber = "13812345678",
            delaySeconds = 5
        )

        assertEquals(contact, decision.matchedContact)
        assertFalse("联系人未开启自动接听时不应自动接听", decision.autoAnswer)
        assertEquals("李叔叔", decision.callerLabel)
    }

    @Test
    fun blankIncomingNumberWithEmptyContactsProducesNullCallerLabel() {
        val decision = IncomingAutoAnswerDecisionMaker.decide(
            contacts = emptyList(),
            incomingNumber = "   ",
            delaySeconds = 5
        )

        assertNull(decision.matchedContact)
        assertNull("空白号码 + 空联系人簿应返回空标签", decision.callerLabel)
        assertFalse(decision.autoAnswer)
    }

    @Test
    fun emptyContactsListNeverAutoAnswers() {
        val decision = IncomingAutoAnswerDecisionMaker.decide(
            contacts = emptyList(),
            incomingNumber = "13812345678",
            delaySeconds = 5
        )

        assertNull(decision.matchedContact)
        assertFalse(decision.autoAnswer)
        assertNotNull(decision.callerLabel)
    }

    // ── 辅助 ───────────────────────────────────────────────────────────────

    private fun contact(
        name: String,
        phoneNumber: String,
        autoAnswer: Boolean = false,
        id: String = phoneNumber
    ) = Contact(
        id = id,
        name = name,
        phoneNumber = phoneNumber,
        autoAnswer = autoAnswer
    )
}
