package com.ss.assistent.actions

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class ActionType(val title: String) {
    OPEN_APP("Open app"),
    OPEN_DOCUMENT("Open document app"),
    INSERT_TEXT("Insert text"),
    DRAFT_REPLY("Draft reply"),
    SHARE_CONTENT("Share content"),
    UNKNOWN("Unknown action")
}

enum class ActionRisk(val title: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High")
}

data class PlannedAction(
    val id: String = UUID.randomUUID().toString(),
    val type: ActionType,
    val title: String,
    val description: String,
    val capability: String,
    val risk: ActionRisk = ActionRisk.LOW,
    val requiresConfirmation: Boolean = true,
    val arguments: Map<String, String> = emptyMap()
)

data class ActionHistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val action: PlannedAction,
    val result: String
) {
    fun formattedTimestamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
