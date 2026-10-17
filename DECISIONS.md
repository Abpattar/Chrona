# Chrona Decision Register

Date: 15 October 2026. `APPROVED` refers to product direction confirmed by the owner. `PROPOSED—P01 VERIFY` identifies an ordinary technical default that requires compatibility/build verification. Reopen a settled choice only with new evidence or a concrete material issue.

| ID | Status | Decision | Trade-off / reason | Affected area |
|---|---|---|---|---|
| ADR-001 | APPROVED | Android-first native app; one Git repo; one OpenCode agent in sequential phases | matches product/platform and master workflow | all |
| ADR-002 | APPROVED | Local-first MVP; no required account/backend/cloud sync | lower cost/privacy/scope; no multi-device sync initially | data/privacy |
| ADR-003 | APPROVED | Deterministic scheduler independent of external AI | reliable offline planning, but parser language coverage is intentionally bounded | scheduler/parser |
| ADR-004 | APPROVED | Never embed provider keys/secrets in APK or client source | mobile binary is not a secret store; future provider must be behind approved secure proxy | security |
| ADR-005 | APPROVED | Tap-to-speak only, no always-listening, no unnecessary raw audio | privacy and platform restrictions | voice |
| ADR-006 | APPROVED | On-device speech only when supported; otherwise typed fallback; optional system path only with disclosure/choice | may reduce voice usability on devices without an on-device recognizer | voice |
| ADR-007 | APPROVED | Room/SQLite local relational persistence, migrations and delete/export path | relationships and atomic revision commits; no cloud recovery initially | data |
| ADR-008 | APPROVED | Manual fixed events; calendar provider later | core scheduler can be evaluated without account scopes | calendar |
| ADR-009 | APPROVED | Overlay optional, permission-gated, best effort; notifications/in-app fallback | Android/OEM behavior differs | Quest Card |
| ADR-010 | APPROVED | Quiet reminders; cap two non-urgent cards/day is a configurable hypothesis | must be evaluated; not a hard universal truth | reminders/research |
| ADR-011 | APPROVED | Card actions Start/Done/Later/Dismiss are semantically distinct; dismiss != done | avoids accidental completion/data loss | task/card |
| ADR-012 | APPROVED | Warm tactile paper/pinned-note board, thin red threads; yellow/blue/green priorities plus labels/icons | matches source visual brief and accessibility | UI |
| ADR-013 | APPROVED | Advanced learning/personalization deferred until core loop works | protects two-credit scope and evidence quality | later release |
| ADR-014 | APPROVED | Ubuntu + Windows parity, shared Gradle wrapper and pinned dependency source | reproducibility across two dev machines | developer workflow |
| ADR-015 | APPROVED | Emulator and physical moto g54 Android 15 testing | platform/OEM differences cannot be inferred from build alone | QA |
| ADR-016 | VERIFIED (Ubuntu, P01) | Kotlin + Compose, single Gradle app module, Room, DataStore and pure domain packages | native, testable, proportionate; `scripts/verify.sh` built, tested and linted clean | P01 |
| ADR-017 | VERIFIED (Ubuntu, P01) | minSdk 26, compileSdk 36, targetSdk 36 | stable API 36 path; built against Platform 36 / Build-Tools 36.0.0. Release target policy must be rechecked before publishing | P01/release |
| ADR-018 | VERIFIED (Ubuntu, P01) | JDK17, AGP8.13.2, Gradle8.13, Kotlin2.2.21, KSP2.2.21-2.0.5, Compose BOM2026.06.00; Room2.8.5, DataStore1.2.1, WorkManager2.12.0, Activity Compose1.13.0 | every pin resolved and built. Navigation/Lifecycle corrected — see ADR-018a | P01 |
| ADR-019 | APPROVED (P06) | WorkManager for deferrable reminders/reconciliation; no exact-alarm promise | lower permission and battery scope; OS may delay non-exact work. Implemented as one inexact hourly `PeriodicWorkRequest` (`ExistingPeriodicWorkPolicy.KEEP`); on-device timing NOT RUN (OPEN-014) | P06 |
| ADR-020 | APPROVED | URE deadline, official report/demo rubric remain unknown until provided | avoid invented submission constraints | P08 |
| ADR-021 | APPROVED | No participant study until institution’s ethics/consent/data rules confirmed | safeguards and course policy unknown | P08 |
| ADR-022 | APPROVED | Standalone visual reference image absent from currently available uploaded files; use written PRD §6 until supplied | don't hallucinate unseen composition | UI |
| ADR-018a | APPROVED — P01 TOOLCHAIN GATE | Pin Navigation Compose **2.9.8** and Lifecycle **2.10.0**, not the originally proposed 2.10.2 / 2.11.0 | Navigation 2.10.x and Lifecycle 2.11.0 declare `minCompileSdk=37` and require AGP 9.1.0+, which forces the API-37 path this project deliberately avoids. 2.9.8 / 2.10.0 are the newest releases that keep compileSdk 36 + AGP 8.13.2. Costs us the newest navigation library; buys a stable, documented API-36 build | P01 |
| ADR-023 | APPROVED | Cloud backup and device transfer are disabled; a deliberate export path replaces them | `MASTER_ARCHITECTURE.md` §10 requires an intentional backup decision. Keeps private task data on the device by default without claiming encryption. Reversible; needs an owner decision if a user-visible backup feature is ever wanted | P01 |
| ADR-024 | APPROVED | Release builds are unminified for now | no shrink/obfuscation audit has been done; an unverified R8 config risks stripping Room/Compose classes in a demonstrator build. Revisit before any release | P01 |
| ADR-025 | APPROVED | Application identity `com.chrona.app` / "Chrona" | no approved reverse-DNS ID exists. "Chrona" is provisional; trademark and domain availability are unchecked. Renaming the product is an owner decision | P01 |
| ADR-026 | APPROVED | Parser moved to `domain/parse`; speech boundary to `platform/speech`; capture UI to `ui/voice`. `TaskIntentDraft` adds `fixedRange` and drops `clarification` (carried by `NeedsClarification` with a base draft) | Keeps domain pure (no Android imports). The change lets a fixed time interval be represented without guessing and separates an unanswered clarification from a complete draft. Matches the implemented P03 code and tests | P03 |
| ADR-027 | APPROVED | `ScheduleProposal.proposalId` is derived deterministically from its basis and content via `UUID.nameUUIDFromBytes`; never `randomUUID()` | identical inputs must yield the identical proposal (testable determinism, ADR-003); a random id would make tests flaky and break "same snapshot → same plan" | P04 |
| ADR-028 | APPROVED | Undo is deliberately one-step and conservative: only the newest revision can be undone, and only if no task was edited after it | multi-step undo needs full history replay/merge semantics far beyond scope; refusing on later edits prevents silently discarding newer user changes (REC-003 safety) | P04 |
| ADR-029 | APPROVED | Room schema v2 adds `schedule_revisions`/`schedule_placements` with `MIGRATION_1_2` beside the database; no `fallbackToDestructiveMigration` | preserves the explicit-migration policy (ADR-007) required by RELIABILITY-001; a destructive fallback would silently erase user data | P04 |
| ADR-030 | APPROVED | The board reuses `RelationGraph` as the single source of direction and cycle rules; it is projected on demand via `AppContainer.relationGraph(relationships)` rather than re-derived in the UI | one rule for board, editor, scheduler and validation means direction/cycle semantics can never drift (ADR-003); no second graph implementation | P05 |
| ADR-031 | APPROVED | `AddRelationshipUseCase` explicitly rejects a duplicate `(source, target, type)` triple with `Failure.Validation` | Room `@Upsert` silently swallows the unique-index violation and reports success while storing nothing, which would hand the UI an id that was never persisted; an explicit check makes the outcome truthful and testable. The index remains the last-line database guard | P05 |
| ADR-032 | APPROVED | A `SUGGESTED` hard dependency is stored with `confirmedAt = null` and never constrains order until the user confirms it | REL-002 forbids a silent consequential dependency; dormant storage lets a suggestion be shown without changing the plan, and `ConfirmRelationshipUseCase` re-validates before activating | P05 |
| ADR-033 | APPROVED | Board layout is a pure deterministic function (`BoardLayoutCalculator`) over tasks+relationships; no randomness, wall clock or animation state | stable, reproducible note coordinates (P05 "stable coordinates") and JVM-testable layout, including the 100/200 performance fixture | P05 |
| ADR-034 | APPROVED | Reminder style is a user choice with distinct, testable semantics: `QUIET` suppresses `LOW` entirely (`QUIET_MODE`) and batches related tasks; `BALANCED` batches related tasks and subjects `LOW` to the daily cap; `MORE_GUIDANCE` never batches | NOTIFY-001 and ADR-010 require the user to control the intensity of reminders. Making each style a pure, unit-tested rule (rather than platform behaviour) keeps the promise deterministic and evaluable | P06 |
| ADR-035 | APPROVED | Surface selection order is overlay (enabled + permission granted) → notification → in-app, and a presenter returning `null` is a fallback rather than an error; the non-urgent daily cap counts dispatched **cards** (not tasks) and a `priority` column on `reminder_presentation_history` preserves the count across later task edits/deletions | ADR-009 makes the overlay optional and best-effort, so delivery must always fall back cleanly. Counting cards matches what the user actually experienced (a batch is one interruption) and keeps urgent cards uncapped. The added `priority` column is a documented DATA_MODEL deviation: it prevents a later edit/delete from rewriting a past day's cap; `MIGRATION_2_3` is additive with no destructive fallback | P06 |
| ADR-036 | APPROVED (owner-requested correctness fix) | Scheduling is horizon-explicit and deadline-aware: a task's date-only request decides whether it belongs to today (`[dayStart, dayEnd)`), is carried forward (`BEFORE`, with an assumption), or is left for its own day (`AFTER` → `UnplacedReason.OUTSIDE_HORIZON`); among ready tasks the nearest deadline is scheduled first, then priority, then id (earliest-deadline-first / Jackson's rule, policy `p04-v2`) | fixes two P04 correctness defects — date-only requests were ignored by the engine, and a priority-first order could reject a *feasible* deadline combination. EDF is optimal for independent work without release times and stays explainable in one sentence (CH-SCHED-001). A future-day task is deliberately not an overload. The heuristic can still be suboptimal under fixed commitments/differing release times; documented, not hidden | Correctness (post-P06) |
| ADR-037 | APPROVED (owner-requested) | Cloud task understanding is an optional, **off-by-default** enrichment: the app calls Chrona's own Supabase Edge Function (provider failover Groq → Gemini → OpenRouter), and the deterministic parser remains the authority — any failure (offline, timeout, non-2xx, malformed or rule-breaking draft) falls back silently. The app holds only the Supabase **anon** key; provider keys live solely in function secrets. Only text, reference time and zone are sent; no raw model text is stored | data-leaving-device/provider/cost changes require owner approval (this section), which was given ("full Stage 2"). A pure-local MVP cannot read messy free-form input as well; routing through Chrona's own function keeps keys out of the APK and lets provider choice change without an app release. Trade-off: a hosted model sees one phrase, so the feature stays opt-in and never in the scheduling critical path | Cloud interpreter (post-P06) |

