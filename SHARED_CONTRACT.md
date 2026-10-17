# Chrona Shared Contract — Canonical Cross-Module Behavior

This is the authoritative cross-module contract. The data model expands persisted fields in `DATA_MODEL_AND_INTERFACES.md`. Code signatures are canonical conceptual signatures to implement in Kotlin without silently changing semantics.

## Shared conventions
- Stable opaque IDs: `TaskId`, `EventId`, `RelationId`, `RevisionId`; never use title, list index, board position or scheduled time as identity.
- Use `java.time` with explicit timezone/instant semantics for deadlines and fixed commitments.
- Expected user/service failures return typed results or sealed outcomes. Do not use generic exceptions as normal control flow.
- Mutations are transactional and idempotent where retries/notification actions are possible.
- External/parser results are untrusted until schema-validated.
- Inject `Clock`/reference time, IDs, repositories, parser and platform surfaces into tests.

## Canonical core types

```kotlin
@JvmInline value class TaskId(val value: String)
@JvmInline value class EventId(val value: String)
@JvmInline value class RelationId(val value: String)
@JvmInline value class RevisionId(val value: String)

enum class Priority { LOW, IMPORTANT, CRITICAL }
enum class TaskStatus { INBOX, PLANNED, IN_PROGRESS, COMPLETED, POSTPONED, SKIPPED, ARCHIVED }
enum class RelationType { DEPENDS_ON, SUPPORTS, PART_OF_PROJECT, SAME_EVENT, RELATED_REFERENCE }
enum class Flexibility { FIXED, FLEXIBLE }
enum class TaskOrigin { VOICE, TYPED, MANUAL_EDIT, CALENDAR_IMPORT }
enum class RecoveryTrigger {
  LATE_START, COMMITMENT_OVERRUN, TASK_DELAYED, NEW_TASK,
  BREAK_REQUESTED, TASK_CANCELLED, AVAILABILITY_CHANGED
}
enum class UnplacedReason {
  NO_AVAILABLE_WINDOW, DEADLINE_INFEASIBLE, DEPENDENCY_INCOMPLETE,
  FIXED_CONFLICT, MIN_SESSION_UNAVAILABLE, INVALID_DURATION,
  USER_CHOICE_REQUIRED
}
```

## Task intent parsing contract

```kotlin
interface TaskUnderstandingProvider {
    suspend fun understand(request: TaskUnderstandingRequest): TaskUnderstandingResult
}

data class TaskUnderstandingRequest(
    val text: String,
    val referenceTime: ZonedDateTime,
    val zoneId: ZoneId,
    val preferences: ParsePreferences,
)

data class ParsePreferences(
    val dayStart: LocalTime = LocalTime.of(8, 0),
    val dayEnd: LocalTime = LocalTime.of(22, 0),
    val confidenceThreshold: Float = 0.6f,
)

data class TaskIntentDraft(
    val title: String,
    val detail: String? = null,
    val requestedDate: LocalDate? = null,
    val deadline: ZonedDateTime? = null,
    val estimatedDuration: Duration? = null,
    val preferredWindow: TimeWindow? = null,
    val priority: Priority? = null,
    val flexibility: Flexibility? = null,
    val splittable: Boolean? = null,
    val minimumSession: Duration? = null,
    // Absolute interval for an explicitly fixed block, e.g. "dentist 3 to 4 tomorrow".
    // Added in P03: FIXED flexibility alone cannot express an instant (ADR-026).
    val fixedRange: TimeRange? = null,
    val proposedRelations: List<RelationDraft> = emptyList(),
    val assumptions: List<ParseAssumption> = emptyList(),
    // Structural score in 0..1; heuristic, never a calibrated probability.
    val confidence: Float = 0f,
)

data class RelationDraft(
    val type: RelationType,
    val targetText: String,
    val label: String? = null,
    val explanation: String? = null,
    // Always SUGGESTED: the parser does not read the task list, so resolving a spoken
    // reference to a TaskId and confirming the edge is the relation editor's job (P05).
    val createdBy: RelationCreatedBy = RelationCreatedBy.SUGGESTED,
)

data class ParseAssumption(val field: DraftField, val text: String)

enum class DraftField {
    TITLE, REQUESTED_DATE, DEADLINE, DURATION, TIME_OF_DAY, PRIORITY, FLEXIBILITY, RELATION
}

data class ClarificationOption(val label: String, val patch: TaskIntentDraft)

data class ClarificationPrompt(
    val field: DraftField,
    val question: String,
    val options: List<ClarificationOption>,
    val base: TaskIntentDraft,
)

sealed interface TaskUnderstandingResult {
    data class Parsed(val draft: TaskIntentDraft) : TaskUnderstandingResult
    // The (single) prompt carries the unanswered field and the base draft; there is no
    // `clarification` field on the draft, so a partial draft cannot be mistaken for a
    // complete one (P03, ADR-026).
    data class NeedsClarification(val prompt: ClarificationPrompt) : TaskUnderstandingResult
    data class Unsupported(val reason: ParseFailureReason) : TaskUnderstandingResult
}

enum class ParseFailureReason(val message: String) {
    EMPTY_INPUT, INPUT_TOO_LONG, UNSUPPORTED_LANGUAGE, NOT_A_TASK_REQUEST,
    UNSUPPORTED_COMMAND, NO_TITLE_REMAINING, MALFORMED_DATE, IMPLAUSIBLE_DURATION
}
```

