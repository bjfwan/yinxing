package com.google.android.accessibility.selecttospeak

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatContactResultTraversalPolicyTest {
    @Test
    fun `current WeChat clickable result ancestor at depth five is inspected`() {
        assertTrue(WeChatContactResultTraversalPolicy.shouldInspect(depth = 5))
    }

    @Test
    fun `lookup remains bounded before reaching the whole result list`() {
        assertFalse(WeChatContactResultTraversalPolicy.shouldInspect(depth = 7))
    }
}
