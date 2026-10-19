package com.chrona.app.domain.simulation

import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Flexibility
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskStatus
import com.chrona.app.domain.scheduling.DeterministicMultiDayPlanner
import com.chrona.app.domain.scheduling.DeterministicScheduleEngine
import com.chrona.app.domain.scheduling.MultiDayInput
import com.chrona.app.domain.scheduling.MultiDayPlan
import com.chrona.app.domain.scheduling.MultiDayPlanner
import com.chrona.app.domain.scheduling.SchedulePlacement
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Advances a life simulation one simulated day at a time.
 *
 * The engine owns no scheduling logic of its own. Every day it hands the task pool to the same
 * [MultiDayPlanner] production uses, which in turn drives the same single-day
 * `DeterministicScheduleEngine`. If the simulation and the app ever disagreed about where a task
 * belongs, one of them would be wrong — and because both call the same code, they cannot be.
 *
 * Four rules shape the design:
 *
 * - **The clock is injected.** Nothing here reads the device time, so a run is reproducible.
 * - **Tasks are never silently resolved.** After every day the pool is reconciled against the
 *   plan; anything neither placed nor explained fails the step loudly rather than being dropped
 *   to make the numbers look tidy.
 * - **Status changes are scripted, not inferred.** A task is completed because the scenario said
 *   the user completed it, never because its scheduled slot has passed.
 * - **Each day is compared with the day before**, so "what moved and why" is a real before/after
 *   diff rather than a claim.
 */
