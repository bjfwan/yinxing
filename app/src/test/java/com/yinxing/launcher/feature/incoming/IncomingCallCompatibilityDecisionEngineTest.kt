package com.yinxing.launcher.feature.incoming

import android.os.Build
import com.yinxing.launcher.data.contact.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingCallCompatibilityDecisionEngineTest {

    @Test
    fun readyModernDeviceUsesTelecomManagerAndAllowsAutoAnswer() {
        val decision = IncomingCallCompatibilityDecisionEngine.decide(readyInput())

        assertTrue(decision.canAutoAnswer)
        assertTrue(decision.isReliable)
        assertEquals(IncomingCallAcceptStrategy.TelecomManager, decision.strategy)
        assertTrue(decision.blockers.isEmpty())
        assertEquals(94, decision.confidence)
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
        assertEquals(IncomingCallAcceptStrategy.TelecomManager, decision.strategy)
        assertEquals(
            listOf(
                IncomingCallCompatibilityBlocker.ContactWhitelist,
                IncomingCallCompatibilityBlocker.ContactAutoAnswer
            ),
            decision.blockers
        )
    }

    @Test
    fun androidSevenUsesHeadsetHookWithCompatibilityWarning() {
        val decision = IncomingCallCompatibilityDecisionEngine.decide(
            readyInput(sdkInt = Build.VERSION_CODES.N)
        )

        assertTrue(decision.canAutoAnswer)
        assertFalse(decision.isReliable)
        assertEquals(IncomingCallAcceptStrategy.HeadsetHook, decision.strategy)
        assertEquals(66, decision.confidence)
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
        isDefaultLauncher: Boolean = true,
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
        isDefaultLauncher = isDefaultLauncher,
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
