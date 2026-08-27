package com.yinxing.launcher.feature.incoming

import android.os.Build
import com.yinxing.launcher.data.contact.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingCallCompatibilityDecisionEngineTest {

    @Test
    fun readyModernDeviceUsesInCallServiceAndAllowsAutoAnswer() {
        val decision = IncomingCallCompatibilityDecisionEngine.decide(readyInput())

        assertTrue(decision.canAutoAnswer)
        assertTrue(decision.isReliable)
        assertEquals(IncomingCallAcceptStrategy.InCallService, decision.strategy)
        assertTrue(decision.blockers.isEmpty())
        assertEquals(98, decision.confidence)
    }

    @Test
    fun missingDefaultPhoneRoleBlocksAutomaticAnswer() {
        val decision = IncomingCallCompatibilityDecisionEngine.decide(
            readyInput(isDefaultPhone = false)
        )

        assertFalse(decision.canAutoAnswer)
        assertTrue(decision.blockers.contains(IncomingCallCompatibilityBlocker.DefaultPhone))
        assertEquals(IncomingCallAcceptStrategy.TelecomManager, decision.strategy)
    }

    @Test
    fun unknownContactAndDisabledContactAutoAnswerBlockTheCall() {
        val decision = IncomingCallCompatibilityDecisionEngine.decide(
            readyInput(
                knownContact = false,
                contactAutoAnswerEnabled = false
            )
        )

        assertFalse(decision.canAutoAnswer)
        assertEquals(IncomingCallAcceptStrategy.InCallService, decision.strategy)
        assertEquals(
            listOf(
                IncomingCallCompatibilityBlocker.ContactWhitelist,
                IncomingCallCompatibilityBlocker.ContactAutoAnswer
            ),
            decision.blockers
        )
    }

    @Test
    fun androidSevenUsesInCallServiceWhenAppIsDefaultPhone() {
        val decision = IncomingCallCompatibilityDecisionEngine.decide(
            readyInput(sdkInt = Build.VERSION_CODES.N)
        )

        assertTrue(decision.canAutoAnswer)
        assertTrue(decision.isReliable)
        assertEquals(IncomingCallAcceptStrategy.InCallService, decision.strategy)
        assertEquals(98, decision.confidence)
    }

    @Test
    fun androidThirteenRequiresNotificationPermission() {
        val decision = IncomingCallCompatibilityDecisionEngine.decide(
            readyInput(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                hasNotificationPermission = false
            )
        )

        assertFalse(decision.canAutoAnswer)
        assertEquals(
            listOf(IncomingCallCompatibilityBlocker.NotificationPermission),
            decision.blockers
        )
    }

    @Test
    fun disabledNotificationsBlockIncomingUiBeforeAndroidThirteenToo() {
        val decision = IncomingCallCompatibilityDecisionEngine.decide(
            readyInput(
                sdkInt = Build.VERSION_CODES.S,
                hasNotificationPermission = false
            )
        )

        assertFalse(decision.canAutoAnswer)
        assertTrue(decision.blockers.contains(IncomingCallCompatibilityBlocker.NotificationPermission))
    }

    @Test
    fun preNougatFallsBackToManualOnly() {
        val decision = IncomingCallCompatibilityDecisionEngine.decide(
            readyInput(sdkInt = Build.VERSION_CODES.M)
        )

        assertFalse(decision.canAutoAnswer)
        assertFalse(decision.isReliable)
        assertEquals(IncomingCallAcceptStrategy.ManualOnly, decision.strategy)
        assertEquals(
            listOf(IncomingCallCompatibilityBlocker.UnsupportedPlatform),
            decision.blockers
        )
    }

    @Test
    fun fullIncomingDecisionChainAllowsWhitelistedReadyContact() {
        val contact = contact(autoAnswer = true)
        val autoDecision = IncomingAutoAnswerDecisionMaker.decide(
            contacts = listOf(contact),
            incomingNumber = "+86 138 1234 5678",
            delaySeconds = 5,
            globalAutoAnswer = true
        )
        val compatibility = IncomingCallCompatibilityDecisionEngine.decide(
            readyInput(
                knownContact = autoDecision.matchedContact != null,
                contactAutoAnswerEnabled = autoDecision.matchedContact?.autoAnswer == true,
                globalAutoAnswerEnabled = autoDecision.autoAnswer
            )
        )

        assertTrue(autoDecision.autoAnswer)
        assertTrue(autoDecision.autoAnswer && compatibility.canAutoAnswer)
    }

    @Test
    fun fullIncomingDecisionChainBlocksMatchedContactWhenEnvironmentIsNotReady() {
        val contact = contact(autoAnswer = true)
        val autoDecision = IncomingAutoAnswerDecisionMaker.decide(
            contacts = listOf(contact),
            incomingNumber = "13812345678",
            delaySeconds = 5,
            globalAutoAnswer = true
        )
        val compatibility = IncomingCallCompatibilityDecisionEngine.decide(
            readyInput(
                knownContact = autoDecision.matchedContact != null,
                contactAutoAnswerEnabled = autoDecision.matchedContact?.autoAnswer == true,
                globalAutoAnswerEnabled = autoDecision.autoAnswer,
                hasPhonePermission = false,
                ignoresBatteryOptimizations = false
            )
        )

        assertTrue(autoDecision.autoAnswer)
        assertFalse(autoDecision.autoAnswer && compatibility.canAutoAnswer)
        assertEquals(
            listOf(
                IncomingCallCompatibilityBlocker.PhonePermission,
                IncomingCallCompatibilityBlocker.BatteryOptimization
            ),
            compatibility.blockers
        )
    }

    private fun readyInput(
        sdkInt: Int = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
        knownContact: Boolean = true,
        globalAutoAnswerEnabled: Boolean = true,
        contactAutoAnswerEnabled: Boolean = true,
        hasPhonePermission: Boolean = true,
        hasNotificationPermission: Boolean = true,
        isDefaultPhone: Boolean = true,
        ignoresBatteryOptimizations: Boolean = true,
        autoStartConfirmed: Boolean = true,
        backgroundStartConfirmed: Boolean = true
    ) = IncomingCallCompatibilityInput(
        sdkInt = sdkInt,
        knownContact = knownContact,
        globalAutoAnswerEnabled = globalAutoAnswerEnabled,
        contactAutoAnswerEnabled = contactAutoAnswerEnabled,
        hasPhonePermission = hasPhonePermission,
        hasNotificationPermission = hasNotificationPermission,
        isDefaultPhone = isDefaultPhone,
        ignoresBatteryOptimizations = ignoresBatteryOptimizations,
        autoStartConfirmed = autoStartConfirmed,
        backgroundStartConfirmed = backgroundStartConfirmed
    )

    private fun contact(autoAnswer: Boolean) = Contact(
        id = "13812345678",
        name = "张阿姨",
        phoneNumber = "13812345678",
        autoAnswer = autoAnswer
    )
}