class LifeSimulationEngine(
    private val planner: MultiDayPlanner,
    private val store: SimulationStore,
    /** Which processing path produced the plan: local parser, AI-assisted, or deterministic. */
    private val processingMode: String = PROCESSING_MODE_DETERMINISTIC,
) {

    /** What one day produced, plus the lifecycle outcomes that were actually applied. */
    private data class Lifecycle(val completed: List<TaskId>, val missed: List<TaskId>)

    /**
     * Runs one simulated day.
     *
     * [dayOffset] is absolute from the start of the run, so 0, 1, 2 … is the whole run. Running
     * the same offset twice replays that day rather than skipping ahead: task ids are stable and
     * storage is write-once per id, so a replay cannot duplicate anything.
     */
    suspend fun advanceTo(
        scenario: SimulationScenario,
        dayOffset: Int,
    ): Outcome<SimulationStepResult> {
        val clock = scenario.clock
        if (dayOffset < 0 || dayOffset >= clock.totalDays) {
            return Outcome.Failed(
                Failure.Validation(listOf("Day $dayOffset is outside this ${clock.totalDays}-day simulation.")),
            )
        }
        val started = System.nanoTime()
        val reference: ZonedDateTime = clock.referenceAt(dayOffset)

        // 1. The world so far: every task the user already has, plus anything scheduled to have
        //    arrived by today. Storage is write-once per id, so this seeds day 0 and injects a
        //    late arrival on exactly one day without ever duplicating a task.
        val knownByToday = SimulationTaskFactory.allSpecs(clock.runId)
            .filter { spec -> (spec.arrivesOnDayOffset ?: 0) <= dayOffset }
        for (spec in knownByToday) {
            val stored = store.putIfAbsent(SimulationTaskFactory.toTask(spec, clock.runId, clock, reference.toInstant()))
            if (stored is Outcome.Failed) return stored
        }
        val arrivals = knownByToday.filter { it.arrivesOnDayOffset == dayOffset }

        // 2. The persistent task pool: the same pool every day, never a fresh copy.
        val pool = when (val loaded = store.loadAll()) {
            is Outcome.Success -> loaded.value
            is Outcome.Failed -> return loaded
        }
        val openTasks = pool.filter { it.status.isActive }

        // 3. Plan the whole remaining horizon, so future fixed events keep their own dates and
        //    urgent work can still land several days before its deadline.
        val previous = when (val last = store.loadLastPlan()) {
            is Outcome.Success -> last.value
            is Outcome.Failed -> return last
        }
        val plan = when (
            val planned = planner.plan(
                MultiDayInput(
                    days = clock.planningDays(dayOffset),
                    tasks = openTasks,
                    now = reference,
                    buffer = SCHEDULE_BUFFER,
                    previousPlacements = previous,
                ),
            )
        ) {
            is Outcome.Success -> planned.value
            is Outcome.Failed -> return planned
        }

        val today = clock.dateOf(dayOffset)
        val todaysPlacements = plan.placements.filter { it.start.toLocalDate() == today }
        val script = scenario.scriptFor(dayOffset)

        // 4. Scripted lifecycle: exactly the tasks the scenario names, and nothing else.
        val lifecycle = when (val applied = applyLifecycle(script, clock, dayOffset)) {
            is Outcome.Success -> applied.value
            is Outcome.Failed -> return applied
        }

        // 5. Reconcile: every open task must now be either placed or explicitly reported.
        val unaccounted = openTasks.filter {
            it.id !in plan.placedTaskIds && it.id !in plan.unplacedTaskIds
        }
        if (unaccounted.isNotEmpty()) {
            return Outcome.Failed(
                Failure.StorageFailure(
                    "The simulation lost track of ${unaccounted.size} task(s): " +
                        unaccounted.joinToString { it.title },
                ),
            )
        }

        val report = buildReport(
            scenario = scenario,
            dayOffset = dayOffset,
            reference = reference,
            plan = plan,
            pool = pool,
            lifecycle = lifecycle,
            runtimeMillis = (System.nanoTime() - started) / 1_000_000,
        )

        if (store.recordDay(report) is Outcome.Failed) return Outcome.Failed(Failure.StorageFailure("Day report could not be saved."))
        if (store.savePlan(plan.placements) is Outcome.Failed) return Outcome.Failed(Failure.StorageFailure("Plan could not be saved."))

        recordEvents(scenario, dayOffset, arrivals, report, plan, lifecycle)

        return Outcome.Success(
            SimulationStepResult(
                report = report,
                tasksPlaced = todaysPlacements.map { it.taskId },
                tasksUnplaced = plan.unplacedTaskIds.toList(),
                message = report.explanation(),
                processingMode = processingMode,
            ),
        )
    }

    /**
     * Creates the task pool for [scenario] without advancing the simulated clock.
     *
     * Separate from [advanceTo] on purpose: "load" and "advance a day" are different intentions,
     * and a Load button that quietly jumped a day forward would be lying about the run's state.
     * Storage is write-once per id, so seeding twice is harmless.
     */
    suspend fun seed(scenario: SimulationScenario): Outcome<Int> {
        val clock = scenario.clock
        val reference = clock.referenceAt(0).toInstant()
        val specs = SimulationTaskFactory.allSpecs(clock.runId)
        for (spec in specs) {
            val stored = store.putIfAbsent(SimulationTaskFactory.toTask(spec, clock.runId, clock, reference))
            if (stored is Outcome.Failed) return stored
        }
        store.recordEvent(
            SimulationEvent(
                runId = clock.runId,
                dayOffset = -1,
                kind = SimulationEventKind.TASK_ARRIVED,
                detail = "Loaded ${specs.size} simulated tasks for \u201c${scenario.displayName}\u201d.",
            ),
        )
        return Outcome.Success(specs.size)
    }

    /** Runs the whole scenario from day 0, stopping at the first failure. */
    suspend fun runAll(scenario: SimulationScenario): Outcome<List<SimulationDayReport>> {
        val reports = mutableListOf<SimulationDayReport>()
        for (day in 0 until scenario.clock.totalDays) {
            when (val step = advanceTo(scenario, day)) {
                is Outcome.Success -> reports += step.value.report
                is Outcome.Failed -> return step
            }
        }
        return Outcome.Success(reports)
    }

    /** Clears this run's records only. Real user tasks are untouched by design. */
    suspend fun reset(scenario: SimulationScenario): Outcome<Unit> {
        val result = store.reset(scenario.clock.runId)
        if (result is Outcome.Success) {
            store.recordEvent(
                SimulationEvent(
                    runId = scenario.clock.runId,
                    dayOffset = -1,
                    kind = SimulationEventKind.RESET,
                    detail = "Simulation data was reset. User tasks were not touched.",
                ),
            )
        }
        return result
    }

    private suspend fun applyLifecycle(
        script: DayScript,
        clock: SimulationClock,
        dayOffset: Int,
    ): Outcome<Lifecycle> {
        val at = clock.referenceAt(dayOffset).toInstant()
        var completed = emptyList<TaskId>()
        var missed = emptyList<TaskId>()

        for (id in matchOnePerTitle(script.completeTitles, clock, dayOffset)) {
            val result = store.transition(id, TaskStatus.COMPLETED, REASON_COMPLETED, at)
            if (result is Outcome.Failed) return result
            completed = completed + id
        }
        // A missed task is postponed, never completed and never deleted. POSTPONED is still an
        // active state, so tomorrow's plan can reconsider it exactly as recovery would.
        for (id in matchOnePerTitle(script.missTitles, clock, dayOffset)) {
            val result = store.transition(id, TaskStatus.POSTPONED, REASON_MISSED, at)
            if (result is Outcome.Failed) return result
            missed = missed + id
        }
        return Outcome.Success(Lifecycle(completed, missed))
    }

    /**
     * Resolves each scripted title to exactly one open task.
     *
     * Titles rather than ids, so a scenario reads as "the user missed their DBMS revision"
     * instead of encoding an assumption about the planner's ordering.
     *
     * One task per title matters: "the wake-up routine" recurs fourteen times in the run, and
     * completing every open copy on the day it is mentioned would retire fourteen routines at
     * once. The instance whose requested date is today wins; failing that the earliest open one
     * does, so a task the plan slipped to a later day can still be finished early. Id breaks
     * every remaining tie, which keeps the choice reproducible.
     *
     * A title with no open match does nothing. Marking some *other* task instead would be worse
     * than doing nothing.
     */
    private suspend fun matchOnePerTitle(
        titles: List<String>,
        clock: SimulationClock,
        dayOffset: Int,
    ): List<TaskId> {
        if (titles.isEmpty()) return emptyList()
        val today = clock.dateOf(dayOffset)
        val pool = store.loadAll()
        if (pool !is Outcome.Success) return emptyList()
        val open = pool.value.filter { it.status.isActive }

        return titles.distinct().mapNotNull { title ->
            val candidates = open.filter { it.title.equals(title, ignoreCase = true) }
            candidates.minWithOrNull(
                compareBy<Task>(
                    // Today's instance first, then earliest requested, then a stable id.
                    { if (it.requestedDate == today) 0 else 1 },
                    { it.requestedDate ?: LocalDate.MIN },
                    { it.id.value },
                ),
            )?.id
        }
    }

    private suspend fun recordEvents(
        scenario: SimulationScenario,
        dayOffset: Int,
        arrivals: List<SimulatedTaskSpec>,
        report: SimulationDayReport,
        plan: MultiDayPlan,
        lifecycle: Lifecycle,
    ) {
        val runId = scenario.clock.runId
        val titles = SimulationTaskFactory.allSpecs(runId)
            .associate { com.chrona.app.domain.model.TaskId(it.id) to it.title }

        store.recordEvent(
            SimulationEvent(
                runId, dayOffset, SimulationEventKind.DAY_ADVANCED,
                detail = "Day ${dayOffset + 1}: ${report.scheduledTaskCount} scheduled, " +
                    "${report.unscheduledTaskCount} could not be placed. ${report.scenarioNote}",
            ),
        )
        arrivals.forEach {
            store.recordEvent(
                SimulationEvent(
                    runId, dayOffset, SimulationEventKind.TASK_ARRIVED, it.id,
                    "\u201c${it.title}\u201d arrived after the plan had already been made.",
                ),
            )
        }
        // The lifecycle events are the change history the Simulation screen renders, and they
        // are also the only record of *which* task finished on *which* day. The task pool holds
        // a final status only, so anything reasoning about history has to read these.
        lifecycle.completed.forEach {
            store.recordEvent(
                SimulationEvent(
                    runId, dayOffset, SimulationEventKind.TASK_COMPLETED, it.value,
                    "\u201c${titles[it] ?: it.value}\u201d was completed on day ${dayOffset + 1}.",
                ),
            )
        }
        lifecycle.missed.forEach {
            store.recordEvent(
                SimulationEvent(
                    runId, dayOffset, SimulationEventKind.TASK_MISSED, it.value,
                    "\u201c${titles[it] ?: it.value}\u201d was missed. It stays open so recovery can place it again.",
                ),
            )
        }
        val interruption = scenario.clock.interruptedByMinutesOnDay[dayOffset] ?: 0
        if (interruption > 0) {
            store.recordEvent(
                SimulationEvent(
                    runId, dayOffset, SimulationEventKind.DISRUPTION, null,
                    "Lost $interruption minutes on day ${dayOffset + 1}. Planning started " +
                        "${interruption / 60}h later than usual.",
                ),
            )
        }
        report.movedTasks.forEach {
            store.recordEvent(
                SimulationEvent(runId, dayOffset, SimulationEventKind.RECOVERY_APPLIED, it.taskId, it.reason),
            )
        }
        plan.unplaced.forEach { unplaced ->
            store.recordEvent(
                SimulationEvent(
                    runId, dayOffset, SimulationEventKind.UNPLACEABLE, unplaced.taskId.value,
                    "${titles[unplaced.taskId] ?: unplaced.taskId.value}: " +
                        "${unplaced.reason.name} \u2014 ${unplaced.explanation}",
                ),
            )
        }
    }

    private fun buildReport(
        scenario: SimulationScenario,
        dayOffset: Int,
        reference: ZonedDateTime,
        plan: MultiDayPlan,
        pool: List<Task>,
        lifecycle: Lifecycle,
        runtimeMillis: Long,
    ): SimulationDayReport {
        val clock = scenario.clock
        val script = scenario.scriptFor(dayOffset)
        val titles = pool.associate { it.id to it.title }
        val byId = pool.associateBy { it.id }
        val now = reference.toInstant()

        // Overdue: still open, and its deadline has already passed on the simulated clock.
        val overdue = pool.count { it.status.isOpen && it.deadline != null && it.deadline.toInstant().isBefore(now) }
        // At risk: open and not yet overdue, but the plan could not place it before its deadline.
        val atRisk = plan.unplaced.count { unplaced ->
            val deadline = byId[unplaced.taskId]?.deadline
            deadline != null && deadline.toInstant().isAfter(now)
        }

        // A task that had no slot yesterday and has one today was *placed*, not *moved*. Folding
        // the two together would make a healthy day look like a massive reshuffle.
        val (moved, newlyPlaced) = plan.changes.partition { it.fromStart != null && it.toStart != null }
        val movedTasks = moved.map { change ->
            SimulationMovedTask(
                taskId = change.taskId.value,
                title = titles[change.taskId] ?: change.taskId.value,
                fromInstant = change.fromStart?.toInstant()?.toString(),
                toInstant = change.toStart?.toInstant()?.toString(),
                reason = change.explanation,
            )
        }

        return SimulationDayReport(
            runId = clock.runId,
            dayOffset = dayOffset,
            dateIso = clock.dateOf(dayOffset).toString(),
            referenceInstant = now,
            availableWindows = clock.availabilityOn(dayOffset).size,
            lostMinutesToInterruption = clock.interruptedByMinutesOnDay[dayOffset] ?: 0,
            activeTaskCount = pool.count { it.status.isActive },
            scheduledTaskCount = plan.placedTaskIds.size,
            placedBlockCount = plan.placements.size,
            completedTaskCount = lifecycle.completed.size,
            missedTaskCount = lifecycle.missed.size,
            postponedTaskCount = pool.count { it.status == TaskStatus.POSTPONED },
            unscheduledTaskCount = plan.unplacedTaskIds.size,
            overdueTaskCount = overdue,
            atRiskTaskCount = atRisk,
            fixedCommitmentCount = pool.count { it.flexibility == Flexibility.FIXED },
            conflictCount = plan.conflicts.size,
            movedTaskCount = movedTasks.size,
            newlyPlacedCount = newlyPlaced.size,
            revisionCount = 1,
            scenarioNote = script.note,
            unplacedReasons = plan.unplaced.groupingBy { it.reason.name }.eachCount(),
            movedTasks = movedTasks,
            runtimeMillis = runtimeMillis,
        )
    }

    companion object {
        val SCHEDULE_BUFFER: Duration = Duration.ofMinutes(10)
        const val REASON_COMPLETED: String = "TASK_COMPLETED"
        const val REASON_MISSED: String = "TASK_POSTPONED"

        /** Honest names for the processing path. Never claim AI when none was used. */
        const val PROCESSING_MODE_DETERMINISTIC: String = "Deterministic (no AI)"
        const val PROCESSING_MODE_LOCAL_PARSER: String = "Local parser"
        const val PROCESSING_MODE_AI_ASSISTED: String = "AI-assisted"
    }
}

