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

data class TaskIntentDraft(
    val title: String,
    val detail: String?,
    val requestedDate: LocalDate?,
    val deadline: ZonedDateTime?,
    val estimatedDuration: Duration?,
    val preferredWindow: TimeWindow?,
    val priority: Priority?,
    val flexibility: Flexibility?,
    val splittable: Boolean?,
    val minimumSession: Duration?,
    val proposedRelations: List<RelationDraft>,
    val assumptions: List<ParseAssumption>,
    val confidence: Float,
    val clarification: ClarificationPrompt?,
)

sealed interface TaskUnderstandingResult {
    data class Parsed(val draft: TaskIntentDraft) : TaskUnderstandingResult
    data class NeedsClarification(val prompt: ClarificationPrompt) : TaskUnderstandingResult
    data class Unsupported(val reason: ParseFailureReason) : TaskUnderstandingResult
}
```

Parser behavior: interpret the request, not choose final schedule placement; do not persist records; do not silently create a hard dependency from vague language. Confidence is a heuristic indicator, not a calibrated probability. Same text/reference time/timezone/configuration gives same parser result. MVP includes a deterministic parser only; interface existence does not authorize a remote provider.

## Schedule contract

```kotlin
interface ScheduleEngine {
    fun propose(input: ScheduleInput): ScheduleProposal
}

data class ScheduleProposal(
    val proposalId: String,
    val basedOnRevisionId: RevisionId?,
    val placements: List<SchedulePlacement>,
    val unplaced: List<UnplacedTask>,
    val conflicts: List<ScheduleConflict>,
    val changes: List<ScheduleChange>,
    val assumptions: List<ScheduleAssumption>,
    val explanation: String,
    val policyVersion: String,
)
```

Rules:
- Pure deterministic function; no I/O, Android, database, external API or global time read.
- Identical normalized input + policy version yields identical proposal semantics.
- No placement overlaps fixed commitments or violates a confirmed hard prerequisite order.
- Completed/skipped/archived tasks are not silently reactivated.
- Every eligible active task is placed or appears in `unplaced` with a reason.
- Soft preferences may be missed with explanation; do not label them as hard conflicts.
- Engine creates a proposal only, not persisted state.

## Recovery/revision contract

```kotlin
interface BuildScheduleUseCase {
    suspend operator fun invoke(input: ScheduleInput): ScheduleProposal
}
interface ProposeRecoveryUseCase {
    suspend operator fun invoke(request: RecoveryRequest): ScheduleProposal
}
interface ApplyRecoveryUseCase {
    suspend operator fun invoke(proposalId: String, expectedRevision: RevisionId?): ApplyRecoveryResult
}
interface UndoScheduleRevisionUseCase {
    suspend operator fun invoke(revisionId: RevisionId): UndoResult
}
```

`ApplyRecoveryUseCase` rechecks the expected revision inside the transaction. Stale proposals are rejected or recomputed; they may not overwrite later edits. Assignment changes and revision metadata commit atomically. Undo is allowed only when the prior state can be restored without wiping newer constraints; otherwise return `RequiresNewRecovery` plus reasons. Fixed commitments remain unchanged.

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
