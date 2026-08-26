package com.yinxing.launcher.common.lobster

data class ResolvedDeviceIdentity(
    val id: String,
    val changed: Boolean
)

object LobsterDeviceIdentity {
    fun resolve(
        storedId: String?,
        storedDeviceSignature: String?,
        currentDeviceSignature: String,
        createId: () -> String
    ): ResolvedDeviceIdentity {
        val existingId = storedId?.trim().orEmpty()
        val previousSignature = storedDeviceSignature?.trim().orEmpty()
        val currentSignature = currentDeviceSignature.trim()

        if (existingId.isEmpty()) {
            return ResolvedDeviceIdentity(createId(), true)
        }
        if (previousSignature.isNotEmpty() && previousSignature != currentSignature) {
            return ResolvedDeviceIdentity(createId(), true)
        }
        return ResolvedDeviceIdentity(existingId, false)
    }
}
