package com.chrona.app.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.chrona.app.data.local.ChronaDatabase
import com.chrona.app.data.repository.RoomSimulationStore
import com.chrona.app.domain.model.Flexibility
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskOrigin
import com.chrona.app.domain.model.TaskStatus
import com.chrona.app.domain.simulation.SimulationDataset
import com.chrona.app.domain.simulation.SimulationDayReport
import com.chrona.app.domain.simulation.SimulationEvent
import com.chrona.app.domain.simulation.SimulationEventKind
import com.chrona.app.domain.simulation.SimulationTaskFactory
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Schema v3→v4 and the Room-backed simulation store.
 *
 * The migration must be purely additive: real task data has to survive an app upgrade untouched,
 * so the test seeds a v3 database with real rows and checks afterwards that every one of them is
 * still there and still readable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MigrationTest3To4Test {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val v3Tasks = "CREATE TABLE IF NOT EXISTS `tasks` (`id` TEXT NOT NULL, `title` TEXT " +
        "NOT NULL, `details` TEXT, `priority` TEXT NOT NULL, `estimated_duration_minutes` INTEGER, " +
        "`deadline_instant_epoch_millis` INTEGER, `deadline_zone_id` TEXT, `requested_date_iso` TEXT, " +
        "`earliest_start_epoch_millis` INTEGER, `earliest_start_zone_id` TEXT, `preferred_start` TEXT, " +
        "`preferred_end` TEXT, `flexibility` TEXT NOT NULL, `fixed_start_epoch_millis` INTEGER, " +
        "`fixed_end_epoch_millis` INTEGER, `fixed_zone_id` TEXT, `splittable` INTEGER NOT NULL, " +
        "`minimum_session_minutes` INTEGER, `status` TEXT NOT NULL, `origin` TEXT NOT NULL, " +
        "`project_label` TEXT, `created_at_epoch_millis` INTEGER NOT NULL, `updated_at_epoch_millis` " +
        "INTEGER NOT NULL, `completed_at_epoch_millis` INTEGER, `postponed_until_epoch_millis` INTEGER, " +
        "`postponed_until_zone_id` TEXT, `version` INTEGER NOT NULL, `assumptions` TEXT NOT NULL " +
        "DEFAULT '', PRIMARY KEY(`id`))"

    private val v3Reminders = "CREATE TABLE IF NOT EXISTS `reminder_presentation_history` " +
        "(`id` TEXT NOT NULL, `task_id` TEXT NOT NULL, `scheduled_for_epoch_millis` INTEGER NOT NULL, " +
        "`surfaced_at_epoch_millis` INTEGER, `surface_type` TEXT NOT NULL, `outcome` TEXT NOT NULL, " +
        "`reason_code` TEXT NOT NULL, `batch_group` TEXT, `local_date_iso` TEXT NOT NULL, " +
        "`priority` TEXT NOT NULL, `created_at_epoch_millis` INTEGER NOT NULL, PRIMARY KEY(`id`))"

    private val v3Revisions = "CREATE TABLE IF NOT EXISTS `schedule_revisions` " +
        "(`id` TEXT NOT NULL, `based_on_revision_id` TEXT, `reason_code` TEXT NOT NULL, " +
        "`applied_at_epoch_millis` INTEGER NOT NULL, PRIMARY KEY(`id`))"

    private fun openV3(name: String = "migration-3-4-test.db"): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(v3Tasks)
                    db.execSQL(v3Reminders)
                    db.execSQL(v3Revisions)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun `migration from 3 to 4 keeps every existing row and adds the simulation tables`() {
        val db = openV3()
        db.execSQL(
            "INSERT INTO tasks (id, title, priority, flexibility, splittable, status, origin, " +
                "created_at_epoch_millis, updated_at_epoch_millis, version, assumptions) " +
                "VALUES ('t1', 'My real task', 'CRITICAL', 'FLEXIBLE', 0, 'INBOX', 'VOICE', 0, 0, 1, '')",
        )
        db.execSQL(
            "INSERT INTO reminder_presentation_history (id, task_id, scheduled_for_epoch_millis, " +
                "surface_type, outcome, reason_code, local_date_iso, priority, created_at_epoch_millis) " +
                "VALUES ('r1', 't1', 0, 'NOTIFICATION', 'SHOWN', 'DUE_SOON', '2026-01-01', 'CRITICAL', 0)",
        )
        db.execSQL(
            "INSERT INTO schedule_revisions (id, reason_code, applied_at_epoch_millis) " +
                "VALUES ('rev1', 'SCHEDULE_APPLIED', 0)",
        )

        ChronaDatabase.MIGRATION_3_4.migrate(db)

        // Real data survived untouched.
        db.query("SELECT title, origin FROM tasks WHERE id = 't1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("My real task", cursor.getString(0))
            assertEquals("VOICE", cursor.getString(1))
        }
        db.query("SELECT COUNT(*) FROM reminder_presentation_history").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM schedule_revisions").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }

        listOf("simulation_days", "simulation_events", "simulation_plans").forEach { table ->
            db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)).use { cursor ->
                assertEquals("table $table was not created", 1, cursor.count)
            }
        }
        db.close()
    }

    @Test
    fun `the simulation day table has the columns the report reads`() {
        val db = openV3("migration-3-4-columns.db")
        ChronaDatabase.MIGRATION_3_4.migrate(db)

        db.query("PRAGMA table_info(simulation_days)").use { cursor ->
            val columns = buildList {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            listOf(
                "run_id", "day_offset", "date_iso", "active_tasks", "scheduled_tasks",
                "completed_tasks", "missed_tasks", "unscheduled_tasks", "overdue_tasks",
                "at_risk_tasks", "moved_tasks", "newly_placed_tasks", "runtime_millis",
            ).forEach { expected ->
                assertTrue("simulation_days is missing $expected", expected in columns)
            }
        }
        db.close()
    }
}

