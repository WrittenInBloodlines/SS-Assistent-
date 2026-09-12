package com.ss.assistent.continuity

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class WarningType { PLOT_HOLE, LORE_CONFLICT, SECRET_LEAK }
enum class WarningState { OPEN, IGNORED, RESOLVED }

data class LoreFact(
    val id: String = UUID.randomUUID().toString(),
    val subject: String,
    val attribute: String,
    val value: String
)

data class SecretFact(
    val id: String = UUID.randomUUID().toString(),
    val subject: String,
    val secret: String,
    val knownBy: List<String> = emptyList()
)

data class ContinuityWarning(
    val id: String = UUID.randomUUID().toString(),
    val type: WarningType,
    val title: String,
    val details: String,
    val suggestion: String,
    val state: WarningState = WarningState.OPEN,
    val relatedLoreFactId: String? = null,
    val relatedSecretId: String? = null
)

/** Local, user-controlled story canon. It never silently changes facts or secrets. */
class ContinuityRepository(context: Context) {
    private val preferences = context.getSharedPreferences("assistant_continuity", Context.MODE_PRIVATE)
    private val canonHistory = CanonChangeHistory(context)

    fun getLoreFacts(): List<LoreFact> = readArray(KEY_LORE).mapNotNull { item ->
        val subject = item.optString("subject")
        val attribute = item.optString("attribute")
        val value = item.optString("value")
        if (subject.isBlank() || attribute.isBlank() || value.isBlank()) null
        else LoreFact(item.optString("id").ifBlank { UUID.randomUUID().toString() }, subject, attribute, value)
    }

    fun addLoreFact(subject: String, attribute: String, value: String): LoreFact? {
        val cleanSubject = subject.trim()
        val cleanAttribute = attribute.trim()
        val cleanValue = value.trim()
        if (cleanSubject.isEmpty() || cleanAttribute.isEmpty() || cleanValue.isEmpty()) return null
        val existing = getLoreFacts()
        if (existing.any { it.subject.equals(cleanSubject, true) && it.attribute.equals(cleanAttribute, true) }) return null
        return LoreFact(subject = cleanSubject, attribute = cleanAttribute, value = cleanValue).also {
            writeArray(KEY_LORE, existing + it) { fact -> loreJson(fact) }
        }
    }

    fun replaceLoreFact(id: String, value: String): LoreFact? {
        val replacement = value.trim()
        if (replacement.isEmpty()) return null
        val existing = getLoreFacts()
        val old = existing.firstOrNull { it.id == id } ?: return null
        if (old.value == replacement) return old
        val updated = old.copy(value = replacement)
        writeArray(KEY_LORE, existing.map { if (it.id == id) updated else it }) { fact -> loreJson(fact) }
        canonHistory.record(CanonChange(old.id, old.subject, old.attribute, old.value, updated.value))
        return updated
    }

    fun getCanonHistory(): List<CanonChange> = canonHistory.getChanges()

    fun getSecrets(): List<SecretFact> = readArray(KEY_SECRETS).mapNotNull { item ->
        val subject = item.optString("subject")
        val secret = item.optString("secret")
        if (subject.isBlank() || secret.isBlank()) null
        else SecretFact(
            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
            subject = subject,
            secret = secret,
            knownBy = item.optJSONArray("knownBy")?.let { array ->
                (0 until array.length()).map { array.optString(it) }.filter(String::isNotBlank)
            } ?: emptyList()
        )
    }

    fun addSecret(subject: String, secret: String, knownBy: List<String>): SecretFact? {
        val cleanSubject = subject.trim()
        val cleanSecret = secret.trim()
        if (cleanSubject.isEmpty() || cleanSecret.isEmpty()) return null
        val existing = getSecrets()
        if (existing.any { it.subject.equals(cleanSubject, true) && it.secret.equals(cleanSecret, true) }) return null
        return SecretFact(
            subject = cleanSubject,
            secret = cleanSecret,
            knownBy = normalizeNames(knownBy)
        ).also { saveSecrets(existing + it) }
    }

