package com.ss.assistent.memory

/**
 * Detects explicit memory-management commands before they reach the local model.
 * Parsing is intentionally narrow: ambiguous natural language must remain ordinary chat.
 */
object MemoryIntentParser {
    data class ExactSaveRequest(val text: String)

    data class SealedReplacementRequest(
        val previousText: String,
        val replacementText: String
    )

    fun parse(input: String): ExactSaveRequest? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val flags = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        val separator = "\\s*[:\\-]?\\s*"
        val patterns = listOf(
            Regex("^save this exactly as written$separator(.+)$", flags),
            Regex("^save exactly as written$separator(.+)$", flags),
            Regex("^save this verbatim$separator(.+)$", flags),
            Regex("^remember this exactly$separator(.+)$", flags),
            Regex("^remember exactly$separator(.+)$", flags),
            Regex("^remember exactly as written$separator(.+)$", flags),
            Regex("^store this verbatim$separator(.+)$", flags),
            Regex("^store exactly as written$separator(.+)$", flags),
            Regex("^speichere das exakt wie geschrieben$separator(.+)$", flags),
            Regex("^speichere das genau so$separator(.+)$", flags),
            Regex("^speichere das wortgetreu$separator(.+)$", flags),
            Regex("^speichere exakt wie geschrieben$separator(.+)$", flags),
            Regex("^merke dir das exakt$separator(.+)$", flags)
        )

        val match = patterns.firstNotNullOfOrNull { it.find(trimmed) } ?: return null
        val payload = match.groupValues.getOrNull(1) ?: return null
        if (payload.isEmpty()) return null
        return ExactSaveRequest(payload)
    }

    /**
     * Parses only an explicit replacement command. Both the old and new values must be
     * present, and both values must be quoted. This prevents ordinary conversation from being
     * treated as a canon-changing command.
     *
     * Examples:
     * change sealed memory "Ciro has blue eyes" to "Ciro has dark brown eyes"
     * replace exact memory "old value" with "new value"
     * ändere versiegelte erinnerung "alt" zu "neu"
     */
    fun parseSealedReplacement(input: String): SealedReplacementRequest? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val flags = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        val patterns = listOf(
            Regex("^change sealed memory\\s+\\\"(.+)\\\"\\s+to\\s+\\\"(.+)\\\"$", flags),
            Regex("^replace sealed memory\\s+\\\"(.+)\\\"\\s+with\\s+\\\"(.+)\\\"$", flags),
            Regex("^change exact memory\\s+\\\"(.+)\\\"\\s+to\\s+\\\"(.+)\\\"$", flags),
            Regex("^replace exact memory\\s+\\\"(.+)\\\"\\s+with\\s+\\\"(.+)\\\"$", flags),
            Regex("^update sealed memory\\s+\\\"(.+)\\\"\\s+to\\s+\\\"(.+)\\\"$", flags),
            Regex("^update exact memory\\s+\\\"(.+)\\\"\\s+to\\s+\\\"(.+)\\\"$", flags),
            Regex("^ändere versiegelte erinnerung\\s+\\\"(.+)\\\"\\s+zu\\s+\\\"(.+)\\\"$", flags),
            Regex("^ersetze versiegelte erinnerung\\s+\\\"(.+)\\\"\\s+durch\\s+\\\"(.+)\\\"$", flags),
            Regex("^ändere exakte erinnerung\\s+\\\"(.+)\\\"\\s+zu\\s+\\\"(.+)\\\"$", flags),
            Regex("^aktualisiere versiegelte erinnerung\\s+\\\"(.+)\\\"\\s+zu\\s+\\\"(.+)\\\"$", flags)
        )

        val match = patterns.firstNotNullOfOrNull { it.matchEntire(trimmed) } ?: return null
        val previous = match.groupValues.getOrNull(1) ?: return null
        val replacement = match.groupValues.getOrNull(2) ?: return null
        if (previous.isEmpty() || replacement.isEmpty()) return null
        return SealedReplacementRequest(previous, replacement)
    }
}
