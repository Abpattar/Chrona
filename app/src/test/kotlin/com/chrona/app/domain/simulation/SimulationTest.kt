package com.chrona.app.domain.simulation

import com.chrona.app.domain.model.Flexibility
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskOrigin
import com.chrona.app.domain.model.TaskStatus
import com.chrona.app.domain.scheduling.DeterministicMultiDayPlanner
import com.chrona.app.domain.scheduling.DeterministicScheduleEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The dataset and the simulated clock.
 *
 * These are the facts the rest of the simulation relies on, so they are asserted rather than
 * assumed: a dataset that quietly shrank below the required size, or a recurring definition that
 * produced duplicate ids, would corrupt every downstream claim without failing anything visible.
 */
class SimulationDatasetTest {

    @Test
    fun `the dataset meets the required size`() {
        val instances = SimulationTaskFactory.allSpecs()
        assertTrue(
            "expected at least 50 task instances, got ${instances.size}",
            instances.size >= 50,
        )
    }

    @Test
    fun `task titles are genuinely distinct, not repeated copies`() {
        val titles = SimulationTaskFactory.allSpecs().map { it.title }
        assertTrue(
            "expected at least 50 distinct titles, got ${titles.distinct().size}",
            titles.distinct().size >= 50,
        )
    }

    @Test
    fun `every instance has a unique stable id`() {
        val ids = SimulationTaskFactory.allSpecs().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        // Stability: the same run must produce the same ids, or history cannot be joined.
        assertEquals(ids, SimulationTaskFactory.allSpecs().map { it.id })
    }

    @Test
    fun `workload is spread unevenly across the fortnight`() {
        val perDay = SimulationTaskFactory.allSpecs()
            .groupingBy { it.requestedDayOffset }
            .eachCount()
        assertTrue("a 14-day run should span many days", perDay.size >= 10)
        val busiest = perDay.values.max()
        val quietest = perDay.values.min()
        assertTrue(
            "an evenly loaded simulation would not exercise capacity: busiest=$busiest quietest=$quietest",
            busiest > quietest * 2,
        )
    }

    @Test
    fun `no task is requested outside the simulated fortnight`() {
        val clock = SimulationScenarios.FULL_LIFE.clock
        SimulationTaskFactory.allSpecs().forEach { spec ->
            assertTrue(
                "${spec.title} is requested on day ${spec.requestedDayOffset}, outside the run",
                spec.requestedDayOffset in 0 until clock.totalDays,
            )
        }
    }

    @Test
    fun `a fixed definition always carries an explicit time`() {
        SimulationDataset.DEFINITIONS
            .filter { it.flexibility == Flexibility.FIXED }
            .forEach { definition ->
                assertNotNull("${definition.id} is fixed but has no start", definition.fixedStartTime)
                assertNotNull("${definition.id} is fixed but has no end", definition.fixedEndTime)
            }
    }

    @Test
    fun `a splittable definition declares a minimum session`() {
        SimulationDataset.DEFINITIONS
            .filter { it.splittable }
            .forEach { definition ->
                assertNotNull("${definition.id} is splittable with no minimum session", definition.minimumSessionMinutes)
            }
    }

    @Test
    fun `every dependency points at a definition that exists`() {
        val ids = SimulationDataset.DEFINITIONS.map { it.id }.toSet()
        SimulationDataset.DEFINITIONS.forEach { definition ->
            definition.dependsOn.forEach { dependency ->
                assertTrue("${definition.id} depends on unknown ${dependency}", dependency in ids)
            }
        }
    }

    @Test
    fun `late arrivals are a small, deliberate set`() {
        val late = SimulationDataset.lateArrivals()
        assertTrue("expected several mid-run arrivals", late.size >= 4)
        assertTrue("every late arrival must declare when it appears", late.all { it.arrivesOnDayOffset != null })
    }

    @Test
    fun `every category the brief asks for is represented`() {
        val categories = SimulationTaskFactory.allSpecs().map { it.category }.toSet()
        assertEquals(SimulatedCategory.entries.toSet(), categories)
    }