### ADR-018a — Coordinated toolchain change record

- **Date:** 15 October 2026. **Phase:** P01. **Owner approval:** recorded here as a technical
  default under ADR-016/017/018, not a product-scope change, so no separate approval round is
  required. Escalate to the owner if the owner prefers the API-37 path.
- **Evidence:** `./gradlew :app:assembleDebug` failed `checkDebugAarMetadata` with 6 errors.
  Published AAR metadata confirms `minCompileSdk=37` for `navigation-compose-android`
  2.10.0/2.10.1/2.10.2 and for `lifecycle-viewmodel-compose-android` / `lifecycle-runtime-compose-android`
  2.11.0. Full capture in `PROGRESS.md`.
- **Previous choice:** Navigation Compose 2.10.2, Lifecycle 2.10.0 (with 2.11.0 arriving
  transitively from Navigation).
- **New choice:** Navigation Compose 2.9.8, Lifecycle 2.10.0 pinned explicitly.
- **Alternatives considered:**
  1. *Move to compileSdk 37 + AGP 9.x.* Rejected — contradicts
     `CROSS_OS_SETUP_AND_TOOLCHAIN.md` ("Never use a preview SDK purely to be newest") and
     moves the whole project onto an unvalidated platform line for a library minor version.
  2. *Drop Navigation Compose and hand-roll navigation.* Rejected — reimplementing back
     stack, state restoration and accessibility for a five-destination shell is far more
     complexity than the version pin costs.
  3. *Stay on 2.9.7.* Rejected — 2.9.8 is API-compatible, same `minCompileSdk`, and is the
     latest patch of that line.
