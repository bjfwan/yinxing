package com.bajianfeng.launcher.model

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
)