/**
 * The Room-backed simulation store, against a real database.
 *
 * The property that matters most is isolation: resetting a simulation must never remove a task
 * the user created. That is enforced by a marker, and this test is what proves the marker works.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoomSimulationStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: ChronaDatabase

    private fun store() = RoomSimulationStore(database)

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    private fun open(name: String = "simulation-store-test.db") {
        // A real on-disk database rather than in-memory: the migrations have to be exercised, and
        // Room only runs them against a file-backed store.
        database = Room.databaseBuilder(context, ChronaDatabase::class.java, name)
            .addMigrations(
                ChronaDatabase.MIGRATION_1_2,
                ChronaDatabase.MIGRATION_2_3,
                ChronaDatabase.MIGRATION_3_4,
            )
            .allowMainThreadQueries()
            .build()
        database.clearAllTables()
    }

    private fun simulatedTask(id: String, title: String = "Sim $id") = Task(
        id = TaskId(id),
        title = title,
        priority = Priority.IMPORTANT,
        estimatedDuration = java.time.Duration.ofMinutes(30),
        status = TaskStatus.INBOX,
        origin = TaskOrigin.SIMULATION,
        projectLabel = SimulationTaskFactory.SIMULATION_LABEL,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun userTask(id: String) = Task(
        id = TaskId(id),
        title = "Mine",
        priority = Priority.IMPORTANT,
        estimatedDuration = java.time.Duration.ofMinutes(30),
        status = TaskStatus.INBOX,
        origin = TaskOrigin.TYPED,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `a simulated task round-trips through the database`() = runTest {
        open()
        val subject = store()
        subject.putIfAbsent(simulatedTask("sim-x-1"))

        val loaded = (subject.loadAll() as Outcome.Success).value
        assertEquals(1, loaded.size)
        assertEquals("Sim sim-x-1", loaded.single().title)
        assertEquals(TaskOrigin.SIMULATION, loaded.single().origin)
    }

    @Test
    fun `putIfAbsent is idempotent so replaying a day cannot duplicate work`() = runTest {
        open()
        val subject = store()
        subject.putIfAbsent(simulatedTask("sim-x-1", "First"))
        subject.putIfAbsent(simulatedTask("sim-x-1", "Second"))

        val loaded = (subject.loadAll() as Outcome.Success).value
        assertEquals(1, loaded.size)
        assertEquals("the original row must not be overwritten", "First", loaded.single().title)
    }

    @Test
    fun `a status change writes an audit row production can read`() = runTest {
        open()
        val subject = store()
        subject.putIfAbsent(simulatedTask("sim-x-1"))
        subject.transition(TaskId("sim-x-1"), TaskStatus.COMPLETED, "TASK_COMPLETED", Instant.ofEpochMilli(5))

        val loaded = (subject.loadAll() as Outcome.Success).value.single()
        assertEquals(TaskStatus.COMPLETED, loaded.status)

        val history = database.taskStatusLogDao().findRecentWithTitles(10)
        assertTrue("a status change must be auditable", history.any { it.taskId == "sim-x-1" })
        assertEquals("Sim sim-x-1", history.first().taskTitle)
    }

    @Test
    fun `an illegal transition is refused rather than forced through`() = runTest {
        open()
        val subject = store()
        subject.putIfAbsent(simulatedTask("sim-x-1"))
        subject.transition(TaskId("sim-x-1"), TaskStatus.COMPLETED, "TASK_COMPLETED", Instant.EPOCH)

        val second = subject.transition(
            TaskId("sim-x-1"),
            TaskStatus.IN_PROGRESS,
            "TASK_STARTED",
            Instant.EPOCH,
        )
        assertTrue("completing then restarting should be refused", second is Outcome.Failed)
    }

    @Test
    fun `reset removes the simulation and keeps every user task`() = runTest {
        open()
        val subject = store()
        subject.putIfAbsent(userTask("mine-1"))
        subject.putIfAbsent(userTask("mine-2"))
        subject.putIfAbsent(simulatedTask("sim-${SimulationDataset.RUN_ID}-a"))
        subject.putIfAbsent(simulatedTask("sim-${SimulationDataset.RUN_ID}-b"))
        subject.recordEvent(
            SimulationEvent(
                SimulationDataset.RUN_ID, 0, SimulationEventKind.DAY_ADVANCED,
                detail = "day 0",
            ),
        )

        subject.reset(SimulationDataset.RUN_ID)

        // Read through the ordinary task repository, not the simulation store's own read: the
        // store deliberately only ever sees its own marker, so asking it about user tasks would
        // return an empty list whether or not they survived.
        val remaining = (database.taskDao().findAll()).map { it.id }.sorted()
        assertEquals(listOf("mine-1", "mine-2"), remaining)
        assertTrue((subject.loadEvents() as Outcome.Success).value.isEmpty())
    }

    @Test
    fun `the simulation store only ever reads its own marker`() = runTest {
        open()
        val subject = store()
        subject.putIfAbsent(userTask("mine-1"))
        subject.putIfAbsent(simulatedTask("sim-${SimulationDataset.RUN_ID}-a"))

        val visible = (subject.loadAll() as Outcome.Success).value
        assertEquals(
            "a user task must never appear in the simulation pool",
            listOf("sim-${SimulationDataset.RUN_ID}-a"),
            visible.map { it.id.value },
        )
    }

    @Test
    fun `reset does not touch another run's tasks`() = runTest {
        open()
        val subject = store()
        subject.putIfAbsent(simulatedTask("sim-other-run-a"))

        subject.reset(SimulationDataset.RUN_ID)

        val remaining = (subject.loadAll() as Outcome.Success).value
        assertEquals(
            "reset must be scoped to the run it was given",
            listOf("sim-other-run-a"),
            remaining.map { it.id.value },
        )
    }

    @Test
    fun `a day report round-trips with every counted column`() = runTest {
        open()
        val subject = store()
        val report = SimulationDayReport(
            runId = SimulationDataset.RUN_ID,
            dayOffset = 3,
            dateIso = "2026-01-08",
            referenceInstant = Instant.ofEpochMilli(1_700_000_000_000),
            availableWindows = 2,
            lostMinutesToInterruption = 120,
            activeTaskCount = 100,
            scheduledTaskCount = 90,
            placedBlockCount = 95,
            completedTaskCount = 4,
            missedTaskCount = 1,
            postponedTaskCount = 2,
            unscheduledTaskCount = 10,
            overdueTaskCount = 3,
            atRiskTaskCount = 5,
            fixedCommitmentCount = 7,
            conflictCount = 1,
            movedTaskCount = 6,
            newlyPlacedCount = 8,
            revisionCount = 1,
            scenarioNote = "a note",
            unplacedReasons = mapOf("DEADLINE_INFEASIBLE" to 3, "NO_AVAILABLE_WINDOW" to 7),
            movedTasks = emptyList(),
            runtimeMillis = 42,
        )

        subject.recordDay(report)
        val restored = (subject.loadDays() as Outcome.Success).value.single()

        assertEquals(report.dayOffset, restored.dayOffset)
        assertEquals(report.dateIso, restored.dateIso)
        assertEquals(report.activeTaskCount, restored.activeTaskCount)
        assertEquals(report.unscheduledTaskCount, restored.unscheduledTaskCount)
        assertEquals(report.movedTaskCount, restored.movedTaskCount)
        assertEquals(report.newlyPlacedCount, restored.newlyPlacedCount)
        assertEquals(report.runtimeMillis, restored.runtimeMillis)
        assertEquals(report.unplacedReasons, restored.unplacedReasons)
    }

    /**
     * Regression test for a data-loss bug only a device run exposed.
     *
     * `simulation_days` originally had a synthetic autoincrement key defaulting to 0, so Room's
     * REPLACE strategy deleted the *previous* row on every insert and a multi-day run ended up
     * holding only the last day. Recording three different days must keep all three.
     */
    @Test
    fun `recording several days keeps every one of them`() = runTest {
        open("simulation-store-multi-day.db")
        val subject = store()
        val base = SimulationDayReport.empty(SimulationDataset.RUN_ID).copy(movedTasks = emptyList())

        (0..3).forEach { offset ->
            subject.recordDay(
                base.copy(
                    dayOffset = offset,
                    dateIso = "2026-01-0${offset + 5}",
                    scheduledTaskCount = offset * 10,
                ),
            )
        }

        val days = (subject.loadDays() as Outcome.Success).value
        assertEquals("every simulated day must survive", 4, days.size)
        assertEquals(listOf(0, 1, 2, 3), days.map { it.dayOffset })
        assertEquals(listOf(0, 10, 20, 30), days.map { it.scheduledTaskCount })
    }

    @Test
    fun `recording the same day twice replaces rather than duplicates`() = runTest {
        open()
        val subject = store()
        val base = SimulationDayReport.empty(SimulationDataset.RUN_ID).copy(
            dayOffset = 0,
            dateIso = "2026-01-05",
            movedTasks = emptyList(),
        )
        subject.recordDay(base.copy(scheduledTaskCount = 10))
        subject.recordDay(base.copy(scheduledTaskCount = 20))

        val days = (subject.loadDays() as Outcome.Success).value
        assertEquals(1, days.size)
        assertEquals(20, days.single().scheduledTaskCount)
    }

    @Test
    fun `the stored plan round-trips through its compact encoding`() = runTest {
        open()
        val subject = store()
        val zone = java.time.ZoneId.of("Asia/Kolkata")
        val placements = listOf(
            com.chrona.app.domain.scheduling.SchedulePlacement(
                taskId = TaskId("sim-x-1"),
                start = java.time.ZonedDateTime.of(2026, 1, 5, 9, 0, 0, 0, zone),
                end = java.time.ZonedDateTime.of(2026, 1, 5, 10, 0, 0, 0, zone),
            ),
            com.chrona.app.domain.scheduling.SchedulePlacement(
                taskId = TaskId("sim-x-2"),
                start = java.time.ZonedDateTime.of(2026, 1, 5, 11, 0, 0, 0, zone),
                end = java.time.ZonedDateTime.of(2026, 1, 5, 12, 0, 0, 0, zone),
                sessionIndex = 1,
            ),
        )

        subject.savePlan(placements)
        val restored = (subject.loadLastPlan() as Outcome.Success).value

        assertEquals(placements.size, restored.size)
        assertEquals(placements.map { it.taskId }, restored.map { it.taskId })
        assertEquals(placements.map { it.start.toInstant() }, restored.map { it.start.toInstant() })
        assertEquals(placements.map { it.sessionIndex }, restored.map { it.sessionIndex })
        // The zone has to survive too, or a replayed plan would be interpreted in the device's
        // zone rather than the simulated one.
        assertEquals(zone, restored.first().start.zone)
    }

    @Test
    fun `a corrupt plan row degrades safely instead of crashing a day`() = runTest {
        open()
        val subject = store()
        database.simulationDao().savePlan(SimulationDataset.RUN_ID, "garbage||")

        val restored = (subject.loadLastPlan() as Outcome.Success).value
        assertTrue("unparseable placements should be skipped", restored.isEmpty())
    }

    @Test
    fun `a fixed task keeps its exact time through the store`() = runTest {
        open()
        val subject = store()
        val zone = java.time.ZoneId.of("UTC")
        val start = java.time.ZonedDateTime.of(2026, 1, 10, 19, 0, 0, 0, zone)
        subject.putIfAbsent(
            simulatedTask("sim-x-party").copy(
                flexibility = Flexibility.FIXED,
                fixedStart = start,
                fixedEnd = start.plusHours(3),
            ),
        )

        val restored = (subject.loadAll() as Outcome.Success).value.single()
        assertNotNull(restored.fixedRange)
        assertEquals(start.toInstant(), restored.fixedRange!!.start.toInstant())
    }
}
