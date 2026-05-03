package com.yinxing.launcher.data.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun orderApps_emptyAppsReturnsEmpty() {
        val result = HomeAppOrderPolicy.orderApps(emptyList(), listOf("pkg.a"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun retainSelectedPackages_preservesSavedOrder() {
        val result = HomeAppOrderPolicy.retainSelectedPackages(
            listOf("pkg.c", "pkg.a", "pkg.b"),
            listOf("pkg.a", "pkg.c")
        )
        assertEquals(listOf("pkg.c", "pkg.a"), result)
    }

    @Test
    fun retainSelectedPackages_emptySelectionReturnsEmpty() {
        val result = HomeAppOrderPolicy.retainSelectedPackages(
            listOf("pkg.a", "pkg.b"),
            emptyList()
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun normalizeSavedOrder_trimsFiltersEmptyAndDeduplicates() {
        val result = HomeAppOrderPolicy.normalizeSavedOrder(listOf("  pkg.a  ", "", "  ", "pkg.a", "pkg.b"))
        assertEquals(listOf("pkg.a", "pkg.b"), result)
    }

    @Test
    fun normalizeSavedOrder_emptyInputReturnsEmpty() {
        val result = HomeAppOrderPolicy.normalizeSavedOrder(emptyList())
        assertTrue(result.isEmpty())
    }
}
