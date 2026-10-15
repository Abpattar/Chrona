package com.chrona.app.usecase

import com.chrona.app.data.repository.TaskRepository
import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskQuery
import com.chrona.app.domain.model.TaskStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicInteger

/** Deterministic seams shared by the use-case tests (CODING_CONVENTIONS.md). */
object Fixtures {
    val zone: ZoneId = ZoneOffset.UTC
    val start: Instant = Instant.parse("2026-10-15T08:00:00Z")

    class FixedTimeSource(private val instant: Instant = start, private val zoneId: ZoneId = zone) :
        TimeSource {
        override fun now(): Instant = instant
        override fun zone(): ZoneId = zoneId
    }

    class SequentialIdGenerator : IdGenerator {
        private val counter = AtomicInteger(0)
        override fun newId(): String = "id-${counter.incrementAndGet()}"
    }
}

/**
 * In-memory [TaskRepository] used by use-case tests.
 *
 * It is deliberately not a Room database: these tests are about use-case and state-machine
 * behavior, and persistence is covered separately by the Room integration tests.
 */
class FakeTaskRepository(
    private val clock: () -> Instant,
) : TaskRepository {

    val rows = linkedMapOf<String, Task>()

    override suspend fun get(taskId: TaskId): Outcome<Task?> = Outcome.Success(rows[taskId.value])

    override suspend fun list(query: TaskQuery): Outcome<List<Task>> = Outcome.Success(
        rows.values
            .filter { it.status in query.includeStatuses }
            .sortedWith(compareBy({ it.createdAt }, { it.id.value }))
            .let { all -> query.limit?.let { all.take(it) } ?: all },
    )

    override suspend fun insert(task: Task): Outcome<TaskId> {
        rows[task.id.value] = task
        return Outcome.Success(task.id)
    }

    override suspend fun update(task: Task, expectedVersion: Long): Outcome<Task> {
        val current = rows[task.id.value] ?: return Outcome.Failed(Failure.NotFound)
        if (current.version != expectedVersion) {
            return Outcome.Failed(Failure.Conflict("stale"))
        }
        val next = task.copy(version = expectedVersion + 1, updatedAt = clock())
        rows[task.id.value] = next
        return Outcome.Success(next)
    }

    override suspend fun delete(taskId: TaskId): Outcome<Unit> {
        rows.remove(taskId.value) ?: return Outcome.Failed(Failure.NotFound)
        return Outcome.Success(Unit)
    }

    override suspend fun updateStatus(
        taskId: TaskId,
        transition: (Task) -> Outcome<Task>,
        reasonCode: String,
        occurredAt: Instant,
    ): Outcome<Task> {
        val current = rows[taskId.value] ?: return Outcome.Failed(Failure.NotFound)
        return when (val result = transition(current)) {
            is Outcome.Failed -> result
            is Outcome.Success -> {
                val next = result.value.copy(version = current.version + 1, updatedAt = occurredAt)
                rows[taskId.value] = next
                Outcome.Success(next)
            }
        }
    }
}

class CreateTaskUseCaseTest {

    private val repository = FakeTaskRepository { Fixtures.start }
    private val create = CreateTaskUseCase(repository, Fixtures.SequentialIdGenerator(), Fixtures.FixedTimeSource())

    @Test
    fun `a typed task is created in the inbox with a stable id`() = runTest {
        val result = create(NewTask(title = "Study for GATE", estimatedDuration = Duration.ofHours(2)))
        val task = (result as Outcome.Success).value
        assertEquals("id-1", task.id.value)
        assertEquals(TaskStatus.INBOX, task.status)
        assertEquals(Fixtures.start, task.createdAt)
        assertEquals(1L, task.version)
    }

    @Test
    fun `each created task gets a distinct identity`() = runTest {
        val first = (create(NewTask(title = "One")) as Outcome.Success).value
        val second = (create(NewTask(title = "Two")) as Outcome.Success).value
        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `a blank title is rejected before anything is stored`() = runTest {
        val result = create(NewTask(title = "   "))
        assertTrue((result as Outcome.Failed).failure is Failure.Validation)
        assertTrue(repository.rows.isEmpty())
    }

    @Test
    fun `an invalid duration is rejected and nothing is persisted`() = runTest {
        val result = create(NewTask(title = "Bad", estimatedDuration = Duration.ZERO))
        assertTrue((result as Outcome.Failed).failure is Failure.Validation)
        assertTrue(repository.rows.isEmpty())
    }

    @Test
    fun `origin is recorded as typed for typed input`() = runTest {
        val task = (create(NewTask(title = "Typed")) as Outcome.Success).value
        assertEquals(com.chrona.app.domain.model.TaskOrigin.TYPED, task.origin)
    }

    @Test
    fun `assumptions are stored so the interpretation stays visible`() = runTest {
        val task = (
            create(NewTask(title = "Call mum", assumptions = listOf("Assumed today")))
            as Outcome.Success
            ).value
        assertEquals(listOf("Assumed today"), task.assumptions)
    }

    @Test
    fun `title is stored trimmed`() = runTest {
        val task = (create(NewTask(title = "  Study  ")) as Outcome.Success).value
        assertEquals("Study", task.title)
    }
}

class TaskLifecycleUseCasesTest {

    private val repository = FakeTaskRepository { Fixtures.start }
    private val lifecycle = TaskLifecycleUseCases(repository, Fixtures.FixedTimeSource())
    private val create = CreateTaskUseCase(repository, Fixtures.SequentialIdGenerator(), Fixtures.FixedTimeSource())

