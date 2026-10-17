# Chrona Shared Symbol Registry

`PLANNED` means no implementation exists yet. `IMPLEMENTED` means the symbol exists in the
working tree. `TESTED` means a named test has actually run. A registry status changes only
when actual code/test evidence supports it. See `PROGRESS.md` for command-level evidence.

| ID | Canonical symbol | Definition / owning area | Phase | Status | Consumers / verification |
|---|---|---|---|---|---|
| CH-DOM-001 | `TaskId` | `SHARED_CONTRACT.md`, domain/model | P02 | TESTED | stable identity proven in `RoomTaskRepositoryTest` and `TaskUseCaseTest` |
| CH-DOM-002 | `EventId` | same | P02 | TESTED | fixed-commitment round trip and overlap query in `RoomTaskRepositoryTest` |
| CH-DOM-003 | `Priority` | LOW, IMPORTANT, CRITICAL | P02 | IMPLEMENTED | persisted and rendered with a text label (`priorityStyleOf`); board mapping is P05 |
| CH-DOM-004 | `TaskStatus` | seven canonical states | P02 | TESTED | `TaskStatusMachineTest` (11 cases) covers every transition and rejection |
| CH-DOM-005 | `RelationType` | five typed relation values | P02/P05 | TESTED | `RelationGraphTest` (15 cases); typed board rendering + wording in `RelationPresentationTest` (4) |
| CH-REL-001 | `AddRelationshipUseCase` / `ConfirmRelationshipUseCase` | `usecase/RelationshipUseCases.kt`; validate against `RelationGraph` (self-edge, duplicate triple, hard cycle; dormant suggestion) | P05 | TESTED | `RelationshipUseCasesTest` (10) + `RoomRelationshipRepositoryTest` (8, real SQLite) |
| CH-UI-002 | `BoardLayoutCalculator` / `BoardViewModel` | `ui/board/`; pure deterministic layout + board/list state over the shared task rows | P05 | TESTED | `BoardLayoutTest` (6, incl. 100/200 fixture) + `BoardViewModelTest` (12); harness `BoardSliceTest` NOT RUN (no device) |
| CH-NAV-001 | `ChronaDestination` | canonical route registry, `navigation/` | P01 | TESTED | navigation shell; `ChronaDestinationTest` PASS, `NavigationShellTest` NOT RUN (no device) |
| CH-UI-000 | `ChronaTheme` / `PinnedNoteCard` / `EmptyState` / `priorityStyleOf` | `ui/design/`, paper-and-pin design tokens | P01 | IMPLEMENTED | all screens; `lintDebug` PASS; every priority also carries a text label |
| CH-PARSE-001 | `TaskUnderstandingProvider` | `SHARED_CONTRACT.md`, `domain/parse` | P03 | TESTED | text/voice capture; `DeterministicTaskIntentParserTest` (38 cases) + `TaskIntentDraftValidatorTest`; provider-neutral contract |
| CH-PARSE-002 | `TaskIntentDraft` | same | P03 | TESTED | validation/editor; malformed/ambiguous parser fixtures; `CreateTaskFromIntentUseCaseTest` (8 cases) |
| CH-USE-001 | `CreateTaskUseCase` | `SHARED_CONTRACT.md`, usecase | P02 | TESTED | `CreateTaskUseCaseTest` (7 cases) + Room round trip |
| CH-SCH-001 | `ScheduleEngine.propose` | pure domain scheduling | P04 | TESTED | `ScheduleEngineTest` (21 cases); deterministic, no Android/Room/parser imports |
| CH-SCH-002 | `ScheduleProposal` | basis/placements/unplaced/conflicts/changes/assumptions/overloaded/explanation | P04 | TESTED | preview/history; every active task placed or given a reason; `ScheduleViewModelTest` proves preview writes nothing |
| CH-REC-001 | `ApplyRecoveryUseCase` | atomic application against expected revision + task versions | P04 | TESTED | `RecoveryUseCasesTest` (7) + `RoomScheduleRevisionRepositoryTest` (6); stale revision and DB-failure paths |
| CH-REC-002 | `UndoScheduleRevisionUseCase` | guarded undo (newest revision, no later edits) | P04 | TESTED | `RecoveryUseCasesTest` + Room test `undo is refused once a task was edited after the plan` |
| CH-DATA-001 | `TaskRepository` | repository interface | P02 | TESTED | `RoomTaskRepositoryTest` (17 cases, real SQLite) |
| CH-DATA-002 | `ScheduleRevisionRepository` | transaction `current`/`apply`/`undo` | P04 | TESTED | `RoomScheduleRevisionRepositoryTest` (6, real SQLite) + `MigrationTest1To2Test` |
| CH-DATA-003 | `PreferencesRepository` | local settings | P06 | IMPLEMENTED | `PreferencesDataStore` (`DataStorePreferencesRepository`, DataStore name `chrona_settings`); enum-by-name + `HH:mm` encodings, total reads fall back to defaults; Settings surface `SettingsViewModel`; existing policy symbols test the model |
| CH-REM-001 | `ReminderPolicy` | pure policy; cap/quiet/batching/repeat logic | P06 | TESTED | `DeterministicReminderPolicy` (`domain/reminder/DeterministicReminderPolicy.kt`); `DeterministicReminderPolicyTest` (20) covers quiet hours (midnight wrap), batching, per-card non-urgent cap, dismissal/repeat suppression and styles |
| CH-REM-002 | `ReminderDecision` | surface/suppress/batch/reason | P06 | TESTED | decision types in `domain/reminder/Reminder.kt`; `ReminderCandidateSelectorTest` (10), `ReminderUseCasesTest` (5), `DeliverRemindersUseCaseTest` (4) prove selection, surface mapping and fallback |
| CH-PLAT-001 | `SpeechRecognitionAdapter` | explicit lifecycle-aware speech boundary, `platform/speech` | P03 | TESTED | capture UI/parser; `AndroidSpeechRecognitionAdapterTest` (11, Robolectric) + `VoiceCaptureViewModelTest` (11); device availability NOT RUN |
| CH-PLAT-002 | `OverlayPresenter` | optional best-effort system overlay | P06 | IMPLEMENTED | `platform/overlay/OverlayPresenter.kt` (opt-in, `Settings.canDrawOverlays`, `TYPE_APPLICATION_OVERLAY`, returns null on failure); device windowing NOT RUN |
| CH-PLAT-003 | `NotificationPresenter` | ordinary Android notification/action surface | P06 | IMPLEMENTED | `platform/notification/NotificationPresenter.kt` + `ReminderActionReceiver.kt`; channels `chrona_reminders_urgent`/`chrona_reminders_quiet`, tag = card id, fixed id 4001; posting/actions NOT RUN (device) |
| CH-SEC-001 | `DeleteUserDataUseCase` | clears app-owned local data/work/surfaces | P07 | PLANNED | privacy UI; residual-state tests |
| CH-SEC-002 | `ExportUserDataUseCase` | versioned local export | P07 | PLANNED | privacy UI; schema/integrity tests |
| CH-UI-001 | `TaskAction` | Start/Done/Later/Dismiss, distinct semantics | P06 | TESTED | `ApplyReminderActionUseCaseTest` (3) — `DISMISS` writes history only and returns `null`, task stays `INBOX`; START/DONE/LATER transition the task; device buttons NOT RUN |

Rows marked `PLANNED` are specifications only. A file existing does not prove symbol
implementation or test completion. `IMPLEMENTED` means the symbol exists in the working tree;
`TESTED` means a named test actually ran. See `PROGRESS.md` for the command-level evidence.
