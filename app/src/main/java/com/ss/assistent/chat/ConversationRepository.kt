package com.ss.assistent.chat

import android.content.Context
import com.ss.assistent.assistant.ChatMessage
import org.json.JSONArray
import org.json.JSONObject

/** Keeps the current conversation locally so closing the screen does not erase it. */
class ConversationRepository(context: Context) {
    private val preferences = context.getSharedPreferences("conversations", Context.MODE_PRIVATE)

    fun loadMessages(): List<ChatMessage> {
        val raw = preferences.getString(KEY_MESSAGES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(ChatMessage(item.getString("role"), item.getString("content")))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveMessages(messages: List<ChatMessage>) {
        val array = JSONArray()
        messages.forEach { message ->
            array.put(JSONObject().apply {
                put("role", message.role)
                put("content", message.content)
            })
        }
        preferences.edit().putString(KEY_MESSAGES, array.toString()).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_MESSAGES).apply()
    }

    companion object {
        private const val KEY_MESSAGES = "current_conversation"
    }
}
