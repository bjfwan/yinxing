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
        return buildSearchKeywords(name, phoneNumber, wechatId, searchKeywords).any { keyword ->
            keyword.contains(normalizedQuery)
        }
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
    val digitsOnlyValue = value
        ?.filter { it.isDigit() }
        .orEmpty()
    if (digitsOnlyValue.isNotEmpty() && digitsOnlyValue != normalizedValue) {
        add(digitsOnlyValue)
    }
}

private fun String.toSearchToken(): String {
    return trim()
        .lowercase()
        .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
}
