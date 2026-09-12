package com.ss.assistent.assistant

import com.ss.assistent.model.ModelInfo

/**
 * Runtime boundary for local language-model inference.
 *
 * The Android UI talks to this interface instead of depending on a native engine.
 * This keeps the app modular so a GGUF runtime can be swapped in later without
 * rebuilding the assistant UI or device-action layer.
 */
interface AssistantRuntime {
    suspend fun load(model: ModelInfo): RuntimeResult
    suspend fun generate(
        messages: List<ChatMessage>,
        maxTokens: Int = 256
    ): RuntimeResult
    fun unload()
    fun isLoaded(): Boolean
}

data class ChatMessage(
    val role: String,
    val content: String
)

sealed interface RuntimeResult {
    data class Success(val text: String) : RuntimeResult
    data class Error(val message: String) : RuntimeResult
}