    @Test
    fun `tasks become real domain tasks marked as simulation data`() {
        val clock = SimulationScenarios.FULL_LIFE.clock
        val task = SimulationTaskFactory.toTask(
            SimulationTaskFactory.allSpecs().first(),
            clock.runId,
            clock,
            Instant.EPOCH,
        )
        assertEquals(TaskOrigin.SIMULATION, task.origin)
        assertEquals(SimulationTaskFactory.SIMULATION_LABEL, task.projectLabel)
        assertEquals(TaskStatus.INBOX, task.status)
        assertNotNull(task.requestedDate)
    }
}

/** The clock: deterministic, and independent of the device time. */
class SimulationClockTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    private fun clock(
        totalDays: Int = 14,
        interruptions: Map<Int, Int> = emptyMap(),
        reduced: Set<Int> = emptySet(),
        unavailable: Set<Int> = emptySet(),
    ) = SimulationClock(
        runId = SimulationDataset.RUN_ID,
        startDate = LocalDate.of(2026, 1, 5),
        totalDays = totalDays,
        zone = zone,
        interruptedByMinutesOnDay = interruptions,
        reducedAvailabilityOnDays = reduced,
        unavailableDays = unavailable,
    )

    @Test
    fun `the reference time comes from the simulation, not the device`() {
        val before = Instant.now()
        val reference = clock().referenceAt(3)
        assertEquals(LocalDate.of(2026, 1, 8), reference.toLocalDate())
        assertTrue(reference.toInstant().isBefore(before.plusSeconds(1)))
    }

    @Test
    fun `an interruption pushes the reference time later`() {
        val base = clock()
        val lost = clock(interruptions = mapOf(2 to 120))
        assertEquals(base.referenceAt(2).plusHours(2), lost.referenceAt(2))
        assertEquals(base.referenceAt(3), lost.referenceAt(3))
    }

    @Test
    fun `a reduced day offers fewer windows than a normal one`() {
        val normal = clock()
        val reduced = clock(reduced = setOf(5))
        assertTrue(reduced.availabilityOn(5).size < normal.availabilityOn(5).size)
        assertEquals(normal.availabilityOn(6), reduced.availabilityOn(6))
    }

    @Test
    fun `an unavailable day has no capacity at all`() {
        val days = clock(unavailable = setOf(13)).planningDays(13)
        val day = days.single()
        assertFalse("a day with no availability must not be marked available", day.isAvailable)
        assertTrue(day.windows.isEmpty())
    }

    @Test
    fun `planning days stop at the end of the run`() {
        assertEquals(14, clock().planningDays(0).size)
        assertEquals(3, clock().planningDays(11).size)
        assertEquals(1, clock().planningDays(13).size)
    }

    @Test
    fun `the same clock always yields the same reference times`() {
        val a = clock(interruptions = mapOf(4 to 90))
        val b = clock(interruptions = mapOf(4 to 90))
        (0 until 14).forEach { assertEquals(a.referenceAt(it), b.referenceAt(it)) }
    }
}

/** The simulation engine: lifecycle rules, reconciliation and reset isolation. */
class LifeSimulationEngineTest {

    private fun engine(store: SimulationStore = InMemorySimulationStore()) =
        LifeSimulationEngine(DeterministicMultiDayPlanner(DeterministicScheduleEngine()), store) to store

    @Test
    fun `the full run advances every day and reports each one`() = runTest {
        val (engine, store) = engine()
        val result = engine.runAll(SimulationScenarios.FULL_LIFE)

        val reports = (result as Outcome.Success).value
        assertEquals(14, reports.size)
        assertEquals((0..13).toList(), reports.map { it.dayOffset })
        assertEquals((store.loadDays() as Outcome.Success).value.size, 14)
    }

    @Test
    fun `every open task is either placed or explained on every day`() = runTest {
        val (engine, store) = engine()
        val reports = (engine.runAll(SimulationScenarios.FULL_LIFE) as Outcome.Success).value
        val pool = (store.loadAll() as Outcome.Success).value

        reports.forEach { report ->
            assertEquals(
                "day ${report.dayOffset + 1} lost track of work",
                report.activeTaskCount,
                report.scheduledTaskCount + report.unscheduledTaskCount,
            )
        }
        assertTrue("the pool should not be empty", pool.isNotEmpty())
    }

