package com.ss.assistent.assistant

import android.content.Context
import com.ss.assistent.model.ModelInfo
import com.tensai.llamakt.ChatMessage as NativeChatMessage
import com.tensai.llamakt.LlamaEngine
import com.tensai.llamakt.SamplingParams
import com.tensai.llamakt.decode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Real on-device GGUF runtime backed by llama.cpp through llama.kt.
 *
 * The UI still talks to AssistantRuntime, so the native engine remains isolated
 * from the rest of the assistant architecture.
 */
class LlamaAssistantRuntime(private val context: Context) : AssistantRuntime {
    private var engine: LlamaEngine? = null
    private var loadedModelPath: String? = null
    private var contextTokens: Int = ModelDiagnostics.DEFAULT_CONTEXT_TOKENS

    override suspend fun load(model: ModelInfo): RuntimeResult = withContext(Dispatchers.Default) {
        if (!File(model.path).exists()) {
            unload()
            return@withContext RuntimeResult.Error("The selected model file no longer exists on the device.")
        }

        if (engine != null && loadedModelPath == model.path) {
            return@withContext RuntimeResult.Success("Model already loaded.")
        }

        when (val diagnostic = ModelDiagnostics.inspect(context, model)) {
            is DiagnosticResult.Error -> return@withContext RuntimeResult.Error(diagnostic.message)
            is DiagnosticResult.Ready -> contextTokens = diagnostic.contextTokens
        }

        return@withContext try {
            unload()
            val newEngine = LlamaEngine()
            newEngine.load(
                path = model.path,
                nGpuLayers = 0,
                nCtx = contextTokens,
                nThreads = 0,
                kvCacheType = "q8_0",
                flashAttn = null,
            )
            engine = newEngine
            loadedModelPath = model.path
            RuntimeResult.Success("Model loaded.")
        } catch (error: Throwable) {
            unload()
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

            val promptTokens = activeEngine.tokenize(prompt).size
            if (promptTokens >= contextTokens - 32) {
                return@withContext RuntimeResult.Error(
                    "The conversation is too long for the current local context window. Clear the conversation and try again."
                )
            }

            val output = StringBuilder()
            val params = samplingParams(maxTokens)
            val sampledTokens = activeEngine.completion(
                prompt = prompt,
                params = params,
                callback = com.tensai.llamakt.TokenCallback { token -> output.append(token) },
            )

            if (sampledTokens < 0) {
                unload()
                RuntimeResult.Error("Local inference failed. The model was unloaded so it can be reloaded safely on the next attempt.")
            } else {
                val text = output.toString().trim()
                if (text.isEmpty()) {
                    RuntimeResult.Error("The model finished without producing a response.")
                } else {
                    RuntimeResult.Success(text)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            unload()
            RuntimeResult.Error(
                error.message ?: "Local inference failed unexpectedly. The model was unloaded and can be retried."
            )
        }
    }

    override fun generateStream(
        messages: List<ChatMessage>,
        maxTokens: Int
    ): Flow<RuntimeResult> = flow {
        val activeEngine = engine
        if (activeEngine == null) {
            emit(RuntimeResult.Error("No local model is loaded."))
            return@flow
        }
        if (messages.isEmpty()) {
            emit(RuntimeResult.Error("There is no message to generate a response to."))
            return@flow
        }

        try {
            val nativeMessages = messages.map { message ->
                NativeChatMessage(message.role, message.content)
            }
            val prompt = activeEngine.formatChat(
                messages = nativeMessages,
                enableThinking = false,
            )
            val promptTokens = activeEngine.tokenize(prompt).size
            if (promptTokens >= contextTokens - 32) {
                emit(RuntimeResult.Error(
                    "The conversation is too long for the current local context window. Clear the conversation and try again."
                ))
                return@flow
            }

            activeEngine.decode(prompt, samplingParams(maxTokens)).collect { token ->
                if (token.isNotEmpty()) {
                    emit(RuntimeResult.Success(token))
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            unload()
            emit(RuntimeResult.Error(
                error.message ?: "Local inference failed unexpectedly. The model was unloaded and can be retried."
            ))
        }
    }.flowOn(Dispatchers.Default)

    private fun samplingParams(maxTokens: Int): SamplingParams = SamplingParams(
        nPredict = maxTokens.coerceIn(32, 1024),
        temperature = 0.7f,
        topK = 40,
        topP = 0.95f,
        minP = 0.05f,
    )

    override fun unload() {
        engine?.free()
        engine = null
        loadedModelPath = null
        contextTokens = ModelDiagnostics.DEFAULT_CONTEXT_TOKENS
    }

    override fun isLoaded(): Boolean = engine != null
}
