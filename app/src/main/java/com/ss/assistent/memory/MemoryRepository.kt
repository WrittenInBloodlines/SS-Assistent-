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
        fun fromKey(key: String?): MemoryCategory = values().firstOrNull { it.key == key } ?: FACT
    }
}

/** Explicit user-controlled memories. Nothing is added automatically yet. */
class MemoryRepository(context: Context) {
    private val preferences = context.getSharedPreferences("assistant_memory", Context.MODE_PRIVATE)

    fun getAll(): List<MemoryEntry> {
        val raw = preferences.getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        MemoryEntry(
                            id = item.getString("id"),
                            category = MemoryCategory.fromKey(item.getString("category")),
                            text = item.getString("text")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(category: MemoryCategory, text: String): MemoryEntry {
        val entry = MemoryEntry(category = category, text = text.trim())
        save(getAll() + entry)
        return entry
    }

    fun delete(id: String) {
        save(getAll().filterNot { it.id == id })
    }

    fun clear() {
        preferences.edit().remove(KEY_ENTRIES).apply()
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
    }
}
