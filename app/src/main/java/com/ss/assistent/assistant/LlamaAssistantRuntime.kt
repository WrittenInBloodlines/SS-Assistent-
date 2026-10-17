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
                val prompt = formatChatPrompt(activeEngine, messages)
                val rawOutput = StringBuilder()
                val sampledTokens = activeEngine.completion(
                    prompt = prompt,
                    params = samplingParams(settings),
                    callback = TokenCallback { token -> rawOutput.append(token) },
                )
                val filter = ThinkBlockFilter()
                val output = filter.push(rawOutput.toString()) + filter.finish()
                when {
                    sampledTokens < 0 -> {
                        unloadLocked()
                        RuntimeResult.Error("Native text generation failed. The model was unloaded for safety.")
                    }
                    sampledTokens == 0 -> RuntimeResult.Error("The native model could not start text generation.")
                    output.isBlank() -> RuntimeResult.Error("The model finished without producing a visible text response.")
                    else -> RuntimeResult.Success(output.trim())
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

                val prompt = formatChatPrompt(activeEngine, messages)
                val thinkFilter = ThinkBlockFilter()
                val sampledTokens = activeEngine.completion(
                    prompt = prompt,
                    params = samplingParams(settings),
                    callback = TokenCallback { token ->
                        if (token.isNotEmpty()) {
                            val visible = thinkFilter.push(token)
                            if (visible.isNotEmpty() &&
                                trySend(RuntimeResult.Success(visible)).isFailure
                            ) {
                                activeEngine.interrupt()
                            }
                        }
                    },
                )
                if (sampledTokens >= 0) {
                    val finalVisible = thinkFilter.finish()
                    if (finalVisible.isNotEmpty()) trySend(RuntimeResult.Success(finalVisible))
                }
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

    /** Qwen3 accepts /no_think on the current user turn to skip long reasoning traces. */
    private fun formatChatPrompt(activeEngine: LlamaEngine, messages: List<ChatMessage>): String {
        val lastUserIndex = messages.indexOfLast { it.role.equals("user", ignoreCase = true) }
        val nativeMessages = messages.mapIndexed { index, message ->
            val content = if (index == lastUserIndex) {
                message.content.trimEnd() + "\n/no_think"
            } else {
                message.content
            }
            NativeChatMessage(role = message.role, content = content)
        }
        return activeEngine.formatChat(nativeMessages)
    }

    private fun samplingParams(settings: GenerationSettings) = SamplingParams(
        nPredict = settings.maxTokens.coerceIn(1, 256),
        temperature = settings.temperature.coerceIn(0.1f, 1.5f),
        topK = settings.topK.coerceIn(1, 100),
        topP = settings.topP.coerceIn(0.1f, 1.0f),
        minP = 0.05f,
    )

    /** Removes Qwen3 reasoning blocks incrementally, including tags split across tokens. */
    private class ThinkBlockFilter {
        private var pending = ""
        private var insideThink = false

        fun push(chunk: String): String {
            pending += chunk
            val visible = StringBuilder()
            while (pending.isNotEmpty()) {
                if (insideThink) {
                    val closeAt = pending.indexOf(END_THINK, ignoreCase = true)
                    if (closeAt >= 0) {
                        pending = pending.substring(closeAt + END_THINK.length)
                        insideThink = false
                        continue
                    }
                    val keep = longestSuffixThatCouldStart(pending, END_THINK)
                    pending = if (keep == 0) "" else pending.takeLast(keep)
                    break
                }

                val openAt = pending.indexOf(START_THINK, ignoreCase = true)
                if (openAt >= 0) {
                    visible.append(pending.substring(0, openAt))
                    pending = pending.substring(openAt + START_THINK.length)
                    insideThink = true
                    continue
                }

                val keep = longestSuffixThatCouldStart(pending, START_THINK)
                val safeLength = pending.length - keep
                if (safeLength > 0) visible.append(pending.substring(0, safeLength))
                pending = if (keep == 0) "" else pending.takeLast(keep)
                break
            }
            return visible.toString()
        }

        fun finish(): String {
            val remaining = if (insideThink) "" else pending
            pending = ""
            insideThink = false
            return remaining
        }

        private fun longestSuffixThatCouldStart(value: String, tag: String): Int {
            val limit = minOf(value.length, tag.length - 1)
            for (length in limit downTo 1) {
                if (value.regionMatches(value.length - length, tag, 0, length, ignoreCase = true)) {
                    return length
                }
            }
            return 0
        }

        private companion object {
            const val START_THINK = "<think>"
            const val END_THINK = "</think>"
        }
    }

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
