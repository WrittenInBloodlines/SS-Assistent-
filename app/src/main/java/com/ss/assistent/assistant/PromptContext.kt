package com.ss.assistent.assistant

import com.ss.assistent.memory.MemoryEntry
import com.ss.assistent.memory.MemoryLock
import com.ss.assistent.memory.MemoryRetriever

/**
 * Builds a compact prompt context for small local models.
 *
 * Retrieval stays deterministic and fully offline. MemoryRetriever provides semantic-ish
 * matching while this class remains responsible for strict prompt-size budgeting and the
 * preservation rules for sealed memories.
 */
object PromptContext {
    private const val MAX_MEMORY_ENTRIES = 6
    private const val MAX_MEMORY_CHARS = 2400
    private const val MAX_HISTORY_CHARS = 9000
    private const val MIN_RELEVANCE_SCORE = 3

    fun relevantMemories(memories: List<MemoryEntry>, query: String): List<MemoryEntry> {
        if (memories.isEmpty()) return emptyList()

        val sealed = memories.filter { it.lock == MemoryLock.SEALED }
        val ranked = MemoryRetriever.rank(memories, query)
            .filter { it.score >= MIN_RELEVANCE_SCORE }
            .map { it.memory }

        // Sealed memories remain protected from being displaced by topical editable memories.
        // For a normal query, however, unrelated sealed memories are not injected into the
        // prompt merely because they are sealed. They must also have a retrieval match.
        val relevantSealed = ranked.filter { it.lock == MemoryLock.SEALED }
        val relevantEditable = ranked.filter { it.lock != MemoryLock.SEALED }
        val ordered = (relevantSealed + relevantEditable).distinctBy { it.id }

        // If there is no lexical/semantic match, do not leak the entire memory store into the
        // model context. This keeps prompts small and prevents accidental unrelated context.
        if (ordered.isEmpty()) return emptyList()

        var used = 0
        return ordered.take(MAX_MEMORY_ENTRIES).filter { memory ->
            val cost = memory.text.length + memory.category.title.length + 5
            if (used + cost > MAX_MEMORY_CHARS) return@filter false
            used += cost
            true
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
            relevant.joinToString("\n") {
                val lockLabel = if (it.lock == MemoryLock.SEALED) " [SEALED EXACT]" else ""
                "- ${it.category.title}$lockLabel: ${it.text}"
            }
        }

        return buildString {
            append("You are SS Assistent, a local Android device assistant. ")
            append(styleInstruction)
            append("\n")
            append("Only use saved memories when they are relevant to the current request.\n")
            append("Relevant user-approved memories:\n")
            append(memoryText)
            append("\n")
            append("[SEALED EXACT] memories are user-preserved text. Do not rewrite, normalize, shorten, or reinterpret their wording when referring to them.\n")
            append("Never claim to have performed a device action unless the app actually reports that action as completed.")
        }
    }
}