    fun replaceSecret(id: String, subject: String, secret: String, knownBy: List<String>): SecretFact? {
        val cleanSubject = subject.trim()
        val cleanSecret = secret.trim()
        if (cleanSubject.isEmpty() || cleanSecret.isEmpty()) return null
        val existing = getSecrets()
        val old = existing.firstOrNull { it.id == id } ?: return null
        val updated = old.copy(subject = cleanSubject, secret = cleanSecret, knownBy = normalizeNames(knownBy))
        if (old == updated) return old
        saveSecrets(existing.map { if (it.id == id) updated else it })
        return updated
    }

    fun getTimelines(): List<TimelineScene> = readArray(KEY_TIMELINE)
        .mapNotNull { item ->
            val title = item.optString("title")
            val location = item.optString("location")
            if (title.isBlank() || location.isBlank()) null else TimelineScene(
                id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                title = title,
                order = item.optInt("order", 0),
                location = location,
                characterIds = item.optJSONArray("characterIds")?.let { a ->
                    (0 until a.length()).map { a.optString(it) }.filter(String::isNotBlank)
                } ?: emptyList(),
                notes = item.optString("notes")
            )
        }.sortedWith(compareBy<TimelineScene> { it.order }.thenBy { it.title.lowercase() })

    fun addTimelineScene(title: String, order: Int, location: String, characterIds: List<String> = emptyList(), notes: String = ""): TimelineScene? {
        val cleanTitle = title.trim()
        val cleanLocation = location.trim()
        if (cleanTitle.isEmpty() || cleanLocation.isEmpty()) return null
        val existing = getTimelines()
        if (existing.any { it.title.equals(cleanTitle, true) && it.order == order }) return null
        return TimelineScene(
            id = UUID.randomUUID().toString(),
            title = cleanTitle,
            order = order,
            location = cleanLocation,
            characterIds = normalizeNames(characterIds),
            notes = notes.trim()
        ).also { saveTimelines(existing + it) }
    }

    fun removeTimelineScene(id: String): Boolean {
        val existing = getTimelines()
        if (existing.none { it.id == id }) return false
        saveTimelines(existing.filterNot { it.id == id })
        return true
    }

    fun getKnowledgeRules(): List<CharacterKnowledgeRule> = readArray(KEY_KNOWLEDGE_RULES).mapNotNull { item ->
        val characterId = item.optString("characterId")
        val characterName = item.optString("characterName")
        val secretId = item.optString("secretId")
        if (characterId.isBlank() || characterName.isBlank() || secretId.isBlank()) null else CharacterKnowledgeRule(
            characterId = characterId,
            characterName = characterName,
            secretId = secretId,
            discoverySceneOrder = if (item.has("discoverySceneOrder") && !item.isNull("discoverySceneOrder")) item.optInt("discoverySceneOrder") else null,
            allowNarratedKnowledge = item.optBoolean("allowNarratedKnowledge", true),
            allowExplicitReveal = item.optBoolean("allowExplicitReveal", true)
        )
    }

    fun upsertKnowledgeRule(rule: CharacterKnowledgeRule): CharacterKnowledgeRule? {
        if (rule.characterId.isBlank() || rule.characterName.isBlank() || rule.secretId.isBlank()) return null
        val updated = getKnowledgeRules().filterNot { it.characterId == rule.characterId && it.secretId == rule.secretId } + rule
        saveKnowledgeRules(updated)
        return rule
    }

    fun removeKnowledgeRule(characterId: String, secretId: String): Boolean {
        val existing = getKnowledgeRules()
        val filtered = existing.filterNot { it.characterId == characterId && it.secretId == secretId }
        if (filtered.size == existing.size) return false
        saveKnowledgeRules(filtered)
        return true
    }

