package com.ss.assistent.chat

import android.content.Context
import com.ss.assistent.assistant.ChatMessage
import com.ss.assistent.settings.GenerationSettings
import com.ss.assistent.settings.ResponseStyle
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Keeps conversation data local and provides explicit metadata/profile/export primitives. */
class ConversationRepository(context: Context) {
    private val preferences = context.getSharedPreferences("conversations", Context.MODE_PRIVATE)

    var title: String
        get() = preferences.getString(KEY_TITLE, "New conversation") ?: "New conversation"
        set(value) = preferences.edit().putString(KEY_TITLE, value.trim().take(MAX_TITLE_CHARS).ifBlank { "New conversation" }).apply()

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

    fun profile(): ConversationGenerationProfile {
        val style = ResponseStyle.fromKey(preferences.getString(KEY_PROFILE_STYLE, null))
        return ConversationGenerationProfile(
            responseStyle = if (preferences.contains(KEY_PROFILE_STYLE)) style else null,
            customStyleInstruction = preferences.getString(KEY_PROFILE_CUSTOM, "") ?: "",
            generation = GenerationSettings(
                maxTokens = preferences.getInt(KEY_PROFILE_MAX_TOKENS, GenerationSettings.DEFAULT.maxTokens).coerceIn(64, 1024),
                temperature = preferences.getFloat(KEY_PROFILE_TEMPERATURE, GenerationSettings.DEFAULT.temperature).coerceIn(0.1f, 1.5f),
                topK = preferences.getInt(KEY_PROFILE_TOP_K, GenerationSettings.DEFAULT.topK).coerceIn(1, 100),
                topP = preferences.getFloat(KEY_PROFILE_TOP_P, GenerationSettings.DEFAULT.topP).coerceIn(0.1f, 1.0f)
            )
        )
    }

    fun saveProfile(profile: ConversationGenerationProfile) {
        preferences.edit()
            .apply {
                profile.responseStyle?.let { putString(KEY_PROFILE_STYLE, it.key) } ?: remove(KEY_PROFILE_STYLE)
                putString(KEY_PROFILE_CUSTOM, profile.customStyleInstruction.take(MAX_CUSTOM_STYLE_CHARS))
                putInt(KEY_PROFILE_MAX_TOKENS, profile.generation.maxTokens.coerceIn(64, 1024))
                putFloat(KEY_PROFILE_TEMPERATURE, profile.generation.temperature.coerceIn(0.1f, 1.5f))
                putInt(KEY_PROFILE_TOP_K, profile.generation.topK.coerceIn(1, 100))
                putFloat(KEY_PROFILE_TOP_P, profile.generation.topP.coerceIn(0.1f, 1.0f))
            }
            .apply()
    }

    fun clearProfile() = preferences.edit()
        .remove(KEY_PROFILE_STYLE)
        .remove(KEY_PROFILE_CUSTOM)
        .remove(KEY_PROFILE_MAX_TOKENS)
        .remove(KEY_PROFILE_TEMPERATURE)
        .remove(KEY_PROFILE_TOP_K)
        .remove(KEY_PROFILE_TOP_P)
        .apply()

    fun exportText(): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        return buildString {
            appendLine(title)
            appendLine("Exported: $timestamp")
            appendLine()
            appendLine("Response profile: ${profile().summary()}")
            appendLine()
            loadMessages().forEach { message ->
                appendLine("${if (message.role == "user") "You" else message.role.replaceFirstChar { it.uppercase() }}:")
                appendLine(message.content)
                appendLine()
            }
        }.trimEnd()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    companion object {
        private const val KEY_MESSAGES = "current_conversation"
        private const val KEY_TITLE = "conversation_title"
        private const val KEY_PROFILE_STYLE = "profile_style"
        private const val KEY_PROFILE_CUSTOM = "profile_custom_style"
        private const val KEY_PROFILE_MAX_TOKENS = "profile_max_tokens"
        private const val KEY_PROFILE_TEMPERATURE = "profile_temperature"
        private const val KEY_PROFILE_TOP_K = "profile_top_k"
        private const val KEY_PROFILE_TOP_P = "profile_top_p"
        private const val MAX_TITLE_CHARS = 120
        private const val MAX_CUSTOM_STYLE_CHARS = 1000
    }
}

data class ConversationGenerationProfile(
    val responseStyle: ResponseStyle? = null,
    val customStyleInstruction: String = "",
    val generation: GenerationSettings = GenerationSettings.DEFAULT
) {
    fun summary(): String = buildString {
        append(responseStyle?.title ?: "Global style")
        append(" • ")
        append(generation.maxTokens)
        append(" tokens • T ")
        append("%.2f".format(Locale.US, generation.temperature))
        append(" • Top-K ")
        append(generation.topK)
        append(" • Top-P ")
        append("%.2f".format(Locale.US, generation.topP))
    }
}
