package com.chrona.app.ui.board

import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskRelationship
import kotlin.math.abs

/** A pinned note's slot. Coordinates are grid cells, not pixels, so layout stays stable. */
data class BoardNote(
    val taskId: TaskId,
    val column: Int,
    val row: Int,
    /** Small, restrained tilt derived from the id; identical every render. */
    val tiltDegrees: Float,
)

/** A thread between two visible notes, carrying the relationship it stands for. */
data class BoardThread(
    val relationship: TaskRelationship,
    val from: BoardNote,
    val to: BoardNote,
)

/**
 * The deterministic board layout.
 *
 * Same tasks + same relationships always produce the same slots: no randomness, no wall clock,
 * no animation state. That makes the board reproducible in tests and keeps a note from jumping
 * around between recompositions (P05 "stable coordinates").
 */
data class BoardLayout(
    val notes: List<BoardNote>,
    val threads: List<BoardThread>,
) {
    val isEdgeEmpty: Boolean get() = threads.isEmpty()
    val notesById: Map<TaskId, BoardNote> = notes.associateBy { it.taskId }
}

object BoardLayoutCalculator {

    /** Notes per row. Kept small so a phone board stays readable without zooming. */
    const val COLUMNS = 3

    /** Maximum tilt in degrees; deliberately tiny so text stays level enough to read. */
    private const val MAX_TILT = 2.5f

    /**
     * Places [tasks] on a grid ordered by project then id, and joins the subset of
     * [relationships] whose both endpoints are visible.
     *
     * Complexity is O(n + m), so the 100-task / 200-relationship fixture is a single pass.
     */
    fun layout(tasks: List<Task>, relationships: List<TaskRelationship>): BoardLayout {
        val ordered = tasks.sortedWith(
            compareBy({ it.projectLabel ?: "" }, { it.id.value }),
        )
        val notes = ordered.mapIndexed { index, task ->
            BoardNote(
                taskId = task.id,
                column = index % COLUMNS,
                row = index / COLUMNS,
                tiltDegrees = tiltOf(task.id),
            )
        }
        val byId = notes.associateBy { it.taskId }
        val threads = relationships
            .asSequence()
            .mapNotNull { relationship ->
                val from = byId[relationship.sourceTaskId] ?: return@mapNotNull null
                val to = byId[relationship.targetTaskId] ?: return@mapNotNull null
                BoardThread(relationship, from, to)
            }
            .sortedWith(
                compareBy(
                    { it.relationship.sourceTaskId.value },
                    { it.relationship.targetTaskId.value },
                    { it.relationship.type.name },
                ),
            )
            .toList()
        return BoardLayout(notes = notes, threads = threads)
    }

    /** Deterministic tilt in [-MAX_TILT, MAX_TILT]; stable per id, never random. */
    private fun tiltOf(id: TaskId): Float {
        val hash = id.value.hashCode()
        val bucket = abs(hash % 7) - 3 // -3..3
        return bucket / 3f * MAX_TILT
    }
}
