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
                        phoneNumber = obj.optNullableString("phoneNumber"),
                        wechatId = obj.optNullableString("wechatId"),
                        avatarUri = obj.optNullableString("avatarUri"),
                        isPinned = obj.optBoolean("isPinned", false),
                        callCount = obj.optInt("callCount", 0),
                        lastCallTime = obj.optLong("lastCallTime", 0),
                        searchKeywords = obj.optStringList("searchKeywords")
                    )
                        .normalized()
                )
            }
            contacts
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun encode(contacts: List<Contact>): String {
        val jsonArray = JSONArray()
        contacts.forEach { rawContact ->
            val contact = rawContact.normalized()
            val keywordArray = JSONArray()
            contact.searchKeywords.forEach(keywordArray::put)
            jsonArray.put(
                JSONObject().apply {
                    put("id", contact.id)
                    put("name", contact.name)
                    put("phoneNumber", contact.phoneNumber ?: "")
                    put("wechatId", contact.wechatId ?: "")
                    put("avatarUri", contact.avatarUri ?: "")
                    put("isPinned", contact.isPinned)
                    put("callCount", contact.callCount)
                    put("lastCallTime", contact.lastCallTime)
                    put("searchKeywords", keywordArray)
                }
            )
        }
        return jsonArray.toString()
    }

    fun normalize(contact: Contact): Contact {
        return contact.normalized()
    }

    fun sort(contacts: List<Contact>): List<Contact> {
        return contacts.sortedWith(
            compareByDescending<Contact> { it.isPinned }
                .thenByDescending { it.callCount }
                .thenByDescending { it.lastCallTime }
                .thenBy { it.name }
        )
    }

    fun filter(contacts: List<Contact>, query: String): List<Contact> {
        if (query.isBlank()) {
            return sort(contacts)
        }
        return sort(contacts.filter { it.matchesQuery(query) })
    }

    private fun JSONObject.optNullableString(key: String): String? {
        val value = optString(key).trim()
        return value.takeIf { it.isNotEmpty() }
    }

    private fun JSONObject.optStringList(key: String): List<String> {
        val jsonArray = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (index in 0 until jsonArray.length()) {
                val value = jsonArray.optString(index).trim()
                if (value.isNotEmpty()) {
                    add(value)
                }
            }
        }
    }
}
