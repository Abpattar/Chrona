package com.chrona.app.domain.simulation

import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskOrigin
import com.chrona.app.domain.model.TaskStatus

/**
 * Everything the simulation needs from storage, expressed as ports.
 *
 * Ports rather than a Room type so the whole simulation can run against an in-memory store in a
 * test at full fidelity — the engine, the planner and the lifecycle rules are exercised exactly
 * as they are on a device, with only the SQLite underneath swapped out.
 */
interface SimulationStore {

    /**
     * Stores a task if it is new, and leaves an existing one alone.
     *
     * Idempotent on purpose: advancing the clock twice must not create a second copy of a task
     * that already exists, which is the most common way a "simulation" quietly corrupts itself.
     */
    suspend fun putIfAbsent(task: Task): Outcome<Unit>

    suspend fun loadAll(): Outcome<List<Task>>

    suspend fun load(ids: Set<TaskId>): Outcome<List<Task>>

    /**
     * Applies a status change through the same path production uses, so the audit trail and the
     * optimistic-concurrency guard behave identically.
     */
    suspend fun transition(
        taskId: TaskId,
        to: TaskStatus,
        reasonCode: String,
        at: java.time.Instant,
    ): Outcome<Task>

    /** Writes the per-day outcome of one simulated day. */
    suspend fun recordDay(report: SimulationDayReport): Outcome<Unit>

    /** Appends one notable thing that happened, for the change history. */
    suspend fun recordEvent(event: SimulationEvent): Outcome<Unit>

    /** Everything this run has recorded so far, oldest day first. */
    suspend fun loadDays(): Outcome<List<SimulationDayReport>>

    suspend fun loadEvents(): Outcome<List<SimulationEvent>>

    /**
     * The placements produced by the previous step.
     *
     * Without this a replan has nothing to compare against, so "these four tasks moved" would be
     * an assertion rather than an observation. It is the simulation's stand-in for the revision
     * the production schedule screen diffs against.
     */
    suspend fun loadLastPlan(): Outcome<List<com.chrona.app.domain.scheduling.SchedulePlacement>>

    suspend fun savePlan(placements: List<com.chrona.app.domain.scheduling.SchedulePlacement>): Outcome<Unit>

    /** Removes only this run's records. Ordinary user tasks are never in scope. */
    suspend fun reset(runId: String): Outcome<Unit>
}

/** One recorded thing that happened during the run. */
data class SimulationEvent(
    val runId: String,
    val dayOffset: Int,
    val kind: SimulationEventKind,
    val taskId: String? = null,
    val detail: String,
)

enum class SimulationEventKind {
    DAY_ADVANCED,
    TASK_ARRIVED,
    TASK_COMPLETED,
    TASK_MISSED,
    TASK_POSTPONED,
    DISRUPTION,
    RECOVERY_APPLIED,
    UNPLACEABLE,
    RESET,
}

/** What one simulated day produced. Every number here is counted, never declared. */
data class SimulationDayReport(
    val runId: String,
    val dayOffset: Int,
    val dateIso: String,
    val referenceInstant: java.time.Instant,
    val availableWindows: Int,
    val lostMinutesToInterruption: Int,
    val activeTaskCount: Int,
    val scheduledTaskCount: Int,
    val placedBlockCount: Int,
    val completedTaskCount: Int,
    val missedTaskCount: Int,
    val postponedTaskCount: Int,
    val unscheduledTaskCount: Int,
    val overdueTaskCount: Int,
    val atRiskTaskCount: Int,
    val fixedCommitmentCount: Int,
    val conflictCount: Int,
    val movedTaskCount: Int,
    /** Tasks that had no placement yesterday and got one today. Not the same as "moved". */
    val newlyPlacedCount: Int,
    val revisionCount: Int,
    val scenarioNote: String,
    val unplacedReasons: Map<String, Int>,
    val movedTasks: List<SimulationMovedTask>,
    val runtimeMillis: Long,
) {
    companion object {
        /** A day on which nothing happened, used before day 0 so reports read uniformly. */
        fun empty(runId: String): SimulationDayReport = SimulationDayReport(
            runId = runId,
            dayOffset = -1,
            dateIso = "",
            referenceInstant = java.time.Instant.EPOCH,
            availableWindows = 0,
            lostMinutesToInterruption = 0,
            activeTaskCount = 0,
            scheduledTaskCount = 0,
            placedBlockCount = 0,
            completedTaskCount = 0,
            missedTaskCount = 0,
            postponedTaskCount = 0,
            unscheduledTaskCount = 0,
            overdueTaskCount = 0,
            atRiskTaskCount = 0,
            fixedCommitmentCount = 0,
            conflictCount = 0,
            movedTaskCount = 0,
            newlyPlacedCount = 0,
            revisionCount = 0,
            scenarioNote = "",
            unplacedReasons = emptyMap(),
            movedTasks = emptyList(),
            runtimeMillis = 0,
        )
    }
}

