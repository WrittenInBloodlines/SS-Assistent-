package com.ss.assistent.assistant

import android.content.Context
import com.ss.assistent.model.ModelInfo
import com.ss.assistent.settings.GenerationSettings
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
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Real on-device GGUF runtime backed by llama.cpp through llama.kt. */
class LlamaAssistantRuntime(private val context: Context) : AssistantRuntime {
    /**
     * llama.cpp owns native state that must never be touched concurrently.
     * A load/free racing with decode can terminate the Android process before
     * Kotlin gets a chance to catch an exception.
     */
    private val nativeLock = ReentrantLock()

    private var engine: LlamaEngine? = null
    private var loadedModelPath: String? = null
    private var contextTokens: Int = ModelDiagnostics.DEFAULT_CONTEXT_TOKENS

    override suspend fun load(model: ModelInfo): RuntimeResult = withContext(Dispatchers.Default) {
        nativeLock.withLock {
            if (!File(model.path).exists()) {
                unloadLocked()
                return@withLock RuntimeResult.Error("The selected model file no longer exists on the device.")
            }

            if (engine != null && loadedModelPath == model.path) {
                return@withLock RuntimeResult.Success("Model already loaded.")
            }

            when (val diagnostic = ModelDiagnostics.inspect(context, model)) {
                is DiagnosticResult.Error -> return@withLock RuntimeResult.Error(diagnostic.message)
                is DiagnosticResult.Ready -> contextTokens = diagnostic.contextTokens
            }

            try {
                // Never replace/free a native engine while another native
                // operation is running. The lock also serializes model loads.
                unloadLocked()

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
                unloadLocked()
                RuntimeResult.Error(error.message ?: "The native GGUF runtime could not load this model.")
            }
        }
    }

    override suspend fun generate(
        messages: List<ChatMessage>,
        settings: GenerationSettings
    ): RuntimeResult = withContext(Dispatchers.Default) {
        nativeLock.withLock {
            val activeEngine = engine
                ?: return@withLock RuntimeResult.Error("No local model is loaded.")
            if (messages.isEmpty()) {
                return@withLock RuntimeResult.Error("There is no message to generate a response to.")
            }

            try {
                val prompt = activeEngine.formatChat(
                    messages = messages.map { NativeChatMessage(it.role, it.content) },
                    enableThinking = false,
                )
                if (activeEngine.tokenize(prompt).size >= contextTokens - 32) {
                    return@withLock RuntimeResult.Error("The conversation is too long for the current local context window. Clear the conversation and try again.")
                }

                val output = StringBuilder()
                val sampledTokens = activeEngine.completion(
                    prompt = prompt,
                    params = samplingParams(settings),
                    callback = com.tensai.llamakt.TokenCallback { token -> output.append(token) },
                )

                if (sampledTokens < 0) {
                    unloadLocked()
                    RuntimeResult.Error("Local inference failed. The model was unloaded so it can be reloaded safely on the next attempt.")
                } else {
                    val text = output.toString().trim()
                    if (text.isEmpty()) RuntimeResult.Error("The model finished without producing a response.")
                    else RuntimeResult.Success(text)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                unloadLocked()
                RuntimeResult.Error(error.message ?: "Local inference failed unexpectedly. The model was unloaded and can be retried.")
            }
        }
    }

    override fun generateStream(
        messages: List<ChatMessage>,
        settings: GenerationSettings
    ): Flow<RuntimeResult> = flow {
        nativeLock.withLock {
            val activeEngine = engine
            if (activeEngine == null) {
                emit(RuntimeResult.Error("No local model is loaded."))
                return@withLock
            }
            if (messages.isEmpty()) {
                emit(RuntimeResult.Error("There is no message to generate a response to."))
                return@withLock
            }

            try {
                val prompt = activeEngine.formatChat(
                    messages = messages.map { NativeChatMessage(it.role, it.content) },
                    enableThinking = false,
                )
                if (activeEngine.tokenize(prompt).size >= contextTokens - 32) {
                    emit(RuntimeResult.Error("The conversation is too long for the current local context window. Clear the conversation and try again."))
                    return@withLock
                }

                // Keep the entire native generation inside the same critical
                // section. A second generation, model reload, or cleanup cannot
                // invalidate the LlamaEngine while decode() is using it.
                activeEngine.decode(prompt, samplingParams(settings)).collect { token ->
                    if (token.isNotEmpty()) emit(RuntimeResult.Success(token))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                unloadLocked()
                emit(RuntimeResult.Error(error.message ?: "Local inference failed unexpectedly. The model was unloaded and can be retried."))
            }
        }
    }.flowOn(Dispatchers.Default)

    private fun samplingParams(settings: GenerationSettings): SamplingParams = SamplingParams(
        nPredict = settings.maxTokens.coerceIn(32, 1024),
        temperature = settings.temperature.coerceIn(0.1f, 1.5f),
        topK = settings.topK.coerceIn(1, 100),
        topP = settings.topP.coerceIn(0.1f, 1.0f),
        minP = 0.05f,
    )

    override fun unload() {
        nativeLock.withLock {
            unloadLocked()
        }
    }

    /** Must only be called while [nativeLock] is held. */
    private fun unloadLocked() {
        try {
            engine?.free()
        } finally {
            engine = null
            loadedModelPath = null
            contextTokens = ModelDiagnostics.DEFAULT_CONTEXT_TOKENS
        }
    }

    override fun isLoaded(): Boolean = nativeLock.withLock { engine != null }
}
