package com.yinxing.launcher.common.lobster

import com.yinxing.launcher.BuildConfig
import org.json.JSONObject

data class LobsterBuildIdentity(
    val sha: String,
    val buildType: String,
    val applicationId: String,
    val sourceState: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("build_sha", sha.lowercase().takeIf(SHA::matches) ?: "unknown")
        put("build_type", buildType.trim().lowercase().takeIf(BUILD_TYPE::matches) ?: "unknown")
        put("application_id", applicationId.trim().lowercase().takeIf(APPLICATION_ID::matches) ?: "unknown")
        put("build_source_state", sourceState.trim().lowercase().takeIf(SOURCE_STATES::contains) ?: "unknown")
    }

    fun writeTo(target: JSONObject) {
        val json = toJson()
        json.keys().forEach { key -> target.put(key, json.get(key)) }
    }

    companion object {
        private val SHA = Regex("^[a-f0-9]{40}$")
        private val BUILD_TYPE = Regex("^[a-z][a-z0-9_-]{0,39}$")
        private val APPLICATION_ID = Regex("^[a-z][a-z0-9_.]{2,159}$")
        private val SOURCE_STATES = setOf("clean", "dirty", "unknown")

        fun current(): LobsterBuildIdentity = LobsterBuildIdentity(
            sha = BuildConfig.LOBSTER_BUILD_SHA,
            buildType = BuildConfig.BUILD_TYPE,
            applicationId = BuildConfig.APPLICATION_ID,
            sourceState = BuildConfig.LOBSTER_BUILD_SOURCE_STATE,
        )
    }
}
