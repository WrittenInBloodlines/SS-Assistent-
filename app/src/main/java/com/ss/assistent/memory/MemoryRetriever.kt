package com.ss.assistent.memory

import java.util.Locale
import kotlin.math.sqrt

/** Fully local, deterministic memory retrieval. */
object MemoryRetriever {
    data class ScoredMemory(
        val memory: MemoryEntry,
        val score: Int
    )

    private const val VECTOR_DIMENSIONS = 128
    private const val VECTOR_WEIGHT = 18
    private const val VECTOR_MATCH_THRESHOLD = 0.12f

    fun rank(memories: List<MemoryEntry>, query: String): List<ScoredMemory> {
        if (memories.isEmpty()) return emptyList()

        val queryTokens = tokenize(query)
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
        val memoryTokens = tokenize(memory.text)
        val expandedMemory = expand(memoryTokens)
        val expandedQuery = expand(queryTokens)

        val exactOverlap = queryTokens.count { it in memoryTokens }
        val normalizedOverlap = expandedQuery.intersect(expandedMemory).size
        val memoryText = normalizedText(memory.text)
        val phraseBonus = when {
            queryText.length >= 5 && memoryText.contains(queryText) -> 12
            queryText.length >= 8 && queryText.split(' ').take(3).joinToString(" ") in memoryText -> 5
            else -> 0
        }
        val categoryOverlap = expandedQuery.intersect(tokenize(memory.category.title)).size
        val sealedBoost = if (memory.lock == MemoryLock.SEALED) 2 else 0
        val similarity = cosineSimilarity(queryVector, vectorize(memoryTokens))
        val vectorBonus = if (similarity >= VECTOR_MATCH_THRESHOLD) {
            (similarity * VECTOR_WEIGHT).toInt().coerceAtLeast(1)
        } else {
            0
        }

        return exactOverlap * 5 + normalizedOverlap * 3 + categoryOverlap + phraseBonus + sealedBoost + vectorBonus
    }

    private fun expand(input: Set<String>): Set<String> = buildSet {
        addAll(input)
        input.forEach { word ->
            SYNONYM_GROUPS.firstOrNull { word in it }?.let { addAll(it) }
            stem(word)?.let { add(it) }
        }
    }

    private fun vectorize(input: Set<String>): FloatArray {
        val vector = FloatArray(VECTOR_DIMENSIONS)
        if (input.isEmpty()) return vector

        input.forEach { word ->
            addHashedFeature(vector, "w:$word", 1f)
            if (word.length >= 3) {
                val padded = "_$word_"
                val lastStart = padded.length - 3
                for (start in 0..lastStart) {
                    addHashedFeature(vector, "c:${padded.substring(start, start + 3)}", 0.35f)
                }
            }
            SYNONYM_GROUPS.firstOrNull { word in it }?.forEach { synonym ->
                addHashedFeature(vector, "s:$synonym", 0.25f)
            }
        }

        var normSquared = 0.0
        vector.forEach { value -> normSquared += value * value }
        val norm = sqrt(normSquared).toFloat()
        if (norm > 0f) {
            for (index in vector.indices) vector[index] /= norm
        }
        return vector
    }

    private fun addHashedFeature(vector: FloatArray, feature: String, weight: Float) {
        val hash = feature.hashCode() and Int.MAX_VALUE
        val index = hash % VECTOR_DIMENSIONS
        val sign = if (((hash ushr 7) and 1) == 0) 1f else -1f
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

    private fun normalizedText(text: String): String = tokenize(text).joinToString(" ")

    private fun tokenize(text: String): Set<String> = text
        .lowercase(Locale.ROOT)
        .split(Regex("[^\\p{L}\\p{Nd}]+"))
        .asSequence()
        .mapNotNull { stem(it) }
        .filter { it.length >= 2 }
        .toSet()

    private fun stem(token: String): String? {
        val clean = token.trim()
        if (clean.isEmpty()) return null
        if (clean.length <= 3) return clean
        val suffixes = listOf("ern", "em", "er", "en", "es", "e", "n", "s", "ing", "ed", "ly")
        val suffix = suffixes.firstOrNull { clean.endsWith(it) && clean.length - it.length >= 3 }
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
