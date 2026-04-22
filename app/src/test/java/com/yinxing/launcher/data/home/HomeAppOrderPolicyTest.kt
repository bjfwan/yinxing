package com.yinxing.launcher.data.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeAppOrderPolicyTest {
    @Test
    fun orderApps_keepsSavedOrderAndAppendsRemainingByName() {
        val apps = listOf(
            OrderedApp("pkg.weather", "Weather"),
            OrderedApp("pkg.calendar", "Calendar"),
            OrderedApp("pkg.browser", "Browser")
        )

        val orderedPackages = HomeAppOrderPolicy.orderApps(
            apps,
            listOf("pkg.calendar", "pkg.missing", "pkg.calendar")
        ).map { it.packageName }

        assertEquals(
            listOf("pkg.calendar", "pkg.browser", "pkg.weather"),
            orderedPackages
        )
    }

    @Test
    fun updateOrderForSelection_appendsNewSelectionOnce() {
        val updatedOrder = HomeAppOrderPolicy.updateOrderForSelection(
            listOf("pkg.alpha", "pkg.beta", "pkg.alpha"),
            "pkg.gamma",
            true
        )

        assertEquals(
            listOf("pkg.alpha", "pkg.beta", "pkg.gamma"),
            updatedOrder
        )
    }

    @Test
    fun updateOrderForSelection_removesDeselectedPackage() {
        val updatedOrder = HomeAppOrderPolicy.updateOrderForSelection(
            listOf("pkg.alpha", "pkg.beta", "pkg.gamma"),
            "pkg.beta",
            false
        )

        assertEquals(
            listOf("pkg.alpha", "pkg.gamma"),
            updatedOrder
        )
    }

    @Test
    fun retainSelectedPackages_filtersOutStalePackages() {
        val retainedOrder = HomeAppOrderPolicy.retainSelectedPackages(
            listOf("pkg.alpha", "pkg.beta", "pkg.gamma"),
            listOf("pkg.gamma", "pkg.alpha")
        )

        assertEquals(
            listOf("pkg.alpha", "pkg.gamma"),
            retainedOrder
        )
    }
}
