package com.yinxing.launcher.common.ui

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class DarkThemeResourceContractTest {
    private val resourceRoot: Path = sequenceOf(
        Path.of(System.getProperty("user.dir"), "src", "main", "res"),
        Path.of(System.getProperty("user.dir"), "app", "src", "main", "res")
    ).first { Files.isDirectory(it) }

    @Test
    fun `background drawables use theme resources instead of fixed hex colors`() {
        val violations = Files.list(resourceRoot.resolve("drawable")).use { paths ->
            paths.filter { it.extension == "xml" && it.name.startsWith("bg_") }
                .filter { HEX_COLOR.containsMatchIn(it.readText()) }
                .map { it.name }
                .sorted()
                .toList()
        }

        assertTrue("Theme-dependent background colors must be resource tokens: $violations", violations.isEmpty())
    }

    @Test
    fun `layouts do not force a white surface in dark mode`() {
        val violations = Files.list(resourceRoot.resolve("layout")).use { paths ->
            paths.filter { it.extension == "xml" }
                .filter { FIXED_WHITE_SURFACE.containsMatchIn(it.readText()) }
                .map { it.name }
                .sorted()
                .toList()
        }

        assertTrue("Layout surfaces must use semantic colors: $violations", violations.isEmpty())
    }

    @Test
    fun `night palette defines every page-specific semantic color`() {
        val nightColors = mutableSetOf<String>()
        Files.list(resourceRoot.resolve("values-night")).use { paths ->
            paths.filter { it.extension == "xml" }.forEach { file ->
                COLOR_NAME.findAll(file.readText()).forEach { match ->
                    nightColors += match.groupValues[1]
                }
            }
        }
        val missing = REQUIRED_NIGHT_COLORS - nightColors

        assertTrue("Night palette is missing semantic colors: $missing", missing.isEmpty())
    }

    private companion object {
        val HEX_COLOR = Regex("#[0-9A-Fa-f]{6,8}")
        val FIXED_WHITE_SURFACE = Regex("(?:background|cardBackgroundColor)=\"@android:color/white\"")
        val COLOR_NAME = Regex("<color\\s+name=\"([^\"]+)\"")
        val REQUIRED_NIGHT_COLORS = setOf(
            "settings_outline",
            "settings_guard_outline",
            "settings_page_start",
            "settings_page_end",
            "settings_guard_start",
            "settings_guard_end",
            "settings_primary_start",
            "settings_primary_end",
            "settings_contact_action_start",
            "settings_contact_action_end",
            "settings_mode_surface",
            "settings_switch_track_off",
            "settings_switch_thumb_off",
            "weather_detail_hero_start",
            "weather_detail_hero_mid",
            "weather_detail_hero_end",
            "weather_detail_current_start",
            "weather_detail_current_end"
        )
    }
}
