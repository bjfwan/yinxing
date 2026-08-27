package com.yinxing.launcher.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialWeatherLocationPolicyTest {
    @Test
    fun `defers default weather while first location is unresolved`() {
        assertEquals(false, shouldRefreshWeatherOnResume(false, false, false))
        assertEquals(false, shouldRefreshWeatherOnResume(false, true, true))
        assertEquals(true, shouldRefreshWeatherOnResume(true, true, false))
        assertEquals(true, shouldRefreshWeatherOnResume(false, false, true))
    }

    @Test
    fun `requests coarse location once when no city was selected`() {
        assertEquals(
            InitialWeatherLocationAction.RequestPermission,
            initialWeatherLocationAction(
                hasCity = false,
                permissionGranted = false,
                permissionRequested = false,
            ),
        )
    }

    @Test
    fun `resolves location immediately when permission is already granted`() {
        assertEquals(
            InitialWeatherLocationAction.ResolveLocation,
            initialWeatherLocationAction(
                hasCity = false,
                permissionGranted = true,
                permissionRequested = true,
            ),
        )
    }

    @Test
    fun `does not repeat a denied request or overwrite a selected city`() {
        assertEquals(
            InitialWeatherLocationAction.None,
            initialWeatherLocationAction(false, false, true),
        )
        assertEquals(
            InitialWeatherLocationAction.None,
            initialWeatherLocationAction(true, true, false),
        )
    }
}
