package com.ss.assistent.memory

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class MemoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val category: MemoryCategory,
    val text: String
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

    fun getAll(): List<MemoryEntry> {
        val raw = preferences.getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val text = item.optString("text").trim()
                    if (text.isNotEmpty()) {
                        add(
                            MemoryEntry(
                                id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                                category = MemoryCategory.fromKey(item.optString("category")),
                                text = text
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(category: MemoryCategory, text: String): MemoryEntry? {
        val cleanText = normalize(text) ?: return null
        val existing = getAll()
        if (existing.any { it.category == category && it.text.equals(cleanText, ignoreCase = true) }) {
            return null
        }
        val entry = MemoryEntry(category = category, text = cleanText)
        save(existing + entry)
        return entry
    }

    fun update(id: String, category: MemoryCategory, text: String): MemoryEntry? {
        val cleanText = normalize(text) ?: return null
        val existing = getAll()
        if (existing.any {
                it.id != id &&
                    it.category == category &&
                    it.text.equals(cleanText, ignoreCase = true)
            }) {
            return null
        }

        var updated: MemoryEntry? = null
        val result = existing.map { entry ->
            if (entry.id == id) {
                MemoryEntry(id = id, category = category, text = cleanText).also { updated = it }
            } else {
                entry
            }
        }
        if (updated != null) save(result)
        return updated
    }

    fun delete(id: String) {
        save(getAll().filterNot { it.id == id })
    }

    fun clearCategory(category: MemoryCategory) {
        save(getAll().filterNot { it.category == category })
    }

    fun count(category: MemoryCategory? = null): Int =
        if (category == null) getAll().size else getAll().count { it.category == category }

    fun clear() {
        preferences.edit().remove(KEY_ENTRIES).apply()
    }

    private fun normalize(text: String): String? {
        val clean = text.trim().replace(Regex("\\s+"), " ")
        return clean.take(MAX_MEMORY_TEXT_CHARS).takeIf { it.isNotEmpty() }
    }

    private fun save(entries: List<MemoryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("category", entry.category.key)
                put("text", entry.text)
            })
        }
        preferences.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    companion object {
        private const val KEY_ENTRIES = "entries"
        private const val MAX_MEMORY_TEXT_CHARS = 600
    }
}
