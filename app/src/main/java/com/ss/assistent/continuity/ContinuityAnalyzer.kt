package com.ss.assistent.continuity

/**
 * Lightweight, deterministic continuity checks that run fully offline.
 * Warnings are conservative and always reference structured canon IDs when possible.
 */
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
        val lower = draft.lowercase()
        val sentences = splitSentences(draft)
        return facts.mapNotNull { fact ->
            val subjectPresent = lower.contains(fact.subject.lowercase())
            val attributePresent = attributeAliases(fact.attribute).any { alias -> alias.all(lower::contains) }
            if (!subjectPresent || !attributePresent) return@mapNotNull null
            val sentence = sentences.firstOrNull { text ->
                val sentenceLower = text.lowercase()
                sentenceLower.contains(fact.subject.lowercase()) && attributeAliases(fact.attribute).any { alias -> alias.all(sentenceLower::contains) }
            } ?: return@mapNotNull null
            val mentionedValue = extractMentionedValue(sentence, fact)
            val known = fact.value.lowercase()
            if (mentionedValue != null && !mentionedValue.equals(known, true) && !mentionedValue.contains(known)) {
                ContinuityWarning(
                    type = WarningType.LORE_CONFLICT,
                    title = "Lore conflict detected",
                    details = "Canon: ${fact.subject}'s ${fact.attribute} is ${fact.value}. New text says: $mentionedValue.",
                    suggestion = "Keep the canon, change the new value to ${fact.value}, or explicitly change the canon instead.",
                    relatedLoreFactId = fact.id
                )
            } else null
        }
    }

    private fun attributeAliases(attribute: String): List<List<String>> = when (attribute.lowercase().trim()) {
        "eye color", "eye colour", "eyes" -> listOf(listOf("eye"), listOf("eyes"), listOf("eye", "color"), listOf("eye", "colour"))
        "hair color", "hair colour", "hair" -> listOf(listOf("hair"), listOf("hair", "color"), listOf("hair", "colour"))
        "age" -> listOf(listOf("age"), listOf("years", "old"))
        "height" -> listOf(listOf("height"), listOf("tall"))
        else -> listOf(listOf(attribute.lowercase().trim()))
    }

    private fun extractMentionedValue(sentence: String, fact: LoreFact): String? {
        val lower = sentence.lowercase()
        val attribute = Regex.escape(fact.attribute.lowercase())
        Regex("$attribute\\s*(?:is|are|:|=|was|were)\\s+([^,.!?;]+)", RegexOption.IGNORE_CASE).find(lower)?.groupValues?.getOrNull(1)?.trim()?.let { return it }
        val subject = Regex.escape(fact.subject.lowercase())
        Regex("$subject\\s+(?:has|have)\\s+([^,.!?;]+)", RegexOption.IGNORE_CASE).find(lower)?.groupValues?.getOrNull(1)?.trim()?.let { return it }
        if (attributeAliases(fact.attribute).any { alias -> alias.all(lower::contains) }) {
            val color = listOf("dark brown", "light brown", "blue", "green", "brown", "gray", "grey", "hazel", "black", "white", "red").firstOrNull(lower::contains)
            if (color != null) return color
        }
        return null
    }

    private fun findSecretLeaks(
        draft: String,
        secrets: List<SecretFact>,
        rules: List<CharacterKnowledgeRule>,
        currentSceneOrder: Int?
    ): List<ContinuityWarning> {
        val lower = draft.lowercase()
        return secrets.mapNotNull { secret ->
            val secretTerms = meaningfulTerms(secret.secret)
            if (secretTerms.isEmpty()) return@mapNotNull null
            val matches = secretTerms.count(lower::contains)
            val threshold = maxOf(2, (secretTerms.size + 1) / 2)
            if (matches < threshold) return@mapNotNull null

            val relevantRules = rules.filter { it.secretId == secret.id }
            val unauthorized = relevantRules.filter { rule ->
                val discovered = rule.discoverySceneOrder
                val beforeDiscovery = discovered != null && (currentSceneOrder == null || currentSceneOrder < discovered)
                beforeDiscovery || !rule.allowNarratedKnowledge
            }
            val explicitRevealWords = listOf("reveals", "revealed", "discovers", "discovered", "finds out", "learns that", "learned that", "realizes", "realised")
            val explicitReveal = explicitRevealWords.any(lower::contains)
            if (unauthorized.isNotEmpty() && explicitReveal && unauthorized.any { !it.allowExplicitReveal }) {
                val names = unauthorized.filterNot { it.allowExplicitReveal }.joinToString { it.characterName }
                return@mapNotNull ContinuityWarning(
                    type = WarningType.SECRET_LEAK,
                    title = "Unauthorized secret reveal",
                    details = "The draft appears to reveal hidden information to $names before their configured discovery point or permission.",
                    suggestion = "Move the reveal to the configured discovery scene, change the character's rule, or keep the information hidden.",
                    relatedSecretId = secret.id
                )
            }
            if (unauthorized.isNotEmpty()) {
                val names = unauthorized.joinToString { it.characterName }
                return@mapNotNull ContinuityWarning(
                    type = WarningType.SECRET_LEAK,
                    title = "Secret knowledge conflict",
                    details = "The draft appears to reveal hidden information to $names before their configured discovery point or narration permission.",
                    suggestion = "Keep the secret hidden here, or explicitly update the character's discovery rule before using this reveal.",
                    relatedSecretId = secret.id
                )
            }
            if (secret.knownBy.isEmpty() || relevantRules.isEmpty()) {
                ContinuityWarning(
                    type = WarningType.SECRET_LEAK,
                    title = "Secret leak detected",
                    details = "The draft appears to reveal hidden information about ${secret.subject} that may not be known by the characters.",
                    suggestion = "Keep the secret hidden and preserve uncertainty, or explicitly mark this scene as a reveal.",
                    relatedSecretId = secret.id
                )
            } else null
        }
    }

    private fun findLocationTransitionHoles(draft: String): List<ContinuityWarning> {
        val lower = draft.lowercase()
        val locationPairs = listOf(
            listOf("car", "kitchen"), listOf("car", "bedroom"), listOf("car", "living room"),
            listOf("school", "home"), listOf("office", "home"), listOf("street", "house"),
            listOf("outside", "inside"), listOf("restaurant", "home")
        )
        val transitionWords = listOf("arrived", "got home", "returned", "came home", "went back", "drove home", "entered", "left", "walked home", "reached", "after the drive", "later at home", "back at", "on the way")
        return locationPairs.mapNotNull { (origin, destination) ->
            val originIndex = lower.indexOf(origin)
            if (originIndex < 0) return@mapNotNull null
            val destinationIndex = lower.indexOf(destination, originIndex + origin.length)
            if (destinationIndex < 0) return@mapNotNull null
            val between = lower.substring(originIndex, destinationIndex)
            if (transitionWords.any(between::contains)) return@mapNotNull null
            ContinuityWarning(
                type = WarningType.PLOT_HOLE,
                title = "Plot hole detected",
                details = "The draft establishes a character at $origin, then places them at $destination without a clear transition between the locations.",
                suggestion = "Add a short transition showing how the character moved between the locations, or make the scene break explicit."
            )
        }
    }

    /**
     * Compares prose locations with an explicitly ordered scene timeline. This is intentionally
     * conservative: it only flags a scene when the draft clearly names the scene/location and the
     * stored timeline says that the same location would require an impossible backwards jump.
     */
    private fun findTimelineMovementIssues(draft: String, timeline: List<TimelineScene>): List<ContinuityWarning> {
        if (timeline.size < 2) return emptyList()
        val ordered = timeline.sortedBy { it.order }
        val lower = draft.lowercase()
        val mentioned = ordered.filter { scene -> lower.contains(scene.location.lowercase()) || lower.contains(scene.title.lowercase()) }
        if (mentioned.size < 2) return emptyList()
        val issues = mutableListOf<ContinuityWarning>()
        for (index in 1 until mentioned.size) {
            val previous = mentioned[index - 1]
            val current = mentioned[index]
            if (current.order < previous.order) {
                issues += ContinuityWarning(
                    type = WarningType.PLOT_HOLE,
                    title = "Timeline order conflict",
                    details = "The draft mentions '${current.title}' after '${previous.title}', but the stored timeline orders it earlier.",
                    suggestion = "Reorder the scenes, make the flashback/time jump explicit, or update the timeline deliberately."
                )
            }
            if (current.location.equals(previous.location, true) && current.order > previous.order && current.location.isNotBlank()) {
                val movementWords = listOf("left", "arrived", "returned", "went", "drove", "walked", "entered", "came back", "later", "meanwhile", "after")
                val between = lower.substring(0, lower.indexOf(current.location.lowercase()).coerceAtLeast(0))
                if (movementWords.none(between::contains)) {
                    issues += ContinuityWarning(
                        type = WarningType.PLOT_HOLE,
                        title = "Scene transition may be missing",
                        details = "The draft reaches a later timeline scene in the same location without an explicit time or scene transition.",
                        suggestion = "Add a scene break or a clear time transition if this is intentionally a later moment."
                    )
                }
            }
        }
        return issues
    }

    private fun splitSentences(text: String): List<String> = text.split(Regex("(?<=[.!?])\\s+|\\n+")).filter(String::isNotBlank)

    private fun meaningfulTerms(text: String): List<String> = text.lowercase().split(Regex("[^a-z0-9äöüß]+"))
        .filter { it.length >= 5 }.distinct()
}
