package com.ss.assistent.settings

import android.content.Context

/** Stores assistant behavior choices locally on the device. */
class AssistantPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("assistant_preferences", Context.MODE_PRIVATE)

    var responseStyle: ResponseStyle
        get() = ResponseStyle.fromKey(preferences.getString(KEY_STYLE, ResponseStyle.NORMAL.key))
        set(value) = preferences.edit().putString(KEY_STYLE, value.key).apply()

    companion object {
        private const val KEY_STYLE = "response_style"
    }
}

enum class ResponseStyle(val key: String, val title: String, val description: String, val instruction: String) {
    CONCISE("concise", "Concise", "Short, direct answers with little extra detail.", "Keep responses concise and focused. Avoid unnecessary explanations unless they are important."),
    NORMAL("normal", "Normal", "Balanced answers with useful context and clear structure.", "Give balanced, clear answers with enough context to be useful without becoming unnecessarily long."),
    DETAILED("detailed", "Detailed", "Thorough explanations with structure and useful context.", "Give thorough, well-structured answers. Explain important reasoning and context while staying relevant."),
    CUSTOM("custom", "Custom", "Reserved for a future user-defined writing style.", "Follow the user's requested communication style while remaining clear, helpful, and safe.");

    companion object {
        fun fromKey(key: String?): ResponseStyle = values().firstOrNull { it.key == key } ?: NORMAL
    }
}
