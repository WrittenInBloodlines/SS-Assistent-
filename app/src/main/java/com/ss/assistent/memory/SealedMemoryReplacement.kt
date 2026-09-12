package com.ss.assistent.memory

/**
 * Result of resolving an explicit sealed-memory replacement command.
 * The resolver never performs the replacement itself, so the UI can show a confirmation
 * preview before anything is written.
 */
sealed interface SealedMemoryReplacementResolution {
    data class Ready(
        val memory: MemoryEntry,
        val replacementText: String
    ) : SealedMemoryReplacementResolution

    data class NotFound(
        val previousText: String
    ) : SealedMemoryReplacementResolution

    data class Ambiguous(
        val previousText: String,
        val matchCount: Int
    ) : SealedMemoryReplacementResolution

    data class InvalidReplacement(
        val replacementText: String,
        val maxCharacters: Int
    ) : SealedMemoryReplacementResolution
}

/**
 * Resolves an explicit replacement against local sealed memories without mutating storage.
 * Exact character matching is intentional. A command must identify the stored value exactly,
 * otherwise the assistant refuses to guess which memory the user meant.
 */
class SealedMemoryReplacementResolver(
    private val memoryRepository: MemoryRepository
) {
    fun resolve(request: MemoryIntentParser.SealedReplacementRequest): SealedMemoryReplacementResolution {
        if (request.replacementText.isEmpty() || request.replacementText.length > MAX_EXACT_MEMORY_TEXT_CHARS) {
            return SealedMemoryReplacementResolution.InvalidReplacement(
                replacementText = request.replacementText,
                maxCharacters = MAX_EXACT_MEMORY_TEXT_CHARS
            )
        }

        val matches = memoryRepository.findAllSealedExact(request.previousText)
        return when {
            matches.isEmpty() -> SealedMemoryReplacementResolution.NotFound(request.previousText)
            matches.size > 1 -> SealedMemoryReplacementResolution.Ambiguous(
                previousText = request.previousText,
                matchCount = matches.size
            )
            else -> SealedMemoryReplacementResolution.Ready(
                memory = matches.single(),
                replacementText = request.replacementText
            )
        }
    }

    /** Applies a previously resolved replacement only after an explicit confirmation. */
    fun apply(resolution: SealedMemoryReplacementResolution.Ready): MemoryEntry? =
        memoryRepository.updateSealed(
            id = resolution.memory.id,
            category = resolution.memory.category,
            exactReplacement = resolution.replacementText,
            explicitOverride = true
        )

    companion object {
        private const val MAX_EXACT_MEMORY_TEXT_CHARS = 6000
    }
}
