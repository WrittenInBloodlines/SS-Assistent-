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
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Real on-device GGUF runtime backed by llama.cpp through llama.kt. */
class LlamaAssistantRuntime(private val context: Context) : AssistantRuntime {
    /** Serializes every native engine load, generation and cleanup operation. */
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

    override suspend fun generate(messages: List<ChatMessage>, settings: GenerationSettings): RuntimeResult =
        withContext(Dispatchers.Default) {
            nativeLock.withLock {
                val activeEngine = engine ?: return@withLock RuntimeResult.Error("No local model is loaded.")
                if (messages.isEmpty()) return@withLock RuntimeResult.Error("There is no message to generate a response to.")
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

    override fun generateStream(messages: List<ChatMessage>, settings: GenerationSettings): Flow<RuntimeResult> = flow {
        /*
         * The native engine must remain locked for the entire decode operation
         * so load/free cannot race with native inference. ReentrantLock.withLock
         * is inline and therefore rejects suspension points such as collect.
         * Use explicit lock/unlock instead, with finally guaranteeing release
         * on normal completion, cancellation, or an exception.
         *
         * Tokens are buffered and emitted after native inference finishes. The
         * AssistantScreen still receives the complete result through the Flow,
         * while the native engine remains protected for the whole operation.
         */
        val result = withContext(Dispatchers.Default) {
            nativeLock.lock()
            try {
                val activeEngine = engine ?: return@withContext RuntimeResult.Error("No local model is loaded.")
                if (messages.isEmpty()) {
                    return@withContext RuntimeResult.Error("There is no message to generate a response to.")
                }
                try {
                    val prompt = activeEngine.formatChat(
                        messages = messages.map { NativeChatMessage(it.role, it.content) },
                        enableThinking = false,
                    )
                    if (activeEngine.tokenize(prompt).size >= contextTokens - 32) {
                        return@withContext RuntimeResult.Error("The conversation is too long for the current local context window. Clear the conversation and try again.")
                    }
                    val output = StringBuilder()
                    activeEngine.decode(prompt, samplingParams(settings)).collect { token ->
                        if (token.isNotEmpty()) output.append(token)
                    }
                    val text = output.toString().trim()
                    if (text.isEmpty()) RuntimeResult.Error("The model finished without producing a response.")
                    else RuntimeResult.Success(text)
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
        nPredict = settings.maxTokens.coerceIn(32, 1024),
        temperature = settings.temperature.coerceIn(0.1f, 1.5f),
        topK = settings.topK.coerceIn(1, 100),
        topP = settings.topP.coerceIn(0.1f, 1.0f),
        minP = 0.05f,
    )

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
