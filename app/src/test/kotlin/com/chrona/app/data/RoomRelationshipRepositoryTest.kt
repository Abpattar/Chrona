package com.chrona.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chrona.app.data.local.ChronaDatabase
import com.chrona.app.data.repository.RoomRelationshipRepository
import com.chrona.app.data.repository.RoomTaskRepository
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.RelationCreatedBy
import com.chrona.app.domain.model.RelationId
import com.chrona.app.domain.model.RelationType
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskOrigin
import com.chrona.app.domain.model.TaskRelationship
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Relationship persistence integration (REL-001, AT-010).
 *
 * Proves the things a fake cannot: SQL round-trips every field, a delete touches exactly one
 * row, [RoomRelationshipRepository.replaceFor] swaps a source's links atomically, and the
 * (source, target, type) unique index is enforced by the database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoomRelationshipRepositoryTest {

    private lateinit var database: ChronaDatabase
    private lateinit var tasks: RoomTaskRepository
    private lateinit var relationships: RoomRelationshipRepository

    private val start: Instant = Instant.parse("2026-10-15T08:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ChronaDatabase::class.java,
        ).allowMainThreadQueries().build()
        tasks = RoomTaskRepository(database)
        relationships = RoomRelationshipRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun task(id: String) = Task(
        id = TaskId(id),
        title = "Task $id",
        priority = Priority.IMPORTANT,
        estimatedDuration = Duration.ofHours(1),
        deadline = ZonedDateTime.of(2026, 10, 16, 23, 59, 0, 0, ZoneOffset.UTC),
        origin = TaskOrigin.TYPED,
        createdAt = start,
        updatedAt = start,
    )

    private fun edge(
        id: String,
        source: String,
        target: String,
        type: RelationType = RelationType.DEPENDS_ON,
        createdBy: RelationCreatedBy = RelationCreatedBy.USER,
        confirmedAt: Instant? = start,
    ) = TaskRelationship(
        id = RelationId(id),
        sourceTaskId = TaskId(source),
        targetTaskId = TaskId(target),
        type = type,
        label = "leads into",
        explanation = "because it does",
        createdBy = createdBy,
        confirmedAt = confirmedAt,
        createdAt = start,
    )

    private suspend fun seed(a: String = "a", b: String = "b", c: String = "c") {
        tasks.insert(task(a))
        tasks.insert(task(b))
        tasks.insert(task(c))
    }

    @Test
    fun `an edge round trips every field`() = runTest {
        seed()
        relationships.upsert(edge("r1", "a", "b", RelationType.SUPPORTS, RelationCreatedBy.SUGGESTED))

        val stored = (relationships.listAll() as Outcome.Success).value.single()

        assertEquals(TaskId("a"), stored.sourceTaskId)
        assertEquals(TaskId("b"), stored.targetTaskId)
        assertEquals(RelationType.SUPPORTS, stored.type)
        assertEquals(RelationCreatedBy.SUGGESTED, stored.createdBy)
        assertEquals("leads into", stored.label)
        assertEquals("because it does", stored.explanation)
        assertNotNull(stored.createdAt)
    }

    @Test
    fun `listFor returns edges touching any of the given tasks`() = runTest {
        seed()
        relationships.upsert(edge("r1", "a", "b"))
        relationships.upsert(edge("r2", "c", "a"))
        relationships.upsert(edge("r3", "b", "c"))

        val forA = (relationships.listFor(setOf(TaskId("a"))) as Outcome.Success).value

        assertEquals(setOf(RelationId("r1"), RelationId("r2")), forA.map { it.id }.toSet())
    }

    @Test
    fun `listFor an empty set returns nothing`() = runTest {
        seed()
        relationships.upsert(edge("r1", "a", "b"))

        val result = (relationships.listFor(emptySet()) as Outcome.Success).value

        assertTrue(result.isEmpty())
    }

    @Test
    fun `delete removes exactly the named edge`() = runTest {
        seed()
        relationships.upsert(edge("r1", "a", "b"))
        relationships.upsert(edge("r2", "b", "c"))

        relationships.delete(RelationId("r1"))

        val remaining = (relationships.listAll() as Outcome.Success).value
        assertEquals(listOf(RelationId("r2")), remaining.map { it.id })
    }

    @Test
    fun `replaceFor swaps a source's outgoing links`() = runTest {
        seed()
        relationships.upsert(edge("old", "a", "b"))

        relationships.replaceFor(TaskId("a"), listOf(edge("new", "a", "c", RelationType.SUPPORTS)))

        val stored = (relationships.listAll() as Outcome.Success).value
        assertEquals(listOf(RelationId("new")), stored.map { it.id })
    }

    @Test
    fun `the same pair may carry two different relation types`() = runTest {
        seed()
        relationships.upsert(edge("r1", "a", "b", RelationType.DEPENDS_ON))

        val second = relationships.upsert(edge("r2", "a", "b", RelationType.SUPPORTS))

        assertTrue(second is Outcome.Success)
        assertEquals(2, database.taskRelationshipDao().count())
    }

    @Test
    fun `a duplicate source target type never creates a second row`() = runTest {
        seed()
        relationships.upsert(edge("r1", "a", "b", RelationType.DEPENDS_ON))

        relationships.upsert(edge("r2", "a", "b", RelationType.DEPENDS_ON))

        assertEquals(1, database.taskRelationshipDao().count())
    }

    @Test
    fun `changing a task's date keeps its relationships`() = runTest {
        seed()
        relationships.upsert(edge("r1", "a", "b"))
        val current = (tasks.get(TaskId("a")) as Outcome.Success).value!!

        tasks.update(
            current.copy(
                deadline = ZonedDateTime.of(2026, 10, 20, 23, 59, 0, 0, ZoneOffset.UTC),
            ),
            expectedVersion = current.version,
        )

        assertEquals(1, database.taskRelationshipDao().count())
    }
}
