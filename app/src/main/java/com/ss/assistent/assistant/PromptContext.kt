package com.ss.assistent.assistant

import com.ss.assistent.memory.MemoryEntry
import java.util.Locale

/**
 * Builds a compact prompt context for small local models.
 *
 * This deliberately uses deterministic local scoring instead of another model:
 * the assistant remains fully offline and predictable.
 */
object PromptContext {
    private const val MAX_MEMORY_ENTRIES = 6
    private const val MAX_MEMORY_CHARS = 2400
    private const val MAX_HISTORY_CHARS = 9000

    fun relevantMemories(memories: List<MemoryEntry>, query: String): List<MemoryEntry> {
        if (memories.isEmpty()) return emptyList()

        val queryTerms = terms(query)
        if (queryTerms.isEmpty()) return memories.take(MAX_MEMORY_ENTRIES)

        return memories
            .map { memory -> memory to score(memory, queryTerms) }
            .filter { (_, score) -> score > 0 }
            .sortedWith(
                compareByDescending<Pair<MemoryEntry, Int>> { it.second }
                    .thenBy { it.first.id }
            )
            .map { it.first }
            .take(MAX_MEMORY_ENTRIES)
            .let { selected ->
                var used = 0
                selected.filter { memory ->
                    val cost = memory.text.length + memory.category.title.length + 5
                    if (used + cost > MAX_MEMORY_CHARS) return@filter false
                    used += cost
                    true
                }
            }
    }

    fun recentConversation(messages: List<ChatMessage>): List<ChatMessage> {
        if (messages.isEmpty()) return emptyList()

        var used = 0
        val selected = ArrayDeque<ChatMessage>()
        messages.asReversed().forEach { message ->
            val cost = message.content.length + 24
            if (used + cost <= MAX_HISTORY_CHARS) {
                selected.addFirst(message)
                used += cost
            }
        }
        return selected.toList()
    }

    fun buildSystemPrompt(
        styleInstruction: String,
        memories: List<MemoryEntry>,
        query: String,
    ): String {
        val relevant = relevantMemories(memories, query)
        val memoryText = if (relevant.isEmpty()) {
            "No relevant saved memories are available."
        } else {
            relevant.joinToString("\n") { "- ${it.category.title}: ${it.text}" }
        }

        return buildString {
            append("You are SS Assistent, a local Android device assistant. ")
            append(styleInstruction)
            append("\n")
            append("Only use saved memories when they are relevant to the current request.\n")
            append("Relevant user-approved memories:\n")
            append(memoryText)
            append("\n")
            append("Never claim to have performed a device action unless the app actually reports that action as completed.")
        }
    }

    private fun score(memory: MemoryEntry, queryTerms: Set<String>): Int {
        val memoryTerms = terms(memory.text)
        val overlap = queryTerms.count { it in memoryTerms }
        val categoryTerms = terms(memory.category.title)
        val categoryBoost = queryTerms.count { it in categoryTerms }
        return overlap * 3 + categoryBoost
    }

    private fun terms(text: String): Set<String> = text
        .lowercase(Locale.ROOT)
        .split(Regex("[^\\p{L}\\p{Nd}]+"))
        .asSequence()
        .filter { it.length >= 3 }
        .toSet()
}
