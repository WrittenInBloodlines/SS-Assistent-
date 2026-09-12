package com.ss.assistent.memory

import java.util.Locale
import kotlin.math.sqrt

/**
 * Fully local memory retrieval.
 *
 * Retrieval deliberately does not require a second neural model. It combines lexical matching
 * with a small deterministic vector representation built from word features, character n-grams,
 * conservative stemming, and the local synonym map. This gives fuzzy/semantic-ish recall while
 * keeping memory retrieval fast, explainable, offline, and private on-device.
 */
object MemoryRetriever {
    data class ScoredMemory(
        val memory: MemoryEntry,
        val score: Int
    )

    private const val VECTOR_DIMENSIONS = 128
    private const val VECTOR_WEIGHT = 18
    private const val VECTOR_MATCH_THRESHOLD = 0.12

    fun rank(memories: List<MemoryEntry>, query: String): List<ScoredMemory> {
        if (memories.isEmpty()) return emptyList()

        val queryTokens = tokens(query)
        val queryText = normalizedText(query)
        if (queryTokens.isEmpty() && queryText.isEmpty()) {
            return memories.map { ScoredMemory(it, 0) }
        }

        val queryVector = vectorize(queryTokens)

        return memories
            .map { memory -> memory to score(memory, queryTokens, queryText, queryVector) }
            .filter { (_, score) -> score > 0 }
            .sortedWith(
                compareByDescending<Pair<MemoryEntry, Int>> { it.second }
                    .thenBy { if (it.first.lock == MemoryLock.SEALED) 0 else 1 }
                    .thenBy { it.first.id }
            )
            .map { ScoredMemory(it.first, it.second) }
    }

    private fun score(
        memory: MemoryEntry,
        queryTokens: Set<String>,
        queryText: String,
        queryVector: FloatArray
    ): Int {
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

        val vectorSimilarity = cosineSimilarity(queryVector, vectorize(memoryTokens))
        val vectorBonus = if (vectorSimilarity >= VECTOR_MATCH_THRESHOLD) {
            (vectorSimilarity * VECTOR_WEIGHT).toInt().coerceAtLeast(1)
        } else {
            0
        }

        return exactOverlap * 5 + normalizedOverlap * 3 + categoryOverlap + phraseBonus + sealedBoost + vectorBonus
    }

    private fun expand(tokens: Set<String>): Set<String> = buildSet {
        addAll(tokens)
        tokens.forEach { token ->
            SYNONYM_GROUPS.firstOrNull { token in it }?.let { addAll(it) }
            stem(token)?.let(::add)
        }
    }

    /**
     * Creates a tiny deterministic vector from token features. This is intentionally not a
     * pretend neural embedding: it is a stable feature vector that provides fuzzy retrieval
     * without shipping another model or sending memory data anywhere.
     */
    private fun vectorize(tokenSet: Set<String>): FloatArray {
        val vector = FloatArray(VECTOR_DIMENSIONS)
        if (tokenSet.isEmpty()) return vector

        tokenSet.forEach { token ->
            addHashedFeature(vector, "w:$token", 1f)

            if (token.length >= 3) {
                val padded = "_$token_"
                for (index in 0..padded.length - 3) {
                    addHashedFeature(vector, "c:${padded.substring(index, index + 3)}", 0.35f)
                }
            }

            SYNONYM_GROUPS.firstOrNull { token in it }?.forEach { synonym ->
                addHashedFeature(vector, "s:$synonym", 0.25f)
            }
        }

        var norm = 0.0
        vector.forEach { value -> norm += value * value }
        val length = sqrt(norm).toFloat()
        if (length > 0f) {
            for (index in vector.indices) vector[index] /= length
        }
        return vector
    }

    private fun addHashedFeature(vector: FloatArray, feature: String, weight: Float) {
        val hash = feature.hashCode() and Int.MAX_VALUE
        val index = hash % VECTOR_DIMENSIONS
        val sign = if ((hash ushr 7 and 1) == 0) 1f else -1f
        vector[index] += sign * weight
    }

    private fun cosineSimilarity(first: FloatArray, second: FloatArray): Float {
        if (first.size != second.size) return 0f
        var dot = 0f
        var firstNorm = 0f
        var secondNorm = 0f
        for (index in first.indices) {
            dot += first[index] * second[index]
            firstNorm += first[index] * first[index]
            secondNorm += second[index] * second[index]
        }
        if (firstNorm <= 0f || secondNorm <= 0f) return 0f
        return (dot / (sqrt(firstNorm) * sqrt(secondNorm))).coerceIn(-1f, 1f)
    }

    private fun normalizedText(text: String): String = tokens(text).joinToString(" ")

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
        setOf("routine", "routines", "alltag", "täglich", "daily"),
        setOf("name", "names", "called", "heißt"),
        setOf("birthday", "geburtstag", "born", "geboren"),
        setOf("height", "größe", "tall", "groß"),
        setOf("color", "colour", "farbe", "augenfarbe", "haare", "haarfarbe")
    )
}
