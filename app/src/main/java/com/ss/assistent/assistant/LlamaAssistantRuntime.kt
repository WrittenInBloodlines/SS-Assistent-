package com.ss.assistent.assistant

import android.content.Context
import com.ss.assistent.model.ModelInfo
import com.ss.assistent.settings.GenerationSettings
import com.tensai.llamakt.ChatMessage as NativeChatMessage
import com.tensai.llamakt.LlamaEngine
import com.tensai.llamakt.SamplingParams
import com.tensai.llamakt.TokenCallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference
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
            if (!File(model.path).isFile || !File(model.path).canRead()) {
                unloadLocked()
                return@withLock RuntimeResult.Error("The selected model file cannot be read on this device.")
            }
            if (engine != null && loadedModelPath == model.path) {
                return@withLock RuntimeResult.Success("Model already loaded.")
            }
            val diagnosedContextTokens = when (val diagnostic = ModelDiagnostics.inspect(context, model)) {
                is DiagnosticResult.Error -> return@withLock RuntimeResult.Error(diagnostic.message)
                is DiagnosticResult.Ready -> diagnostic.contextTokens
            }
            try {
                unloadLocked()
                contextTokens = diagnosedContextTokens
                loadNativeModelLocked(model.path)
                RuntimeResult.Success("Model loaded.")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                unloadLocked()
                RuntimeResult.Error(error.message ?: "The native GGUF runtime could not load this model.")
            }
        }
    }

    override suspend fun generate(
        messages: List<ChatMessage>,
        settings: GenerationSettings
    ): RuntimeResult = withContext(Dispatchers.IO) {
        nativeLock.withLock {
            if (messages.isEmpty()) {
                return@withLock RuntimeResult.Error("There is no message to generate a response to.")
            }
            try {
                if (!reloadNativeModelLocked()) {
                    return@withLock RuntimeResult.Error("The local model could not be reloaded safely for inference.")
                }
                val activeEngine = engine
                    ?: return@withLock RuntimeResult.Error("No local model is loaded.")
                val prompt = activeEngine.formatChat(
                    messages.map { NativeChatMessage(role = it.role, content = it.content) }
                )
                val output = StringBuilder()
                val sampledTokens = activeEngine.completion(
                    prompt = prompt,
                    params = samplingParams(settings),
                    callback = TokenCallback { token -> output.append(token) },
                )
                when {
                    sampledTokens < 0 -> {
                        unloadLocked()
                        RuntimeResult.Error("Native text generation failed. The model was unloaded for safety.")
                    }
                    sampledTokens == 0 -> RuntimeResult.Error("The native model could not start text generation.")
                    output.isBlank() -> RuntimeResult.Error("The model finished without producing a text response.")
                    else -> RuntimeResult.Success(output.toString())
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                unloadLocked()
                RuntimeResult.Error(
                    error.message ?: "Local inference failed unexpectedly. The model was unloaded and can be retried."
                )
            }
        }
    }

    override fun generateStream(
        messages: List<ChatMessage>,
        settings: GenerationSettings
    ): Flow<RuntimeResult> = callbackFlow {
        val activeEngineRef = AtomicReference<LlamaEngine?>(null)
        val worker = launch(Dispatchers.IO) {
            var lockAcquired = false
            try {
                nativeLock.lock()
                lockAcquired = true
                currentCoroutineContext().ensureActive()

                if (messages.isEmpty()) {
                    trySend(RuntimeResult.Error("There is no message to generate a response to."))
                    return@launch
                }
                if (!reloadNativeModelLocked()) {
                    trySend(RuntimeResult.Error("The local model could not be reloaded safely for inference."))
                    return@launch
                }
                val activeEngine = engine
                    ?: run {
                        trySend(RuntimeResult.Error("No local model is loaded."))
                        return@launch
                    }
                activeEngineRef.set(activeEngine)

                val prompt = activeEngine.formatChat(
                    messages.map { NativeChatMessage(role = it.role, content = it.content) }
                )
                val sampledTokens = activeEngine.completion(
                    prompt = prompt,
                    params = samplingParams(settings),
                    callback = TokenCallback { token ->
                        if (token.isNotEmpty() &&
                            trySend(RuntimeResult.Success(token)).isFailure
                        ) {
                            activeEngine.interrupt()
                        }
                    },
                )
                when {
                    sampledTokens < 0 -> {
                        unloadLocked()
                        trySend(RuntimeResult.Error("Native text generation failed. The model was unloaded for safety."))
                    }
                    sampledTokens == 0 ->
                        trySend(RuntimeResult.Error("The native model could not start text generation."))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (lockAcquired) unloadLocked()
                trySend(
                    RuntimeResult.Error(
                        error.message ?: "Local inference failed unexpectedly. The model was unloaded and can be retried."
                    )
                )
            } finally {
                activeEngineRef.set(null)
                if (lockAcquired) nativeLock.unlock()
                close()
            }
        }

        awaitClose {
            activeEngineRef.get()?.interrupt()
            worker.cancel()
        }
    }

    private fun samplingParams(settings: GenerationSettings) = SamplingParams(
        nPredict = settings.maxTokens.coerceIn(1, 256),
        temperature = settings.temperature.coerceIn(0.1f, 1.5f),
        topK = settings.topK.coerceIn(1, 100),
        topP = settings.topP.coerceIn(0.1f, 1.0f),
        minP = 0.05f,
    )

    /** Reinitializing the native context before each request avoids reusing KV state. */
    private fun reloadNativeModelLocked(): Boolean {
        val path = loadedModelPath ?: return false
        val file = File(path)
        if (!file.isFile || !file.canRead()) return false
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
