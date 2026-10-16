package com.chrona.app.usecase

import com.chrona.app.data.repository.RelationshipRepository
import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.RelationCreatedBy
import com.chrona.app.domain.model.RelationId
import com.chrona.app.domain.model.RelationType
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskRelationship
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Relationship editing rules (REL-001, REL-002, AT-010, AT-011).
 *
 * The graph itself is tested in `RelationGraphTest`; these tests prove the use cases refuse
 * invalid writes and never create a silent consequential dependency.
 */
class RelationshipUseCasesTest {

    private val a = TaskId("A")
    private val b = TaskId("B")
    private val c = TaskId("C")

    /** In-memory relationship store; the real one is covered by the Room test. */
    class FakeRelationshipRepository : RelationshipRepository {
        val rows = linkedMapOf<String, TaskRelationship>()

        override suspend fun listFor(taskIds: Set<TaskId>): Outcome<List<TaskRelationship>> =
            Outcome.Success(
                rows.values.filter { it.sourceTaskId in taskIds || it.targetTaskId in taskIds },
            )

        override suspend fun listAll(): Outcome<List<TaskRelationship>> = Outcome.Success(rows.values.toList())

        override suspend fun upsert(relationship: TaskRelationship): Outcome<TaskRelationship> {
            rows[relationship.id.value] = relationship
            return Outcome.Success(relationship)
        }

        override suspend fun delete(relationId: RelationId): Outcome<Unit> {
            rows.remove(relationId.value) ?: return Outcome.Failed(Failure.NotFound)
            return Outcome.Success(Unit)
        }

        override suspend fun replaceFor(source: TaskId, links: List<TaskRelationship>): Outcome<Unit> {
            rows.values.removeAll { it.sourceTaskId == source }
            links.forEach { rows[it.id.value] = it }
            return Outcome.Success(Unit)
        }
    }

    private val idGenerator = Fixtures.SequentialIdGenerator()

    private fun addUseCase(repo: FakeRelationshipRepository) = AddRelationshipUseCase(
        repo,
        Fixtures.FixedTimeSource(),
        idGenerator,
    )

    @Test
    fun `a user dependency is stored active and ordered`() = runTest {
        val repo = FakeRelationshipRepository()

        val result = addUseCase(repo)(a, b, RelationType.DEPENDS_ON)

        val stored = (result as Outcome.Success).value
        assertNotNull(stored.confirmedAt)
        assertTrue(stored.isActiveHardDependency)
        assertEquals(1, repo.rows.size)
    }

    @Test
    fun `a self link is refused and nothing is written`() = runTest {
        val repo = FakeRelationshipRepository()

        val result = addUseCase(repo)(a, a, RelationType.DEPENDS_ON)

        assertTrue(result is Outcome.Failed)
        assertTrue((result as Outcome.Failed).failure is Failure.Validation)
        assertTrue(repo.rows.isEmpty())
    }

    @Test
    fun `a dependency that would close a cycle is refused with the path`() = runTest {
        val repo = FakeRelationshipRepository()
        addUseCase(repo)(a, b, RelationType.DEPENDS_ON)
        addUseCase(repo)(b, c, RelationType.DEPENDS_ON)

        val result = addUseCase(repo)(c, a, RelationType.DEPENDS_ON)

        val failure = (result as Outcome.Failed).failure as Failure.Validation
        assertTrue(failure.reasons.single().contains("loop"))
        assertEquals(2, repo.rows.size)
    }

    @Test
    fun `a suggested hard dependency is stored dormant`() = runTest {
        val repo = FakeRelationshipRepository()

        val stored = (addUseCase(repo)(
            a,
            b,
            RelationType.DEPENDS_ON,
            createdBy = RelationCreatedBy.SUGGESTED,
        ) as Outcome.Success).value

        assertNull(stored.confirmedAt)
        assertFalse(stored.isActiveHardDependency)
    }

    @Test
    fun `a supportive link never constrains order`() = runTest {
        val repo = FakeRelationshipRepository()

        val stored = (addUseCase(repo)(a, b, RelationType.SUPPORTS) as Outcome.Success).value

        assertFalse(stored.isActiveHardDependency)
    }

    @Test
    fun `adding the same link twice is refused`() = runTest {
        val repo = FakeRelationshipRepository()
        addUseCase(repo)(a, b, RelationType.SUPPORTS)

        val result = addUseCase(repo)(a, b, RelationType.SUPPORTS)

        val failure = (result as Outcome.Failed).failure as Failure.Validation
        assertTrue(failure.reasons.single().contains("already exists"))
        assertEquals(1, repo.rows.size)
    }

    @Test
    fun `updating a link so that it closes a cycle is refused`() = runTest {
        val repo = FakeRelationshipRepository()
        val aToB = (addUseCase(repo)(a, b, RelationType.DEPENDS_ON) as Outcome.Success).value
        addUseCase(repo)(b, c, RelationType.DEPENDS_ON)

        // Redirecting A -> B to C -> B leaves C -> B -> C.
        val result = UpdateRelationshipUseCase(repo)(
            aToB.copy(sourceTaskId = c, targetTaskId = b),
        )

        val failure = (result as Outcome.Failed).failure as Failure.Validation
        assertTrue(failure.reasons.single().contains("loop"))
    }

    @Test
    fun `confirming a suggested dependency activates it`() = runTest {
        val repo = FakeRelationshipRepository()
        val added = (addUseCase(repo)(
            a,
            b,
            RelationType.DEPENDS_ON,
            createdBy = RelationCreatedBy.SUGGESTED,
        ) as Outcome.Success).value

        val confirmed = (ConfirmRelationshipUseCase(repo, Fixtures.FixedTimeSource())(
            added.id,
        ) as Outcome.Success).value

        assertNotNull(confirmed.confirmedAt)
        assertTrue(confirmed.isActiveHardDependency)
    }

    @Test
    fun `confirming a dependency that now closes a cycle is refused`() = runTest {
        val repo = FakeRelationshipRepository()
        val suggested = (addUseCase(repo)(
            c,
            a,
            RelationType.DEPENDS_ON,
            createdBy = RelationCreatedBy.SUGGESTED,
        ) as Outcome.Success).value
        // A now depends on B and B depends on C, so C -> A would close a loop.
        addUseCase(repo)(a, b, RelationType.DEPENDS_ON)
        addUseCase(repo)(b, c, RelationType.DEPENDS_ON)

        val result = ConfirmRelationshipUseCase(repo, Fixtures.FixedTimeSource())(suggested.id)

        val failure = (result as Outcome.Failed).failure as Failure.Validation
        assertTrue(failure.reasons.single().contains("loop"))
    }

    @Test
    fun `deleting a link leaves the endpoints alone`() = runTest {
        val repo = FakeRelationshipRepository()
        val edge = (addUseCase(repo)(a, b, RelationType.SUPPORTS) as Outcome.Success).value

        val result = DeleteRelationshipUseCase(repo)(edge.id)

        assertTrue(result is Outcome.Success)
        assertTrue(repo.rows.isEmpty())
    }
}
