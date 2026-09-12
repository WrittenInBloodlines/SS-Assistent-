package com.ss.assistent.memory

/** Detects explicit user requests to store text exactly as supplied. */
object MemoryIntentParser {
    data class ExactSaveRequest(val text: String)

    fun parse(input: String): ExactSaveRequest? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val patterns = listOf(
            Regex("^save this exactly as written\\s*[:\\-]?\\s*(.+)$", RegexOption.IGNORE_CASE or RegexOption.DOT_MATCHES_ALL),
            Regex("^remember this exactly\\s*[:\\-]?\\s*(.+)$", RegexOption.IGNORE_CASE or RegexOption.DOT_MATCHES_ALL),
            Regex("^remember exactly\\s*[:\\-]?\\s*(.+)$", RegexOption.IGNORE_CASE or RegexOption.DOT_MATCHES_ALL),
            Regex("^store this verbatim\\s*[:\\-]?\\s*(.+)$", RegexOption.IGNORE_CASE or RegexOption.DOT_MATCHES_ALL),
            Regex("^save exactly as written\\s*[:\\-]?\\s*(.+)$", RegexOption.IGNORE_CASE or RegexOption.DOT_MATCHES_ALL),
            Regex("^speichere das exakt wie geschrieben\\s*[:\\-]?\\s*(.+)$", RegexOption.IGNORE_CASE or RegexOption.DOT_MATCHES_ALL),
            Regex("^speichere das genau so\\s*[:\\-]?\\s*(.+)$", RegexOption.IGNORE_CASE or RegexOption.DOT_MATCHES_ALL)
        )

        val match = patterns.firstNotNullOfOrNull { it.find(trimmed) } ?: return null
        val payload = match.groupValues.getOrNull(1) ?: return null
        if (payload.isEmpty()) return null
        return ExactSaveRequest(payload)
    }
}