- **Affected requirements/files/contracts/tests:** PLAT-002, PRJ-001, UI-001;
  `gradle/libs.versions.toml`, `CROSS_OS_SETUP_AND_TOOLCHAIN.md`, `README.md`, this file,
  `CHANGELOG.md`, `PROGRESS.md`. No shared-contract symbol changed.
- **Compatibility/migration:** no schema, API or contract migration. Compile-target-only change.
- **Privacy/security/cost:** none.
- **Verification:** `./scripts/verify.sh` passes with the new pins on Ubuntu.

### ADR-026 — P03 structure and parser-contract adjustment

- **Date:** 16 October 2026. **Phase:** P03.
- **Structural change:** `domain/parse` (pure Kotlin) rather than `input/parser`; `platform/speech` rather than `input/voice`; `ui/voice` for capture UI. Preserves the domain core import boundary (no Android imports).
- **Contract change:** `TaskIntentDraft.fixedRange: TimeRange?` added; `clarification` removed from the draft and moved to `TaskUnderstandingResult.NeedsClarification(prompt)` with `prompt.base`. No semantics lost.
- **Verification:** 152 unit/Robolectric tests pass; `scripts/verify.sh` passes; only `RECORD_AUDIO` added, no `INTERNET` permission and no networking dependency. Device on-device availability remains unmeasured (OPEN-007).

