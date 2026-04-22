package com.yinxing.launcher.data.contact

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
                val phoneNumber = obj.optNullableString("phoneNumber")
                val wechatId = obj.optNullableString("wechatId")
                contacts.add(
                    Contact(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        phoneNumber = phoneNumber,
                        wechatId = wechatId,
                        avatarUri = obj.optNullableString("avatarUri"),
                        preferredAction = Contact.PreferredAction.fromStorage(
                            obj.optString("preferredAction"),
                            phoneNumber,
                            wechatId
                        ),
                        isPinned = obj.optBoolean("isPinned", false),
                        callCount = obj.optInt("callCount", 0),
                        lastCallTime = obj.optLong("lastCallTime", 0),
                        searchKeywords = obj.optStringList("searchKeywords"),
                        autoAnswer = obj.optBoolean("autoAnswer", false)
                    ).normalized()
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
                    put("preferredAction", contact.preferredAction.name)
                    put("isPinned", contact.isPinned)
                    put("callCount", contact.callCount)
                    put("lastCallTime", contact.lastCallTime)
                    put("searchKeywords", keywordArray)
                    put("autoAnswer", contact.autoAnswer)
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
