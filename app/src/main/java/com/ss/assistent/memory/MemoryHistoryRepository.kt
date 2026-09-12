package com.ss.assistent.memory

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class MemoryVersion(
    val id: String = UUID.randomUUID().toString(),
    val memoryId: String,
    val category: MemoryCategory,
    val text: String,
    val savedAt: Long,
    val reason: String
)

/**
 * Local audit trail for sealed-memory replacements.
 * History is append-only from the UI perspective: changing the current sealed value
 * creates a new version entry instead of destroying the previous exact value.
 *
 * History is encrypted with the same Android Keystore-backed mechanism as current memories,
 * but uses a separate key alias so the two stores remain independently protected.
 */
class MemoryHistoryRepository(context: Context) {
    private val store = EncryptedMemoryStore(
        context = context,
        preferencesName = "assistant_memory_history",
        legacyKey = KEY_VERSIONS,
        keystoreAlias = "ss_assistent_memory_history_v1"
    )

    fun getForMemory(memoryId: String): List<MemoryVersion> {
        val raw = store.read() ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    if (item.optString("memoryId") == memoryId) {
                        add(
                            MemoryVersion(
                                id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                                memoryId = memoryId,
                                category = MemoryCategory.fromKey(item.optString("category")),
                                text = item.optString("text"),
                                savedAt = item.optLong("savedAt"),
                                reason = item.optString("reason").ifBlank { "Sealed memory replacement" }
                            )
                        )
                    }
                }
            }.sortedByDescending { it.savedAt }
        }.getOrDefault(emptyList())
    }

    fun append(version: MemoryVersion) {
        val existing = getAll()
        save(existing + version)
    }

    fun clearMemory(memoryId: String) {
        save(getAll().filterNot { it.memoryId == memoryId })
    }

    fun clearAll() {
        store.clear()
    }

    private fun getAll(): List<MemoryVersion> {
        val raw = store.read() ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val memoryId = item.optString("memoryId")
                    val text = item.optString("text")
                    if (memoryId.isNotEmpty() && text.isNotEmpty()) {
                        add(
                            MemoryVersion(
                                id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                                memoryId = memoryId,
                                category = MemoryCategory.fromKey(item.optString("category")),
                                text = text,
                                savedAt = item.optLong("savedAt"),
                                reason = item.optString("reason").ifBlank { "Sealed memory replacement" }
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun save(versions: List<MemoryVersion>) {
        val array = JSONArray()
        versions.forEach { version ->
            array.put(JSONObject().apply {
                put("id", version.id)
                put("memoryId", version.memoryId)
                put("category", version.category.key)
                put("text", version.text)
                put("savedAt", version.savedAt)
                put("reason", version.reason)
            })
        }
        check(store.write(array.toString())) { "Unable to securely persist memory history" }
    }

    companion object {
        private const val KEY_VERSIONS = "versions"
    }
}
