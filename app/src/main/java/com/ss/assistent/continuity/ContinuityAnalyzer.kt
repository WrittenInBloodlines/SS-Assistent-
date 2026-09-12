package com.ss.assistent.continuity

/**
 * Lightweight, deterministic continuity checks that can run fully offline.
 * These checks are deliberately conservative: a warning is safer than silently changing canon.
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
            addAll(findKnownPlotHoles(draft))
        }.distinctBy { "${it.type}|${it.title}|${it.details}" }
    }

    private fun findLoreConflicts(draft: String, facts: List<LoreFact>): List<ContinuityWarning> {
        val lower = draft.lowercase()
        return facts.mapNotNull { fact ->
            val subjectPresent = lower.contains(fact.subject.lowercase())
            val attributePresent = attributeAliases(fact.attribute).any { alias ->
                alias.all { lower.contains(it) }
            }
            if (!subjectPresent || !attributePresent) return@mapNotNull null

            val sentences = draft.split(Regex("(?<=[.!?])\\s+|\\n+"))
            val sentence = sentences.firstOrNull { sentenceText ->
                val sentenceLower = sentenceText.lowercase()
                sentenceLower.contains(fact.subject.lowercase()) &&
                    attributeAliases(fact.attribute).any { alias -> alias.all { sentenceLower.contains(it) } }
            } ?: return@mapNotNull null

            val known = fact.value.lowercase()
            val mentionedValue = extractMentionedValue(sentence, fact)
            if (mentionedValue != null && !mentionedValue.equals(known, true) && !mentionedValue.contains(known)) {
                ContinuityWarning(
                    type = WarningType.LORE_CONFLICT,
                    title = "Lore conflict detected",
                    details = "Canon: ${fact.subject}'s ${fact.attribute} is ${fact.value}. New text says: $mentionedValue.",
                    suggestion = "Keep the canon, change the new value to ${fact.value}, or explicitly change the canon instead."
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
        val afterAttribute = Regex("$attribute\\s*(?:is|are|:|=|was|were)\\s+([^,.!?;]+)", RegexOption.IGNORE_CASE)
            .find(lower)?.groupValues?.getOrNull(1)?.trim()
        if (afterAttribute != null) return afterAttribute

        val subject = Regex.escape(fact.subject.lowercase())
        val hasPattern = Regex("$subject\\s+(?:has|have)\\s+([^,.!?;]+)", RegexOption.IGNORE_CASE)
            .find(lower)?.groupValues?.getOrNull(1)?.trim()
        if (hasPattern != null) return hasPattern

        if (attributeAliases(fact.attribute).any { alias -> alias.all { lower.contains(it) } }) {
            val color = listOf("blue", "green", "brown", "dark brown", "light brown", "gray", "grey", "hazel", "black", "white", "red").firstOrNull { lower.contains(it) }
            if (color != null) return color
        }
        return null
    }

    private fun findSecretLeaks(draft: String, secrets: List<SecretFact>): List<ContinuityWarning> {
        val lower = draft.lowercase()
        return secrets.mapNotNull { secret ->
            val secretTerms = meaningfulTerms(secret.secret)
            if (secretTerms.isEmpty()) return@mapNotNull null
            val matches = secretTerms.count { lower.contains(it) }
            val threshold = maxOf(2, (secretTerms.size + 1) / 2)
            if (matches < threshold) return@mapNotNull null
            ContinuityWarning(
                type = WarningType.SECRET_LEAK,
                title = "Secret leak detected",
                details = "The draft appears to reveal hidden information about ${secret.subject} that may not be known by the characters.",
                suggestion = "Keep the secret hidden and preserve uncertainty, or explicitly mark this scene as a reveal."
            )
        }
    }

    private fun findKnownPlotHoles(draft: String): List<ContinuityWarning> {
        val lower = draft.lowercase()
        val hasCar = lower.contains("car") || lower.contains("in the car")
        val hasKitchen = lower.contains("kitchen")
        val movesToKitchen = lower.contains("went to the kitchen") ||
            lower.contains("goes to the kitchen") ||
            lower.contains("walked to the kitchen") ||
            lower.contains("entered the kitchen")
        val hasTransition = listOf("arrived home", "got home", "returned home", "came home", "arrived at home", "drove home", "back home").any(lower::contains)

        if (hasCar && hasKitchen && movesToKitchen && !hasTransition) {
            return listOf(
                ContinuityWarning(
                    type = WarningType.PLOT_HOLE,
                    title = "Plot hole detected",
                    details = "A character is established in a car, then appears in the kitchen without a clear transition showing how they got home.",
                    suggestion = "Add a short transition such as arriving home, entering the house, or another believable route between the locations."
                )
            )
        }
        return emptyList()
    }

    private fun meaningfulTerms(text: String): List<String> =
        text.lowercase()
            .split(Regex("[^a-z0-9äöüß]+"))
            .filter { it.length >= 5 }
            .distinct()
}
