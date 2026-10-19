package com.chrona.app.domain.simulation

import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskStatus
import com.chrona.app.domain.scheduling.SchedulePlacement
import com.chrona.app.domain.task.TaskStatusMachine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * An in-memory [SimulationStore] for tests, previews and the JVM simulation report.
 *
 * It is a real implementation of the port rather than a stub: task ids are stable, status
 * transitions go through the same validity rules production uses, and a transition that would
 * be illegal fails instead of succeeding quietly. That is what makes a JVM run a meaningful
 * rehearsal of the on-device behaviour instead of a happy-path sketch.
 *
 * Thread-safe, because the UI advances a day on the main dispatcher while a background report
 * generation may read the same store.
 */
class InMemorySimulationStore(
    private val clock: java.time.Clock = java.time.Clock.systemUTC(),
) : SimulationStore {

    private val lock = Mutex()
    private val tasks = linkedMapOf<TaskId, Task>()
    private val days = mutableListOf<SimulationDayReport>()
    private val events = mutableListOf<SimulationEvent>()
    private var lastPlan: List<SchedulePlacement> = emptyList()

    override suspend fun putIfAbsent(task: Task): Outcome<Unit> = lock.withLock {
        // Existing rows are left alone on purpose: replaying a day must not reset a task's
        // status or duplicate it.
        if (tasks[task.id] == null) tasks[task.id] = task
        Outcome.Success(Unit)
    }

    override suspend fun loadAll(): Outcome<List<Task>> = lock.withLock {
        Outcome.Success(tasks.values.sortedBy { it.id.value })
    }

    override suspend fun load(ids: Set<TaskId>): Outcome<List<Task>> = lock.withLock {
        Outcome.Success(ids.mapNotNull { tasks[it] }.sortedBy { it.id.value })
    }

    override suspend fun transition(
        taskId: TaskId,
        to: TaskStatus,
        reasonCode: String,
        at: Instant,
    ): Outcome<Task> = lock.withLock {
        val current = tasks[taskId]
            ?: return@withLock Outcome.Failed(Failure.NotFound)
        // The production status machine decides, including the timestamp bookkeeping, so the
        // simulation cannot grant a transition the app itself would refuse.
        return@withLock when (val result = TaskStatusMachine.transition(current, to, at)) {
            is TaskStatusMachine.Result.Changed -> {
                tasks[taskId] = result.task
                Outcome.Success(result.task)
            }

            is TaskStatusMachine.Result.Unchanged -> Outcome.Success(result.task)
            is TaskStatusMachine.Result.Rejected -> Outcome.Failed(Failure.Conflict(result.reason))
        }
    }

    override suspend fun recordDay(report: SimulationDayReport): Outcome<Unit> = lock.withLock {
        days.removeAll { it.runId == report.runId && it.dayOffset == report.dayOffset }
        days += report
        days.sortBy { it.dayOffset }
        Outcome.Success(Unit)
    }

    override suspend fun recordEvent(event: SimulationEvent): Outcome<Unit> = lock.withLock {
        events += event
        Outcome.Success(Unit)
    }

    override suspend fun loadDays(): Outcome<List<SimulationDayReport>> = lock.withLock {
        Outcome.Success(days.toList())
    }

    override suspend fun loadEvents(): Outcome<List<SimulationEvent>> = lock.withLock {
        Outcome.Success(events.toList())
    }

    override suspend fun loadLastPlan(): Outcome<List<SchedulePlacement>> = lock.withLock {
        Outcome.Success(lastPlan)
    }

    override suspend fun savePlan(placements: List<SchedulePlacement>): Outcome<Unit> = lock.withLock {
        lastPlan = placements
        Outcome.Success(Unit)
    }

    override suspend fun reset(runId: String): Outcome<Unit> = lock.withLock {
        // Scoped to this run's own records. A task that does not belong to the run is never
        // touched, which is the property that keeps ordinary user data safe.
        val ownedPrefix = "sim-$runId-"
        tasks.keys.filter { it.value.startsWith(ownedPrefix) }.forEach { tasks.remove(it) }
        days.removeAll { it.runId == runId }
        lastPlan = emptyList()
        Outcome.Success(Unit)
    }

    /** The reference time the store would use; exposed so tests can assert on it. */
    fun now(): Instant = clock.instant()
}
