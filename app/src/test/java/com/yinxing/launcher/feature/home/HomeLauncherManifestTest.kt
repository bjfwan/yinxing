package com.yinxing.launcher.feature.home

import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

class HomeLauncherManifestTest {
    private val manifestPath: Path = sequenceOf(
        Path.of(System.getProperty("user.dir"), "src", "main", "AndroidManifest.xml"),
        Path.of(System.getProperty("user.dir"), "app", "src", "main", "AndroidManifest.xml")
    ).first(Files::isRegularFile)

    private val document: Document = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(manifestPath.toFile())

    @Test
    fun `home activity uses standard launcher task semantics`() {
        val activities = document.getElementsByTagName("activity")
        val homeActivity = (0 until activities.length)
            .mapNotNull { activities.item(it) as? Element }
            .single { it.androidAttribute("name") == ".feature.home.MainActivity" }

        assertEquals("singleTask", homeActivity.androidAttribute("launchMode"))
        assertEquals("true", homeActivity.androidAttribute("clearTaskOnLaunch"))
        assertEquals("true", homeActivity.androidAttribute("stateNotNeeded"))
        assertEquals("", homeActivity.androidAttribute("taskAffinity"))
        assertEquals("true", homeActivity.androidAttribute("resizeableActivity"))
    }

    @Test
    fun `launcher3 alias is the only home intent handler`() {
        val aliases = document.getElementsByTagName("activity-alias")
        val launcherAlias = (0 until aliases.length)
            .mapNotNull { aliases.item(it) as? Element }
            .single { it.androidAttribute("name") == LAUNCHER3_ALIAS }

        assertEquals(".feature.home.MainActivity", launcherAlias.androidAttribute("targetActivity"))
        assertEquals("true", launcherAlias.androidAttribute("exported"))

        val homeHandlers = listOf("activity", "activity-alias")
            .flatMap { tagName ->
                val nodes = document.getElementsByTagName(tagName)
                (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
            }
            .filter { component ->
                component.getElementsByTagName("category")
                    .let { categories ->
                        (0 until categories.length)
                            .mapNotNull { categories.item(it) as? Element }
                            .any { it.androidAttribute("name") == HOME_CATEGORY }
                    }
            }
            .map { it.androidAttribute("name") }

        assertEquals(listOf(LAUNCHER3_ALIAS), homeHandlers)
    }

    @Test
    fun `oplus compatibility install is explicit and package-visible`() {
        val permissions = document.getElementsByTagName("uses-permission")
        val permissionNames = (0 until permissions.length)
            .mapNotNull { permissions.item(it) as? Element }
            .map { it.androidAttribute("name") }
        val packages = document.getElementsByTagName("package")
        val packageNames = (0 until packages.length)
            .mapNotNull { packages.item(it) as? Element }
            .map { it.androidAttribute("name") }

        assertTrue("android.permission.REQUEST_INSTALL_PACKAGES" in permissionNames)
        assertTrue("com.android.cts.robot" in packageNames)
    }

    private fun Element.androidAttribute(name: String): String =
        getAttributeNS(ANDROID_NAMESPACE, name)

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        const val HOME_CATEGORY = "android.intent.category.HOME"
        const val LAUNCHER3_ALIAS = "com.android.launcher3.Launcher"
    }
}
