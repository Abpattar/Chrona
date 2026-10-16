package com.chrona.app.usecase

import com.chrona.app.data.repository.RelationshipRepository
import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.RelationCreatedBy
import com.chrona.app.domain.model.RelationId
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskRelationship
import com.chrona.app.domain.relation.RelationGraph
import com.chrona.app.domain.relation.RelationValidation

/**
 * Adds a relationship after checking it against the graph it would join.
 *
 * Self-edges and hard-dependency cycles are refused. A hard dependency that is merely
 * *suggested* (not user-confirmed) is accepted but stored inactive, so no consequential
 * order appears without an explicit confirmation step (REL-002).
 */
class AddRelationshipUseCase(
    private val repository: RelationshipRepository,
    private val timeSource: TimeSource,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(
        source: TaskId,
        target: TaskId,
        type: com.chrona.app.domain.model.RelationType,
        label: String? = null,
        explanation: String? = null,
        createdBy: RelationCreatedBy = RelationCreatedBy.USER,
    ): Outcome<TaskRelationship> {
        val now = timeSource.now()
        val candidate = TaskRelationship(
            id = RelationId(idGenerator.newId()),
            sourceTaskId = source,
            targetTaskId = target,
            type = type,
            label = label,
            explanation = explanation,
            createdBy = createdBy,
            // Only a user-created edge is active on creation. A suggestion stays dormant.
            confirmedAt = if (createdBy == RelationCreatedBy.USER) now else null,
            createdAt = now,
        )
        return when (val result = repository.listAll()) {
            is Outcome.Failed -> result
            is Outcome.Success -> {
                val duplicate = result.value.any {
                    it.sourceTaskId == source && it.targetTaskId == target && it.type == type
                }
                if (duplicate) {
                    Outcome.Failed(Failure.Validation(listOf("That link already exists.")))
                } else {
                    when (val validation = RelationGraph.validateNewEdge(result.value, candidate)) {
                        is RelationValidation.Rejected ->
                            Outcome.Failed(Failure.Validation(listOf(validation.reason)))

                        RelationValidation.Accepted, RelationValidation.Suggested ->
                            repository.upsert(candidate)
                    }
                }
            }
        }
    }
}

/**
 * Replaces the relationship only if the edited graph stays acyclic and self-edge free.
 */
class UpdateRelationshipUseCase(private val repository: RelationshipRepository) {
    suspend operator fun invoke(relationship: TaskRelationship): Outcome<TaskRelationship> =
        when (val result = repository.listAll()) {
            is Outcome.Failed -> result
            is Outcome.Success -> {
                val others = result.value.filterNot { it.id == relationship.id }
                when (val validation = RelationGraph.validateNewEdge(others, relationship)) {
                    is RelationValidation.Rejected ->
                        Outcome.Failed(Failure.Validation(listOf(validation.reason)))

                    RelationValidation.Accepted, RelationValidation.Suggested ->
                        repository.upsert(relationship)
                }
            }
        }
}

/** Deletes a relationship. Its endpoint tasks are untouched. */
class DeleteRelationshipUseCase(private val repository: RelationshipRepository) {
    suspend operator fun invoke(id: RelationId): Outcome<Unit> = repository.delete(id)
}

/**
 * Activates a suggested edge, after re-validating it against the graph as it stands now.
 *
 * Re-validation matters: the graph may have gained a cycle since the suggestion was made, in
 * which case activating it would break the scheduler's acyclicity guarantee (REL-001).
 */
class ConfirmRelationshipUseCase(
    private val repository: RelationshipRepository,
    private val timeSource: TimeSource,
) {
    suspend operator fun invoke(id: RelationId): Outcome<TaskRelationship> =
        when (val result = repository.listAll()) {
            is Outcome.Failed -> result
            is Outcome.Success -> {
                val existing = result.value.find { it.id == id }
                    ?: return Outcome.Failed(Failure.NotFound)
                val confirmed = existing.copy(confirmedAt = timeSource.now())
                val others = result.value.filterNot { it.id == id }
                when (val validation = RelationGraph.validateNewEdge(others, confirmed)) {
                    is RelationValidation.Rejected ->
                        Outcome.Failed(Failure.Validation(listOf(validation.reason)))

                    RelationValidation.Accepted, RelationValidation.Suggested ->
                        repository.upsert(confirmed)
                }
            }
        }
}
