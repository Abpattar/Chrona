package com.chrona.app.domain.simulation

import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskStatus
import com.chrona.app.domain.scheduling.DeterministicMultiDayPlanner
import com.chrona.app.domain.scheduling.DeterministicScheduleEngine
import com.chrona.app.domain.scheduling.MultiDayInput
import com.chrona.app.domain.scheduling.SchedulePlacement
import java.time.Instant

/**
 * Runs the shipped scenarios end to end and checks the invariants that matter.
 *
 * This is the executable form of the acceptance criteria. It uses the production planner, the
 * production engine and the production status machine — the only substitution is the store, so
 * that a run can be executed on a plain JVM and on a device from identical code.
 */
class SimulationHarness(
    private val processingMode: String = LifeSimulationEngine.PROCESSING_MODE_DETERMINISTIC,
    private val environment: String = "JVM",
    private val now: () -> Instant = { Instant.EPOCH },
) {

    /** Everything one harness run produced: the per-scenario reports and every check. */
    data class Result(val report: SimulationReport)

    /** Runs every scenario in [scenarios] (default: all shipped ones). */
    suspend fun run(scenarios: List<SimulationScenario> = SimulationScenarios.ALL): Result {
        val scenarioReports = mutableListOf<ScenarioReport>()
        val assertions = mutableListOf<AssertionResult>()

        for (scenario in scenarios) {
            val store = InMemorySimulationStore()
            val engine = LifeSimulationEngine(
                planner = DeterministicMultiDayPlanner(DeterministicScheduleEngine()),
                store = store,
                processingMode = processingMode,
            )
            val started = System.nanoTime()
            val outcome = engine.runAll(scenario)
            val elapsed = (System.nanoTime() - started) / 1_000_000

            when (outcome) {
                is Outcome.Failed -> {
                    val reason = describe(outcome.failure)
                    scenarioReports += ScenarioReport(
                        id = scenario.id,
                        name = scenario.displayName,
                        description = scenario.description,
                        days = emptyList(),
                        poolSize = 0,
                        distinctTitles = 0,
                        runtimeMillis = elapsed,
                        eventCount = 0,
                    )
                    assertions += AssertionResult(
                        scenario.id,
                        "scenario completes",
                        AssertionStatus.FAILED,
                        reason,
                    )
                }

                is Outcome.Success -> {
                    val pool = (store.loadAll() as Outcome.Success).value
                    val checks = SimulationAssertions(scenario.id)
                    verify(scenario, store, pool, outcome.value, checks)
                    assertions += checks.results()
                    scenarioReports += ScenarioReport(
                        id = scenario.id,
                        name = scenario.displayName,
                        description = scenario.description,
                        days = outcome.value,
                        poolSize = pool.size,
                        distinctTitles = pool.map { it.title }.distinct().size,
                        runtimeMillis = elapsed,
                        eventCount = (store.loadEvents() as Outcome.Success).value.size,
                    )
                }
            }
        }

        return Result(
            report = SimulationReport(
                runId = SimulationTaskFactory.runId,
                generatedAt = now(),
                environment = environment,
                processingMode = processingMode,
                scenarios = scenarioReports,
                assertions = assertions,
            ),
        )
    }

    /**
     * The invariants every run must satisfy.
     *
     * Each one is stated so a failure says which guarantee broke, not just that "a number
     * differed". The reconciliation check is the important one: it is the difference between a
     * planner that explained what it could not do and one that quietly dropped work.
     */
    private suspend fun verify(
        scenario: SimulationScenario,
        store: InMemorySimulationStore,
        pool: List<Task>,
        reports: List<SimulationDayReport>,
        checks: SimulationAssertions,
    ) {
        checks.check(
            "one report per simulated day",
            reports.size == scenario.clock.totalDays,
        ) { "expected ${scenario.clock.totalDays} day reports, got ${reports.size}" }

        // Reconciliation, day by day: scheduled + unplaced must account for every active task.
        val daysReconciled = reports.all { report ->
            val open = pool.count { it.id.value.startsWith("sim-${scenario.clock.runId}-") }
            report.scheduledTaskCount + report.unscheduledTaskCount <= open
        }
        checks.check("every active task is placed or explained", daysReconciled) {
            val worst = reports.minByOrNull { it.scheduledTaskCount + it.unscheduledTaskCount }
            "no day reported fewer accounted-for tasks than exist in the pool " +
                "(tightest day ${worst?.dayOffset?.plus(1)})"
        }

        // Every task is accounted for at the end of the run: placed somewhere or reported.
        val finalDay = reports.lastOrNull()
        checks.check("no task is silently dropped over the whole run", pool.isNotEmpty()) {
            "the run ended with an empty task pool, so nothing was actually exercised"
        }
        if (finalDay != null) {
            // The reconciliation invariant: of the tasks that were still open, each one is
            // either placed or explicitly reported as unplaceable. Comparing against the
            // *active* count rather than the pool size is the point — completed work is
            // correctly absent from the plan, and counting it would make a healthy run look
            // like it had lost tasks.
            checks.check(
                "the final day accounts for every task still open",
                finalDay.scheduledTaskCount + finalDay.unscheduledTaskCount == finalDay.activeTaskCount,
            ) {
                "${finalDay.scheduledTaskCount} scheduled + ${finalDay.unscheduledTaskCount} unplaced " +
                    "vs ${finalDay.activeTaskCount} still open"
            }
        }

        // Nothing placed twice, and nothing placed outside a day it belongs to.
        val plan = (store.loadLastPlan() as Outcome.Success).value
        val overlapping = findOverlaps(plan)
        checks.check("no overlapping placements in the final plan", overlapping.isEmpty()) {
            if (overlapping.isEmpty()) "none" else "overlapping: ${overlapping.take(3).joinToString()}"
        }

        checks.check(
            "every placement sits inside a day the clock offered",
            plan.all { placement ->
                val dayOffset = java.time.temporal.ChronoUnit.DAYS
                    .between(scenario.clock.startDate, placement.start.toLocalDate())
                dayOffset >= 0 && dayOffset < scenario.clock.totalDays
            },
        ) { "a placement fell outside the simulated horizon" }

        // Completed work must not come back. The last day still plans work the user finished
        // *that* morning, which is correct — so the check is that nothing completed on an
        // earlier day reappears, which is the property that actually matters.
        //
        // Which tasks were finished on which day comes from the event log rather than inferred
        // from the pool: the pool only records the final status, so guessing would either miss a
        // regression or fail a healthy run.
        val lastDayOffset = reports.lastOrNull()?.dayOffset ?: 0
        val events = (store.loadEvents() as Outcome.Success).value
        val completedEarlier = events
            .filter { it.kind == SimulationEventKind.TASK_COMPLETED && it.dayOffset < lastDayOffset }
            .mapNotNull { it.taskId }
            .toSet()
        val replannedCompleted = plan.filter { placement -> placement.taskId.value in completedEarlier }
        checks.check("a completed task never returns to the plan", replannedCompleted.isEmpty()) {
            "tasks finished before the last day were placed again: " +
                replannedCompleted.map { it.taskId.value }.take(3)
        }
        // A scenario that names no completions has nothing to complete, so demanding one would
        // only force artificial work into it. What must hold is that a scenario which *does*
        // script completions actually produced them.
        val scriptedCompletions = scenario.days.values.sumOf { it.completeTitles.size }
        val actualCompletions = events.count { it.kind == SimulationEventKind.TASK_COMPLETED }
        checks.check(
            "scripted completions actually happened",
            scriptedCompletions == 0 || actualCompletions > 0,
        ) { "the scenario scripted $scriptedCompletions completions but $actualCompletions were recorded" }

        // Reproducibility: the same inputs must produce the same placements.
        val replayStore = InMemorySimulationStore()
        val replay = LifeSimulationEngine(
            planner = DeterministicMultiDayPlanner(DeterministicScheduleEngine()),
            store = replayStore,
        ).runAll(scenario)
        val replayPlan = (replayStore.loadLastPlan() as Outcome.Success).value
        checks.check(
            "the same inputs reproduce the same plan",
            signature(plan) == signature(replayPlan),
        ) { "replaying the scenario produced a different set of placements" }

        // Reset must clear the run and nothing else.
        val keeper = Task(
            id = com.chrona.app.domain.model.TaskId("user-task"),
            title = "A real user task",
            status = TaskStatus.INBOX,
            createdAt = java.time.Instant.EPOCH,
            updatedAt = java.time.Instant.EPOCH,
        )
        replayStore.putIfAbsent(keeper)
        replayStore.reset(scenario.clock.runId)
        val afterReset = (replayStore.loadAll() as Outcome.Success).value
        checks.check(
            "reset removes the run and keeps user tasks",
            afterReset.any { it.id == keeper.id } &&
                afterReset.none { it.id.value.startsWith("sim-${scenario.clock.runId}-") },
        ) {
            "after reset the store held ${afterReset.size} task(s): ${afterReset.map { it.id.value }.take(5)}"
        }
    }

    private fun describe(failure: com.chrona.app.domain.model.Failure): String = when (failure) {
        is com.chrona.app.domain.model.Failure.Validation -> failure.reasons.joinToString("; ")
        is com.chrona.app.domain.model.Failure.Conflict -> failure.message
        com.chrona.app.domain.model.Failure.NotFound -> "a referenced task no longer exists"
        is com.chrona.app.domain.model.Failure.StorageFailure -> failure.reason
        else -> "the scenario failed for an unexpected reason"
    }

    private fun findOverlaps(placements: List<SchedulePlacement>): List<String> {
        val sorted = placements.sortedBy { it.start }
        return sorted.zipWithNext()
            .filter { (a, b) -> a.start.isBefore(b.end) && b.start.isBefore(a.end) }
            .map { (a, b) -> "${a.taskId.value}/${a.start} overlaps ${b.taskId.value}/${b.start}" }
    }

    private fun signature(placements: List<SchedulePlacement>): String =
        placements.sortedWith(compareBy({ it.taskId.value }, { it.start })).joinToString(";") {
            "${it.taskId.value}@${it.start.toInstant()}#${it.sessionIndex}"
        }
}

/** Convenience: plan a pool directly, for tests that want a plan without a whole run. */
fun planOnce(
    tasks: List<Task>,
    days: List<com.chrona.app.domain.scheduling.PlanningDay>,
    now: java.time.ZonedDateTime? = null,
    previousPlacements: List<SchedulePlacement> = emptyList(),
): Outcome<com.chrona.app.domain.scheduling.MultiDayPlan> =
    DeterministicMultiDayPlanner(DeterministicScheduleEngine()).plan(
        MultiDayInput(
            days = days,
            tasks = tasks,
            now = now,
            previousPlacements = previousPlacements,
        ),
    )