    private suspend fun seed(title: String = "Study"): TaskId {
        val created = (create(NewTask(title = title)) as Outcome.Success).value
        return created.id
    }

    @Test
    fun `completing a task sets the status and timestamp`() = runTest {
        val id = seed()
        val result = lifecycle.complete(id)
        val task = (result as Outcome.Success).value
        assertEquals(TaskStatus.COMPLETED, task.status)
        assertEquals(Fixtures.start, task.completedAt)
    }

    @Test
    fun `completing twice is idempotent and does not bump the version`() = runTest {
        val id = seed()
        val first = (lifecycle.complete(id) as Outcome.Success).value
        val second = (lifecycle.complete(id) as Outcome.Success).value
        assertEquals(first.version, second.version)
    }

    @Test
    fun `postponing records the requested time`() = runTest {
        val id = seed()
        val until = ZonedDateTime.of(2026, 10, 16, 9, 0, 0, 0, Fixtures.zone)
        val task = (lifecycle.postpone(id, until) as Outcome.Success).value
        assertEquals(TaskStatus.POSTPONED, task.status)
        assertEquals(until, task.postponedUntil)
    }

    @Test
    fun `postponing without a date still postpones`() = runTest {
        val id = seed()
        val task = (lifecycle.postpone(id, null) as Outcome.Success).value
        assertEquals(TaskStatus.POSTPONED, task.status)
        assertNull(task.postponedUntil)
    }

    @Test
    fun `a completed task cannot be started again`() = runTest {
        val id = seed()
        lifecycle.complete(id)
        val result = lifecycle.start(id)
        assertTrue((result as Outcome.Failed).failure is Failure.Conflict)
    }

    @Test
    fun `skipping and archiving are separate actions`() = runTest {
        val id = seed()
        assertEquals(TaskStatus.SKIPPED, (lifecycle.skip(id) as Outcome.Success).value.status)
        assertEquals(TaskStatus.ARCHIVED, (lifecycle.archive(id) as Outcome.Success).value.status)
    }

    @Test
    fun `an archived task can be restored to the list`() = runTest {
        val id = seed()
        lifecycle.archive(id)
        assertEquals(TaskStatus.INBOX, (lifecycle.restore(id) as Outcome.Success).value.status)
    }

    @Test
    fun `acting on a task that does not exist reports not found`() = runTest {
        val result = lifecycle.complete(TaskId("missing"))
        assertEquals(Failure.NotFound, (result as Outcome.Failed).failure)
    }
}

class EditTaskUseCaseTest {

    private val repository = FakeTaskRepository { Fixtures.start }
    private val create = CreateTaskUseCase(repository, Fixtures.SequentialIdGenerator(), Fixtures.FixedTimeSource())
    private val edit = EditTaskUseCase(repository, Fixtures.FixedTimeSource())

    @Test
    fun `an edit is applied when the version matches`() = runTest {
        val created = (create(NewTask(title = "Draft")) as Outcome.Success).value
        val result = edit(
            created.id,
            created.version,
            TaskEdits(title = "Final title", priority = Priority.CRITICAL),
        )
        val task = (result as Outcome.Success).value
        assertEquals("Final title", task.title)
        assertEquals(Priority.CRITICAL, task.priority)
        assertEquals(created.version + 1, task.version)
    }

    @Test
    fun `a stale edit is refused instead of overwriting newer work`() = runTest {
        val created = (create(NewTask(title = "Draft")) as Outcome.Success).value
        edit(created.id, created.version, TaskEdits(title = "First writer wins"))
        val stale = edit(created.id, created.version, TaskEdits(title = "Second writer loses"))

        assertTrue((stale as Outcome.Failed).failure is Failure.Conflict)
        assertEquals("First writer wins", repository.rows[created.id.value]?.title)
    }

    @Test
    fun `an edit that breaks a rule is rejected`() = runTest {
        val created = (create(NewTask(title = "Draft")) as Outcome.Success).value
        val result = edit(created.id, created.version, TaskEdits(title = "  "))
        assertTrue((result as Outcome.Failed).failure is Failure.Validation)
    }
}

class ListTasksUseCaseTest {

    private val repository = FakeTaskRepository { Fixtures.start }
    private val list = ListTasksUseCase(repository)
    private val create = CreateTaskUseCase(repository, Fixtures.SequentialIdGenerator(), Fixtures.FixedTimeSource())
    private val lifecycle = TaskLifecycleUseCases(repository, Fixtures.FixedTimeSource())

    @Test
    fun `the active list excludes completed work`() = runTest {
        val kept = (create(NewTask(title = "Keep")) as Outcome.Success).value
        val closed = (create(NewTask(title = "Close")) as Outcome.Success).value
        lifecycle.complete(closed.id)

        val tasks = (list() as Outcome.Success).value
        assertEquals(listOf(kept.id), tasks.map { it.id })
    }

    @Test
    fun `the closed list returns completed work`() = runTest {
        val closed = (create(NewTask(title = "Close")) as Outcome.Success).value
        lifecycle.complete(closed.id)

        val tasks = (list(TaskQuery.CLOSED) as Outcome.Success).value
        assertEquals(listOf(closed.id), tasks.map { it.id })
    }

    @Test
    fun `a limit truncates the result`() = runTest {
        repeat(3) { index -> create(NewTask(title = "Task $index")) }
        val tasks = (list(TaskQuery.ACTIVE.copy(limit = 2)) as Outcome.Success).value
        assertEquals(2, tasks.size)
    }
}