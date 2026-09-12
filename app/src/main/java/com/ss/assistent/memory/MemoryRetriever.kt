package com.ss.assistent.memory

import java.util.Locale

/**
 * Deterministic semantic-ish retrieval for local memories.
 *
 * This is intentionally not an embedding model: it is fast, offline, explainable, and adds
 * no network dependency. Retrieval combines exact terms, normalized word stems, phrase matches,
 * category hints, and a small multilingual synonym map. Sealed memories keep priority because
 * they are explicitly preserved by the user.
 */
object MemoryRetriever {
    data class ScoredMemory(
        val memory: MemoryEntry,
        val score: Int
    )

    fun rank(memories: List<MemoryEntry>, query: String): List<ScoredMemory> {
        if (memories.isEmpty()) return emptyList()

        val queryTokens = tokens(query)
        val queryText = normalizedText(query)
        if (queryTokens.isEmpty() && queryText.isEmpty()) {
            return memories.map { ScoredMemory(it, 0) }
        }

        return memories
            .map { memory -> memory to score(memory, queryTokens, queryText) }
            .filter { (_, score) -> score > 0 }
            .sortedWith(
                compareByDescending<Pair<MemoryEntry, Int>> { it.second }
                    .thenBy { if (it.first.lock == MemoryLock.SEALED) 0 else 1 }
                    .thenBy { it.first.id }
            )
            .map { ScoredMemory(it.first, it.second) }
    }

    private fun score(memory: MemoryEntry, queryTokens: Set<String>, queryText: String): Int {
        val memoryTokens = tokens(memory.text)
        val expandedMemory = expand(memoryTokens)
        val expandedQuery = expand(queryTokens)

        val exactOverlap = queryTokens.count { it in memoryTokens }
        val normalizedOverlap = expandedQuery.intersect(expandedMemory).size
        val phraseBonus = when {
            queryText.length >= 5 && normalizedText(memory.text).contains(queryText) -> 12
            queryText.length >= 8 && queryText.split(' ').take(3).joinToString(" ") in normalizedText(memory.text) -> 5
            else -> 0
        }
        val categoryOverlap = expandedQuery.intersect(tokens(memory.category.title)).size
        val sealedBoost = if (memory.lock == MemoryLock.SEALED) 2 else 0

        return exactOverlap * 5 + normalizedOverlap * 3 + categoryOverlap + phraseBonus + sealedBoost
    }

    private fun expand(tokens: Set<String>): Set<String> = buildSet {
        addAll(tokens)
        tokens.forEach { token ->
            SYNONYM_GROUPS.firstOrNull { token in it }?.let { addAll(it) }
            stem(token)?.let(::add)
        }
    }

    private fun normalizedText(text: String): String =
        tokens(text).joinToString(" ")

    private fun tokens(text: String): Set<String> = text
        .lowercase(Locale.ROOT)
        .split(Regex("[^\\p{L}\\p{Nd}]+"))
        .asSequence()
        .mapNotNull(::stem)
        .filter { it.length >= 2 }
        .toSet()

    /** Small suffix reducer, deliberately conservative to avoid changing names or canon text. */
    private fun stem(token: String): String? {
        val clean = token.trim()
        if (clean.isEmpty()) return null
        if (clean.length <= 3) return clean

        val candidates = listOf(
            "ern", "em", "er", "en", "es", "e", "n", "s",
            "ing", "ed", "ly"
        )
        val suffix = candidates.firstOrNull { clean.endsWith(it) && clean.length - it.length >= 3 }
        return if (suffix == null) clean else clean.removeSuffix(suffix)
    }

    private val SYNONYM_GROUPS = listOf(
        setOf("remember", "recall", "memory", "memories", "remembering", "merke", "erinnerung"),
        setOf("home", "house", "wohnung", "zuhause", "daheim"),
        setOf("school", "schule", "class", "unterricht", "lernen", "study"),
        setOf("work", "job", "arbeit", "beruf", "office", "büro"),
        setOf("friend", "friends", "freund", "freundin", "freundschaft"),
        setOf("family", "familie", "parent", "parents", "mutter", "vater", "geschwister"),
        setOf("project", "projekt", "app", "application", "projektarbeit"),
        setOf("story", "geschichte", "plot", "lore", "canon", "character", "charakter"),
        setOf("preference", "preferences", "prefer", "like", "likes", "mag", "liebt", "lieblings"),
        setOf("routine", "routines", "routine", "alltag", "täglich", "daily"),
        setOf("name", "names", "called", "heißt", "name"),
        setOf("birthday", "geburtstag", "born", "geboren"),
        setOf("height", "größe", "tall", "groß"),
        setOf("color", "colour", "farbe", "augenfarbe", "haare", "haarfarbe")
    )
}
