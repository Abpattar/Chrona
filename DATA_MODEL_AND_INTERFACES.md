# Data Model and Interfaces

Room/SQLite is the proposed local store. This is the logical schema; exact Room annotations/SQL belong to implementation but must preserve these invariants.

## Task
- `id`: TaskId/string primary key, stable, immutable.
- `title`: required after confirmation, trimmed and bounded.
- `details`: optional; never put in logs.
- `priority`: LOW / IMPORTANT / CRITICAL; explicit persisted value.
- `estimatedDurationMinutes`: positive integer when set; may be absent in inbox pending clarification.
- `deadline`: optional absolute latest acceptable instant/timezone; distinct from preferred window.
- `requestedDate`: optional local date for date-only requests; distinct from an instant deadline.
- `earliestStart`: optional bound with documented hard/soft semantics.
- `preferredStart` / `preferredEnd`: optional soft local-time window; not equivalent to deadline.
- `flexibility`: FIXED / FLEXIBLE; a fixed task has explicit start/end and cannot be auto-moved.
- `splittable`: boolean; default false.
- `minimumSessionMinutes`: positive when splitting is allowed.
- `status`: canonical `TaskStatus`; transitions validated in domain layer.
- `origin`: VOICE / TYPED / MANUAL_EDIT / future calendar import. No raw audio blob.
- `projectId`/category: optional; MVP can use one simple grouping field rather than a full project platform.
- `createdAt`, `updatedAt`: instants; tests inject clock.
- `completedAt`, `postponedUntil`: optional state-related timestamps.
- `version`: optimistic concurrency integer for edits/proposals.
- `assumptionsJson`: optional, minimized versioned text used to show interpretation assumptions; no hidden model traces.

Indexes should follow measured queries: status, deadline/request date, project/group and updatedAt as appropriate. Validate fields at domain boundaries, not solely in text fields.

## CalendarEvent (manual fixed commitment)
Fields: `id`, `title`, `details?`, `startAt`, `endAt`, `zoneId`, `isFixed` (true for MVP fixed commitments), `source` (MANUAL in MVP; future provider only after approval), `createdAt`, `updatedAt`, `version`. End must follow start. Overlapping fixed events can be represented only if explicitly confirmed; the conflict must be visible and not silently merged.

## TaskRelationship
Fields: `id`, `sourceTaskId`, `targetTaskId`, `type`, `label?`, `explanation?`, `createdBy` (USER/SUGGESTED), `confirmedAt?`, `createdAt`. Foreign keys point to Tasks; no self-edge.
**Canonical dependency direction:** source task `DEPENDS_ON` target task means source is the dependent task and target is its prerequisite. This same direction must be used in graph, editor, validation and scheduler. Reject/flag cycles in hard `DEPENDS_ON` edges. A suggested hard dependency is inactive until owner/user confirms it.
`PART_OF_PROJECT` may initially use relation edges plus an optional project/category label; avoid creating a general project subsystem unless required.

## ScheduleAssignment
Fields: `id`, `taskId`, `startAt`, `endAt`, `zoneId`, `sessionIndex`, `revisionId`, `source` (ENGINE/USER_EDIT), `createdAt`. Allows splitting. Intervals are normalized as absolute instants for overlap checks. Assignment is not task identity.

## ScheduleRevision
Fields: `id`, `createdAt`, `trigger`, `summary`, `policyVersion`, `baseRevisionId?`, `appliedAt?`, `undoneAt?`, `state` (PROPOSED/APPLIED/UNDONE/SUPERSEDED), versioned before/after assignment snapshot or normalized change rows, and reasons. Keep only enough history to explain and safely undo; avoid unbounded activity surveillance.
Applying revision and assignments must be one Room transaction.

## UserPreferences
Local preference data may use DataStore or Room as appropriate: `dayStart?`, `dayEnd?`, `flexibleDay`, `reminderStyle` (QUIET/BALANCED/MORE_GUIDANCE), `nonUrgentDailyCap` (editable default 2 hypothesis), `quietHoursStart?`, `quietHoursEnd?`, `overlayEnabled` (false until explicit opt-in), `voiceRecognitionMode` (ON_DEVICE_ONLY default), `reducedMotion`, `personalizationEnabled` (false/deferred), `updatedAt`. Task/relationship records do not belong in DataStore.

## ReminderPresentationHistory
Fields: `id`, `taskId`, `scheduledFor`, `surfacedAt?`, `surfaceType`, `outcome` (SHOWN/STARTED/DONE/LATER/DISMISSED/SUPPRESSED/FAILED_FALLBACK), `reasonCode`, `batchGroup?`, `localDate`, `createdAt`. Record only needed context for cap/repeat suppression and evaluation; allow bounded retention/clear. `DISMISSED` never changes task state.

## Persistence and lifecycle rules
- Version Room schema and write explicit migrations; do not use destructive migration fallback for real user data.
- `applyRecovery` validates expected revision inside a transaction, then persists new assignments and revision atomically.
- WorkManager unique-work names/IDs must be stable and idempotent; recovery/delete reconciles obsolete work.
- Task deletion cannot leave dangling relations, assignments or revisions. Choose a clear policy between archive and permanent delete; ask for confirmation for irreversible all-data deletion.
- Export uses a documented versioned JSON schema including schema version, generated time/time zone and entity type inventory. Do not include raw audio. Import is not MVP unless conflict/merge semantics are designed.
- Use app-private local storage and document Android backup/data extraction policy. Owner delete path covers app-owned DB rows, preferences, revisions, app exports (where managed), pending work, app-managed notification/surface state. Explain that Chrona cannot delete content processed independently by an OS speech provider.
- Do not claim independent database encryption unless actual encryption is implemented/tested. App sandbox/device protection is not the same as independently encrypted SQLite.

## Interface boundaries
- Repositories expose domain-level records/results, not Room entities.
- Scheduler accepts an immutable snapshot, injected time/timezone and policy version; no hidden reads.
- Voice states are explicit: Idle, RequestingPermission, Listening, Transcribing, Parsed, NeedsCorrection, Error, Cancelled. Cancel/destroy recognizer on cancellation/lifecycle exit.
- Overlay/notification surfaces report available/denied/unavailable/failure; core use-case action remains idempotent.
- Future sync requires its own conflict-resolution and server-authorization protocol; it is not merely “swap repository with network”.
