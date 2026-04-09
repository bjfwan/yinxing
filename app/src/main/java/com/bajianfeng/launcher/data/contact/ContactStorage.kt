package com.bajianfeng.launcher.data.contact

import org.json.JSONArray
import org.json.JSONObject

object ContactStorage {
    fun decode(json: String?): MutableList<Contact> {
        if (json.isNullOrBlank()) {
            return mutableListOf()
        }

        return try {
            val jsonArray = JSONArray(json)
            val contacts = mutableListOf<Contact>()
            for (index in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(index)
                contacts.add(
                    Contact(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        wechatId = obj.optNullableString("wechatId"),
                        avatarUri = obj.optNullableString("avatarUri"),
                        isPinned = obj.optBoolean("isPinned", false),
                        callCount = obj.optInt("callCount", 0),
                        lastCallTime = obj.optLong("lastCallTime", 0)
                    )
                )
            }
            contacts
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun encode(contacts: List<Contact>): String {
        val jsonArray = JSONArray()
        contacts.forEach { contact ->
            jsonArray.put(
                JSONObject().apply {
                    put("id", contact.id)
                    put("name", contact.name)
                    put("wechatId", contact.wechatId ?: "")
                    put("avatarUri", contact.avatarUri ?: "")
                    put("isPinned", contact.isPinned)
                    put("callCount", contact.callCount)
                    put("lastCallTime", contact.lastCallTime)
                }
            )
        }
        return jsonArray.toString()
    }

    fun sort(contacts: List<Contact>): List<Contact> {
        return contacts.sortedWith(
            compareByDescending<Contact> { it.isPinned }
                .thenByDescending { it.callCount }
                .thenByDescending { it.lastCallTime }
                .thenBy { it.name }
        )
    }

    private fun JSONObject.optNullableString(key: String): String? {
        val value = optString(key).trim()
        return value.takeIf { it.isNotEmpty() }
    }
}