Parser behavior: interpret the request, not choose final schedule placement; do not persist records; do not silently create a hard dependency from vague language. Confidence is a heuristic indicator, not a calibrated probability. Same text/reference time/timezone/configuration gives same parser result. The deterministic parser is the authority and the default; an optional cloud provider (ADR-037) may be wired behind this seam, but only as an enrichment — it runs off the scheduling critical path and any failure falls back to the deterministic parser. `ParseFailureReason` is a closed enum so the UI knows when to offer typing rather than guess.

## Schedule contract

```kotlin
interface ScheduleEngine {
    fun propose(input: ScheduleInput): ScheduleProposal
}

data class ScheduleProposal(
    val proposalId: String,          // deterministic; nameUUIDFromBytes, never randomUUID
    val basis: ScheduleBasis,        // revisionId it was built from + per-task versions it saw
    val placements: List<SchedulePlacement>,
    val unplaced: List<UnplacedTask>,   // each carries an UnplacedReason + explanation
    val conflicts: List<ScheduleConflict>,
    val changes: List<ScheduleChange>,  // movement vs previousPlacements, explained
    val assumptions: List<ScheduleAssumption>,
    val overloaded: Boolean,            // more work than the day can hold; never the user's fault
    val explanation: String,
    val policyVersion: String,
)
```

`ScheduleInput` carries `tasks`, `fixedCommitments`, `dayStart`, `dayEnd`, `relationships`,
an injected `now`, `buffer`, `previousPlacements`, `basedOnRevisionId` and `policyVersion`.
It is an immutable snapshot; the engine reads no clock, storage or network.

Rules:
- Pure deterministic function; no I/O, Android, database, external API or global time read.
- Identical normalized input + policy version yields identical proposal semantics and the same
  `proposalId`.
- No placement overlaps fixed commitments or FIXED tasks; those are never moved.
- No placement violates a confirmed hard prerequisite order; a hard cycle rejects the affected
  set with an explanation instead of guessing.
- A deadline that cannot be met is reported (`UnplacedReason.DEADLINE_INFEASIBLE`), never overrun.
- Completed/skipped/archived tasks are not silently reactivated and are not scheduled.
- Every eligible active task is placed or appears in `unplaced` with a reason.
- Soft preferences (preferred windows, buffers, earliest start) may be missed with explanation;
  do not label them as hard conflicts.
- Overload is stated as a capacity limit, with alternatives left to the user; the user is never
  blamed or punished.
- Engine creates a proposal only, not persisted state.

## Recovery/revision contract

```kotlin
interface ProposeRecoveryUseCase {
    suspend operator fun invoke(request: RecoveryRequest): ScheduleProposal
}
interface ApplyRecoveryUseCase {
    suspend operator fun invoke(
        proposal: ScheduleProposal,
        expectedRevision: RevisionId?,
        reasonCode: String = ScheduleRevisionReason.APPLIED,
    ): ApplyRecoveryResult
}
interface UndoScheduleRevisionUseCase {
    suspend operator fun invoke(revisionId: RevisionId): UndoResult
}

interface ScheduleRevisionRepository {
    suspend fun current(): Outcome<ScheduleRevision?>
    suspend fun apply(
        proposal: ScheduleProposal,
        expectedRevision: RevisionId?,
        reasonCode: String,
        appliedAt: Instant,
    ): Outcome<RevisionId>
    suspend fun undo(revisionId: RevisionId, appliedAt: Instant): Outcome<Unit>
}
```

