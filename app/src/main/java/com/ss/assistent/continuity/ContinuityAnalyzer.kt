package com.ss.assistent.continuity

/** Lightweight, deterministic continuity checks that run fully offline. */
object ContinuityAnalyzer {
    fun analyze(
        draft: String,
        loreFacts: List<LoreFact>,
        secrets: List<SecretFact>,
        timeline: List<TimelineScene> = emptyList(),
        knowledgeRules: List<CharacterKnowledgeRule> = emptyList(),
        currentSceneOrder: Int? = null
    ): List<ContinuityWarning> {
        if (draft.isBlank()) return emptyList()
        return buildList {
            addAll(findLoreConflicts(draft, loreFacts))
            addAll(findSecretLeaks(draft, secrets, knowledgeRules, currentSceneOrder))
            addAll(findLocationTransitionHoles(draft))
            addAll(findTimelineMovementIssues(draft, timeline))
        }.distinctBy { "${it.type}|${it.title}|${it.details}|${it.relatedLoreFactId}|${it.relatedSecretId}" }
    }

    private fun findLoreConflicts(draft: String, facts: List<LoreFact>): List<ContinuityWarning> {
        val sentences = splitSentences(draft)
        return facts.mapNotNull { fact ->
            val subject = fact.subject.lowercase()
            val sentence = sentences.firstOrNull { it.lowercase().contains(subject) && attributeMentioned(it, fact.attribute) }
                ?: return@mapNotNull null
            val mentioned = extractMentionedValue(sentence, fact) ?: return@mapNotNull null
            if (valuesConflict(fact.value, mentioned)) {
                ContinuityWarning(
                    type = WarningType.LORE_CONFLICT,
                    title = "Lore conflict detected",
                    details = "Canon: ${fact.subject}'s ${fact.attribute} is ${fact.value}. New text says: $mentioned.",
                    suggestion = "Keep the canon, change the new value to ${fact.value}, or explicitly change the canon instead.",
                    relatedLoreFactId = fact.id
                )
            } else null
        }
    }

    private fun attributeMentioned(sentence: String, attribute: String): Boolean {
        val lower = sentence.lowercase()
        return attributeAliases(attribute).any { alias -> alias.all(lower::contains) }
    }

    private fun attributeAliases(attribute: String): List<List<String>> = when (attribute.lowercase().trim()) {
        "eye color", "eye colour", "eyes" -> listOf(listOf("eye"), listOf("eyes"), listOf("eye", "color"), listOf("eye", "colour"), listOf("irises"), listOf("gaze"))
        "hair color", "hair colour", "hair" -> listOf(listOf("hair"), listOf("hair", "color"), listOf("hair", "colour"), listOf("locks"), listOf("curls"))
        "age" -> listOf(listOf("age"), listOf("years", "old"), listOf("aged"))
        "height" -> listOf(listOf("height"), listOf("tall"), listOf("meters"), listOf("metres"))
        else -> listOf(listOf(attribute.lowercase().trim()))
    }

    private fun extractMentionedValue(sentence: String, fact: LoreFact): String? {
        val lower = sentence.lowercase()
        val subject = Regex.escape(fact.subject.lowercase())
        val attribute = Regex.escape(fact.attribute.lowercase())

        val directPatterns = listOf(
            Regex("$attribute\\s*(?:is|are|:|=|was|were|looks|looked)\\s+([^,.!?;]+)", RegexOption.IGNORE_CASE),
            Regex("$subject\\s+(?:has|have|had|wears|wore)\\s+([^,.!?;]+)", RegexOption.IGNORE_CASE),
            Regex("$subject[^.?!;]{0,80}\\b(?:is|was|stands at|measures)\\s+([^,.!?;]+)", RegexOption.IGNORE_CASE)
        )
        directPatterns.forEach { pattern ->
            pattern.find(lower)?.groupValues?.getOrNull(1)?.trim()?.let { raw ->
                normalizeMention(raw, fact.attribute)?.let { return it }
            }
        }

        val aliasColors = listOf("dark brown", "light brown", "dark blue", "light blue", "blue", "green", "brown", "gray", "grey", "hazel", "black", "white", "red")
        if (attributeMentioned(sentence, fact.attribute)) {
            aliasColors.firstOrNull(lower::contains)?.let { return it }
        }
        val numeric = Regex("\\b(?:1[0-9]|[2-9][0-9])(?:\\.[0-9]+)?\\s*(?:years? old|years?|m|meters?|metres?)\\b", RegexOption.IGNORE_CASE)
            .find(lower)?.value
        return normalizeMention(numeric, fact.attribute)
    }

