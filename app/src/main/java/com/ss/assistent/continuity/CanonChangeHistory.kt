package com.ss.assistent.continuity

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Local audit trail for intentional canon changes.
 * History is append-only from the feature's point of view: changing canon never erases the old value.
 */
data class CanonChange(
    val id: String = UUID.randomUUID().toString(),
    val factId: String,
    val subject: String,
    val attribute: String,
    val oldValue: String,
    val newValue: String,
    val changedAt: Long = System.currentTimeMillis()
) {
    fun displayTime(): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(changedAt))
}

class CanonChangeHistory(context: Context) {
    private val preferences = context.getSharedPreferences("assistant_canon_history", Context.MODE_PRIVATE)

    fun getChanges(): List<CanonChange> {
        val raw = preferences.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val factId = item.optString("factId")
                    val subject = item.optString("subject")
                    val attribute = item.optString("attribute")
                    val oldValue = item.optString("oldValue")
                    val newValue = item.optString("newValue")
                    if (factId.isNotBlank() && subject.isNotBlank() && attribute.isNotBlank()) {
                        add(
                            CanonChange(
                                id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                                factId = factId,
                                subject = subject,
                                attribute = attribute,
                                oldValue = oldValue,
                                newValue = newValue,
                                changedAt = item.optLong("changedAt", System.currentTimeMillis())
                            )
                        )
                    }
                }
            }.sortedByDescending { it.changedAt }
        }.getOrDefault(emptyList())
    }

    fun record(change: CanonChange) {
        val array = JSONArray()
        (getChanges() + change).forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("factId", item.factId)
                    put("subject", item.subject)
                    put("attribute", item.attribute)
                    put("oldValue", item.oldValue)
                    put("newValue", item.newValue)
                    put("changedAt", item.changedAt)
                }
            )
        }
        preferences.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    companion object {
        private const val KEY_HISTORY = "changes"
    }
}