`DeterministicRecoveryPlanner` runs the same engine as first planning; there is deliberately no
second algorithm. `ApplyRecoveryUseCase` rechecks the expected revision **and** every task
version inside one transaction. Stale proposals are rejected with a plain-language reason; they
may not overwrite later edits. Placement changes, revision metadata and any `PLANNED` status
change commit atomically, with a status-history row. Undo is allowed only for the newest
revision and only when no task was edited after it was applied; otherwise it is refused with a
safe next step ("build a new plan"). Fixed commitments remain unchanged.

## Task and card actions are distinct
- `startTask(taskId, occurredAt)` moves an eligible task to `IN_PROGRESS`.
- `completeTask(taskId, occurredAt)` sets `COMPLETED` and completion timestamp.
- `postponeTask(taskId, until?, reason?)` records postponement and triggers/requests a plan adjustment as appropriate.
- `skipTask(taskId, reason?)` is an explicit user choice.
- `archiveTask(taskId)` is a separate lifecycle action; retention/purge semantics must be documented.
- `dismissQuestCard(cardId)` only closes/persists presentation dismissal; it never changes task status.
- `snoozeQuestCard(cardId, until)` requests later re-eligibility only after policy checks; it is not completion.

## Repository contract (conceptual)
```kotlin
interface TaskRepository {
    suspend fun get(taskId: TaskId): Task?
    suspend fun list(query: TaskQuery): List<Task>
    suspend fun insert(task: Task): WriteResult<TaskId>
    suspend fun update(task: Task, expectedVersion: Long): WriteResult<Unit>
    suspend fun delete(taskId: TaskId): WriteResult<Unit>
}
interface EventRepository {
    suspend fun list(range: TimeRange): List<CalendarEvent>
    suspend fun upsert(event: CalendarEvent): WriteResult<EventId>
}
interface RelationshipRepository {
    suspend fun listFor(taskIds: Set<TaskId>): List<TaskRelationship>
    suspend fun replaceFor(source: TaskId, links: List<TaskRelationship>): WriteResult<Unit>
}
interface ScheduleRevisionRepository {
    suspend fun apply(proposal: ScheduleProposal, expectedRevision: RevisionId?): ApplyRecoveryResult
    suspend fun undo(revisionId: RevisionId): UndoResult
}
interface PreferencesRepository {
    suspend fun get(): UserPreferences
    suspend fun update(value: UserPreferences): WriteResult<Unit>
}
```

Implement exact concrete return/error types consistently. Do not expose Room entity types to domain/use cases/UI. Any cross-module signature change requires updating this file, the symbol registry, consumers, tests and change log in the same change.

## Reminder contract

```kotlin
data class ReminderCandidate(
    val taskId: TaskId,
    val eligibleAt: Instant,
    val priority: Priority,
    val relationGroup: String?,
)
data class ReminderDecision(
    val action: ReminderAction,
    val tasks: List<TaskId>,
    val reason: ReminderDecisionReason,
    val nextEligibleAt: Instant?,
)
enum class ReminderAction { SUPPRESS, IN_APP, NOTIFICATION, OVERLAY_BEST_EFFORT, BATCH }
interface ReminderPolicy {
    fun decide(input: ReminderPolicyInput): List<ReminderDecision>
}
```

The policy receives user mode, quiet hours, configurable per-day nonurgent cap, repeated-dismiss history, relation grouping and platform permission/surface availability as explicit input. Cap 2 is a configurable hypothesis; it is not a hardcoded universal truth. Platform overlay failure falls back to notification/in-app. Surfacing/dismissing reminders never changes the task status.

## Error outcome families
Expected failure outcomes include `ValidationFailure`, `NotFound`, `Conflict` (fixed constraint or stale revision), `PermissionDenied`, `Unsupported`, `StorageFailure`, `PlatformUnavailable`, `RequiresUserChoice`, `Cancelled` and `UnknownFailure`. UI uses plain language and safe recovery guidance, never stack traces or secrets.
