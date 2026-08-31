package com.yinxing.launcher.common.lobster

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LobsterEndpointContractTest {
    private val sourcePath: Path = sequenceOf(
        Path.of(System.getProperty("user.dir"), "src", "main", "java", "com", "yinxing", "launcher", "common", "lobster", "LobsterClient.kt"),
        Path.of(System.getProperty("user.dir"), "app", "src", "main", "java", "com", "yinxing", "launcher", "common", "lobster", "LobsterClient.kt"),
    ).first(Files::isRegularFile)

    @Test
    fun `default upload uses the domestic log hostname`() {
        val source = String(Files.readAllBytes(sourcePath), Charsets.UTF_8)

        assertTrue(source.contains("https://log.722688.xyz/api/upload"))
        assertFalse(source.contains("https://log.likeyou.qzz.io/api/upload"))
    }
}
