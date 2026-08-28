package com.yinxing.launcher.feature.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherDetailHintDecisionTest {
    @Test
    fun hintAppearsOnlyWhenWeatherAndHomeAreReady() {
        assertTrue(
            shouldRevealWeatherDetailHint(
                weatherAvailable = true,
                hostResumed = true,
                familySetupPending = false,
                alreadyShown = false,
            )
        )
    }

    @Test
    fun hintWaitsWhileFamilySetupCoversHome() {
        assertFalse(
            shouldRevealWeatherDetailHint(
                weatherAvailable = true,
                hostResumed = true,
                familySetupPending = true,
                alreadyShown = false,
            )
        )
    }

    @Test
    fun hintDoesNotConsumeItsOnlyDisplayBeforeWeatherLoads() {
        assertFalse(
            shouldRevealWeatherDetailHint(
                weatherAvailable = false,
                hostResumed = true,
                familySetupPending = false,
                alreadyShown = false,
            )
        )
    }

    @Test
    fun hintNeverRepeatsAfterItWasShown() {
        assertFalse(
            shouldRevealWeatherDetailHint(
                weatherAvailable = true,
                hostResumed = true,
                familySetupPending = false,
                alreadyShown = true,
            )
        )
    }
}