    fun getWarnings(includeClosed: Boolean = true): List<ContinuityWarning> = readArray(KEY_WARNINGS).mapNotNull { item ->
        runCatching {
            ContinuityWarning(
                id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                type = WarningType.valueOf(item.optString("type")),
                title = item.optString("title"),
                details = item.optString("details"),
                suggestion = item.optString("suggestion"),
                state = runCatching { WarningState.valueOf(item.optString("state")) }.getOrDefault(WarningState.OPEN),
                relatedLoreFactId = item.optString("relatedLoreFactId").ifBlank { null },
                relatedSecretId = item.optString("relatedSecretId").ifBlank { null }
            )
        }.getOrNull()
    }.filter { includeClosed || it.state == WarningState.OPEN }

    fun addWarnings(newWarnings: List<ContinuityWarning>) {
        if (newWarnings.isEmpty()) return
        val existing = getWarnings()
        val deduped = (existing + newWarnings).distinctBy {
            "${it.type}|${it.title}|${it.details}|${it.relatedLoreFactId}|${it.relatedSecretId}"
        }
        saveWarnings(deduped)
    }

    fun setWarningState(id: String, state: WarningState) {
        saveWarnings(getWarnings().map { if (it.id == id) it.copy(state = state) else it })
    }

    fun clearWarnings() = preferences.edit().remove(KEY_WARNINGS).apply()

    private fun saveSecrets(entries: List<SecretFact>) = writeArray(KEY_SECRETS, entries) { secret ->
        JSONObject().apply { put("id", secret.id); put("subject", secret.subject); put("secret", secret.secret); put("knownBy", JSONArray(secret.knownBy)) }
    }

    private fun saveTimelines(entries: List<TimelineScene>) = writeArray(KEY_TIMELINE, entries) { scene ->
        JSONObject().apply {
            put("id", scene.id); put("title", scene.title); put("order", scene.order); put("location", scene.location)
            put("characterIds", JSONArray(scene.characterIds)); put("notes", scene.notes)
        }
    }

    private fun saveKnowledgeRules(entries: List<CharacterKnowledgeRule>) = writeArray(KEY_KNOWLEDGE_RULES, entries) { rule ->
        JSONObject().apply {
            put("characterId", rule.characterId); put("characterName", rule.characterName); put("secretId", rule.secretId)
            rule.discoverySceneOrder?.let { put("discoverySceneOrder", it) }
            put("allowNarratedKnowledge", rule.allowNarratedKnowledge); put("allowExplicitReveal", rule.allowExplicitReveal)
        }
    }

    private fun saveWarnings(entries: List<ContinuityWarning>) = writeArray(KEY_WARNINGS, entries) { warning ->
        JSONObject().apply {
            put("id", warning.id); put("type", warning.type.name); put("title", warning.title); put("details", warning.details)
            put("suggestion", warning.suggestion); put("state", warning.state.name)
            warning.relatedLoreFactId?.let { put("relatedLoreFactId", it) }
            warning.relatedSecretId?.let { put("relatedSecretId", it) }
        }
    }

    private fun loreJson(fact: LoreFact) = JSONObject().apply {
        put("id", fact.id); put("subject", fact.subject); put("attribute", fact.attribute); put("value", fact.value)
    }

    private fun normalizeNames(names: List<String>): List<String> = names.map(String::trim).filter(String::isNotEmpty).distinctBy(String::lowercase)

    private fun readArray(key: String): List<JSONObject> {
        val raw = preferences.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList { for (i in 0 until array.length()) add(array.getJSONObject(i)) }
        }.getOrDefault(emptyList())
    }

    private fun <T> writeArray(key: String, entries: List<T>, mapper: (T) -> JSONObject) {
        val array = JSONArray()
        entries.forEach { array.put(mapper(it)) }
        preferences.edit().putString(key, array.toString()).apply()
    }

    companion object {
        private const val KEY_LORE = "lore"
        private const val KEY_SECRETS = "secrets"
        private const val KEY_WARNINGS = "warnings"
        private const val KEY_TIMELINE = "timeline"
        private const val KEY_KNOWLEDGE_RULES = "knowledge_rules"
    }
}
