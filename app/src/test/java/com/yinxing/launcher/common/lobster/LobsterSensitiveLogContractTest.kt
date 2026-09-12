package com.yinxing.launcher.common.lobster

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Test

class LobsterSensitiveLogContractTest {
    @Test
    fun `buffered logs do not interpolate contact names or phone numbers`() {
        val sourceRoot = sequenceOf(
            Path.of(System.getProperty("user.dir"), "src", "main", "java"),
            Path.of(System.getProperty("user.dir"), "app", "src", "main", "java"),
        ).first(Files::isDirectory)
        val source = Files.walk(sourceRoot).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .map { String(Files.readAllBytes(it), Charsets.UTF_8) }
                .toList()
                .joinToString("\n")
        }

        assertFalse(source.contains("LobsterClient.log(\"[微信自动] 请求开始: 联系人=\$contactName"))
        assertFalse(source.contains("LobsterClient.log(\"[微信视频] 流程开始: 联系人=\${contact.displayName}"))
        assertFalse(source.contains("LobsterClient.log(\"[微信视频] 请求级超时: 联系人=\$contactName"))
        assertFalse(source.contains("LobsterClient.log(\"[来电服务] 启动: Caller=\$callerName, Number=\$incomingNumber"))
        assertFalse(source.contains("LobsterClient.log(\"[来电处理] applyIntent | 来电者: \$callerName"))
    }
}
