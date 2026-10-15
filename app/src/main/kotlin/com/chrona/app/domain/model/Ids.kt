package com.chrona.app.domain.model

/**
 * Stable, opaque identity for a task (CH-DOM-001).
 *
 * A TaskId never changes when a task is moved, appears on another surface, or is
 * connected on the board. It is the only thing that ties Today, Schedule, Board and
 * History to the same record.
 */
@JvmInline
value class TaskId(val value: String) {
    init {
        require(value.isNotBlank()) { "TaskId must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
value class EventId(val value: String) {
    init {
        require(value.isNotBlank()) { "EventId must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
value class RelationId(val value: String) {
    init {
        require(value.isNotBlank()) { "RelationId must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
value class RevisionId(val value: String) {
    init {
        require(value.isNotBlank()) { "RevisionId must not be blank" }
    }

    override fun toString(): String = value
}