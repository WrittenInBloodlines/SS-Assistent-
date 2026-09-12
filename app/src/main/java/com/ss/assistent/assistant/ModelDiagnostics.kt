package com.ss.assistent.assistant

import android.app.ActivityManager
import android.content.Context
import com.ss.assistent.model.ModelInfo
import com.tensai.llamakt.GgufMetadata
import com.tensai.llamakt.LlamaEngine
import java.io.File

/**
 * Lightweight preflight checks performed before allocating native model memory.
 * These checks are intentionally conservative: they reject clearly unsafe or
 * incompatible inputs without pretending to predict exact device performance.
 */
object ModelDiagnostics {
    private const val CONTEXT_TOKENS = 4096
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

        val memoryInfo = ActivityManager.MemoryInfo()
        val activityManager = context.getSystemService(ActivityManager::class.java)
        activityManager?.getMemoryInfo(memoryInfo)
        if (memoryInfo.availMem in 1 until MIN_AVAILABLE_MEMORY_BYTES) {
            return DiagnosticResult.Error(
                "Android reports too little available memory for safe local inference. Close other apps and try again."
            )
        }

        val contextLength = metadata.contextLength
        val effectiveContext = minOf(contextLength.takeIf { it > 0 } ?: CONTEXT_TOKENS.toLong(), CONTEXT_TOKENS.toLong())
        return DiagnosticResult.Ready(metadata, effectiveContext.toInt())
    }
}

sealed interface DiagnosticResult {
    data class Ready(val metadata: GgufMetadata, val contextTokens: Int) : DiagnosticResult
    data class Error(val message: String) : DiagnosticResult
}
