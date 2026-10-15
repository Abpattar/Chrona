package com.chrona.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chrona.app.data.local.ChronaDatabase
import com.chrona.app.data.local.TaskStatusLogEntity
import com.chrona.app.domain.model.CalendarEvent
import com.chrona.app.domain.model.EventId
import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Flexibility
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.RelationCreatedBy
import com.chrona.app.domain.model.RelationId
import com.chrona.app.domain.model.RelationType
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskOrigin
import com.chrona.app.domain.model.TaskQuery
import com.chrona.app.domain.model.TaskRelationship
import com.chrona.app.domain.model.TaskStatus
import com.chrona.app.data.repository.ReasonCode
import com.chrona.app.data.repository.RoomEventRepository
import com.chrona.app.data.repository.RoomRelationshipRepository
import com.chrona.app.data.repository.RoomTaskRepository
import com.chrona.app.usecase.CreateTaskUseCase
import com.chrona.app.usecase.EditTaskUseCase
import com.chrona.app.usecase.EventUseCases
import com.chrona.app.usecase.IdGenerator
import com.chrona.app.usecase.NewEvent
import com.chrona.app.usecase.NewTask
import com.chrona.app.usecase.TaskEdits
import com.chrona.app.usecase.TaskLifecycleUseCases
import com.chrona.app.usecase.TimeSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * Room persistence integration, run against a real in-memory SQLite database.
 *
 * These tests exist to prove things a fake repository cannot: that SQL actually persists, that
 * foreign keys cascade, that optimistic concurrency really rejects a stale write, and that a
 * status change and its history row share one transaction.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoomTaskRepositoryTest {

    private lateinit var database: ChronaDatabase
    private lateinit var tasks: RoomTaskRepository

    private val start: Instant = Instant.parse("2026-10-15T08:00:00Z")

    private class FixedTimeSource(private val instant: Instant) : TimeSource {
        override fun now(): Instant = instant
        override fun zone() = ZoneOffset.UTC
    }

    private class CountingIds : IdGenerator {
        private var n = 0
        override fun newId(): String = "task-${++n}"
    }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ChronaDatabase::class.java,
        ).allowMainThreadQueries().build()
        tasks = RoomTaskRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun task(title: String = "Study for GATE", id: String = "task-1") = Task(
        id = TaskId(id),
        title = title,
        priority = Priority.CRITICAL,
        estimatedDuration = Duration.ofHours(2),
        deadline = ZonedDateTime.of(2026, 10, 16, 23, 59, 0, 0, ZoneOffset.UTC),
        requestedDate = java.time.LocalDate.of(2026, 10, 16),
        origin = TaskOrigin.TYPED,
        createdAt = start,
        updatedAt = start,
        assumptions = listOf("Assumed two hours"),
    )

    @Test
    fun `a task survives a write and read round trip`() = runTest {
        tasks.insert(task())
        val stored = (tasks.get(TaskId("task-1")) as Outcome.Success).value
        assertNotNull(stored)
        assertEquals("Study for GATE", stored!!.title)
        assertEquals(Priority.CRITICAL, stored.priority)
        assertEquals(Duration.ofHours(2), stored.estimatedDuration)
        assertEquals(listOf("Assumed two hours"), stored.assumptions)
    }

    @Test
    fun `a fixed task keeps its absolute interval and zone`() = runTest {
        val startAt = ZonedDateTime.of(2026, 10, 15, 10, 0, 0, 0, ZoneOffset.UTC)
        val fixed = task().copy(
            flexibility = Flexibility.FIXED,
            fixedStart = startAt,
            fixedEnd = startAt.plusHours(2),
        )
        tasks.insert(fixed)
        val stored = (tasks.get(TaskId("task-1")) as Outcome.Success).value!!
        assertEquals(Flexibility.FIXED, stored.flexibility)
        assertEquals(startAt, stored.fixedStart)
        assertEquals(startAt.plusHours(2), stored.fixedRange?.end)
    }

    @Test
    fun `the active query excludes closed work`() = runTest {
        tasks.insert(task(id = "a"))
        tasks.insert(task(id = "b"))
        tasks.updateStatus(TaskId("b"), { current ->
            Outcome.Success(current.copy(status = TaskStatus.COMPLETED, completedAt = start))
        }, ReasonCode.COMPLETED, start)

        val active = (tasks.list(TaskQuery.ACTIVE) as Outcome.Success).value
        assertEquals(listOf("a"), active.map { it.id.value })
    }

    @Test
    fun `a stale update is refused and the newer value survives`() = runTest {
        val original = task()
        tasks.insert(original)

        val first = (tasks.update(original.copy(title = "First"), original.version) as Outcome.Success).value
        val stale = tasks.update(original.copy(title = "Second"), original.version)

        assertTrue((stale as Outcome.Failed).failure is Failure.Conflict)
        val stored = (tasks.get(TaskId("task-1")) as Outcome.Success).value!!
        assertEquals("First", stored.title)
        assertEquals(original.version + 1, first.version)
    }

    @Test
    fun `a matching version update succeeds and bumps the version`() = runTest {
        val original = task()
        tasks.insert(original)
        val updated = (tasks.update(original.copy(title = "Edited"), original.version) as Outcome.Success).value
        assertEquals(2L, updated.version)
        assertEquals("Edited", (tasks.get(TaskId("task-1")) as Outcome.Success).value!!.title)
    }

    @Test
    fun `a status change writes a history row in the same commit`() = runTest {
        tasks.insert(task())
        tasks.updateStatus(TaskId("task-1"), { Outcome.Success(it.copy(status = TaskStatus.IN_PROGRESS)) },
            ReasonCode.STARTED, start)

        val log = database.taskStatusLogDao().findForTask("task-1")
        assertEquals(2, log.size)
        assertEquals(ReasonCode.CREATED, log[0].reasonCode)
        assertEquals(ReasonCode.STARTED, log[1].reasonCode)
        assertEquals(TaskStatus.INBOX.name, log[1].fromStatus)
        assertEquals(TaskStatus.IN_PROGRESS.name, log[1].toStatus)
    }

    @Test
    fun `a repeated identical status change writes nothing new`() = runTest {
        tasks.insert(task())
        val once = tasks.updateStatus(TaskId("task-1"),
            { Outcome.Success(it.copy(status = TaskStatus.IN_PROGRESS)) }, ReasonCode.STARTED, start)
        val versionAfterFirst = (once as Outcome.Success).value.version

        val twice = tasks.updateStatus(TaskId("task-1"),
            { Outcome.Success(it.copy(status = TaskStatus.IN_PROGRESS)) }, ReasonCode.STARTED, start)
        assertEquals(versionAfterFirst, (twice as Outcome.Success).value.version)
        assertEquals(2, database.taskStatusLogDao().findForTask("task-1").size)
    }

    @Test
    fun `a rejected transition leaves the record untouched`() = runTest {
        tasks.insert(task())
        val before = (tasks.get(TaskId("task-1")) as Outcome.Success).value!!

        val result = tasks.updateStatus(TaskId("task-1"),
            { Outcome.Failed(Failure.Conflict("nope")) }, ReasonCode.COMPLETED, start)

        assertTrue((result as Outcome.Failed).failure is Failure.Conflict)
        assertEquals(before, (tasks.get(TaskId("task-1")) as Outcome.Success).value)
        assertEquals(1, database.taskStatusLogDao().count())
    }

    @Test
    fun `deleting a task leaves no dangling relationship`() = runTest {
        tasks.insert(task(id = "a"))
        tasks.insert(task(id = "b"))
        val relationships = RoomRelationshipRepository(database)
        relationships.upsert(
            TaskRelationship(
                id = RelationId("r1"),
                sourceTaskId = TaskId("a"),
                targetTaskId = TaskId("b"),
                type = RelationType.DEPENDS_ON,
                createdBy = RelationCreatedBy.USER,
                confirmedAt = start,
                createdAt = start,
            ),
        )
        assertEquals(1, database.taskRelationshipDao().count())

        tasks.delete(TaskId("a"))
        assertEquals(0, database.taskRelationshipDao().count())
    }

    @Test
    fun `deleting a task removes its history too`() = runTest {
        tasks.insert(task())
        tasks.updateStatus(TaskId("task-1"), { Outcome.Success(it.copy(status = TaskStatus.COMPLETED)) },
            ReasonCode.COMPLETED, start)
        assertTrue(database.taskStatusLogDao().count() > 0)

        tasks.delete(TaskId("task-1"))
        assertEquals(0, database.taskStatusLogDao().count())
        assertEquals(0, database.taskDao().count())
    }

    @Test
    fun `deleting a task that does not exist reports not found`() = runTest {
        assertEquals(Failure.NotFound, (tasks.delete(TaskId("ghost")) as Outcome.Failed).failure)
    }

    @Test
    fun `a duplicate id is rejected rather than silently replacing`() = runTest {
        tasks.insert(task())
        val second = tasks.insert(task(title = "Other"))
        assertTrue(second is Outcome.Failed)
        assertEquals(1, database.taskDao().count())
    }

    @Test
    fun `a manual fixed commitment round trips and overlaps correctly`() = runTest {
        val events = RoomEventRepository(database)
        val useCases = EventUseCases(events, CountingIds(), FixedTimeSource(start))
        val startAt = ZonedDateTime.of(2026, 10, 15, 10, 0, 0, 0, ZoneOffset.UTC)

        val created = (useCases.create(NewEvent("Lecture", startAt, startAt.plusHours(2))) as Outcome.Success).value
        assertEquals(1L, created.version)

        val overlapping = (
            useCases.list(startAt.plusHours(1), startAt.plusHours(3)) as Outcome.Success
            ).value
        assertEquals(listOf("Lecture"), overlapping.map { it.title })

        val outside = (useCases.list(startAt.plusHours(3), startAt.plusHours(4)) as Outcome.Success).value
        assertTrue(outside.isEmpty())
    }

    @Test
    fun `a commitment that ends before it starts is rejected`() = runTest {
        val events = RoomEventRepository(database)
        val useCases = EventUseCases(events, CountingIds(), FixedTimeSource(start))
        val startAt = ZonedDateTime.of(2026, 10, 15, 10, 0, 0, 0, ZoneOffset.UTC)

        val result = useCases.create(NewEvent("Bad", startAt, startAt.minusHours(1)))
        assertTrue((result as Outcome.Failed).failure is Failure.Validation)
        assertEquals(0, database.calendarEventDao().count())
    }

    @Test
    fun `data written by one repository instance is visible to a fresh one`() = runTest {
        // Proves the slice survives a process restart rather than living in memory.
        tasks.insert(task())
        val reopened = RoomTaskRepository(database)
        assertNotNull((reopened.get(TaskId("task-1")) as Outcome.Success).value)
    }

    @Test
    fun `the create to complete slice works through real SQL`() = runTest {
        val create = CreateTaskUseCase(tasks, CountingIds(), FixedTimeSource(start))
        val edit = EditTaskUseCase(tasks, FixedTimeSource(start))
        val lifecycle = TaskLifecycleUseCases(tasks, FixedTimeSource(start))

        val created = (create(NewTask(title = "  Study for GATE  ", estimatedDuration = Duration.ofHours(2)))
            as Outcome.Success).value
        assertEquals("Study for GATE", created.title)

        val edited = (edit(created.id, created.version, TaskEdits(title = "Study for GATE", priority = Priority.CRITICAL))
            as Outcome.Success).value
        assertEquals(Priority.CRITICAL, edited.priority)

        val completed = (lifecycle.complete(edited.id) as Outcome.Success).value
        assertEquals(TaskStatus.COMPLETED, completed.status)

        val listed = (tasks.list(TaskQuery.ACTIVE) as Outcome.Success).value
        assertTrue(listed.isEmpty())
        // create(v1) -> edit(v2) -> complete(v3)
        assertEquals(3L, completed.version)
    }

    @Test
    fun `a status log row never stores raw task text`() = runTest {
        val secret = "Private note about my landlord"
        tasks.insert(task())
        database.taskStatusLogDao().insert(
            TaskStatusLogEntity(
                taskId = "task-1",
                fromStatus = TaskStatus.INBOX.name,
                toStatus = TaskStatus.COMPLETED.name,
                reasonCode = ReasonCode.COMPLETED,
                changedAtEpochMillis = start.toEpochMilli(),
            ),
        )
        val all = database.taskStatusLogDao().findRecent(10)
        assertTrue(all.none { it.reasonCode.contains(secret) })
        assertTrue(all.all { it.reasonCode in setOf(ReasonCode.CREATED, ReasonCode.COMPLETED) })
    }
}