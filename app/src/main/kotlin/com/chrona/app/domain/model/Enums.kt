package com.chrona.app.domain.model

/**
 * Explicit persisted priority (CH-DOM-003).
 *
 * Priority is a stored field, never inferred from colour or from a deadline. The UI pairs
 * each value with a text label so meaning never depends on colour alone.
 */
enum class Priority {
    /** Side task. */
    LOW,

    /** High priority. */
    IMPORTANT,

    /** Very important. */
    CRITICAL,
}

/**
 * The seven canonical task states (CH-DOM-004).
 *
 * These are task lifecycle states only. Dismissing a reminder card, snoozing a card and
 * navigating away are *not* status changes and must never be expressed here.
 */
enum class TaskStatus {
    /** Captured but not yet planned. */
    INBOX,

    /** Has an accepted schedule assignment. */
    PLANNED,

    /** The user explicitly started it. */
    IN_PROGRESS,

    /** Finished; [Task.completedAt] is set. */
    COMPLETED,

    /** Explicitly deferred; [Task.postponedUntil] may be set. */
    POSTPONED,

    /** Explicitly skipped by the user. */
    SKIPPED,

    /** Removed from active surfaces but retained in History. */
    ARCHIVED,
    ;

    /** States the scheduler may place. Everything else is inert for planning. */
    val isActive: Boolean
        get() = this == INBOX || this == PLANNED || this == IN_PROGRESS || this == POSTPONED

    /** States the user can still act on without reopening History. */
    val isOpen: Boolean
        get() = this != COMPLETED && this != SKIPPED && this != ARCHIVED
}

/** Typed relationship kinds (CH-DOM-005). Only [DEPENDS_ON] constrains ordering. */
enum class RelationType {
    /** Source depends on target: target is the prerequisite. Hard constraint. */
    DEPENDS_ON,

    /** Supportive link. Never blocks or orders on its own. */
    SUPPORTS,

    PART_OF_PROJECT,
    SAME_EVENT,
    RELATED_REFERENCE,
    ;

    /** True when this edge constrains scheduling order. */
    val isHardDependency: Boolean get() = this == DEPENDS_ON
}

/** FIXED tasks carry an explicit start/end and are never auto-moved. */
enum class Flexibility { FIXED, FLEXIBLE }

/** How a task entered Chrona. No enum member implies raw audio storage. */
enum class TaskOrigin {
    VOICE,
    TYPED,
    MANUAL_EDIT,
    CALENDAR_IMPORT,
}

/** Manual fixed commitments in MVP; a provider source needs an approved decision first. */
enum class EventSource { MANUAL }