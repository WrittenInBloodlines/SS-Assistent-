package com.ss.assistent.assistant

import com.ss.assistent.model.ModelInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Compatibility fallback for tests or future runtime implementations.
 * It deliberately never pretends that a model has generated an answer.
 */
class LocalAssistantRuntime : AssistantRuntime {
    private var loadedModel: ModelInfo? = null

    override suspend fun load(model: ModelInfo): RuntimeResult {
        loadedModel = model
        return RuntimeResult.Error(
            "The GGUF model is imported, but this fallback runtime does not perform inference."
        )
    }

    override suspend fun generate(
        messages: List<ChatMessage>,
        maxTokens: Int
    ): RuntimeResult {
        return RuntimeResult.Error(
            "Local inference is not available in the fallback runtime."
        )
    }

    override fun generateStream(
        messages: List<ChatMessage>,
        maxTokens: Int
    ): Flow<RuntimeResult> = flowOf(
        RuntimeResult.Error("Local inference is not available in the fallback runtime.")
    )

    override fun unload() {
        loadedModel = null
    }

    override fun isLoaded(): Boolean = loadedModel != null
}
