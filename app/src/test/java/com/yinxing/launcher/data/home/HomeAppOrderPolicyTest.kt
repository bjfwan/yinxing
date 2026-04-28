package com.yinxing.launcher.data.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

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

    @Test
    fun orderApps_usesLocaleIndependentNameSort() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            val orderedPackages = HomeAppOrderPolicy.orderApps(
                listOf(
                    OrderedApp("pkg.zulu", "Zulu"),
                    OrderedApp("pkg.ibis", "Ibis")
                ),
                emptyList()
            ).map { it.packageName }

            assertEquals(listOf("pkg.ibis", "pkg.zulu"), orderedPackages)
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