    private fun normalizeMention(raw: String?, attribute: String): String? {
        val value = raw?.trim()?.trim(',', '.', ':') ?: return null
        if (value.isEmpty()) return null
        return when (attribute.lowercase().trim()) {
            "age" -> Regex("\\b(?:[1-9][0-9]?)(?:\\.[0-9]+)?\\b").find(value)?.value ?: value
            "height" -> Regex("\\b[0-9]+(?:\\.[0-9]+)?\\s*(?:m|meters?|metres?)\\b").find(value)?.value ?: value
            else -> value
        }
    }

    private fun valuesConflict(canon: String, mentioned: String): Boolean {
        val known = normalizeComparable(canon)
        val new = normalizeComparable(mentioned)
        if (known == new || known.contains(new) || new.contains(known)) return false
        val canonNumbers = numberTokens(canon)
        val newNumbers = numberTokens(mentioned)
        if (canonNumbers.isNotEmpty() && newNumbers.isNotEmpty()) return canonNumbers != newNumbers
        val aliases = mapOf(
            "grey" to "gray", "dark-brown" to "darkbrown", "light-brown" to "lightbrown",
            "dark-blue" to "darkblue", "light-blue" to "lightblue"
        )
        return aliases.getOrDefault(known, known) != aliases.getOrDefault(new, new)
    }

    private fun normalizeComparable(value: String): String = value.lowercase().replace(Regex("[^a-z0-9äöüß.]+"), "").trim()
    private fun numberTokens(value: String): Set<String> = Regex("\\b\\d+(?:\\.\\d+)?\\b").findAll(value).map { it.value }.toSet()

    private fun findSecretLeaks(draft: String, secrets: List<SecretFact>, rules: List<CharacterKnowledgeRule>, currentSceneOrder: Int?): List<ContinuityWarning> {
        val lower = draft.lowercase()
        return secrets.mapNotNull { secret ->
            val secretTerms = meaningfulTerms(secret.secret)
            if (secretTerms.isEmpty()) return@mapNotNull null
            val matches = secretTerms.count(lower::contains)
            val threshold = maxOf(2, (secretTerms.size + 1) / 2)
            if (matches < threshold) return@mapNotNull null
            val relevantRules = rules.filter { it.secretId == secret.id }
            val unauthorized = relevantRules.filter { rule ->
                val beforeDiscovery = rule.discoverySceneOrder != null && (currentSceneOrder == null || currentSceneOrder < rule.discoverySceneOrder)
                beforeDiscovery || !rule.allowNarratedKnowledge
            }
            val explicitReveal = listOf("reveals", "revealed", "discovers", "discovered", "finds out", "learns that", "learned that", "realizes", "realised").any(lower::contains)
            if (unauthorized.isNotEmpty()) {
                val blocked = unauthorized.filterNot { it.allowExplicitReveal }
                val names = unauthorized.joinToString { it.characterName }
                val title = if (explicitReveal && blocked.isNotEmpty()) "Unauthorized secret reveal" else "Secret knowledge conflict"
                return@mapNotNull ContinuityWarning(
                    type = WarningType.SECRET_LEAK,
                    title = title,
                    details = if (blocked.isNotEmpty()) "The draft appears to reveal hidden information to ${blocked.joinToString { it.characterName }} without permission." else "The draft appears to give hidden information to $names before their configured discovery point or narration permission.",
                    suggestion = "Keep the secret hidden, move the reveal to the configured discovery scene, or explicitly update the character's rule.",
                    relatedSecretId = secret.id
                )
            }
            if (secret.knownBy.isEmpty() || relevantRules.isEmpty()) ContinuityWarning(
                type = WarningType.SECRET_LEAK,
                title = "Secret leak detected",
                details = "The draft appears to reveal hidden information about ${secret.subject} that may not be known by the characters.",
                suggestion = "Keep the secret hidden and preserve uncertainty, or explicitly mark this scene as a reveal.",
                relatedSecretId = secret.id
            ) else null
        }
    }

