package com.chrona.app.domain.relation

import com.chrona.app.domain.model.RelationCreatedBy
import com.chrona.app.domain.model.RelationId
import com.chrona.app.domain.model.RelationType
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskRelationship
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Relation direction, cycles and the hard/supportive split (REL-001, REL-002, AT-010, AT-011).
 */
class RelationGraphTest {

    private val at: Instant = Instant.parse("2026-10-15T08:00:00Z")

    private val a = TaskId("A")
    private val b = TaskId("B")
    private val c = TaskId("C")

    private fun edge(
        source: TaskId,
        target: TaskId,
        type: RelationType = RelationType.DEPENDS_ON,
        confirmed: Boolean = true,
        createdBy: RelationCreatedBy = RelationCreatedBy.USER,
        id: String = "${source.value}-${target.value}-${type.name}",
    ) = TaskRelationship(
        id = RelationId(id),
        sourceTaskId = source,
        targetTaskId = target,
        type = type,
        createdBy = createdBy,
        confirmedAt = if (confirmed) at else null,
        createdAt = at,
    )

    @Test
    fun `source depends on target means source waits for target`() {
        val graph = RelationGraph(listOf(edge(source = a, target = b)))
        assertTrue(graph.mustFollow(a, b))
        assertFalse(graph.mustFollow(b, a))
        assertTrue(graph.mustPrecede(b, a))
    }

    @Test
    fun `a support relation never imposes order`() {
        val graph = RelationGraph(listOf(edge(source = c, target = a, type = RelationType.SUPPORTS)))
        assertTrue(graph.hardEdges.isEmpty())
        assertFalse(graph.mustFollow(c, a))
        assertFalse(graph.mustPrecede(c, a))
    }

    @Test
    fun `part of project same event and reference never impose order`() {
        val soft = RelationType.entries.filter { !it.isHardDependency }
        soft.forEach { type ->
            val graph = RelationGraph(listOf(edge(source = a, target = b, type = type)))
            assertTrue("$type must not constrain order", graph.hardEdges.isEmpty())
        }
    }

    @Test
    fun `an unconfirmed suggested dependency stays inactive`() {
        val graph = RelationGraph(
            listOf(edge(source = a, target = b, confirmed = false, createdBy = RelationCreatedBy.SUGGESTED)),
        )
        assertTrue(graph.hardEdges.isEmpty())
        assertFalse(graph.mustFollow(a, b))
    }

    @Test
    fun `a cycle is detected and reported as a path`() {
        val cycle = RelationGraph(
            listOf(
                edge(source = a, target = b),
                edge(source = b, target = c),
                edge(source = c, target = a),
            ),
        ).findHardDependencyCycle()

        // A cycle is reported as a closed path: first node repeats as the last, and every
        // node in the loop appears. Which node it starts at depends on traversal order, so
        // the assertion is about the shape, not about one particular start.
        assertTrue(cycle != null)
        val nodes = cycle!!.map { it.value }
        assertEquals(nodes.first(), nodes.last())
        assertEquals(setOf("A", "B", "C"), nodes.toSet())
    }

    @Test
    fun `a two node cycle is detected`() {
        val cycle = RelationGraph(
            listOf(edge(source = a, target = b), edge(source = b, target = a)),
        ).findHardDependencyCycle()
        assertTrue(cycle != null)
    }

    @Test
    fun `an acyclic graph reports no cycle`() {
        assertNull(
            RelationGraph(
                listOf(edge(source = a, target = c), edge(source = b, target = c)),
            ).findHardDependencyCycle(),
        )
    }

    @Test
    fun `a self edge is rejected before it can create a loop`() {
        val result = RelationGraph.validateNewEdge(emptyList(), edge(source = a, target = a))
        assertTrue((result as RelationValidation.Rejected).reason.contains("linked to itself"))
    }

    @Test
    fun `an edge that would close a cycle is rejected with the path`() {
        val existing = listOf(edge(source = a, target = b), edge(source = b, target = c))
        val result = RelationGraph.validateNewEdge(existing, edge(source = c, target = a))
        val rejected = result as RelationValidation.Rejected
        assertTrue(rejected.reason.contains("would create a loop"))
        assertTrue(rejected.reason.contains("A"))
    }

    @Test
    fun `a suggested hard dependency is accepted but flagged for confirmation`() {
        val result = RelationGraph.validateNewEdge(
            emptyList(),
            edge(source = a, target = b, confirmed = false, createdBy = RelationCreatedBy.SUGGESTED),
        )
        assertTrue(result is RelationValidation.Suggested)
    }

    @Test
    fun `topological order places prerequisites first`() {
        val order = RelationGraph(
            listOf(edge(source = a, target = b), edge(source = b, target = c)),
        ).topologicalOrder()
        assertEquals(listOf("C", "B", "A"), order.map { it.value })
    }

    @Test
    fun `topological order is deterministic for independent tasks`() {
        val graph = RelationGraph(emptyList())
        val first = graph.topologicalOrder()
        val second = RelationGraph(emptyList()).topologicalOrder()
        assertEquals(first, second)
    }

    @Test
    fun `topological order throws on a cycle rather than looping forever`() {
        val result = runCatching {
            RelationGraph(
                listOf(edge(source = a, target = b), edge(source = b, target = a)),
            ).topologicalOrder()
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun `prerequisites and dependents are reported for a task`() {
        val graph = RelationGraph(
            listOf(edge(source = a, target = b), edge(source = c, target = b)),
        )
        assertEquals(setOf(b), graph.prerequisitesOf(a))
        assertEquals(setOf(a, c), graph.dependentsOf(b))
    }
}