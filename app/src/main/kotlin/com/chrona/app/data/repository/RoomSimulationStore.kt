package com.chrona.app.data.repository

import androidx.room.withTransaction
import com.chrona.app.data.local.ChronaDatabase
import com.chrona.app.data.local.SimulationDao
import com.chrona.app.data.local.SimulationDayEntity
import com.chrona.app.data.local.SimulationEventEntity
import com.chrona.app.data.local.toDomain
import com.chrona.app.data.local.toEntity
import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskStatus
import com.chrona.app.domain.scheduling.SchedulePlacement
import com.chrona.app.domain.simulation.SimulationDayReport
import com.chrona.app.domain.simulation.SimulationEvent
import com.chrona.app.domain.simulation.SimulationEventKind
import com.chrona.app.domain.simulation.SimulationMovedTask
import com.chrona.app.domain.simulation.SimulationStore
import com.chrona.app.domain.simulation.SimulationTaskFactory
import com.chrona.app.domain.task.TaskStatusMachine
import java.time.Instant
import java.time.ZoneId

/**
 * Room-backed [SimulationStore].
 *
 * Simulation *tasks* are ordinary rows in the shared `tasks` table, tagged with
 * [SimulationTaskFactory.SIMULATION_LABEL] and a run-specific id prefix. That is deliberate: a
 * simulated task then obeys exactly the same persistence, validation, concurrency and history
 * rules as a real one, so the simulation exercises production behaviour instead of a parallel toy
 * model that could drift away from it.
 *
 * The consequence is that isolation has to be enforced rather than structural, and it is enforced
 * in exactly one place — [reset]. Every delete there is filtered by the run's id prefix or by the
 * reserved label, and no other method in this class can remove a task at all.
 */
class RoomSimulationStore(
    private val database: ChronaDatabase,
    private val simulationDao: SimulationDao = database.simulationDao(),
    /** The one run this device stores. See [activeRunId]. */
    private val runId: String = SimulationTaskFactory.runId,
) : SimulationStore {

    override suspend fun putIfAbsent(task: Task): Outcome<Unit> = storage {
        // Insert-and-ignore rather than upsert: replaying a day must not reset a task's status,
        // bump its version, or overwrite an edit made since it was first created.
        database.taskDao().insertIgnoringConflict(task.toEntity())
        Unit
    }

    override suspend fun loadAll(): Outcome<List<Task>> = storage {
        database.taskDao()
            .findByProjectLabel(SimulationTaskFactory.SIMULATION_LABEL)
            .map { it.toDomain() }
    }

    override suspend fun load(ids: Set<TaskId>): Outcome<List<Task>> = storage {
        ids.mapNotNull { id -> database.taskDao().findById(id.value)?.toDomain() }
    }

    /**
     * Applies a status change through the production task repository.
     *
     * Delegating rather than writing SQL keeps one definition of "a valid status change" and one
     * definition of the audit row it produces, so the simulation's history is the same history
     * the user sees.
     */
    override suspend fun transition(
        taskId: TaskId,
        to: TaskStatus,
        reasonCode: String,
        at: Instant,
    ): Outcome<Task> =
        RoomTaskRepository(database).updateStatus(
            taskId = taskId,
            transition = { current ->
                when (val result = TaskStatusMachine.transition(current, to, at)) {
                    is TaskStatusMachine.Result.Changed -> Outcome.Success(result.task)
                    is TaskStatusMachine.Result.Unchanged -> Outcome.Success(current)
                    is TaskStatusMachine.Result.Rejected -> Outcome.Failed(Failure.Conflict(result.reason))
                }
            },
            reasonCode = reasonCode,
            occurredAt = at,
        )

    override suspend fun recordDay(report: SimulationDayReport): Outcome<Unit> = storage {
        simulationDao.upsertDay(report.toEntity())
        Unit
    }

    override suspend fun recordEvent(event: SimulationEvent): Outcome<Unit> = storage {
        simulationDao.insertEvent(
            SimulationEventEntity(
                runId = event.runId,
                dayOffset = event.dayOffset,
                kind = event.kind.name,
                taskId = event.taskId,
                detail = event.detail,
            ),
        )
        Unit
    }

    override suspend fun loadDays(): Outcome<List<SimulationDayReport>> = storage {
        simulationDao.days(runId).map { it.toDomain() }
    }

    override suspend fun loadEvents(): Outcome<List<SimulationEvent>> = storage {
        simulationDao.events(runId).mapNotNull { row ->
            val kind = runCatching { SimulationEventKind.valueOf(row.kind) }.getOrNull()
                ?: return@mapNotNull null
            SimulationEvent(row.runId, row.dayOffset, kind, row.taskId, row.detail)
        }
    }

    override suspend fun loadLastPlan(): Outcome<List<SchedulePlacement>> = storage {
        simulationDao.plan(runId)?.placements?.let(::decodePlacements) ?: emptyList()
    }

    override suspend fun savePlan(placements: List<SchedulePlacement>): Outcome<Unit> = storage {
        simulationDao.savePlan(runId, encodePlacements(placements))
        Unit
    }

    /**
     * Clears this run only.
     *
     * One transaction, so a failure cannot leave the run half-deleted and the app with a pool
     * whose tasks have no report. The status log and relationships are deleted explicitly
     * before the tasks: they are history rather than cascading children, so foreign keys do not
     * reach them.
     */
    override suspend fun reset(runId: String): Outcome<Unit> = storageOutcome {
        database.withTransaction {
            val ownedIds = database.taskDao()
                .findByProjectLabel(SimulationTaskFactory.SIMULATION_LABEL)
                .map { it.id }
                .filter { it.startsWith("sim-$runId-") }

            if (ownedIds.isNotEmpty()) {
                database.taskStatusLogDao().deleteForTasks(ownedIds)
                database.taskRelationshipDao().deleteForTasks(ownedIds)
                database.taskDao().deleteByIds(ownedIds)
            }
            simulationDao.deleteDays(runId)
            simulationDao.deleteEvents(runId)
            simulationDao.deletePlan(runId)
            Outcome.Success(Unit)
        }
    }
}

