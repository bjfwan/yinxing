package com.yinxing.launcher.data.contact

import com.yinxing.launcher.common.util.AccessibilityServiceMatcher
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
                "name":" 张三 ",
                "phoneNumber":" 138 0013 8000 ",
                "wechatId":"   ",
                "avatarUri":"",
                "searchKeywords":[" 家人 ","老张"]
              }
            ]
            """.trimIndent()
        )

        assertEquals(1, contacts.size)
        assertEquals("张三", contacts.first().name)
        assertEquals("138 0013 8000", contacts.first().phoneNumber)
        assertNull(contacts.first().wechatId)
        assertNull(contacts.first().avatarUri)
        assertTrue(contacts.first().searchKeywords.contains("13800138000"))
        assertTrue(contacts.first().searchKeywords.contains("家人"))
        assertTrue(contacts.first().searchKeywords.contains("老张"))
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
    fun filterMatchesSearchKeywordsAndKeepsSortOrder() {
        val contacts = listOf(
            ContactStorage.normalize(Contact(id = "1", name = "张三", searchKeywords = listOf("家人"), callCount = 2)),
            ContactStorage.normalize(Contact(id = "2", name = "李四", wechatId = "laosi", isPinned = true, callCount = 1)),
            ContactStorage.normalize(Contact(id = "3", name = "王五", phoneNumber = "138-0013-8000", callCount = 5))
        )

        assertEquals(listOf("1"), ContactStorage.filter(contacts, "家人").map { it.id })
        assertEquals(listOf("2"), ContactStorage.filter(contacts, "lao").map { it.id })
        assertEquals(listOf("3"), ContactStorage.filter(contacts, "1380013").map { it.id })
    }

    @Test
    fun encodeAndDecodePreserveSearchKeywords() {
        val json = ContactStorage.encode(
            listOf(Contact(id = "1", name = "张三", wechatId = "lao_zhang", searchKeywords = listOf("家人")))
        )

        val contact = ContactStorage.decode(json).first()

        assertTrue(contact.searchKeywords.contains("lao_zhang"))
        assertTrue(contact.searchKeywords.contains("家人"))
    }

    @Test
    fun accessibilityServiceMatchUsesExactEntries() {
        val enabledServices = "com.demo/.Alpha:com.example/.BetaService"

        assertTrue(AccessibilityServiceMatcher.contains(enabledServices, "com.example/.BetaService"))
        assertFalse(AccessibilityServiceMatcher.contains(enabledServices, "com.example/.Beta"))
    }
}
