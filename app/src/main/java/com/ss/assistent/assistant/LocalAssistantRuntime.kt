package com.ss.assistent.assistant

import com.ss.assistent.model.ModelInfo

/**
 * Temporary runtime implementation used until the native GGUF engine is wired in.
 * It deliberately never pretends that a model has generated an answer.
 */
class LocalAssistantRuntime : AssistantRuntime {
    private var loadedModel: ModelInfo? = null

    override suspend fun load(model: ModelInfo): RuntimeResult {
        loadedModel = model
        return RuntimeResult.Error(
            "The GGUF model is imported, but the native inference engine is not connected yet."
        )
    }

    override suspend fun generate(
        messages: List<ChatMessage>,
        maxTokens: Int
    ): RuntimeResult {
        return RuntimeResult.Error(
            "Local inference is not available yet. Connect the GGUF runtime before generating text."
        )
    }

    override fun unload() {
        loadedModel = null
    }

    override fun isLoaded(): Boolean = loadedModel != null
}