/** A before/after pair for one task, which is what makes a revision explainable. */
data class SimulationMovedTask(
    val taskId: String,
    val title: String,
    val fromInstant: String?,
    val toInstant: String?,
    val reason: String,
)

/** Everything known about one run, assembled from the persisted records. */
data class SimulationRunState(
    val runId: String,
    val days: List<SimulationDayReport>,
    val events: List<SimulationEvent>,
) {
    val currentDay: Int get() = days.lastOrNull()?.dayOffset ?: -1
    val completedDays: Int get() = days.size
    val totals: SimulationTotals get() = SimulationTotals.from(days)
}

/** Run-level totals, each the sum of counted day values. */
data class SimulationTotals(
    val completed: Int = 0,
    val missed: Int = 0,
    val postponed: Int = 0,
    val unscheduled: Int = 0,
    val overdue: Int = 0,
    val atRisk: Int = 0,
    val revisions: Int = 0,
    val moved: Int = 0,
    val conflicts: Int = 0,
) {
    companion object {
        fun from(days: List<SimulationDayReport>): SimulationTotals = SimulationTotals(
            completed = days.sumOf { it.completedTaskCount },
            missed = days.sumOf { it.missedTaskCount },
            postponed = days.sumOf { it.postponedTaskCount },
            unscheduled = days.sumOf { it.unscheduledTaskCount },
            overdue = days.sumOf { it.overdueTaskCount },
            atRisk = days.sumOf { it.atRiskTaskCount },
            revisions = days.sumOf { it.revisionCount },
            moved = days.sumOf { it.movedTaskCount },
            conflicts = days.sumOf { it.conflictCount },
        )
    }
}

/** Result of one simulated day. */
data class SimulationStepResult(
    val report: SimulationDayReport,
    val tasksPlaced: List<TaskId>,
    val tasksUnplaced: List<TaskId>,
    val message: String,
    /** Which processing path actually ran, surfaced verbatim in the UI. */
    val processingMode: String,
)

/** Maps a definition/spec onto a real [Task] the domain already understands. */
object SimulationTaskFactory {

    /** Reserved project label: it is how a simulation record is told apart from a real task. */
    const val SIMULATION_LABEL: String = "simulation"

    /** The single run a device stores. Reset and id prefixes are both scoped by it. */
    val runId: String get() = SimulationDataset.RUN_ID

    fun toTask(
        spec: SimulatedTaskSpec,
        runId: String,
        clock: SimulationClock,
        createdAt: java.time.Instant,
    ): Task {
        val zone = clock.zone
        val requestedDate = clock.dateOf(spec.requestedDayOffset)
        val deadline = spec.deadlineDayOffset?.let { offset ->
            val day = clock.dateOf(offset)
            // A deadline that lands on an earlier offset than the request belongs to the next
            // occurrence of that day, not to the past; silently putting it behind the task would
            // make it overdue on arrival for no reason.
            val resolved = if (offset < spec.requestedDayOffset) day.plusDays(1) else day
            resolved.atTime(spec.deadlineTime).atZone(zone)
        }
        val fixedStart = spec.fixedStartTime?.let { requestedDate.atTime(it).atZone(zone) }
        val fixedEnd = spec.fixedEndTime?.let { time ->
            val sameDay = requestedDate.atTime(time).atZone(zone)
            // A fixed block that ends earlier than it starts wraps midnight rather than being
            // rejected: an evening event that runs past midnight is a normal thing to have.
            if (fixedStart != null && !sameDay.isAfter(fixedStart)) sameDay.plusDays(1) else sameDay
        }
        val preferred = spec.preferredStartTime?.let { start ->
            val end = start.plusMinutes(spec.durationMinutes.toLong())
            if (end > java.time.LocalTime.MAX) return@let null
            com.chrona.app.domain.model.TimeWindow(start, end)
        }
        return Task(
            id = TaskId(spec.id),
            title = spec.title,
            details = "Simulated ${spec.category.label.lowercase()} task, day ${spec.requestedDayOffset + 1} of the run.",
            priority = spec.priority,
            estimatedDuration = java.time.Duration.ofMinutes(spec.durationMinutes.toLong()),
            deadline = deadline,
            requestedDate = requestedDate,
            preferredWindow = preferred,
            flexibility = spec.flexibility,
            fixedStart = fixedStart,
            fixedEnd = fixedEnd,
            splittable = spec.splittable,
            minimumSession = spec.minimumSessionMinutes?.let { java.time.Duration.ofMinutes(it.toLong()) },
            status = TaskStatus.INBOX,
            // The marker that keeps simulation rows separable from ordinary user data.
            origin = TaskOrigin.SIMULATION,
            projectLabel = SIMULATION_LABEL,
            createdAt = createdAt,
            updatedAt = createdAt,
        )
    }

    /** Every task this run owns, by definition and by late arrival, in a stable order. */
    fun allSpecs(runId: String = SimulationDataset.RUN_ID): List<SimulatedTaskSpec> =
        (SimulationDataset.instances(runId) + SimulationDataset.lateArrivals(runId))
            .sortedBy { it.id }
}
