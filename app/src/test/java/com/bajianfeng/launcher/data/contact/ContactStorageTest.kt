package com.bajianfeng.launcher.data.contact

import com.bajianfeng.launcher.common.util.AccessibilityServiceMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactStorageTest {
    @Test
    fun decodeConvertsBlankStringsToNull() {
        val contacts = ContactStorage.decode(
            """
            [
              {
                "id":"1",
                "name":"张三",
                "wechatId":"   ",
                "avatarUri":""
              }
            ]
            """.trimIndent()
        )

        assertEquals(1, contacts.size)
        assertNull(contacts.first().wechatId)
        assertNull(contacts.first().avatarUri)
    }

    @Test
    fun sortPrioritizesPinnedThenCallCountThenLastCallTime() {
        val contacts = listOf(
            Contact(id = "1", name = "王五", isPinned = false, callCount = 2, lastCallTime = 10),
            Contact(id = "2", name = "李四", isPinned = true, callCount = 1, lastCallTime = 5),
            Contact(id = "3", name = "张三", isPinned = true, callCount = 1, lastCallTime = 20),
            Contact(id = "4", name = "赵六", isPinned = false, callCount = 5, lastCallTime = 1)
        )

        val sortedIds = ContactStorage.sort(contacts).map { it.id }

        assertEquals(listOf("3", "2", "4", "1"), sortedIds)
    }

    @Test
    fun accessibilityServiceMatchUsesExactEntries() {
        val enabledServices = "com.demo/.Alpha:com.example/.BetaService"

        assertTrue(AccessibilityServiceMatcher.contains(enabledServices, "com.example/.BetaService"))
        assertFalse(AccessibilityServiceMatcher.contains(enabledServices, "com.example/.Beta"))
    }
}
