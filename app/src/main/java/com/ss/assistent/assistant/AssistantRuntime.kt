package com.ss.assistent.assistant

import com.ss.assistent.model.ModelInfo
import kotlinx.coroutines.flow.Flow

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

    /**
     * Streams generated text as it becomes available.
     * Implementations may emit one final chunk when streaming is unavailable.
     */
    fun generateStream(
        messages: List<ChatMessage>,
        maxTokens: Int = 256
    ): Flow<RuntimeResult>

    /** Interrupts an active generation and releases the native model. */
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
