package com.ss.assistent.continuity

/** A stable story location/scene that can be ordered without relying on prose alone. */
data class TimelineScene(
    val id: String,
    val title: String,
    val order: Int,
    val location: String,
    val characterIds: List<String> = emptyList(),
    val notes: String = ""
)

data class CharacterKnowledgeRule(
    val characterId: String,
    val characterName: String,
    val secretId: String,
    /** First scene order in which this character is allowed to know the secret. */
    val discoverySceneOrder: Int? = null,
    /** If false, prose may not narrate this character as knowing the secret before discovery. */
    val allowNarratedKnowledge: Boolean = true,
    /** If true, an explicit reveal is permitted at the discovery point. */
    val allowExplicitReveal: Boolean = true
)

data class TimelineIssue(
    val title: String,
    val details: String,
    val suggestion: String,
    val sceneId: String? = null
)
