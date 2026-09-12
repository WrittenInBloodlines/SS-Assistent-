package com.ss.assistent.memory

/** Detects explicit user requests to store text exactly as supplied. */
object MemoryIntentParser {
    data class ExactSaveRequest(val text: String)

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
}