/** Plain-language summary of a day, used by the UI and by the generated report. */
fun SimulationDayReport.explanation(): String = buildString {
    append("Day ").append(dayOffset + 1).append(" (").append(dateIso).append("): ")
    append(scheduledTaskCount).append(" scheduled, ")
    append(completedTaskCount).append(" completed, ")
    append(unscheduledTaskCount).append(" could not be placed")
    if (atRiskTaskCount > 0) append(", ").append(atRiskTaskCount).append(" deadline(s) at risk")
    if (overdueTaskCount > 0) append(", ").append(overdueTaskCount).append(" overdue")
    if (lostMinutesToInterruption > 0) {
        append(". ").append(lostMinutesToInterruption).append(" min lost to an interruption")
    }
    append('.')
}

/** Builds the production wiring: the real planner, driven by the real deterministic engine. */
fun productionSimulationEngine(store: SimulationStore): LifeSimulationEngine = LifeSimulationEngine(
    planner = DeterministicMultiDayPlanner(DeterministicScheduleEngine()),
    store = store,
)

/** Convenience for tests that need a fresh run without the production composition root. */
fun testSimulationEngine(store: SimulationStore): LifeSimulationEngine = productionSimulationEngine(store)

internal fun SimulationClock.instantAt(dayOffset: Int): Instant = referenceAt(dayOffset).toInstant()
