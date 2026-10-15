package com.chrona.app.domain.relation

import com.chrona.app.domain.model.RelationCreatedBy
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskRelationship

/**
 * Hard-dependency graph over tasks.
 *
 * Canonical direction: `source DEPENDS_ON target` means **source waits for target**, so
 * `target` must be scheduled first. This is the single implementation of that rule; the
 * board, the editor, validation and the scheduler (P04) all read it from here, so the
 * direction can never drift between surfaces.
 *
 * Only *confirmed* hard dependencies constrain order. `SUPPORTS`, `PART_OF_PROJECT`,
 * `SAME_EVENT` and `RELATED_REFERENCE` are informative links and are never treated as
 * blockers (REL-002).
 */
class RelationGraph(relationships: List<TaskRelationship> = emptyList()) {

    /** Only active hard dependencies participate in ordering. */
    val hardEdges: List<TaskRelationship> = relationships.filter { it.isActiveHardDependency }

    private val dependents: Map<TaskId, Set<TaskId>> = hardEdges
        .groupBy { it.targetTaskId }
        .mapValues { (_, edges) -> edges.map { it.sourceTaskId.value }.toSortedSet().map(::TaskId).toSet() }

    private val prerequisites: Map<TaskId, Set<TaskId>> = hardEdges
        .groupBy { it.sourceTaskId }
        .mapValues { (_, edges) -> edges.map { it.targetTaskId.value }.toSortedSet().map(::TaskId).toSet() }

    /** True when [task] must be scheduled before [other]. */
    fun mustPrecede(task: TaskId, other: TaskId): Boolean = task in prerequisites.neighbors(other)

    /** True when [task] must be scheduled after [other]. */
    fun mustFollow(task: TaskId, other: TaskId): Boolean = other in prerequisites.neighbors(task)

    fun prerequisitesOf(task: TaskId): Set<TaskId> = prerequisites.neighbors(task)

    fun dependentsOf(task: TaskId): Set<TaskId> = dependents.neighbors(task)

    /**
     * Detects a cycle in the confirmed hard-dependency graph.
     *
     * Returns the offending path in traversal order, or null when the graph is acyclic.
     * Iterative depth-first search with colour marking, so a cycle cannot cause unbounded
     * recursion (AT-011).
     */
    fun findHardDependencyCycle(): List<TaskId>? {
        val white = 0
        val grey = 1
        val black = 2
        val colour = mutableMapOf<TaskId, Int>()
        val path = mutableListOf<TaskId>()

        fun visit(node: TaskId): List<TaskId>? {
            colour[node] = grey
            path.add(node)
            for (next in prerequisites.neighbors(node)) {
                when (colour[next] ?: white) {
                    grey -> {
                        val start = path.indexOf(next)
                        return path.subList(start, path.size).toList() + next
                    }

                    white -> visit(next)?.let { return it }
                    else -> Unit
                }
            }
            path.removeAt(path.lastIndex)
            colour[node] = black
            return null
        }

        nodes().forEach { node ->
            if ((colour[node] ?: white) == white) {
                visit(node)?.let { return it }
            }
        }
        return null
    }

    /**
     * A deterministic topological order of the confirmed hard-dependency graph.
     *
     * Ties are broken by [TaskId] so the same input always produces the same output; the
     * scheduler relies on that (MASTER_ARCHITECTURE.md §5).
     */
    fun topologicalOrder(): List<TaskId> {
        findHardDependencyCycle()?.let {
            throw IllegalStateException("Hard dependency cycle: ${it.joinToString(" -> ") { id -> id.value }}")
        }

        val remaining = mutableMapOf<TaskId, Int>()
        nodes().forEach { remaining[it] = prerequisites.neighbors(it).size }

        // A sorted set, not a bare priority queue: it makes the TaskId tie-break explicit and
        // guarantees that independent tasks come out in a stable, reproducible order.
        val ready = java.util.TreeSet<TaskId>(compareBy { it.value })
        nodes().filter { remaining.getValue(it) == 0 }.forEach { ready.add(it) }
        val order = mutableListOf<TaskId>()

        while (true) {
            val next = ready.pollFirst() ?: break
            order.add(next)
            for (dependent in dependents.neighbors(next)) {
                val left = (remaining[dependent] ?: 0) - 1
                remaining[dependent] = left
                if (left == 0) ready.add(dependent)
            }
        }
        return order
    }

    private fun nodes(): List<TaskId> =
        (prerequisites.keys + dependents.keys).sortedBy { it.value }

    private fun Map<TaskId, Set<TaskId>>.neighbors(key: TaskId): Set<TaskId> = this[key] ?: emptySet()

    companion object {
        /**
         * Checks a candidate edge against the graph it would create.
         *
         * A suggested hard dependency is accepted but reported as [RelationValidation.Suggested]
         * so the UI can ask the user to confirm it. A suggested edge never constrains the
         * scheduler until `confirmedAt` is set.
         */
        fun validateNewEdge(
            existing: List<TaskRelationship>,
            candidate: TaskRelationship,
        ): RelationValidation = when {
            candidate.sourceTaskId == candidate.targetTaskId ->
                RelationValidation.Rejected("A task cannot be linked to itself.")

            candidate.type.isHardDependency &&
                candidate.confirmedAt == null &&
                candidate.createdBy == RelationCreatedBy.SUGGESTED ->
                RelationValidation.Suggested

            else -> {
                val projected = existing.filterNot { it.id == candidate.id } + candidate
                val cycle = RelationGraph(projected).findHardDependencyCycle()
                if (cycle == null) {
                    RelationValidation.Accepted
                } else {
                    RelationValidation.Rejected(
                        "That link would create a loop: " +
                            cycle.joinToString(" -> ") { it.value },
                    )
                }
            }
        }
    }
}

sealed interface RelationValidation {
    data object Accepted : RelationValidation

    /** Valid, but a consequential hard dependency the user must confirm before it constrains. */
    data object Suggested : RelationValidation

    data class Rejected(val reason: String) : RelationValidation
}