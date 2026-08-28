package com.yinxing.launcher.automation.wechat.teaching

import org.json.JSONArray
import org.json.JSONObject

object WeChatTeachingRecordCodec {
    fun encode(record: WeChatTeachingRecord): String = JSONObject().apply {
        put("schema_version", 2)
        put("fingerprint", JSONObject().apply {
            put("manufacturer", record.fingerprint.manufacturer)
            put("model", record.fingerprint.model)
            put("android_sdk", record.fingerprint.androidSdk)
            put("screen_width", record.fingerprint.screenWidth)
            put("screen_height", record.fingerprint.screenHeight)
            put("density_dpi", record.fingerprint.densityDpi)
            put("font_scale_permille", record.fingerprint.fontScalePermille)
            put("locale_tag", record.fingerprint.localeTag)
            put("wechat_version_name", record.fingerprint.weChatVersionName)
            put("wechat_version_code", record.fingerprint.weChatVersionCode)
        })
        put("video_confirmed", record.videoConfirmed)
        put("learned_actions", JSONArray().apply {
            record.learnedActions.forEach { put(it.name) }
        })
        put("verified_actions", JSONArray().apply {
            record.verifiedActions.forEach { put(it.name) }
        })
        put("added_actions", JSONArray().apply {
            record.addedActions.forEach { put(it.name) }
        })
        put("created_at_epoch_ms", record.createdAtEpochMs)
    }.toString()

    fun decode(raw: String?): WeChatTeachingRecord? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val json = JSONObject(raw)
            val schemaVersion = json.getInt("schema_version")
            if (schemaVersion !in 1..2) return null
            val fingerprint = json.getJSONObject("fingerprint")
            val learnedActions = decodeActions(json.getJSONArray("learned_actions"))
            WeChatTeachingRecord(
                fingerprint = WeChatTeachingFingerprint(
                    manufacturer = fingerprint.getString("manufacturer"),
                    model = fingerprint.getString("model"),
                    androidSdk = fingerprint.getInt("android_sdk"),
                    screenWidth = fingerprint.getInt("screen_width"),
                    screenHeight = fingerprint.getInt("screen_height"),
                    densityDpi = fingerprint.getInt("density_dpi"),
                    fontScalePermille = fingerprint.getInt("font_scale_permille"),
                    localeTag = fingerprint.getString("locale_tag"),
                    weChatVersionName = fingerprint.getString("wechat_version_name"),
                    weChatVersionCode = fingerprint.getLong("wechat_version_code")
                ),
                videoConfirmed = json.getBoolean("video_confirmed"),
                learnedActions = learnedActions,
                verifiedActions = if (schemaVersion >= 2) {
                    decodeActions(json.getJSONArray("verified_actions"))
                } else {
                    emptySet()
                },
                addedActions = if (schemaVersion >= 2) {
                    decodeActions(json.getJSONArray("added_actions"))
                } else {
                    emptySet()
                },
                createdAtEpochMs = json.getLong("created_at_epoch_ms")
            )
        }.getOrNull()
    }

    private fun decodeActions(array: JSONArray): Set<WeChatTeachingAction> = buildSet {
        for (index in 0 until array.length()) {
            add(WeChatTeachingAction.valueOf(array.getString(index)))
        }
    }
}