### P04 change record — scheduling, recovery and schema v2

- **Date:** 17 October 2026. **Phase:** P04.
- **Structure:** engine lives in `domain/scheduling` (pure, no Android/Room/parser imports);
  recovery planner and apply/undo use cases plus the `ScheduleRevisionRepository` contract live
  in `domain/recovery`; the Room implementation (`RoomScheduleRevisionRepository`) and shared
  `applyStatusChange` helper live in `data/repository`. Recovery UI is a single
  `ui/schedule` surface (preview/apply/undo) rather than a separate `ui/recovery` package.
- **Contract additions:** `ScheduleInput`, `ScheduleProposal` (+`ScheduleBasis`, `ScheduleRevision`,
  `ScheduleRevisionReason`), `RecoveryTrigger`/`RecoveryRequest`/`ApplyRecoveryResult`/`UndoResult`,
  `ApplyRecoveryUseCase`, `UndoScheduleRevisionUseCase`; `ReasonCode.SCHEDULE_APPLIED` and
  `ReasonCode.SCHEDULE_UNDONE`.
- **Decisions:** ADR-027 (deterministic `proposalId`), ADR-028 (conservative one-step undo),
  ADR-029 (schema v2 + `MIGRATION_1_2`, no destructive fallback). Missing task duration assumes
  a documented 30 minutes; zero/negative duration is refused; fixed commitments and `FIXED`
  tasks are never moved; a dependency cycle unplaces the affected set with `DEPENDENCY_INCOMPLETE`.
- **Verification:** `./scripts/verify.sh` passes on Ubuntu — 191 unit/Robolectric tests, 0
  failures; lint 0 errors; `assembleDebugAndroidTest` compiles; debug APK builds. Device run
  remains NOT RUN (OPEN-014).

### P05 change record — board and relationships

- **Date:** 17 October 2026. **Phase:** P05.
- **Structure:** relationship editing lives in `usecase/RelationshipUseCases.kt`; all board
  presentation and state lives in `ui/board/{RelationPresentation,BoardLayout,BoardViewModel,BoardScreen,BoardRoute}.kt`.
  No new storage was added — `RoomRelationshipRepository`, `TaskRelationshipDao`,
  `TaskRelationshipEntity` and the `(source, target, type)` unique index already existed from P02
  and were reused unchanged.
- **Contract additions:** `RelationType.displayLabel()`, `describeRelation(...)`,
  `TaskRelationship.constrainsOrder()`, `BoardNote`/`BoardThread`/`BoardLayout`,
  `BoardLayoutCalculator`, `BoardViewMode`, `BoardFilter`, `BoardUiState`, `BoardViewModel`.
- **Decisions:** ADR-030 (single `RelationGraph`, projected on demand), ADR-031 (explicit
  duplicate-triple rejection), ADR-032 (dormant suggestions), ADR-033 (pure deterministic layout).
- **Verification:** `./scripts/verify.sh` passes on Ubuntu — 231 unit/Robolectric tests, 0
  failures; lint 0 errors; `assembleDebugAndroidTest` compiles; debug APK builds. Instrumented
  Compose board tests compile but are NOT RUN (OPEN-014).

### P06 change record — Quest Cards and reminders

