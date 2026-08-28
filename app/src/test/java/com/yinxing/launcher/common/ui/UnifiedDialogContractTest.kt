package com.yinxing.launcher.common.ui

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedDialogContractTest {
    private val appRoot: Path = sequenceOf(
        Path.of(System.getProperty("user.dir")),
        Path.of(System.getProperty("user.dir"), "app")
    ).first { Files.isDirectory(it.resolve("src/main")) }

    private val resourceRoot: Path = appRoot.resolve("src/main/res")
    private val sourceRoot: Path = appRoot.resolve("src/main/java")

    @Test
    fun `every dialog layout uses the shared launcher surface`() {
        val violations = Files.list(resourceRoot.resolve("layout")).use { paths ->
            paths.filter { it.extension == "xml" && it.name.startsWith("dialog_") }
                .filter { SHARED_SURFACE_STYLE !in it.readText() }
                .map { it.name }
                .sorted()
                .toList()
        }

        assertTrue("Dialog layouts must use the shared surface style: $violations", violations.isEmpty())
    }

    @Test
    fun `production dialogs are created through the shared window factory`() {
        val violations = Files.walk(sourceRoot).use { paths ->
            paths.filter { it.extension == "kt" && it.name != FACTORY_FILE }
                .filter { LEGACY_DIALOG_BUILDER in it.readText() }
                .map { sourceRoot.relativize(it).toString() }
                .sorted()
                .toList()
        }

        assertTrue("Dialog windows must use LauncherDialogFactory: $violations", violations.isEmpty())
    }

    @Test
    fun `shared dialog styles use centralized geometry tokens`() {
        val typography = resourceRoot.resolve("values/typography.xml").readText()
        val dimensionsFile = resourceRoot.resolve("values/dimens_dialog.xml")

        assertTrue("Missing shared dialog dimensions", Files.exists(dimensionsFile))
        val dimensions = dimensionsFile.readText()
        REQUIRED_DIMENSIONS.forEach { name ->
            assertTrue("Missing dialog dimension: $name", "name=\"$name\"" in dimensions)
            assertTrue("Dialog styles must reference $name", "@dimen/$name" in typography)
        }
    }

    private companion object {
        const val SHARED_SURFACE_STYLE = "style=\"@style/Widget.OldLauncher.DialogSurfaceCard\""
        const val LEGACY_DIALOG_BUILDER = "AlertDialog.Builder"
        const val FACTORY_FILE = "LauncherDialogFactory.kt"
        val REQUIRED_DIMENSIONS = listOf(
            "launcher_dialog_surface_corner",
            "launcher_dialog_button_corner"
        )
    }
}