/**
 * Compact, round-trippable encoding for the single `simulation_plans` row.
 *
 * One row per run rather than a table of revisions: the simulation only ever needs to diff the
 * previous step against the current one, so keeping every plan would be storage nobody reads,
 * and keeping one makes reset a single delete.
 */
internal fun encodePlacements(placements: List<SchedulePlacement>): String =
    placements.joinToString(FIELD_SEPARATOR) {
        listOf(
            it.taskId.value,
            it.start.toInstant().toEpochMilli().toString(),
            it.end.toInstant().toEpochMilli().toString(),
            it.sessionIndex.toString(),
            it.start.zone.id,
            it.end.zone.id,
        ).joinToString(RECORD_SEPARATOR)
    }

/**
 * Decodes what [encodePlacements] wrote, skipping anything that does not parse.
 *
 * Losing a placement is the safe direction to fail in: the planner then reports those tasks as
 * newly placed, which is true, rather than crashing a simulated day over a corrupt row.
 */
internal fun decodePlacements(encoded: String): List<SchedulePlacement> {
    if (encoded.isBlank()) return emptyList()
    return encoded.split(FIELD_SEPARATOR).mapNotNull { record ->
        val parts = record.split(RECORD_SEPARATOR)
        if (parts.size < 6) return@mapNotNull null
        runCatching {
            SchedulePlacement(
                taskId = TaskId(parts[0]),
                start = Instant.ofEpochMilli(parts[1].toLong()).atZone(ZoneId.of(parts[4])),
                end = Instant.ofEpochMilli(parts[2].toLong()).atZone(ZoneId.of(parts[5])),
                sessionIndex = parts[3].toInt(),
            )
        }.getOrNull()
    }
}

private const val FIELD_SEPARATOR = ";"
private const val RECORD_SEPARATOR = "|"

private fun SimulationDayReport.toEntity() = SimulationDayEntity(
    runId = runId,
    dayOffset = dayOffset,
    dateIso = dateIso,
    referenceEpochMillis = referenceInstant.toEpochMilli(),
    availableWindows = availableWindows,
    lostMinutes = lostMinutesToInterruption,
    activeTasks = activeTaskCount,
    scheduledTasks = scheduledTaskCount,
    placedBlocks = placedBlockCount,
    completedTasks = completedTaskCount,
    missedTasks = missedTaskCount,
    postponedTasks = postponedTaskCount,
    unscheduledTasks = unscheduledTaskCount,
    overdueTasks = overdueTaskCount,
    atRiskTasks = atRiskTaskCount,
    fixedCommitments = fixedCommitmentCount,
    conflictCount = conflictCount,
    movedTasks = movedTaskCount,
    newlyPlacedTasks = newlyPlacedCount,
    revisionCount = revisionCount,
    scenarioNote = scenarioNote,
    // Sorted so the column is byte-stable between runs and diffable in a report.
    unplacedReasons = unplacedReasons.entries
        .sortedBy { it.key }
        .joinToString(",") { "${it.key}=${it.value}" },
    runtimeMillis = runtimeMillis,
)

private fun SimulationDayEntity.toDomain() = SimulationDayReport(
    runId = runId,
    dayOffset = dayOffset,
    dateIso = dateIso,
    referenceInstant = Instant.ofEpochMilli(referenceEpochMillis),
    availableWindows = availableWindows,
    lostMinutesToInterruption = lostMinutes,
    activeTaskCount = activeTasks,
    scheduledTaskCount = scheduledTasks,
    placedBlockCount = placedBlocks,
    completedTaskCount = completedTasks,
    missedTaskCount = missedTasks,
    postponedTaskCount = postponedTasks,
    unscheduledTaskCount = unscheduledTasks,
    overdueTaskCount = overdueTasks,
    atRiskTaskCount = atRiskTasks,
    fixedCommitmentCount = fixedCommitments,
    conflictCount = conflictCount,
    movedTaskCount = movedTasks,
    newlyPlacedCount = newlyPlacedTasks,
    revisionCount = revisionCount,
    scenarioNote = scenarioNote,
    unplacedReasons = unplacedReasons.split(',')
        .mapNotNull { entry ->
            val parts = entry.split('=')
            if (parts.size != 2) return@mapNotNull null
            parts[1].toIntOrNull()?.let { parts[0] to it }
        }
        .toMap(),
    // Per-task movement detail lives in `simulation_events`, which is what the change history
    // reads. A report row keeps the counted numbers the totals are built from.
    movedTasks = emptyList<SimulationMovedTask>(),
    runtimeMillis = runtimeMillis,
)
