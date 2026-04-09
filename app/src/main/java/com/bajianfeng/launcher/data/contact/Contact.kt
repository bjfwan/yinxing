package com.bajianfeng.launcher.data.contact

data class Contact(
    val id: String,
    val name: String,
    val phoneNumber: String? = null,
    val wechatId: String? = null,
    val avatarUri: String? = null,
    val isPinned: Boolean = false,
    val callCount: Int = 0,
    val lastCallTime: Long = 0,
    val searchKeywords: List<String> = emptyList()
) {
    fun normalized(): Contact {
        val normalizedName = name.trim()
        val normalizedPhoneNumber = phoneNumber?.trim().orEmpty().ifBlank { null }
        val normalizedWechatId = wechatId?.trim().orEmpty().ifBlank { null }
        val normalizedAvatarUri = avatarUri?.trim().orEmpty().ifBlank { null }
        return copy(
            name = normalizedName,
            phoneNumber = normalizedPhoneNumber,
            wechatId = normalizedWechatId,
            avatarUri = normalizedAvatarUri,
            searchKeywords = buildSearchKeywords(
                normalizedName,
                normalizedPhoneNumber,
                normalizedWechatId
            )
        )
    }

    companion object {
        fun buildSearchKeywords(
            name: String,
            phoneNumber: String?,
            wechatId: String?
        ): List<String> {
            return buildSet {
                addToken(name)
                addToken(phoneNumber)
                addToken(wechatId)
                phoneNumber
                    ?.filter(Char::isDigit)
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { add(it) }
            }.toList()
        }

        private fun MutableSet<String>.addToken(value: String?) {
            val normalized = value?.trim()?.lowercase().orEmpty()
            if (normalized.isNotEmpty()) {
                add(normalized)
            }
        }
    }
}
