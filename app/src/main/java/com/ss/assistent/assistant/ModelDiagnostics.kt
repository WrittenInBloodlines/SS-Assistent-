package com.ss.assistent.assistant

import android.app.ActivityManager
import android.content.Context
import com.ss.assistent.model.ModelInfo
import com.tensai.llamakt.GgufMetadata
import com.tensai.llamakt.LlamaEngine
import java.io.File
import kotlin.math.roundToInt

/**
 * Lightweight preflight checks performed before allocating native model memory.
 * These checks are intentionally conservative: they reject clearly unsafe or
 * incompatible inputs without pretending to predict exact device performance.
 */
object ModelDiagnostics {
    const val DEFAULT_CONTEXT_TOKENS = 2048
    private const val MIN_AVAILABLE_MEMORY_BYTES = 900L * 1024L * 1024L

    fun inspect(context: Context, model: ModelInfo): DiagnosticResult {
        val file = File(model.path)
        if (!file.isFile || !file.canRead()) {
            return DiagnosticResult.Error("The selected model file cannot be read on this device.")
        }

        val metadata = try {
            LlamaEngine.readMetadata(file.absolutePath)
        } catch (error: Throwable) {
            return DiagnosticResult.Error(
                error.message ?: "The GGUF metadata could not be read."
            )
        } ?: return DiagnosticResult.Error("The GGUF metadata could not be read.")

        if (metadata.architecture.isBlank()) {
            return DiagnosticResult.Error("The model architecture could not be identified.")
        }

        val activityManager = context.getSystemService(ActivityManager::class.java)
            ?: return DiagnosticResult.Error("Android memory information is unavailable.")

        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        if (memoryInfo.availMem < MIN_AVAILABLE_MEMORY_BYTES) {
            return DiagnosticResult.Error(
                "Android reports too little available memory for safe local inference. Close other apps and try again."
            )
        }

        val contextLength = metadata.contextLength
        val effectiveContext = minOf(
            contextLength.takeIf { it > 0 } ?: DEFAULT_CONTEXT_TOKENS.toLong(),
            DEFAULT_CONTEXT_TOKENS.toLong()
        )

        return DiagnosticResult.Ready(
            metadata = metadata,
            contextTokens = effectiveContext.toInt(),
            availableMemoryBytes = memoryInfo.availMem,
            deviceLowMemory = memoryInfo.lowMemory,
        )
    }
}

sealed interface DiagnosticResult {
    data class Ready(
        val metadata: GgufMetadata,
        val contextTokens: Int,
        val availableMemoryBytes: Long,
        val deviceLowMemory: Boolean,
    ) : DiagnosticResult {
        val availableMemoryLabel: String
            get() {
                val gb = availableMemoryBytes / (1024.0 * 1024.0 * 1024.0)
                return "%.1f GB".format(gb)
            }

        val modelParameterLabel: String
            get() {
                val params = metadata.paramCount
                if (params <= 0) return "Unknown parameters"
                val billions = params / 1_000_000_000.0
                return if (billions >= 1.0) "%.1fB parameters".format(billions)
                else "${(params / 1_000_000.0).roundToInt()}M parameters"
            }
    }

    data class Error(val message: String) : DiagnosticResult
}
