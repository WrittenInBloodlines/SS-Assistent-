package com.ss.assistent.assistant

import com.ss.assistent.model.ModelInfo
import com.tensai.llamakt.ChatMessage as NativeChatMessage
import com.tensai.llamakt.LlamaEngine
import com.tensai.llamakt.SamplingParams
import com.tensai.llamakt.TokenCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Real on-device GGUF runtime backed by llama.cpp through llama.kt.
 *
 * The UI still talks to AssistantRuntime, so the native engine remains isolated
 * from the rest of the assistant architecture.
 */
class LlamaAssistantRuntime : AssistantRuntime {
    private var engine: LlamaEngine? = null
    private var loadedModelPath: String? = null

    override suspend fun load(model: ModelInfo): RuntimeResult = withContext(Dispatchers.Default) {
        if (!File(model.path).exists()) {
            return@withContext RuntimeResult.Error("The selected model file no longer exists on the device.")
        }

        if (engine != null && loadedModelPath == model.path) {
            return@withContext RuntimeResult.Success("Model already loaded.")
        }

        try {
            engine?.free()
            val newEngine = LlamaEngine()
            newEngine.load(
                path = model.path,
                nGpuLayers = 0,
                nCtx = 4096,
                nThreads = 0,
                kvCacheType = "q8_0",
                flashAttn = "auto",
            )
            engine = newEngine
            loadedModelPath = model.path
            RuntimeResult.Success("Model loaded.")
        } catch (error: Throwable) {
            engine?.free()
            engine = null
            loadedModelPath = null
            RuntimeResult.Error(
                error.message ?: "The native GGUF runtime could not load this model."
            )
        }
    }

    override suspend fun generate(
        messages: List<ChatMessage>,
        maxTokens: Int
    ): RuntimeResult = withContext(Dispatchers.Default) {
        val activeEngine = engine
            ?: return@withContext RuntimeResult.Error("No local model is loaded.")

        if (messages.isEmpty()) {
            return@withContext RuntimeResult.Error("There is no message to generate a response to.")
        }

        try {
            val nativeMessages = messages.map { message ->
                NativeChatMessage(message.role, message.content)
            }
            val prompt = activeEngine.formatChat(
                messages = nativeMessages,
                enableThinking = false,
            )

            val output = StringBuilder()
            val params = SamplingParams(
                nPredict = maxTokens.coerceIn(32, 1024),
                temperature = 0.7f,
                topK = 40,
                topP = 0.95f,
                minP = 0.05f,
            )

            val sampledTokens = activeEngine.completion(
                prompt = prompt,
                params = params,
                callback = TokenCallback { token -> output.append(token) },
            )

            if (sampledTokens < 0) {
                RuntimeResult.Error("Local inference failed while generating the response.")
            } else {
                val text = output.toString().trim()
                if (text.isEmpty()) {
                    RuntimeResult.Error("The model finished without producing a response.")
                } else {
                    RuntimeResult.Success(text)
                }
            }
        } catch (error: Throwable) {
            RuntimeResult.Error(
                error.message ?: "Local inference failed unexpectedly."
            )
        }
    }

    override fun unload() {
        engine?.free()
        engine = null
        loadedModelPath = null
    }

    override fun isLoaded(): Boolean = engine != null
}
