package com.yinxing.launcher.automation.wechat.teaching

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatTeachingUploadChoicePolicyTest {

    @Test
    fun eachNewSessionDefaultsToUploadButUserCanDisableIt() {
        val first = WeChatTeachingUploadChoicePolicy.newSession()
        val disabled = first.copy(uploadAnonymousData = false)
        val next = WeChatTeachingUploadChoicePolicy.newSession()

        assertTrue(first.uploadAnonymousData)
        assertFalse(disabled.uploadAnonymousData)
        assertTrue(next.uploadAnonymousData)
    }

    @Test
    fun localSaveNeverDependsOnUploadChoiceOrNetworkResult() {
        assertTrue(
            WeChatTeachingUploadChoicePolicy.shouldSaveLocally(
                uploadAnonymousData = false,
                uploadSucceeded = false
            )
        )
        assertFalse(WeChatTeachingUploadChoicePolicy.shouldUpload(uploadAnonymousData = false))
        assertTrue(WeChatTeachingUploadChoicePolicy.shouldUpload(uploadAnonymousData = true))
    }
}
