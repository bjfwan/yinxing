package com.google.android.accessibility.selecttospeak

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatSearchResultSectionPolicyTest {
    @Test
    fun `most-used contact above network results is allowed`() {
        assertTrue(
            WeChatSearchResultSectionPolicy.isAllowed(
                candidateCenterY = 490,
                groupHeaderCenterY = null,
                networkHeaderCenterY = 1_176
            )
        )
    }

    @Test
    fun `network and group result rows are rejected`() {
        assertFalse(
            WeChatSearchResultSectionPolicy.isAllowed(
                candidateCenterY = 1_300,
                groupHeaderCenterY = null,
                networkHeaderCenterY = 1_176
            )
        )
        assertFalse(
            WeChatSearchResultSectionPolicy.isAllowed(
                candidateCenterY = 900,
                groupHeaderCenterY = 800,
                networkHeaderCenterY = null
            )
        )
    }
}
