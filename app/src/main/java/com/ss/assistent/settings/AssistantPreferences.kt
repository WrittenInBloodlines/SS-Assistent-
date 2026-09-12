package com.ss.assistent.settings

import android.content.Context

/** Stores assistant behavior and local generation choices on the device. */
class AssistantPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("assistant_preferences", Context.MODE_PRIVATE)

    var responseStyle: ResponseStyle
        get() = ResponseStyle.fromKey(preferences.getString(KEY_STYLE, ResponseStyle.NORMAL.key))
        set(value) = preferences.edit().putString(KEY_STYLE, value.key).apply()

    var customStyleInstruction: String
        get() = preferences.getString(KEY_CUSTOM_STYLE, "") ?: ""
        set(value) = preferences.edit().putString(KEY_CUSTOM_STYLE, value.trim().take(MAX_CUSTOM_STYLE_CHARS)).apply()

    var maxTokens: Int
        get() = preferences.getInt(KEY_MAX_TOKENS, GenerationSettings.DEFAULT.maxTokens).coerceIn(64, 1024)
        set(value) = preferences.edit().putInt(KEY_MAX_TOKENS, value.coerceIn(64, 1024)).apply()

    var temperature: Float
        get() = preferences.getFloat(KEY_TEMPERATURE, GenerationSettings.DEFAULT.temperature).coerceIn(0.1f, 1.5f)
        set(value) = preferences.edit().putFloat(KEY_TEMPERATURE, value.coerceIn(0.1f, 1.5f)).apply()

    var topK: Int
        get() = preferences.getInt(KEY_TOP_K, GenerationSettings.DEFAULT.topK).coerceIn(1, 100)
        set(value) = preferences.edit().putInt(KEY_TOP_K, value.coerceIn(1, 100)).apply()

    var topP: Float
        get() = preferences.getFloat(KEY_TOP_P, GenerationSettings.DEFAULT.topP).coerceIn(0.1f, 1.0f)
        set(value) = preferences.edit().putFloat(KEY_TOP_P, value.coerceIn(0.1f, 1.0f)).apply()

    fun generationSettings(): GenerationSettings = GenerationSettings(maxTokens, temperature, topK, topP)

    companion object {
        private const val KEY_STYLE = "response_style"
        private const val KEY_CUSTOM_STYLE = "custom_style_instruction"
        private const val KEY_MAX_TOKENS = "max_tokens"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_TOP_K = "top_k"
        private const val KEY_TOP_P = "top_p"
        private const val MAX_CUSTOM_STYLE_CHARS = 1000
    }
}

data class GenerationSettings(
    val maxTokens: Int = 256,
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
) {
    companion object {
        val DEFAULT = GenerationSettings(maxTokens = 256, temperature = 0.7f, topK = 40, topP = 0.95f)
    }
}

enum class ResponseStyle(val key: String, val title: String, val description: String, val instruction: String) {
    CONCISE("concise", "Concise", "Short, direct answers with little extra detail.", "Keep responses concise and focused. Avoid unnecessary explanations unless they are important."),
    NORMAL("normal", "Normal", "Balanced answers with useful context and clear structure.", "Give balanced, clear answers with enough context to be useful without becoming unnecessarily long."),
    DETAILED("detailed", "Detailed", "Thorough explanations with structure and useful context.", "Give thorough, well-structured answers. Explain important reasoning and context while staying relevant."),
    CUSTOM("custom", "Custom", "Use your own saved communication instruction.", "Follow the user's custom communication instruction when it is available. Remain clear, helpful, and safe.");

    companion object {
        fun fromKey(key: String?): ResponseStyle = values().firstOrNull { it.key == key } ?: NORMAL
    }
}
