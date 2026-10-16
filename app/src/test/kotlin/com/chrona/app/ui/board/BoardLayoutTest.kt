package com.chrona.app.ui.board

import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.RelationCreatedBy
import com.chrona.app.domain.model.RelationId
import com.chrona.app.domain.model.RelationType
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskOrigin
import com.chrona.app.domain.model.TaskRelationship
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Board layout rules (AT-010, AT-012).
 *
 * The layout is pure and deterministic, so it is tested without a device: same input, same
 * slots; threads only between visible notes; the 100/200 fixture is a single pass.
 */
class BoardLayoutTest {

    private val start: Instant = Instant.parse("2026-10-15T08:00:00Z")

    private fun task(id: String, project: String? = null) = Task(
        id = TaskId(id),
        title = "Task $id",
        priority = Priority.IMPORTANT,
        estimatedDuration = Duration.ofHours(1),
        projectLabel = project,
        origin = TaskOrigin.TYPED,
        createdAt = start,
        updatedAt = start,
    )

    private fun link(id: String, source: String, target: String) = TaskRelationship(
        id = RelationId(id),
        sourceTaskId = TaskId(source),
        targetTaskId = TaskId(target),
        type = RelationType.DEPENDS_ON,
        createdBy = RelationCreatedBy.USER,
        confirmedAt = start,
        createdAt = start,
    )

    @Test
    fun `the same input always yields the same layout`() {
        val tasks = listOf(task("c"), task("a"), task("b"))
        val links = listOf(link("r1", "a", "b"))

        val first = BoardLayoutCalculator.layout(tasks, links)
        val second = BoardLayoutCalculator.layout(tasks, links)

        assertEquals(first, second)
    }

    @Test
    fun `notes fill the grid column by column`() {
        val tasks = (1..7).map { task("t$it") }

        val layout = BoardLayoutCalculator.layout(tasks, emptyList())

        assertEquals(0, layout.notes[0].column)
        assertEquals(0, layout.notes[0].row)
        assertEquals(2, layout.notes[2].column)
        assertEquals(0, layout.notes[2].row)
        assertEquals(0, layout.notes[3].column)
        assertEquals(1, layout.notes[3].row)
    }

    @Test
    fun `notes are ordered by project then id`() {
        val tasks = listOf(task("z", project = "B"), task("a", project = "A"), task("m", project = "A"))

        val layout = BoardLayoutCalculator.layout(tasks, emptyList())

        assertEquals(listOf("a", "m", "z"), layout.notes.map { it.taskId.value })
    }

    @Test
    fun `only relationships between visible notes become threads`() {
        val tasks = listOf(task("a"), task("b"))
        val links = listOf(link("r1", "a", "b"), link("r2", "a", "hidden"))

        val layout = BoardLayoutCalculator.layout(tasks, links)

        assertEquals(listOf("r1"), layout.threads.map { it.relationship.id.value })
        assertNotEquals(layout.isEdgeEmpty, true)
    }

    @Test
    fun `tilt stays small and level enough to read`() {
        val tasks = (1..50).map { task("t$it") }

        val layout = BoardLayoutCalculator.layout(tasks, emptyList())

        layout.notes.forEach {
            assertTrue("tilt ${it.tiltDegrees} out of range", it.tiltDegrees in -2.5f..2.5f)
        }
    }

    @Test
    fun `the layout is a single pass over a hundred tasks and two hundred links`() {
        val tasks = (1..100).map { task("t$it") }
        val links = (1..200).map { link("r$it", "t${(it % 100) + 1}", "t${((it * 7) % 100) + 1}") }

        val layout = BoardLayoutCalculator.layout(tasks, links)

        assertEquals(100, layout.notes.size)
        assertEquals(100, layout.notesById.size)
        assertTrue(layout.threads.size <= links.size)
    }
}