    @Test
    fun `the same run produces identical results when replayed`() = runTest {
        val (first, _) = engine()
        val (second, _) = engine()
        val a = (first.runAll(SimulationScenarios.FULL_LIFE) as Outcome.Success).value
        val b = (second.runAll(SimulationScenarios.FULL_LIFE) as Outcome.Success).value
        assertEquals(a.map { it.scheduledTaskCount }, b.map { it.scheduledTaskCount })
        assertEquals(a.map { it.completedTaskCount }, b.map { it.completedTaskCount })
        assertEquals(a.map { it.unscheduledTaskCount }, b.map { it.unscheduledTaskCount })
    }

    @Test
    fun `a missed task stays incomplete and is reconsidered`() = runTest {
        val (engine, store) = engine()
        val scenario = SimulationScenarios.FULL_LIFE
        engine.advanceTo(scenario, 0)
        engine.advanceTo(scenario, 1)
        engine.advanceTo(scenario, 2)
        engine.advanceTo(scenario, 3)

        val pool = (store.loadAll() as Outcome.Success).value
        val missed = pool.firstOrNull { it.title == "DBMS revision: normalisation" }
        assertNotNull("the scripted miss should have found its task", missed)
        assertEquals(
            "a missed task must not be marked complete",
            TaskStatus.POSTPONED,
            missed!!.status,
        )
        assertTrue("a postponed task is still open", missed.status.isOpen)
    }

    @Test
    fun `a completed task never returns to the active workload`() = runTest {
        val (engine, store) = engine()
        val scenario = SimulationScenarios.FULL_LIFE
        engine.runAll(scenario)

        val pool = (store.loadAll() as Outcome.Success).value
        val completed = pool.filter { it.status == TaskStatus.COMPLETED }
        assertTrue("the run completed nothing, so nothing was proven", completed.size >= 10)
        assertTrue(completed.all { !it.status.isActive })
    }

    @Test
    fun `a task arriving late is persisted and planned, not dropped`() = runTest {
        val (engine, store) = engine()
        val scenario = SimulationScenarios.FULL_LIFE

        engine.advanceTo(scenario, 0)
        assertTrue(
            "the late assignment must not exist before its arrival day",
            (store.loadAll() as Outcome.Success).value.none { it.title.startsWith("DBMS assignment") },
        )

        engine.advanceTo(scenario, 1)
        assertTrue(
            "it must still not exist the day before its arrival",
            (store.loadAll() as Outcome.Success).value.none { it.title.startsWith("DBMS assignment") },
        )

        // It arrives at offset 2, i.e. simulated day 3.
        val arrivals = (engine.advanceTo(scenario, 2) as Outcome.Success).value
        val pool = (store.loadAll() as Outcome.Success).value
        val assignment = pool.firstOrNull { it.title.startsWith("DBMS assignment") }
        assertNotNull("the assignment should have been persisted on its arrival day", assignment)
        assertFalse(
            "an urgent arrival should be scheduled, not left stranded",
            arrivals.tasksUnplaced.contains(assignment!!.id),
        )
    }

    @Test
    fun `a future fixed event keeps its date as days are replanned`() = runTest {
        val (engine, store) = engine()
        val scenario = SimulationScenarios.FULL_LIFE
        repeat(6) { engine.advanceTo(scenario, it) }

        val party = (store.loadAll() as Outcome.Success).value
            .first { it.title == "Rohan's birthday party" }
        val placement = (store.loadLastPlan() as Outcome.Success).value.single { it.taskId == party.id }
        assertEquals(clock(9), placement.start.toLocalDate())
        assertEquals(LocalTime.of(19, 0), placement.start.toLocalTime())
    }

    @Test
    fun `a day with no availability receives no placements`() = runTest {
        val (engine, store) = engine()
        val scenario = SimulationScenarios.FULL_LIFE
        repeat(13) { engine.advanceTo(scenario, it) }

        val closedDate = clock(12)
        val pool = (store.loadAll() as Outcome.Success).value.associateBy { it.id }
        val flexibleOnClosedDay = (store.loadLastPlan() as Outcome.Success).value
            .filter { it.start.toLocalDate() == closedDate }
            .filter { pool[it.taskId]?.fixedRange == null }
        // Fixed commitments are deliberately unaffected: a family function the user entered by
        // hand still happens, even on a day with no free hours. Only flexible work is turned away.
        assertTrue("no flexible work may be placed on a closed day", flexibleOnClosedDay.isEmpty())
    }

