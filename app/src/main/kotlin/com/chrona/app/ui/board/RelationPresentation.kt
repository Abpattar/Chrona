package com.chrona.app.ui.board

import com.chrona.app.domain.model.RelationType
import com.chrona.app.domain.model.TaskRelationship

/**
 * Human-readable wording for a relationship (UI-002, REL-001, REL-002).
 *
 * Every relation carries a label and a sentence, so meaning never depends on the red thread
 * colour or on an icon alone. Pure functions: no Compose, no Android, so they are unit-tested
 * directly.
 */
fun RelationType.displayLabel(): String = when (this) {
    RelationType.DEPENDS_ON -> "Depends on"
    RelationType.SUPPORTS -> "Supports"
    RelationType.PART_OF_PROJECT -> "Part of project"
    RelationType.SAME_EVENT -> "Same event as"
    RelationType.RELATED_REFERENCE -> "Related to"
}

/** One plain sentence describing a directed edge, using the two task titles. */
fun describeRelation(sourceTitle: String, type: RelationType, targetTitle: String): String =
    when (type) {
        RelationType.DEPENDS_ON -> "\u201C$sourceTitle\u201D waits for \u201C$targetTitle\u201D"
        RelationType.SUPPORTS -> "\u201C$sourceTitle\u201D supports \u201C$targetTitle\u201D"
        RelationType.PART_OF_PROJECT -> "\u201C$sourceTitle\u201D is part of \u201C$targetTitle\u201D"
        RelationType.SAME_EVENT -> "\u201C$sourceTitle\u201D is the same event as \u201C$targetTitle\u201D"
        RelationType.RELATED_REFERENCE -> "\u201C$sourceTitle\u201D is related to \u201C$targetTitle\u201D"
    }

/**
 * Whether [relationship] currently constrains scheduling.
 *
 * The board states this explicitly so a dormant suggestion is never mistaken for a real
 * dependency (REL-002: no silent consequential dependency creation).
 */
fun TaskRelationship.constrainsOrder(): Boolean = isActiveHardDependency
