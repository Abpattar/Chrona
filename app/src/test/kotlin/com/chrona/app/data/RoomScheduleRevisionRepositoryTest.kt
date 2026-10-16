package com.chrona.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chrona.app.data.local.ChronaDatabase
import com.chrona.app.data.repository.ReasonCode
import com.chrona.app.data.repository.RoomScheduleRevisionRepository
import com.chrona.app.data.repository.RoomTaskRepository
import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskStatus
import com.chrona.app.domain.recovery.ScheduleRevisionReason
import com.chrona.app.domain.scheduling.ScheduleBasis
import com.chrona.app.domain.scheduling.SchedulePlacement
import com.chrona.app.domain.scheduling.ScheduleProposal
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
 * Room integration for schedule revisions.
 *
 * Proves the invariants a fake cannot: apply and undo really are transactional, placements
 * cascade with their revision, accepting a plan writes status history, and an undo is refused
 * once newer work exists (AT-016, AT-017, AT-031).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoomScheduleRevisionRepositoryTest {

    private lateinit var database: ChronaDatabase
    private lateinit var tasks: RoomTaskRepository
    private lateinit var revisions: RoomScheduleRevisionRepository

    private val start: Instant = Instant.parse("2026-10-15T08:00:00Z")
    private val zStart: ZonedDateTime = ZonedDateTime.of(2026, 10, 15, 10, 0, 0, 0, ZoneOffset.UTC)

    private var idCounter = 0

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ChronaDatabase::class.java,
        ).allowMainThreadQueries().build()
        tasks = RoomTaskRepository(database)
        revisions = RoomScheduleRevisionRepository(database, newId = { "rev-${++idCounter}" })
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun task(id: String = "task-1", updatedAt: Instant = start) = Task(
        id = TaskId(id),
        title = "Study for GATE",
        priority = Priority.CRITICAL,
        estimatedDuration = Duration.ofHours(2),
        createdAt = start,
        updatedAt = updatedAt,
    )

    private fun proposal(taskVersion: Long = 1L) = ScheduleProposal(
        proposalId = "p1",
        basis = ScheduleBasis(null, mapOf(TaskId("task-1") to taskVersion)),
        placements = listOf(SchedulePlacement(TaskId("task-1"), zStart, zStart.plusHours(1), 0)),
        unplaced = emptyList(),
        conflicts = emptyList(),
        changes = emptyList(),
        assumptions = emptyList(),
        overloaded = false,
        explanation = "",
        policyVersion = "p04-v1",
    )

    @Test
    fun `apply stores the revision, its placements and the status history`() = runTest {
        tasks.insert(task())

        val applied = revisions.apply(proposal(), null, ScheduleRevisionReason.APPLIED, start)
        assertTrue(applied is Outcome.Success)

        val current = (revisions.current() as Outcome.Success).value!!
        assertEquals(1, current.placements.size)
        assertEquals(zStart, current.placements.single().start)

        val stored = (tasks.get(TaskId("task-1")) as Outcome.Success).value!!
        assertEquals(TaskStatus.PLANNED, stored.status)
        assertTrue(
            database.taskStatusLogDao().findForTask("task-1")
                .any { it.reasonCode == ReasonCode.SCHEDULE_APPLIED },
        )
    }

    @Test
    fun `a stale expected revision is refused and nothing is written`() = runTest {
        tasks.insert(task())
        revisions.apply(proposal(), null, ScheduleRevisionReason.APPLIED, start)

        // expectedRevision is now stale (null, but a revision exists).
        val second = revisions.apply(proposal(), null, ScheduleRevisionReason.APPLIED, start)

        assertTrue((second as Outcome.Failed).failure is Failure.Conflict)
        assertEquals(1, database.scheduleRevisionDao().count())
    }

    @Test
    fun `a proposal built from an outdated task version is refused`() = runTest {
        tasks.insert(task())

        val stale = revisions.apply(proposal(taskVersion = 99L), null, ScheduleRevisionReason.APPLIED, start)

        assertTrue((stale as Outcome.Failed).failure is Failure.Conflict)
        assertEquals(0, database.scheduleRevisionDao().count())
    }

    @Test
    fun `undo restores the task and removes the revision`() = runTest {
        tasks.insert(task())
        val applied = revisions.apply(proposal(), null, ScheduleRevisionReason.APPLIED, start)
        val revisionId = (applied as Outcome.Success).value

        val undone = revisions.undo(revisionId, start.plusSeconds(60))
        assertTrue(undone is Outcome.Success)
        assertNull((revisions.current() as Outcome.Success).value)

        val stored = (tasks.get(TaskId("task-1")) as Outcome.Success).value!!
        assertEquals(TaskStatus.INBOX, stored.status)
        assertTrue(
            database.taskStatusLogDao().findForTask("task-1")
                .any { it.reasonCode == ReasonCode.SCHEDULE_UNDONE },
        )
        assertEquals(0, database.scheduleRevisionDao().placementCount())
    }

    @Test
    fun `undo is refused once a task was edited after the plan`() = runTest {
        tasks.insert(task())
        val applied = revisions.apply(proposal(), null, ScheduleRevisionReason.APPLIED, start)
        val revisionId = (applied as Outcome.Success).value

        val stored = (tasks.get(TaskId("task-1")) as Outcome.Success).value!!
        tasks.update(stored.copy(title = "Edited", updatedAt = start.plusSeconds(3600)), stored.version)

        val result = revisions.undo(revisionId, start.plusSeconds(7200))

        assertTrue((result as Outcome.Failed).failure is Failure.Conflict)
        assertTrue(database.scheduleRevisionDao().count() == 1)
    }

    @Test
    fun `undo of an unknown revision reports not found`() = runTest {
        val result = revisions.undo(com.chrona.app.domain.model.RevisionId("ghost"), start)
        assertTrue((result as Outcome.Failed).failure is Failure.NotFound)
    }
}
