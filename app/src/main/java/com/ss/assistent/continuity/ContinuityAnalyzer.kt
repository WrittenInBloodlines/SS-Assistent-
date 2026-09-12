package com.ss.assistent.continuity

/**
 * Lightweight, deterministic continuity checks that run fully offline.
 * Warnings are conservative and always reference structured canon IDs when possible.
 */
object ContinuityAnalyzer {
    fun analyze(
        draft: String,
        loreFacts: List<LoreFact>,
        secrets: List<SecretFact>
    ): List<ContinuityWarning> {
        if (draft.isBlank()) return emptyList()
        return buildList {
            addAll(findLoreConflicts(draft, loreFacts))
            addAll(findSecretLeaks(draft, secrets))
            addAll(findLocationTransitionHoles(draft))
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
                sentenceLower.contains(fact.subject.lowercase()) &&
                    attributeAliases(fact.attribute).any { alias -> alias.all(sentenceLower::contains) }
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

    private fun attributeAliases(attribute: String): List<List<String>> {
        val normalized = attribute.lowercase().trim()
        return when (normalized) {
            "eye color", "eye colour", "eyes" -> listOf(listOf("eye"), listOf("eyes"), listOf("eye", "color"), listOf("eye", "colour"))
            "hair color", "hair colour", "hair" -> listOf(listOf("hair"), listOf("hair", "color"), listOf("hair", "colour"))
            "age" -> listOf(listOf("age"), listOf("years", "old"))
            "height" -> listOf(listOf("height"), listOf("tall"))
            else -> listOf(listOf(normalized))
        }
    }

    private fun extractMentionedValue(sentence: String, fact: LoreFact): String? {
        val lower = sentence.lowercase()
        val attribute = Regex.escape(fact.attribute.lowercase())
        Regex("$attribute\\s*(?:is|are|:|=|was|were)\\s+([^,.!?;]+)", RegexOption.IGNORE_CASE)
            .find(lower)?.groupValues?.getOrNull(1)?.trim()?.let { return it }

        val subject = Regex.escape(fact.subject.lowercase())
        Regex("$subject\\s+(?:has|have)\\s+([^,.!?;]+)", RegexOption.IGNORE_CASE)
            .find(lower)?.groupValues?.getOrNull(1)?.trim()?.let { return it }

        if (attributeAliases(fact.attribute).any { alias -> alias.all(lower::contains) }) {
            val color = listOf("dark brown", "light brown", "blue", "green", "brown", "gray", "grey", "hazel", "black", "white", "red")
                .firstOrNull(lower::contains)
            if (color != null) return color
        }
        return null
    }

    private fun findSecretLeaks(draft: String, secrets: List<SecretFact>): List<ContinuityWarning> {
        val lower = draft.lowercase()
        return secrets.mapNotNull { secret ->
            val secretTerms = meaningfulTerms(secret.secret)
            if (secretTerms.isEmpty()) return@mapNotNull null
            val matches = secretTerms.count(lower::contains)
            val threshold = maxOf(2, (secretTerms.size + 1) / 2)
            if (matches < threshold) return@mapNotNull null
            ContinuityWarning(
                type = WarningType.SECRET_LEAK,
                title = "Secret leak detected",
                details = "The draft appears to reveal hidden information about ${secret.subject} that may not be known by the characters.",
                suggestion = "Keep the secret hidden and preserve uncertainty, or explicitly mark this scene as a reveal.",
                relatedSecretId = secret.id
            )
        }
    }

    /**
     * Finds several high-confidence location jumps, not only car -> kitchen.
     * The detector only warns when the draft explicitly establishes an origin,
     * then jumps to a destination without a transition phrase.
     */
    private fun findLocationTransitionHoles(draft: String): List<ContinuityWarning> {
        val lower = draft.lowercase()
        val locationPairs = listOf(
            listOf("car", "kitchen"),
            listOf("car", "bedroom"),
            listOf("car", "living room"),
            listOf("school", "home"),
            listOf("office", "home"),
            listOf("street", "house"),
            listOf("outside", "inside"),
            listOf("restaurant", "home")
        )
        val transitionWords = listOf(
            "arrived", "got home", "returned", "came home", "went back", "drove home",
            "entered", "left", "walked home", "reached", "after the drive", "later at home",
            "back at", "on the way"
        )

        return locationPairs.mapNotNull { (origin, destination) ->
            val hasOrigin = lower.contains(origin)
            val hasDestination = lower.contains(destination)
            if (!hasOrigin || !hasDestination) return@mapNotNull null

            val originIndex = lower.indexOf(origin)
            val destinationIndex = lower.indexOf(destination, originIndex + origin.length)
            if (destinationIndex < 0) return@mapNotNull null

            val between = lower.substring(originIndex, destinationIndex)
            val hasTransition = transitionWords.any(between::contains)
            if (hasTransition) return@mapNotNull null

            ContinuityWarning(
                type = WarningType.PLOT_HOLE,
                title = "Plot hole detected",
                details = "The draft establishes a character at $origin, then places them at $destination without a clear transition between the locations.",
                suggestion = "Add a short transition showing how the character moved between the locations, or make the scene break explicit."
            )
        }
    }

    private fun splitSentences(text: String): List<String> =
        text.split(Regex("(?<=[.!?])\\s+|\\n+")).filter(String::isNotBlank)

    private fun meaningfulTerms(text: String): List<String> =
        text.lowercase()
            .split(Regex("[^a-z0-9äöüß]+"))
            .filter { it.length >= 5 }
            .distinct()
}
