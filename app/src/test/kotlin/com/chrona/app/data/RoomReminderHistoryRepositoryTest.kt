package com.chrona.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chrona.app.data.local.ChronaDatabase
import com.chrona.app.data.repository.RoomReminderHistoryRepository
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.reminder.ReminderOutcome
import com.chrona.app.domain.reminder.ReminderPresentation
import com.chrona.app.domain.reminder.ReminderSurfaceType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/**
 * Proves the one query the non-urgent cap depends on: it must count distinct *cards* on a local
 * date, ignoring suppressed rows and anything that is not `LOW`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoomReminderHistoryRepositoryTest {

    private lateinit var database: ChronaDatabase
    private lateinit var history: RoomReminderHistoryRepository

    private val day: LocalDate = LocalDate.of(2026, 10, 15)
    private val now: Instant = Instant.parse("2026-10-15T08:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ChronaDatabase::class.java,
        ).allowMainThreadQueries().build()
        history = RoomReminderHistoryRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun row(
        id: String,
        taskId: String,
        outcome: ReminderOutcome,
        priority: Priority,
        batchGroup: String?,
        localDate: LocalDate = day,
    ) = ReminderPresentation(
        id = id,
        taskId = TaskId(taskId),
        scheduledFor = now,
        surfacedAt = if (outcome == ReminderOutcome.SHOWN) now else null,
        surfaceType = if (outcome == ReminderOutcome.SHOWN) {
            ReminderSurfaceType.NOTIFICATION
        } else {
            ReminderSurfaceType.NONE
        },
        outcome = outcome,
        reasonCode = "REMINDER_TEST",
        batchGroup = batchGroup,
        localDate = localDate,
        priority = priority,
        createdAt = now,
    )

    @Test
    fun `entered rows survive a round trip`() = runTest {
        history.record(row("r1", "t1", ReminderOutcome.SHOWN, Priority.LOW, "card-1"))

        val stored = (history.historyFor(setOf(TaskId("t1"))) as Outcome.Success).value
        assertEquals(1, stored.size)
        assertEquals(ReminderOutcome.SHOWN, stored.single().outcome)
        assertEquals("card-1", stored.single().batchGroup)
    }

    @Test
    fun `the cap counts distinct non-urgent cards, not rows`() = runTest {
        // One card covering two tasks: two rows, one card.
        history.recordAll(
            listOf(
                row("r1", "t1", ReminderOutcome.SHOWN, Priority.LOW, "card-1"),
                row("r2", "t2", ReminderOutcome.SHOWN, Priority.LOW, "card-1"),
            ),
        )
        history.record(row("r3", "t3", ReminderOutcome.SHOWN, Priority.LOW, "card-2"))

        val count = (history.nonUrgentSurfacedOn(day) as Outcome.Success).value
        assertEquals(2, count)
    }

    @Test
    fun `urgent and suppressed rows do not count towards the cap`() = runTest {
        history.record(row("r1", "t1", ReminderOutcome.SHOWN, Priority.CRITICAL, "card-1"))
        history.record(row("r2", "t2", ReminderOutcome.SUPPRESSED, Priority.LOW, null))
        history.record(row("r3", "t3", ReminderOutcome.SHOWN, Priority.LOW, "card-2"))

        val count = (history.nonUrgentSurfacedOn(day) as Outcome.Success).value
        assertEquals(1, count)
    }

    @Test
    fun `only the requested local date is counted`() = runTest {
        history.record(row("r1", "t1", ReminderOutcome.SHOWN, Priority.LOW, "card-1"))
        history.record(
            row("r2", "t2", ReminderOutcome.SHOWN, Priority.LOW, "card-2", day.plusDays(1)),
        )

        val count = (history.nonUrgentSurfacedOn(day) as Outcome.Success).value
        assertEquals(1, count)
    }
}
