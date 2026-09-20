package com.ss.assistent.assistant

import android.content.Context
import com.ss.assistent.model.ModelInfo
import com.ss.assistent.settings.GenerationSettings
import com.tensai.llamakt.ChatMessage as NativeChatMessage
import com.tensai.llamakt.LlamaEngine
import com.tensai.llamakt.SamplingParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Real on-device GGUF runtime backed by llama.cpp through llama.kt. */
class LlamaAssistantRuntime(private val context: Context) : AssistantRuntime {
    private val nativeLock = ReentrantLock()

    private var engine: LlamaEngine? = null
    private var loadedModelPath: String? = null
    private var contextTokens: Int = ModelDiagnostics.DEFAULT_CONTEXT_TOKENS

    override suspend fun load(model: ModelInfo): RuntimeResult = withContext(Dispatchers.IO) {
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
                unloadLocked()
                loadNativeModelLocked(model.path)
                RuntimeResult.Success("Model loaded.")
            } catch (error: Throwable) {
                unloadLocked()
                RuntimeResult.Error(error.message ?: "The native GGUF runtime could not load this model.")
            }
        }
    }

    override suspend fun generate(messages: List<ChatMessage>, settings: GenerationSettings): RuntimeResult =
        withContext(Dispatchers.IO) {
            nativeLock.withLock {
                if (messages.isEmpty()) return@withLock RuntimeResult.Error("There is no message to generate a response to.")
                try {
                    if (!reloadNativeModelLocked()) {
                        return@withLock RuntimeResult.Error("The local model could not be reloaded safely for inference.")
                    }
                    val activeEngine = engine ?: return@withLock RuntimeResult.Error("No local model is loaded.")
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

    override fun generateStream(messages: List<ChatMessage>, settings: GenerationSettings): Flow<RuntimeResult> = flow {
        val result = withContext(Dispatchers.IO) {
            nativeLock.lock()
            try {
                if (messages.isEmpty()) {
                    return@withContext RuntimeResult.Error("There is no message to generate a response to.")
                }
                try {
                    /*
                     * llama.cpp keeps prompt/KV state inside the native engine.
                     * Rebuild the engine before every request, following the
                     * reset strategy that stabilized repeated generations in
                     * SS-Story-AI. SS-Story-AI itself is not modified.
                     */
                    if (!reloadNativeModelLocked()) {
                        return@withContext RuntimeResult.Error("The local model could not be reloaded safely for inference.")
                    }
                    val activeEngine = engine ?: return@withContext RuntimeResult.Error("No local model is loaded.")
                    val prompt = activeEngine.formatChat(
                        messages = messages.map { NativeChatMessage(it.role, it.content) },
                        enableThinking = false,
                    )
                    if (activeEngine.tokenize(prompt).size >= contextTokens - 32) {
                        return@withContext RuntimeResult.Error("The conversation is too long for the current local context window. Clear the conversation and try again.")
                    }

                    val output = StringBuilder()
                    val sampledTokens = activeEngine.completion(
                        prompt = prompt,
                        params = samplingParams(settings),
                        callback = com.tensai.llamakt.TokenCallback { token ->
                            if (token.isNotEmpty()) output.append(token)
                        },
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
            } finally {
                nativeLock.unlock()
            }
        }
        emit(result)
    }

    private fun samplingParams(settings: GenerationSettings): SamplingParams = SamplingParams(
        nPredict = settings.maxTokens.coerceIn(32, 256),
        temperature = settings.temperature.coerceIn(0.1f, 1.5f),
        topK = settings.topK.coerceIn(1, 100),
        topP = settings.topP.coerceIn(0.1f, 1.0f),
        minP = 0.05f,
    )

    private fun reloadNativeModelLocked(): Boolean {
        val path = loadedModelPath ?: return false
        val file = File(path)
        if (!file.exists() || !file.isFile || !file.canRead()) return false

        return try {
            engine?.free()
            engine = null
            loadNativeModelLocked(path)
            true
        } catch (_: Throwable) {
            try {
                engine?.free()
            } catch (_: Throwable) {
            }
            engine = null
            loadedModelPath = null
            false
        }
    }

    /** Must only be called while [nativeLock] is held. */
    private fun loadNativeModelLocked(path: String) {
        val newEngine = LlamaEngine()
        try {
            newEngine.load(
                path = path,
                nGpuLayers = 0,
                nCtx = contextTokens,
                nThreads = 4,
                kvCacheType = null,
                flashAttn = "off",
            )
            engine = newEngine
            loadedModelPath = path
        } catch (error: Throwable) {
            try {
                newEngine.free()
            } catch (_: Throwable) {
            }
            throw error
        }
    }

    override fun unload() {
        nativeLock.withLock { unloadLocked() }
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
