package com.yinxing.launcher.data.contact

data class Contact(
    val id: String,
    val name: String,
    val phoneNumber: String? = null,
    val wechatId: String? = null,
    val avatarUri: String? = null,
    val preferredAction: PreferredAction = PreferredAction.PHONE,
    val isPinned: Boolean = false,
    val callCount: Int = 0,
    val lastCallTime: Long = 0,
    val searchKeywords: List<String> = emptyList(),
    val autoAnswer: Boolean = false
) {
    val displayName: String
        get() = name

    val wechatSearchName: String?
        get() = wechatId

    fun requiresWechatSearchName(): Boolean {
        return preferredAction == PreferredAction.WECHAT_VIDEO
    }

    fun hasWechatSearchName(): Boolean {
        return !wechatSearchName.isNullOrBlank()
    }

    enum class PreferredAction {
        PHONE,
        WECHAT_VIDEO;

        companion object {
            fun fromStorage(value: String?, phoneNumber: String?, wechatId: String?): PreferredAction {
                val normalized = value?.trim().orEmpty()
                values().firstOrNull { it.name == normalized }?.let { return it }
                return if (phoneNumber.isNullOrBlank() && !wechatId.isNullOrBlank()) {
                    WECHAT_VIDEO
                } else {
                    PHONE
                }
            }
        }
    }

    fun normalized(): Contact {
        val normalizedName = name.trim()
        val normalizedPhoneNumber = phoneNumber?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedWechatId = wechatId?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedAvatarUri = avatarUri?.trim()?.takeIf { it.isNotEmpty() }
        return copy(
            name = normalizedName,
            phoneNumber = normalizedPhoneNumber,
            wechatId = normalizedWechatId,
            avatarUri = normalizedAvatarUri,
            searchKeywords = buildSearchKeywords(
                normalizedName,
                normalizedPhoneNumber,
                normalizedWechatId,
                searchKeywords
            )
        )
    }

    fun matchesQuery(query: String): Boolean {
        val normalizedQuery = query.toSearchToken()
        if (normalizedQuery.isEmpty()) {
            return true
        }
        val keywords = if (searchKeywords.isNotEmpty()) {
            searchKeywords
        } else {
            buildSearchKeywords(name, phoneNumber, wechatId, emptyList())
        }
        return keywords.any { it.contains(normalizedQuery) }
    }
}

private fun buildSearchKeywords(
    name: String,
    phoneNumber: String?,
    wechatId: String?,
    searchKeywords: List<String>
): List<String> {
    return buildList {
        addSearchKeyword(name)
        addSearchKeyword(phoneNumber)
        addSearchKeyword(wechatId)
        searchKeywords.forEach(::addSearchKeyword)
    }.distinct()
}

private fun MutableList<String>.addSearchKeyword(value: String?) {
    val normalizedValue = value?.toSearchToken().orEmpty()
    if (normalizedValue.isNotEmpty()) {
        add(normalizedValue)
    }
    val digitsOnlyValue = value?.filter { it.isDigit() }.orEmpty()
    if (digitsOnlyValue.isNotEmpty() && digitsOnlyValue != normalizedValue) {
        add(digitsOnlyValue)
    }
}

private fun String.toSearchToken(): String {
    return trim()
        .lowercase()
        .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
}
