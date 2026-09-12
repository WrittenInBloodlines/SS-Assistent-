package com.ss.assistent.assistant

import com.ss.assistent.model.ModelInfo
import com.ss.assistent.settings.GenerationSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Compatibility fallback for tests or future runtime implementations. */
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
        settings: GenerationSettings
    ): RuntimeResult = RuntimeResult.Error(
        "Local inference is not available in the fallback runtime."
    )

    override fun generateStream(
        messages: List<ChatMessage>,
        settings: GenerationSettings
    ): Flow<RuntimeResult> = flowOf(
        RuntimeResult.Error("Local inference is not available in the fallback runtime.")
    )

    override fun unload() {
        loadedModel = null
    }

    override fun isLoaded(): Boolean = loadedModel != null
}
