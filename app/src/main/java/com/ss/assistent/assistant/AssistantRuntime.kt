package com.ss.assistent.assistant

import com.ss.assistent.model.ModelInfo
import com.ss.assistent.settings.GenerationSettings
import kotlinx.coroutines.flow.Flow

/** Runtime boundary for local language-model inference. */
interface AssistantRuntime {
    suspend fun load(model: ModelInfo): RuntimeResult

    suspend fun generate(
        messages: List<ChatMessage>,
        settings: GenerationSettings = GenerationSettings.DEFAULT
    ): RuntimeResult

    /** Streams generated text as it becomes available. */
    fun generateStream(
        messages: List<ChatMessage>,
        settings: GenerationSettings = GenerationSettings.DEFAULT
    ): Flow<RuntimeResult>

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
