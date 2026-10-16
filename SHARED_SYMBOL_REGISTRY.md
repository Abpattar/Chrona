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
| CH-DOM-005 | `RelationType` | five typed relation values | P02/P05 | TESTED | `RelationGraphTest` (15 cases); board rendering is P05 |
| CH-NAV-001 | `ChronaDestination` | canonical route registry, `navigation/` | P01 | TESTED | navigation shell; `ChronaDestinationTest` PASS, `NavigationShellTest` NOT RUN (no device) |
| CH-UI-000 | `ChronaTheme` / `PinnedNoteCard` / `EmptyState` / `priorityStyleOf` | `ui/design/`, paper-and-pin design tokens | P01 | IMPLEMENTED | all screens; `lintDebug` PASS; every priority also carries a text label |
| CH-PARSE-001 | `TaskUnderstandingProvider` | `SHARED_CONTRACT.md`, `domain/parse` | P03 | TESTED | text/voice capture; `DeterministicTaskIntentParserTest` (38 cases) + `TaskIntentDraftValidatorTest`; provider-neutral contract |
| CH-PARSE-002 | `TaskIntentDraft` | same | P03 | TESTED | validation/editor; malformed/ambiguous parser fixtures; `CreateTaskFromIntentUseCaseTest` (8 cases) |
| CH-USE-001 | `CreateTaskUseCase` | `SHARED_CONTRACT.md`, usecase | P02 | TESTED | `CreateTaskUseCaseTest` (7 cases) + Room round trip |
| CH-SCH-001 | `ScheduleEngine.propose` | pure domain scheduling | P04 | PLANNED | planner/recovery; deterministic invariants |
| CH-SCH-002 | `ScheduleProposal` | placements/unplaced/conflicts/changes/explanation | P04 | PLANNED | preview/history; no silent drops |
| CH-REC-001 | `ApplyRecoveryUseCase` | atomic application against expected revision | P04 | PLANNED | recovery UI; stale revision and DB failure tests |
| CH-REC-002 | `UndoScheduleRevisionUseCase` | guarded undo or requires-new-recovery | P04 | PLANNED | history; concurrent later-change tests |
| CH-DATA-001 | `TaskRepository` | repository interface | P02 | TESTED | `RoomTaskRepositoryTest` (17 cases, real SQLite) |
| CH-DATA-002 | `ScheduleRevisionRepository` | transaction/apply/undo | P04 | PLANNED | recovery; atomicity tests |
| CH-DATA-003 | `PreferencesRepository` | local settings | P06 | PLANNED | domain model exists; no repository yet, by design (see PROGRESS.md P02) |
| CH-REM-001 | `ReminderPolicy` | pure policy; cap/quiet/batching/repeat logic | P06 | PLANNED | platform presenters; deterministic clock tests |
| CH-REM-002 | `ReminderDecision` | surface/suppress/batch/reason | P06 | PLANNED | overlay/notification; fallback tests |
| CH-PLAT-001 | `SpeechRecognitionAdapter` | explicit lifecycle-aware speech boundary, `platform/speech` | P03 | TESTED | capture UI/parser; `AndroidSpeechRecognitionAdapterTest` (11, Robolectric) + `VoiceCaptureViewModelTest` (11); device availability NOT RUN |
| CH-PLAT-002 | `OverlayPresenter` | optional best-effort system overlay | P06 | PLANNED | permission system; physical device test |
| CH-PLAT-003 | `NotificationPresenter` | ordinary Android notification/action surface | P06 | PLANNED | reminder manager; channel/permission/action tests |
| CH-SEC-001 | `DeleteUserDataUseCase` | clears app-owned local data/work/surfaces | P07 | PLANNED | privacy UI; residual-state tests |
| CH-SEC-002 | `ExportUserDataUseCase` | versioned local export | P07 | PLANNED | privacy UI; schema/integrity tests |
| CH-UI-001 | `TaskAction` | Start/Done/Later/Dismiss, distinct semantics | P06 | PLANNED | overlay/notification/UI; dismissal regression tests |

Rows marked `PLANNED` are specifications only. A file existing does not prove symbol
implementation or test completion. `IMPLEMENTED` means the symbol exists in the working tree;
`TESTED` means a named test actually ran. See `PROGRESS.md` for the command-level evidence.