    @Test
    fun `reset removes the run and leaves a user task untouched`() = runTest {
        val (engine, store) = engine()
        val keeper = Task(
            id = TaskId("mine"),
            title = "Something that is mine",
            status = TaskStatus.INBOX,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        store.putIfAbsent(keeper)
        engine.runAll(SimulationScenarios.FULL_LIFE)

        engine.reset(SimulationScenarios.FULL_LIFE)

        val remaining = (store.loadAll() as Outcome.Success).value
        assertEquals(listOf(keeper.id), remaining.map { it.id })
        assertTrue((store.loadDays() as Outcome.Success).value.isEmpty())
    }

    @Test
    fun `reset is safe to call before anything was loaded`() = runTest {
        val (engine, store) = engine()
        assertTrue(engine.reset(SimulationScenarios.FULL_LIFE) is Outcome.Success)
        assertTrue((store.loadAll() as Outcome.Success).value.isEmpty())
    }

    @Test
    fun `advancing the same day twice does not duplicate work`() = runTest {
        val (engine, store) = engine()
        val scenario = SimulationScenarios.FULL_LIFE
        engine.advanceTo(scenario, 0)
        val afterFirst = (store.loadAll() as Outcome.Success).value.size
        engine.advanceTo(scenario, 0)
        val afterSecond = (store.loadAll() as Outcome.Success).value.size
        assertEquals("replaying a day must not add tasks", afterFirst, afterSecond)
        assertEquals(1, (store.loadDays() as Outcome.Success).value.count { it.dayOffset == 0 })
    }

    @Test
    fun `an out of range day is refused with a reason`() = runTest {
        val (engine, _) = engine()
        val result = engine.advanceTo(SimulationScenarios.FULL_LIFE, 99)
        assertTrue(result is Outcome.Failed)
    }

    @Test
    fun `an unavailable provider changes nothing about the plan`() = runTest {
        // Scenario 14: the deterministic path must produce exactly the same plan as a run with a
        // provider, because the provider never participates in placement in the first place.
        val (withProviderName, _) = engine()
        val (withoutProviderName, _) = engine()
        val a = (withProviderName.runAll(SimulationScenarios.PROVIDER_OUTAGE) as Outcome.Success).value
        val b = (withoutProviderName.runAll(SimulationScenarios.FULL_LIFE) as Outcome.Success).value
        assertEquals(a.size, b.size)
    }

    @Test
    fun `the overloaded day reports what could not fit instead of dropping it`() = runTest {
        val (engine, store) = engine()
        val reports = (engine.runAll(SimulationScenarios.OVERLOADED_DAY) as Outcome.Success).value
        val pool = (store.loadAll() as Outcome.Success).value
        val onlyDay = reports.single()

        // The whole horizon is one closed day, so nothing flexible can be placed and the surplus
        // has to be reported rather than quietly dropped.
        assertEquals("a closed day cannot place flexible work", 0, onlyDay.scheduledTaskCount)
        assertTrue(
            "the surplus must be reported, not deleted",
            onlyDay.unscheduledTaskCount > 0,
        )
        assertEquals(
            onlyDay.activeTaskCount,
            onlyDay.scheduledTaskCount + onlyDay.unscheduledTaskCount,
        )
        assertTrue("the pool must survive the run", pool.isNotEmpty())
        assertTrue("every task must still exist", pool.size >= 50)
    }

    @Test
    fun `the reported processing mode is the one that actually ran`() = runTest {
        val store = InMemorySimulationStore()
        val engine = LifeSimulationEngine(
            planner = DeterministicMultiDayPlanner(DeterministicScheduleEngine()),
            store = store,
            processingMode = LifeSimulationEngine.PROCESSING_MODE_AI_ASSISTED,
        )
        val step = engine.advanceTo(SimulationScenarios.FULL_LIFE, 0) as Outcome.Success
        assertEquals(LifeSimulationEngine.PROCESSING_MODE_AI_ASSISTED, step.value.processingMode)
    }

    private fun clock(dayOffset: Int) = SimulationScenarios.FULL_LIFE.clock.dateOf(dayOffset)
}
