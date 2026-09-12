package com.ss.assistent.actions

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ActionHistoryRepository(context: Context) {
    private val preferences = context.getSharedPreferences("action_history", Context.MODE_PRIVATE)

    fun load(): List<ActionHistoryEntry> {
        val raw = preferences.getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val args = mutableMapOf<String, String>()
                    item.optJSONObject("arguments")?.let { objectValue ->
                        objectValue.keys().forEach { key -> args[key] = objectValue.optString(key) }
                    }
                    val action = PlannedAction(
                        id = item.optString("actionId"),
                        type = runCatching { ActionType.valueOf(item.optString("type")) }.getOrDefault(ActionType.UNKNOWN),
                        title = item.optString("title"),
                        description = item.optString("description"),
                        capability = item.optString("capability"),
                        risk = runCatching { ActionRisk.valueOf(item.optString("risk")) }.getOrDefault(ActionRisk.LOW),
                        requiresConfirmation = item.optBoolean("requiresConfirmation", true),
                        arguments = args
                    )
                    add(ActionHistoryEntry(
                        id = item.optString("id"),
                        timestamp = item.optLong("timestamp", System.currentTimeMillis()),
                        action = action,
                        result = item.optString("result")
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun record(action: PlannedAction, result: String) {
        val entries = (listOf(ActionHistoryEntry(action = action, result = result)) + load()).take(MAX_ENTRIES)
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("timestamp", entry.timestamp)
                put("actionId", entry.action.id)
                put("type", entry.action.type.name)
                put("title", entry.action.title)
                put("description", entry.action.description)
                put("capability", entry.action.capability)
                put("risk", entry.action.risk.name)
                put("requiresConfirmation", entry.action.requiresConfirmation)
                put("result", entry.result)
                put("arguments", JSONObject(entry.action.arguments))
            })
        }
        preferences.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_ENTRIES).apply()
    }

    companion object {
        private const val KEY_ENTRIES = "entries"
        private const val MAX_ENTRIES = 100
    }
}
