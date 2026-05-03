package com.yinxing.launcher.data.contact

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactTest {

    @Test
    fun displayNameReturnsName() {
        val contact = Contact(id = "1", name = "张阿姨")
        assertEquals("张阿姨", contact.displayName)
    }

    @Test
    fun wechatSearchNameReturnsWechatId() {
        val contact = Contact(id = "1", name = "张阿姨", wechatId = "zhang_yi")
        assertEquals("zhang_yi", contact.wechatSearchName)
    }

    @Test
    fun wechatSearchNameReturnsRawWechatIdEvenIfBlank() {
        val contact = Contact(id = "1", name = "张阿姨", wechatId = "  ")
        assertEquals("  ", contact.wechatSearchName)
    }

    @Test
    fun requiresWechatSearchNameWhenPreferredActionIsWechat() {
        val contact = Contact(id = "1", name = "张阿姨", preferredAction = Contact.PreferredAction.WECHAT_VIDEO)
        assertTrue(contact.requiresWechatSearchName())
    }

    @Test
    fun doesNotRequireWechatSearchNameWhenPreferredActionIsPhone() {
        val contact = Contact(id = "1", name = "张阿姨", preferredAction = Contact.PreferredAction.PHONE)
        assertFalse(contact.requiresWechatSearchName())
    }

    @Test
    fun hasWechatSearchNameWhenNonBlank() {
        val contact = Contact(id = "1", name = "张阿姨", wechatId = "zhang_yi")
        assertTrue(contact.hasWechatSearchName())
    }

    @Test
    fun doesNotHaveWechatSearchNameWhenNull() {
        val contact = Contact(id = "1", name = "张阿姨")
        assertFalse(contact.hasWechatSearchName())
    }

    @Test
    fun doesNotHaveWechatSearchNameWhenBlank() {
        val contact = Contact(id = "1", name = "张阿姨", wechatId = "   ")
        assertFalse(contact.hasWechatSearchName())
    }

    @Test
    fun preferredActionFromStorageReturnsMatchingEnum() {
        assertEquals(Contact.PreferredAction.PHONE, Contact.PreferredAction.fromStorage("PHONE", null, null))
        assertEquals(Contact.PreferredAction.WECHAT_VIDEO, Contact.PreferredAction.fromStorage("WECHAT_VIDEO", null, null))
    }

    @Test
    fun preferredActionFromStorageFallsBackToWechatWhenOnlyWechatId() {
        assertEquals(
            Contact.PreferredAction.WECHAT_VIDEO,
            Contact.PreferredAction.fromStorage(null, null, "wx_id")
        )
    }

    @Test
    fun preferredActionFromStorageFallsBackToPhoneWhenPhoneNumberExists() {
        assertEquals(
            Contact.PreferredAction.PHONE,
            Contact.PreferredAction.fromStorage(null, "13800138000", "wx_id")
        )
    }

    @Test
    fun preferredActionFromStorageFallsBackToPhoneWhenBothNull() {
        assertEquals(
            Contact.PreferredAction.PHONE,
            Contact.PreferredAction.fromStorage(null, null, null)
        )
    }

    @Test
    fun preferredActionFromStorageTrimsAndNormalizes() {
        assertEquals(
            Contact.PreferredAction.PHONE,
            Contact.PreferredAction.fromStorage("  PHONE  ", null, null)
        )
    }

    @Test
    fun preferredActionFromStorageInvalidValueFallsBack() {
        assertEquals(
            Contact.PreferredAction.PHONE,
            Contact.PreferredAction.fromStorage("INVALID", "13800138000", null)
        )
    }

    @Test
    fun normalizedTrimsAllFields() {
        val contact = Contact(
            id = "1",
            name = "  张三  ",
            phoneNumber = " 138 0013 8000 ",
            wechatId = " wx_id ",
            avatarUri = "  uri  "
        ).normalized()

        assertEquals("张三", contact.name)
        assertEquals("138 0013 8000", contact.phoneNumber)
        assertEquals("wx_id", contact.wechatId)
        assertEquals("uri", contact.avatarUri)
    }

    @Test
    fun normalizedConvertsBlankFieldsToNull() {
        val contact = Contact(
            id = "1",
            name = "张三",
            phoneNumber = "   ",
            wechatId = "",
            avatarUri = "  "
        ).normalized()

        assertNull(contact.phoneNumber)
        assertNull(contact.wechatId)
        assertNull(contact.avatarUri)
    }

    @Test
    fun normalizedBuildsSearchKeywords() {
        val contact = Contact(
            id = "1",
            name = "张三",
            phoneNumber = "138-0013-8000",
            wechatId = "wx_zhang"
        ).normalized()

        assertTrue(contact.searchKeywords.contains("13800138000"))
        assertTrue(contact.searchKeywords.contains("wx_zhang"))
    }

    @Test
    fun matchesQueryMatchesKeyword() {
        val contact = Contact(id = "1", name = "张三", searchKeywords = listOf("张三", "zhang", "13800138000"))
        assertTrue(contact.matchesQuery("张"))
        assertTrue(contact.matchesQuery("zhang"))
    }

    @Test
    fun matchesQueryMatchesPhoneNumber() {
        val contact = Contact(id = "1", name = "张三", searchKeywords = listOf("zhang", "13800138000"))
        assertTrue(contact.matchesQuery("138"))
    }

    @Test
    fun matchesQueryReturnsTrueForEmptyQuery() {
        val contact = Contact(id = "1", name = "张三")
        assertTrue(contact.matchesQuery(""))
    }

    @Test
    fun matchesQueryReturnsFalseForNonMatching() {
        val contact = Contact(id = "1", name = "张三", searchKeywords = listOf("zhang"))
        assertFalse(contact.matchesQuery("李四"))
    }

    @Test
    fun matchesQueryIsCaseInsensitiveWhenKeywordsNormalized() {
        val contact = Contact(id = "1", name = "张三", searchKeywords = listOf("zhang"))
        assertTrue(contact.matchesQuery("zhang"))
        assertTrue(contact.matchesQuery("Zhang"))
    }

    @Test
    fun matchesQueryBuildsKeywordsWhenEmpty() {
        val contact = Contact(id = "1", name = "张三", phoneNumber = "13800138000")
        val noKeywords = contact.copy(searchKeywords = emptyList())
        assertTrue(noKeywords.matchesQuery("138"))
    }
}
