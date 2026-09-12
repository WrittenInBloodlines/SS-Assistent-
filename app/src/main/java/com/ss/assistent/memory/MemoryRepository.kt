package com.ss.assistent.memory

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class MemoryLock {
    /** Normal memories may be edited by the user. */
    EDITABLE,

    /** Exact user-provided text is preserved until the user explicitly changes it. */
    SEALED
}

data class MemoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val category: MemoryCategory,
    val text: String,
    val lock: MemoryLock = MemoryLock.EDITABLE
)

enum class MemoryCategory(val key: String, val title: String) {
    PREFERENCE("preference", "Preferences"),
    PROJECT("project", "Projects"),
    PERSON("person", "People"),
    ROUTINE("routine", "Routines"),
    FACT("fact", "Facts");

    companion object {
        fun fromKey(key: String?): MemoryCategory =
            values().firstOrNull { it.key == key } ?: FACT
    }
}

/** Explicit user-controlled memories. Nothing is added automatically. */
class MemoryRepository(context: Context) {
    private val preferences = context.getSharedPreferences("assistant_memory", Context.MODE_PRIVATE)
    private val historyRepository = MemoryHistoryRepository(context)

    fun getAll(): List<MemoryEntry> {
        val raw = preferences.getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val text = item.optString("text")
                    if (text.isNotEmpty()) {
                        add(
                            MemoryEntry(
                                id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                                category = MemoryCategory.fromKey(item.optString("category")),
                                text = text,
                                lock = if (item.optString("lock") == MemoryLock.SEALED.name) MemoryLock.SEALED else MemoryLock.EDITABLE
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    /** Adds a normal memory after applying normal length and whitespace cleanup. */
    fun add(category: MemoryCategory, text: String): MemoryEntry? {
        val cleanText = normalize(text) ?: return null
        return addInternal(category, cleanText, MemoryLock.EDITABLE)
    }

    /**
     * Adds an exact memory. The supplied text is intentionally not trimmed, collapsed,
     * shortened, or otherwise transformed. This is the storage primitive for an explicit
     * "save this exactly as written" request.
     */
    fun addExact(category: MemoryCategory, exactText: String): MemoryEntry? {
        if (exactText.isEmpty() || exactText.length > MAX_EXACT_MEMORY_TEXT_CHARS) return null
        return addInternal(category, exactText, MemoryLock.SEALED)
    }

    fun update(id: String, category: MemoryCategory, text: String): MemoryEntry? {
        val existing = getAll().firstOrNull { it.id == id } ?: return null
        if (existing.lock == MemoryLock.SEALED) {
            return updateSealed(id, category, text, explicitOverride = true)
        }

        val cleanText = normalize(text) ?: return null
        if (getAll().any {
                it.id != id &&
                    it.category == category &&
                    it.text.equals(cleanText, ignoreCase = true)
            }) {
            return null
        }

        return replaceEntry(
            id,
            MemoryEntry(id = id, category = category, text = cleanText, lock = MemoryLock.EDITABLE)
        )
    }

    /**
     * Updates a sealed memory only through an explicit replacement operation.
     * No normalization is applied, so the replacement becomes the new exact sealed value.
     * The previous exact value is recorded before replacement.
     */
    fun updateSealed(
        id: String,
        category: MemoryCategory,
        exactReplacement: String,
        explicitOverride: Boolean
    ): MemoryEntry? {
        if (!explicitOverride || exactReplacement.isEmpty() || exactReplacement.length > MAX_EXACT_MEMORY_TEXT_CHARS) return null
        val existing = getAll().firstOrNull { it.id == id } ?: return null
        if (existing.lock != MemoryLock.SEALED) return null
        if (existing.category == category && existing.text == exactReplacement) return existing
        if (getAll().any {
                it.id != id &&
                    it.category == category &&
                    it.text == exactReplacement
            }) {
            return null
        }

        historyRepository.append(
            MemoryVersion(
                memoryId = existing.id,
                category = existing.category,
                text = existing.text,
                savedAt = System.currentTimeMillis(),
                reason = "Before explicit sealed replacement"
            )
        )

        return replaceEntry(
            id,
            MemoryEntry(id = id, category = category, text = exactReplacement, lock = MemoryLock.SEALED)
        )
    }

    fun getHistory(id: String): List<MemoryVersion> = historyRepository.getForMemory(id)

    fun delete(id: String) {
        save(getAll().filterNot { it.id == id })
        historyRepository.clearMemory(id)
    }

    fun clearCategory(category: MemoryCategory) {
        val removedIds = getAll().filter { it.category == category }.map { it.id }
        save(getAll().filterNot { it.category == category })
        removedIds.forEach(historyRepository::clearMemory)
    }

    fun count(category: MemoryCategory? = null): Int =
        if (category == null) getAll().size else getAll().count { it.category == category }

    fun count(lock: MemoryLock): Int = getAll().count { it.lock == lock }

    fun clear() {
        preferences.edit().remove(KEY_ENTRIES).apply()
        historyRepository.clearAll()
    }

    private fun addInternal(category: MemoryCategory, text: String, lock: MemoryLock): MemoryEntry? {
        val existing = getAll()
        if (existing.any { it.category == category && it.text == text }) return null
        val entry = MemoryEntry(category = category, text = text, lock = lock)
        save(existing + entry)
        return entry
    }

    private fun replaceEntry(id: String, replacement: MemoryEntry): MemoryEntry? {
        val existing = getAll()
        if (existing.none { it.id == id }) return null
        save(existing.map { if (it.id == id) replacement else it })
        return replacement
    }

    private fun normalize(text: String): String? {
        val clean = text.trim().replace(Regex("\\s+"), " ")
        return clean.take(MAX_NORMAL_MEMORY_TEXT_CHARS).takeIf { it.isNotEmpty() }
    }

    private fun save(entries: List<MemoryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("category", entry.category.key)
                put("text", entry.text)
                put("lock", entry.lock.name)
            })
        }
        preferences.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    companion object {
        private const val KEY_ENTRIES = "entries"
        private const val MAX_NORMAL_MEMORY_TEXT_CHARS = 600
        private const val MAX_EXACT_MEMORY_TEXT_CHARS = 6000
    }
}