    private fun findLocationTransitionHoles(draft: String): List<ContinuityWarning> {
        val lower = draft.lowercase()
        val pairs = listOf("car" to "kitchen", "car" to "bedroom", "car" to "living room", "school" to "home", "office" to "home", "street" to "house", "outside" to "inside", "restaurant" to "home")
        val transitionWords = listOf("arrived", "got home", "returned", "came home", "went back", "drove home", "entered", "left", "walked home", "reached", "after the drive", "later at home", "back at", "on the way")
        return pairs.mapNotNull { (origin, destination) ->
            val originIndex = lower.indexOf(origin)
            if (originIndex < 0) return@mapNotNull null
            val destinationIndex = lower.indexOf(destination, originIndex + origin.length)
            if (destinationIndex < 0) return@mapNotNull null
            val between = lower.substring(originIndex, destinationIndex)
            if (transitionWords.any(between::contains)) return@mapNotNull null
            ContinuityWarning(WarningType.PLOT_HOLE, "Plot hole detected", "The draft establishes a character at $origin, then places them at $destination without a clear transition between the locations.", "Add a short transition showing how the character moved between the locations, or make the scene break explicit.")
        }
    }

    private fun findTimelineMovementIssues(draft: String, timeline: List<TimelineScene>): List<ContinuityWarning> {
        if (timeline.size < 2) return emptyList()
        val ordered = timeline.sortedBy { it.order }
        val lower = draft.lowercase()
        val mentions = ordered.mapNotNull { scene ->
            val locationIndex = lower.indexOf(scene.location.lowercase())
            val titleIndex = lower.indexOf(scene.title.lowercase())
            val index = listOf(locationIndex, titleIndex).filter { it >= 0 }.minOrNull() ?: return@mapNotNull null
            scene to index
        }.sortedBy { it.second }
        if (mentions.size < 2) return emptyList()
        buildList {
            for (i in 1 until mentions.size) {
                val (previous, previousIndex) = mentions[i - 1]
                val (current, currentIndex) = mentions[i]
                if (current.order < previous.order) add(ContinuityWarning(WarningType.PLOT_HOLE, "Timeline order conflict", "The draft mentions '${current.title}' after '${previous.title}', but the stored timeline orders it earlier.", "Reorder the scenes, make the flashback/time jump explicit, or update the timeline deliberately."))
                if (current.location.equals(previous.location, true) && current.order > previous.order) {
                    val between = lower.substring(previousIndex.coerceAtLeast(0), currentIndex.coerceAtLeast(previousIndex))
                    if (listOf("later", "meanwhile", "after", "hours later", "the next day", "scene", "cut to", "meanwhile").none(between::contains)) add(ContinuityWarning(WarningType.PLOT_HOLE, "Scene transition may be missing", "The draft reaches a later timeline scene in the same location without an explicit time or scene transition.", "Add a scene break or clear time transition if this is intentionally a later moment."))
                }
            }
        }
    }

    private fun splitSentences(text: String): List<String> = text.split(Regex("(?<=[.!?])\\s+|\\n+")).filter(String::isNotBlank)
    private fun meaningfulTerms(text: String): List<String> = text.lowercase().split(Regex("[^a-z0-9äöüß]+" )).filter { it.length >= 5 }.distinct()
}
