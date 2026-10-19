package com.chrona.app.usecase

import com.chrona.app.domain.model.CalendarEvent
import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.RevisionId
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskRelationship
import com.chrona.app.domain.model.UserPreferences
import com.chrona.app.domain.scheduling.DayHorizon
import com.chrona.app.domain.scheduling.DayHorizonFactory
import com.chrona.app.domain.scheduling.FixedCommitment
import com.chrona.app.domain.scheduling.ScheduleEngine
import com.chrona.app.domain.scheduling.ScheduleInput
import com.chrona.app.domain.scheduling.ScheduleProposal
import com.chrona.app.domain.scheduling.ScheduleRevision
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Everything needed to render one day's plan.
 *
 * [proposal] is null only when the caller asked for a day with no revision and no tasks at all;
 * every other case carries a proposal, even an empty one, so the UI can say "nothing fitted"
 * instead of "nothing happened".
 */
data class DayProposalResult(
    val date: LocalDate,
    val horizon: DayHorizon,
    val proposal: ScheduleProposal,
    val tasks: List<Task>,
    val currentRevisionId: RevisionId?,
)

/**
 * Builds one day's plan from the real world: tasks, commitments, relationships and preferences.
 *
 * This is the single place the production app turns stored state into a schedule input. The
 * Schedule screen, the task browser and the simulation all read through it, so they cannot
 * disagree about what "today" means or what hours are available — a class of bug that is
 * invisible until a user notices two screens disagreeing.
 *
 * Pure in the sense that matters: the clock and the zone are injected, so the same stored state
 * and the same reference time always produce the same plan.
 */
class BuildDayProposalUseCase(
    private val loadTasks: suspend (LocalDate) -> Outcome<List<Task>>,
    private val loadCommitments: suspend (ZonedDateTime, ZonedDateTime) -> Outcome<List<CalendarEvent>>,
    private val loadRelationships: suspend () -> Outcome<List<TaskRelationship>>,
    private val loadPreferences: suspend () -> Outcome<UserPreferences>,
    private val loadRevision: suspend () -> Outcome<ScheduleRevision?>,
    private val engine: ScheduleEngine,
    private val now: () -> ZonedDateTime,
    private val zone: ZoneId,
    private val buffer: Duration = Duration.ofMinutes(10),
) {

    suspend operator fun invoke(date: LocalDate = now().toLocalDate()): Outcome<DayProposalResult> {
        val reference = now()

        val tasks = when (val result = loadTasks(date)) {
            is Outcome.Success -> result.value
            is Outcome.Failed -> return result
        }
        // The user's configured day hours, not midnight. Planning a 09:00–20:00 person against a
        // midnight-to-midnight horizon is what let work land in their sleep.
        val preferences = when (val result = loadPreferences()) {
            is Outcome.Success -> result.value
            is Outcome.Failed -> return result
        }
        val horizon = DayHorizonFactory.horizonFor(date, preferences, zone)

        val commitments = when (val result = loadCommitments(horizon.start, horizon.end)) {
            is Outcome.Success -> result.value
            is Outcome.Failed -> return result
        }
        val relationships = when (val result = loadRelationships()) {
            is Outcome.Success -> result.value
            is Outcome.Failed -> return result
        }
        val revision = when (val result = loadRevision()) {
            is Outcome.Success -> result.value
            is Outcome.Failed -> return result
        }

        val proposal = engine.propose(
            ScheduleInput(
                tasks = tasks,
                fixedCommitments = commitments.map { FixedCommitment(it.id.value, it.title, it.range) },
                dayStart = horizon.start,
                dayEnd = horizon.end,
                relationships = relationships,
                now = reference,
                availabilityWindows = horizon.windows,
                buffer = buffer,
                previousPlacements = revision?.placements ?: emptyList(),
                basedOnRevisionId = revision?.id,
            ),
        )
        return Outcome.Success(
            DayProposalResult(
                date = date,
                horizon = horizon,
                proposal = proposal,
                tasks = tasks,
                currentRevisionId = revision?.id,
            ),
        )
    }
}

/** Plain-language text for a failure, shared by every surface that shows one. */
fun describe(failure: Failure): String = when (failure) {
    is Failure.Validation -> failure.reasons.joinToString("\n")
    Failure.NotFound -> "That no longer exists."
    is Failure.Conflict -> failure.message
    is Failure.PermissionDenied -> "Chrona needs a permission it was not given."
    is Failure.Unsupported -> failure.reason
    is Failure.StorageFailure -> "Chrona could not read local storage. Try again."
    is Failure.PlatformUnavailable -> failure.reason
    is Failure.RequiresUserChoice -> failure.question
    Failure.Cancelled -> "That was cancelled."
    is Failure.UnknownFailure -> failure.reason
}
