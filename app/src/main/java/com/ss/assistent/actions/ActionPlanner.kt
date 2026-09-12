package com.ss.assistent.actions

/**
 * Deterministic action proposal layer. It never executes an Android action.
 * The language model can eventually feed this planner, while Android remains
 * responsible for validating capabilities and asking for confirmation.
 */
class ActionPlanner {
    fun plan(request: String): List<PlannedAction> {
        val text = request.trim()
        if (text.isBlank()) return emptyList()

        val normalized = text.lowercase()
        val actions = mutableListOf<PlannedAction>()

        knownApps.firstOrNull { normalized.contains(it.trigger) }?.let { app ->
            actions += PlannedAction(
                type = ActionType.OPEN_APP,
                title = "Open ${app.label}",
                description = "Open the installed ${app.label} app if Android provides a matching launch intent.",
                capability = "open_app",
                risk = ActionRisk.LOW,
                arguments = mapOf("package" to app.packageName, "label" to app.label)
            )
        }

        if (normalized.contains("google docs") || normalized.contains("docs") || normalized.contains("document app")) {
            actions += PlannedAction(
                type = ActionType.OPEN_DOCUMENT,
                title = "Open a document app",
                description = "Open a supported document application. The planner does not choose or modify a document automatically.",
                capability = "open_document_app",
                risk = ActionRisk.LOW
            )
        }

        if (normalized.startsWith("draft ") || normalized.contains("draft a reply") || normalized.contains("prepare a reply")) {
            actions += PlannedAction(
                type = ActionType.DRAFT_REPLY,
                title = "Prepare a reply draft",
                description = "Prepare text for review. Sending is never part of generation and requires a separate explicit action.",
                capability = "draft_reply",
                risk = ActionRisk.MEDIUM,
                arguments = mapOf("request" to text)
            )
        }

        if (normalized.contains("insert text") || normalized.contains("type this") || normalized.contains("put this in the text field")) {
            actions += PlannedAction(
                type = ActionType.INSERT_TEXT,
                title = "Insert text into the current field",
                description = "Insert prepared text into a compatible focused text field without submitting it.",
                capability = "insert_text",
                risk = ActionRisk.MEDIUM,
                arguments = mapOf("request" to text)
            )
        }

        if (normalized.contains("share this") || normalized.contains("share content")) {
            actions += PlannedAction(
                type = ActionType.SHARE_CONTENT,
                title = "Prepare content for sharing",
                description = "Open the Android share flow for content the user explicitly selected.",
                capability = "share_content",
                risk = ActionRisk.MEDIUM
            )
        }

        return actions.distinctBy { it.type to it.arguments["package"] }
    }

    private data class KnownApp(val trigger: String, val label: String, val packageName: String)

    private val knownApps = listOf(
        KnownApp("whatsapp", "WhatsApp", "com.whatsapp"),
        KnownApp("tiktok", "TikTok", "com.zhiliaoapp.musically"),
        KnownApp("youtube", "YouTube", "com.google.android.youtube"),
        KnownApp("chrome", "Chrome", "com.android.chrome")
    )
}