- **Date:** 17 October 2026. **Phase:** P06.
- **Structure:** the pure reminder rules live in `domain/reminder/{Reminder,DeterministicReminderPolicy,ReminderCandidateSelector}.kt`;
  orchestration in `usecase/ReminderUseCases.kt`; persistence in `data/local` (Room v3) and
  `data/repository`; settings in `data/preferences`; Android surfaces in
  `platform/{notification,overlay,permission,work}`; UI in `ui/questcard` and `ui/settings`.
- **Contract additions:** `ReminderPolicy`, `ReminderDecision`, `ReminderSurfaceType`,
  `ReminderOutcome`, `ReminderCard`, `ReminderPresenter`, `TaskAction`, `ReminderPolicyConfig`,
  `ReminderCandidateSelector`, `EvaluateRemindersUseCase`, `RecordReminderHistoryUseCase`,
  `ApplyReminderActionUseCase`, `DeliverRemindersUseCase`, `PreferencesRepository`,
  `ReminderHistoryRepository`; `UserPreferences.QuietHours`/`quietHours`.
- **Decisions:** ADR-019 (WorkManager, now APPROVED), ADR-034 (style semantics), ADR-035 (surface
  order + card-counting cap and the `priority` column). `DISMISS` writes presentation history only
  and never completes/deletes/postpones a task.
- **Verification:** `./scripts/verify.sh` passes on Ubuntu — 280 unit/Robolectric tests, 0
  failures; lint 0 errors; `assembleDebugAndroidTest` compiles; debug APK builds. v2→v3 migration
  tested (`MigrationTest2To3Test`). Notification/overlay/worker runtime checks are NOT RUN
  (OPEN-011/OPEN-014).

### Scheduling correctness change record — requested dates and deadline feasibility (ADR-036)

- **Date:** 17 October 2026. **Phase:** correctness revision of the P04 scheduling policy
  (owner-requested; recorded as ADR-036). **Policy version:** `ScheduleInput.policyVersion`
  default `p04-v1` → `p04-v2`.
- **Defect 1 — requested dates ignored:** `Task.requestedDate` was stored (for example
  `usecase/TaskUseCases.kt` line 81) but the engine never read it; `eligibleForScheduling()` was
  just `status.isActive`. A task requested for a later day could be planned into today and an
  overdue task was silently moved to today with no explanation. Fixed by classifying each active
  task with `Task.horizonMembership(horizon)` over the single local day `[dayStart, dayEnd)`:
  `AFTER` → `UnplacedReason.OUTSIDE_HORIZON`, `BEFORE` → carried forward with a stated
  assumption, `BELONGS` → planned.
- **Defect 2 — avoidable deadline miss:** `schedulingComparator()` ordered by priority *before*
  deadline, so a `CRITICAL` task could occupy the only slot a sooner deadline needed and an
  actually feasible combination was reported `DEADLINE_INFEASIBLE`. Fixed by ordering
  earliest-deadline-first, then priority, then id — Jackson's rule (EDF), optimal for independent
  work without release times. Only the order tasks are offered to the placer changed; the
  unchanged placement check still enforces every deadline.
- **Horizon made explicit:** added `ScheduleInput.horizonDate()` (= `dayStart.toLocalDate()`) as
  the single source of truth. The preview (`ui/schedule/ScheduleViewModel`) and recovery
  (`DeterministicRecoveryPlanner`) already pass the same `ScheduleInput`, so preview, apply and
  recovery share one horizon and cannot drift.
- **New outcome + UI:** `UnplacedReason.OUTSIDE_HORIZON` (with a label in
  `ui/schedule/ScheduleScreen.reasonText`). It is deliberately excluded from `overloaded`:
  a task for tomorrow is not a capacity failure. Adding the enum value makes the UI `when`
  exhaustive-checked at compile time.
- **Trade-offs:** EDF maximises deadline protection but can place a lower-priority task ahead of a
  higher-priority one when its deadline is nearer; this is intended because a deadline is hard
  and priority is soft (CH-SCHED-005). The heuristic remains suboptimal in pathological cases with
  fixed commitments and differing release times — it is a declared heuristic, never presented as a
  proven optimum.
- **Alternatives considered:** (1) *Change only the comparator.* Rejected — it would still ignore
  requested dates and the horizon. (2) *Bounded feasibility search / repair.* Rejected as
  disproportionate: EDF already resolves the reported class of avoidable miss on a personal
  planner's day, and a search would add complexity without a new user-visible guarantee.
  (3) *Report future tasks as `NO_AVAILABLE_WINDOW`.* Rejected — it would wrongly make the day
  look overloaded.
