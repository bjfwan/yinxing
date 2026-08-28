package com.yinxing.launcher.feature.weather

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherCityHintDecisionTest {
    @Test
    fun cityHintAppearsOnFirstResumedWeatherDetailVisit() {
        assertTrue(
            shouldRevealWeatherCityHint(
                hostResumed = true,
                alreadyShown = false,
            )
        )
    }

    @Test
    fun cityHintDoesNotAppearBeforeWeatherDetailIsVisible() {
        assertFalse(
            shouldRevealWeatherCityHint(
                hostResumed = false,
                alreadyShown = false,
            )
        )
    }

    @Test
    fun cityHintNeverRepeatsAfterItWasShown() {
        assertFalse(
            shouldRevealWeatherCityHint(
                hostResumed = true,
                alreadyShown = true,
            )
        )
    }
}
