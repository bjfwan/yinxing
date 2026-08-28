package com.yinxing.launcher.automation.wechat.teaching

import org.json.JSONArray
import org.json.JSONObject

object WeChatTeachingProfileCodec {
    fun encode(profile: WeChatTeachingProfile): String = JSONObject().apply {
        put("schema_version", profile.schemaVersion)
        put("fingerprint", encodeFingerprint(profile.fingerprint))
        put("steps", JSONArray().apply {
            profile.steps.forEach { step -> put(encodeStep(step)) }
        })
        put("reliability_score", profile.reliabilityScore)
        put("reliability", profile.reliability.name)
        put("created_at_epoch_ms", profile.createdAtEpochMs)
    }.toString()

    fun decode(raw: String?): WeChatTeachingProfile? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val json = JSONObject(raw)
            if (json.getInt("schema_version") != 1) return null
            val stepsJson = json.getJSONArray("steps")
            val steps = buildList {
                for (index in 0 until stepsJson.length()) {
                    add(decodeStep(stepsJson.getJSONObject(index)))
                }
            }
            WeChatTeachingProfile(
                schemaVersion = 1,
                fingerprint = decodeFingerprint(json.getJSONObject("fingerprint")),
                steps = steps,
                reliabilityScore = json.getInt("reliability_score"),
                reliability = WeChatTeachingReliability.valueOf(json.getString("reliability")),
                createdAtEpochMs = json.getLong("created_at_epoch_ms")
            )
        }.getOrNull()
    }

    private fun encodeFingerprint(value: WeChatTeachingFingerprint): JSONObject = JSONObject().apply {
        put("manufacturer", value.manufacturer)
        put("model", value.model)
        put("android_sdk", value.androidSdk)
        put("screen_width", value.screenWidth)
        put("screen_height", value.screenHeight)
        put("density_dpi", value.densityDpi)
        put("font_scale_permille", value.fontScalePermille)
        put("locale_tag", value.localeTag)
        put("wechat_version_name", value.weChatVersionName)
        put("wechat_version_code", value.weChatVersionCode)
    }

    private fun decodeFingerprint(json: JSONObject) = WeChatTeachingFingerprint(
        manufacturer = json.getString("manufacturer"),
        model = json.getString("model"),
        androidSdk = json.getInt("android_sdk"),
        screenWidth = json.getInt("screen_width"),
        screenHeight = json.getInt("screen_height"),
        densityDpi = json.getInt("density_dpi"),
        fontScalePermille = json.getInt("font_scale_permille"),
        localeTag = json.getString("locale_tag"),
        weChatVersionName = json.getString("wechat_version_name"),
        weChatVersionCode = json.getLong("wechat_version_code")
    )

    private fun encodeStep(step: WeChatTeachingStep): JSONObject = JSONObject().apply {
        put("action", step.action.name)
        put("window_class", step.windowClass)
        put("expected_window_class", step.expectedWindowClass)
        put("selector", JSONObject().apply {
            step.selector.resourceId?.let { put("resource_id", it) }
            step.selector.nodeClass?.let { put("node_class", it) }
            step.selector.semanticLabel?.let { put("semantic_label", it.name) }
            put("clickable_ancestor_depth", step.selector.clickableAncestorDepth)
            step.selector.centerXRatio?.let { put("center_x_ratio", it.toDouble()) }
            step.selector.centerYRatio?.let { put("center_y_ratio", it.toDouble()) }
        })
    }

    private fun decodeStep(json: JSONObject): WeChatTeachingStep {
        val selectorJson = json.getJSONObject("selector")
        return WeChatTeachingStep(
            action = WeChatTeachingAction.valueOf(json.getString("action")),
            windowClass = json.getString("window_class"),
            expectedWindowClass = json.getString("expected_window_class"),
            selector = WeChatTeachingSelector(
                resourceId = selectorJson.optionalString("resource_id"),
                nodeClass = selectorJson.optionalString("node_class"),
                semanticLabel = selectorJson.optionalString("semantic_label")
                    ?.let(WeChatTeachingSemanticLabel::valueOf),
                clickableAncestorDepth = selectorJson.getInt("clickable_ancestor_depth"),
                centerXRatio = selectorJson.optionalFloat("center_x_ratio"),
                centerYRatio = selectorJson.optionalFloat("center_y_ratio")
            )
        )
    }

    private fun JSONObject.optionalString(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.optionalFloat(key: String): Float? =
        if (has(key) && !isNull(key)) getDouble(key).toFloat() else null
}
