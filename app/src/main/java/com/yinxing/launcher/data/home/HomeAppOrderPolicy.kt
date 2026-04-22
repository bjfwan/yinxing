package com.yinxing.launcher.data.home

data class OrderedApp(
    val packageName: String,
    val appName: String
)

object HomeAppOrderPolicy {
    fun orderApps(apps: Collection<OrderedApp>, savedOrder: Collection<String>): List<OrderedApp> {
        val appsByPackage = apps.associateBy { it.packageName }.toMutableMap()
        val orderedApps = mutableListOf<OrderedApp>()

        normalizeSavedOrder(savedOrder).forEach { packageName ->
            appsByPackage.remove(packageName)?.let(orderedApps::add)
        }

        orderedApps += appsByPackage.values.sortedBy { it.appName.lowercase() }
        return orderedApps
    }

    fun updateOrderForSelection(
        savedOrder: Collection<String>,
        packageName: String,
        isSelected: Boolean
    ): List<String> {
        val updatedOrder = normalizeSavedOrder(savedOrder)
            .filter { it != packageName }
            .toMutableList()

        if (isSelected) {
            updatedOrder.add(packageName)
        }

        return updatedOrder
    }

    fun retainSelectedPackages(
        savedOrder: Collection<String>,
        selectedPackages: Collection<String>
    ): List<String> {
        val selectedPackageSet = selectedPackages.toSet()
        return normalizeSavedOrder(savedOrder).filter { it in selectedPackageSet }
    }

    fun normalizeSavedOrder(savedOrder: Collection<String>): List<String> {
        return savedOrder
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }
}
