package com.yinxing.launcher.common.lobster

object LobsterRuntimePolicy {
    fun shouldUpload(manufacturer: String, model: String, fingerprint: String): Boolean {
        return sequenceOf(manufacturer, model, fingerprint)
            .none { it.contains("robolectric", ignoreCase = true) }
    }
}
