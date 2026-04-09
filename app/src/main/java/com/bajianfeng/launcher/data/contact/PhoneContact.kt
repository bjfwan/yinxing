package com.bajianfeng.launcher.data.contact

data class PhoneContact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val photoUri: String?
) {
    val stableId: Long
        get() = id.toLongOrNull() ?: id.hashCode().toLong()
}