- **Affected requirements:** CH-SCHED-001 (pure, explainable), CH-SCHED-003 (deadline never
  silently overrun), CH-SCHED-004 (deterministic order), human-facing "nothing silently dropped".
- **Verification:** `./scripts/verify.sh` passes on Ubuntu — 288 unit/Robolectric tests, 0
  failures; lint 0 errors; `assembleDebugAndroidTest` compiles; debug APK builds. New
  `ScheduleEngineHorizonTest` (7) reproduces both defects and asserts the placed/unplaced
  invariant; new `ScheduleEngineBenchmarkTest` (2) prints engine and recovery latency for
  10/50/100/200-task days (200 tasks ≈ 12 ms engine, ≈ 27 ms recovery after JIT warm-up) and
  asserts no task is dropped, with no fragile timing threshold. Device checks remain NOT RUN
  (OPEN-014).

### Optional cloud task understanding — change record (ADR-037)

- **Date:** 17 October 2026. **Phase:** post-P06 enrichment (owner-requested; recorded as
  ADR-037). **Approval:** owner selected the full implementation with mocked tests, satisfying the
  "data leaving device / provider / cost / hosting" approval gate below.
- **Boundary:** the domain layer is unchanged and still has no network import. The cloud path
  lives in `platform/cloud` behind the existing `TaskUnderstandingProvider` seam (CH-PARSE-001),
  so the scheduling engine, recovery and persistence never depend on it.
- **Authority stays local:** `CloudTaskUnderstandingProvider` returns a remote draft only when it
  is schema-valid (`TaskIntentWire`) *and* passes `TaskIntentDraftValidator`; otherwise it calls
  the `DeterministicTaskIntentParser`. A remote `Unsupported` is treated the same way, because the
  deterministic parser, not the model, decides whether something is a task.
- **Key handling:** the app sends only the Supabase anon key (a public JWT) as `authorization` and
  `apikey`. Provider keys (`GROQ_API_KEY`, `GEMINI_API_KEY`, `OPENROUTER_API_KEY`) exist only as
  Edge Function secrets; they are never in Kotlin, Gradle, resources, the APK or a log. The
  function requires `verify_jwt = true`.
- **Failover order / cost:** Groq → Gemini → OpenRouter, using whichever provider keys are
  configured; per-provider retry with bounded backoff and `Retry-After` honouring; a request that
  exhausts providers returns 502/503 and the app falls back. Models are overridable per provider.
- **Data minimisation:** only the phrase (≤500 chars, enforced both sides), the reference time and
  the zone leave the device; the structured result stores no raw model output (contract rule
  already enforced for the deterministic parser).
- **Alternatives considered:** (1) *Call providers from the app with keys in BuildConfig.*
  Rejected — an APK key cannot be protected and would leak on any extraction. (2) *A new HTTP
  library (OkHttp/Ktor).* Rejected — `HttpURLConnection` already exists and the need is a single
  POST; no dependency added. (3) *Make cloud the primary parser.* Rejected — it would put a
  network call and a third party in the capture path and contradict the local-first requirement.
- **Affected requirements:** CH-PARSE-001 (provider-neutral seam, now with a second
  implementation), PRIV-001 (local-first: preserved because the path is off by default), and the
  owner-facing "no account/backend" claim (README updated to describe the single opt-in network
  path).
- **Verification:** `gateway.test.ts` 24 pass (`node --test`); `TaskIntentWireTest` +
  `CloudTaskUnderstandingProviderTest` 20 pass; full `./scripts/verify.sh` PASS (309 unit tests).
  On 2026-10-17 the provider was exercised **live against a local Deno Edge Function** with a real
  Gemini key using the app's own client: 2 of 3 phrases produced real drafts (the third fell back on
  a transient provider error, and the provider also returned transient `429`/timeouts). A **hosted
  Supabase deploy is still NOT RUN** (no Supabase CLI/project/credentials) — see OPEN-018. The
  hosted path must not be represented as live-working until a real deploy is exercised.

## Approval-required change categories
Ask owner before material changes to data leaving device, cost, provider, storage/sync/account architecture, raw audio/transcript retention, task/relation/scheduler semantics, fixed commitment protection, mandatory overlay behavior, backup/deletion semantics, major dependency/hosting, or human study recruitment.

## Change record expectations
Document ID/date, evidence, previous/new choice, alternatives, affected requirement IDs/files/contracts/tests, migration/compatibility consequences, privacy/security/cost implications, owner approval and verification. See `CHANGE_CONTROL.md`.
