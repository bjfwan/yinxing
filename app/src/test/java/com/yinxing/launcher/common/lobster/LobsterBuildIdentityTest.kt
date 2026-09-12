package com.yinxing.launcher.common.lobster

import org.junit.Assert.assertEquals
import org.junit.Test

class LobsterBuildIdentityTest {
    @Test
    fun `writes bounded build identity fields used to match a report to source`() {
        val json = LobsterBuildIdentity(
            sha = "ABCDEF1234567890-not-valid",
            buildType = "deviceTest",
            applicationId = "com.yinxing.launcher.devicetest",
            sourceState = "dirty",
        ).toJson()

        assertEquals("unknown", json.getString("build_sha"))
        assertEquals("devicetest", json.getString("build_type"))
        assertEquals("com.yinxing.launcher.devicetest", json.getString("application_id"))
        assertEquals("dirty", json.getString("build_source_state"))
    }

    @Test
    fun `preserves a valid git sha and normalizes unknown source state`() {
        val json = LobsterBuildIdentity(
            sha = "abcdef1234567890abcdef1234567890abcdef12",
            buildType = "release",
            applicationId = "com.yinxing.launcher",
            sourceState = "maybe",
        ).toJson()

        assertEquals("abcdef1234567890abcdef1234567890abcdef12", json.getString("build_sha"))
        assertEquals("unknown", json.getString("build_source_state"))
    }
}
