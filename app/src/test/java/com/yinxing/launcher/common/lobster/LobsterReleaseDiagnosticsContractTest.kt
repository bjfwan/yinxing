package com.yinxing.launcher.common.lobster

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Test

class LobsterReleaseDiagnosticsContractTest {
    private val projectRoot = sequenceOf(
        Path.of(System.getProperty("user.dir")),
        Path.of(System.getProperty("user.dir")).parent,
    ).first { Files.isRegularFile(it.resolve("app/build.gradle.kts")) }

    @Test
    fun `release keeps retrace metadata and defines a source keyed diagnostic bundle`() {
        val proguard = String(Files.readAllBytes(projectRoot.resolve("app/proguard-rules.pro")), Charsets.UTF_8)
        val gradle = String(Files.readAllBytes(projectRoot.resolve("app/build.gradle.kts")), Charsets.UTF_8)

        assertTrue(proguard.lineSequence().any { it.trim() == "-keepattributes SourceFile,LineNumberTable" })
        assertTrue(gradle.contains("bundleReleaseDiagnostics"))
        assertTrue(gradle.contains("outputs/mapping/release/mapping.txt"))
        assertTrue(gradle.contains("lobsterBuildSourceState != \"clean\""))
    }
}
