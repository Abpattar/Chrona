# File Responsibility and Phase Map

`P01–P08` are future implementation phases in `IMPLEMENTATION_PHASE_PLAN.md`; they do not indicate current implementation. Each file has one primary creation phase; later changes must preserve contracts and test dependants.

| Relative path / path family | Primary phase | Responsibility and canonical symbols | Main consumers | Tests / completion evidence |
|---|---:|---|---|---|
| `settings.gradle.kts`, root build files, `gradle.properties`, wrapper, `app/build.gradle.kts` | P01 | SDK/toolchain/plugins/dependency pins and reproducible build | all app/tests/CI | clean wrapper sync/build/test/lint on Ubuntu + Windows |
| `AndroidManifest.xml` | P01; careful extensions P03/P06/P07 | app identity, permissions, exported components, optional overlay declarations | Android adapters | lint/manifest review and permission-denial tests |
| `ChronaApplication.kt`, `MainActivity.kt`, `navigation/` | P01 | entry point, lifecycle, route names and composition root | all UI/use cases | app launch and navigation smoke tests |
| `ui/design/` | P01, later extended | typography, theme, card/button semantics, priority tokens | all screens | accessibility semantics and UI review |
| `domain/model/` | P02 | Task/Event/IDs/status/priority/windows/schedule blocks/preferences | all packages, repositories | pure unit tests, validation, status transitions |
| `domain/task/`, `usecase/` | P02/P05 | task transitions; command boundary; P05 relationship use cases (`AddRelationshipUseCase`, `UpdateRelationshipUseCase`, `DeleteRelationshipUseCase`, `ConfirmRelationshipUseCase`) validating against `RelationGraph` | UI, repository interfaces | lifecycle/idempotency tests; `RelationshipUseCasesTest` (10) |
| `domain/relation/` | P02/P05 | relation enum/model/direction/cycle validation | scheduler, board, editor | direction, cycle and delete-integrity tests |
| `data/local/` | P02; extended P04 | Room DB/entities/DAO/converters; v2 adds `schedule_revisions`/`schedule_placements` | repository implementations | in-memory Room tests; `RoomScheduleRevisionRepositoryTest` |
| `data/migration/` (currently `ChronaDatabase.MIGRATION_1_2`) | P02 and schema updates | versioned explicit migrations | DB startup | `MigrationTest1To2Test` (real SQLite, real migration); no destructive fallback |
| `data/repository/` | P02/P04/P05 | domain-facing repository implementations (`RoomRelationshipRepository` reused unchanged in P05) | use cases | persistence, transaction, version check tests; `RoomRelationshipRepositoryTest` (8) |
| `data/export/` | P07 | versioned export DTO/serializer, delete consistency helpers | Privacy Centre | export shape and deletion residual tests |
| `domain/scheduling/` | P04 | `ScheduleInput`/`ScheduleProposal`/`ScheduleRevision`, slot placement, buffers, dependencies, split rules | planning/recovery | `ScheduleEngineTest` (21: deterministic/scenario) |
| `domain/recovery/` | P04 | triggers, `DeterministicRecoveryPlanner` (same engine), apply/undo use cases, `ScheduleRevisionRepository` contract | schedule/recovery/history UI | `RecoveryUseCasesTest` (7: lateness/overrun/stale/undo) |
| `domain/parse/` | P03 | `TaskUnderstandingProvider`/`TaskIntentDraft`, `ParseGrammar`, `DeterministicTaskIntentParser`, `TaskIntentDraftValidator` | voice UI and create use case | `DeterministicTaskIntentParserTest` (38), `TaskIntentDraftValidatorTest` (2) |
| `platform/speech/` | P03 | on-device-only `SpeechRecognitionAdapter` (API 31+ guard), lifecycle cleanup; no audio persisted | parser, voice UI | `AndroidSpeechRecognitionAdapterTest` (Robolectric); device availability NOT RUN |
| `platform/permission/` | P06/P07 | notification and overlay permission state (mic handled at point of use by `platform/speech`) | notification/overlay | grant/deny/revoke tests |
| `domain/reminder/` | P06 | `ReminderPolicy`, `ReminderDecision`, cap/batching/quiet hours/repeat suppression | notification/overlay adapters | deterministic schedule/cap/history tests |
| `platform/notification/` | P06 | channel, permission-aware notification content/actions/deep links | reminder policy/use cases | channels/denial/action identity tests |
| `platform/overlay/` | P06 | optional overlay window and compact task panel; no domain rule decisions | permission + use cases | moto g54 Android 15 granted/denied/revoked checks |
| `platform/work/` | P04/P06 | unique deferrable work and reminder reconciliation | repository/recovery/reminder | duplicate work, reschedule, process death/Doze test plan |
| `ui/today/`, `ui/schedule/` | P02/P04 | current next action, day preview (planned/moved/unplaced + apply/undo) | use cases | `ScheduleViewModelTest` (preview writes nothing); device rendering NOT RUN |
| `ui/taskeditor/`, `ui/recovery/` | P02/P04 | interpretation correction, editing, proposal/diff/apply/reject/undo | parser/recovery use cases | confirmation and constraint tests |
| `ui/voice/` | P03 | tap-to-speak capture sheet + `VoiceCaptureViewModel`, review/correction, clarification | parser, speech adapter, create use case | `VoiceCaptureViewModelTest` (JVM fake) |
| `ui/board/` | P05 | `RelationPresentation`, `BoardLayout`/`BoardLayoutCalculator` (pure), `BoardViewModel`, `BoardScreen`/`BoardRoute`: paper notes, pins, typed red threads, zoom/pan/filter/project focus/list alternative, link editing | same task/relation repos, relationship use cases | `BoardLayoutTest` (6, incl. 100/200 fixture), `BoardViewModelTest` (12), `RelationPresentationTest` (4); `BoardSliceTest` NOT RUN |
| `ui/questcard/` | P06 | compact task panel and Start/Done/Later/Dismiss actions | platform cards/use cases | dismissed card does not complete task; action idempotency |
| `ui/history/` | P04/P07 | schedule revision/action history and safe undo access | revision repository | ordering, retention and guard tests |
| `ui/settings/`, `ui/privacy/`, `privacy/` | P07 | preferences, permissions/data inventory/export/delete | settings/data/platform | privacy UX, deletion, permission revocation |
| `app/src/test/**` | every phase | pure unit and contract tests | production logic | deterministic fixtures, no live network/speech service |
| `app/src/androidTest/**` | P02 onward | Room/UI/platform integration tests | emulator and physical device | record actual device/API and results |
| `scripts/verify.sh`, `scripts/verify.ps1` | P01, updated each phase | same wrapper tasks, fail-fast scripts | developers/CI | run on each host; preserve exit code |
| `README.md`, `PROGRESS.md`, `CHANGELOG.md`, canonical planning docs | every phase | reproducible setup and honest state/change record | owner/OpenCode | commands/results current and not invented |

## Ownership rule
Shared contracts, Gradle pins, migrations, manifest permissions and navigation IDs are high-impact shared files. Update all affected consumers and tests in one coherent change, then update contracts/registry/map, `CHANGELOG.md`, `PROGRESS.md`, and (for material decisions) `DECISIONS.md`/`CHANGE_CONTROL.md`.

### Phase P06 — Quest Cards and Reminders (IMPLEMENTED)

- `app/src/main/kotlin/com/chrona/app/domain/reminder/Reminder.kt` — core types: ReminderCandidate, ReminderAction, ReminderDecisionReason, ReminderDecision, ReminderSurfaceType, ReminderOutcome, ReminderPresentation, ReminderHistoryEntry, ReminderPolicyConfig, ReminderPolicyInput, ReminderPolicy, TaskAction, ReminderCard, ReminderPresenter — [P06]
- `app/src/main/kotlin/com/chrona/app/domain/reminder/DeterministicReminderPolicy.kt` — pure policy (group-first, cap-per-card) — [P06]
- `app/src/main/kotlin/com/chrona/app/domain/reminder/ReminderCandidateSelector.kt` — due-task selector (lead 10 min default) — [P06]
- `app/src/main/kotlin/com/chrona/app/data/preferences/PreferencesDataStore.kt` — DataStore delegate + DataStorePreferencesRepository — [P06]
- `app/src/main/kotlin/com/chrona/app/usecase/ReminderUseCases.kt` — EvaluateRemindersUseCase, RecordReminderHistoryUseCase, ApplyReminderActionUseCase, DeliverRemindersUseCase (REMINDER_WORK_NAME), ReminderEvaluation, ReminderDelivery, reminderCardId — [P06]
- `app/src/main/kotlin/com/chrona/app/platform/permission/ReminderPermissions.kt` — POST_NOTIFICATIONS constant + permission helpers — [P06]
- `app/src/main/kotlin/com/chrona/app/platform/notification/NotificationPresenter.kt` — ReminderPresenter impl (channels, actions, tag=id, fixed id 4001) — [P06]
- `app/src/main/kotlin/com/chrona/app/platform/notification/ReminderActionReceiver.kt` — BroadcastReceiver for actions (goAsync) — [P06]
- `app/src/main/kotlin/com/chrona/app/platform/overlay/OverlayPresenter.kt` — best-effort overlay (opt-in, permission-gated) — [P06]
- `app/src/main/kotlin/com/chrona/app/platform/work/ReminderReconciliationWorker.kt` — hourly inexact PeriodicWorkRequest (KEEP) — [P06]
- `app/src/main/kotlin/com/chrona/app/ui/questcard/QuestCardViewModel.kt`, `QuestCardScreen.kt`, `QuestCardRoute.kt` — in-app Quest Card — [P06]
- `app/src/main/kotlin/com/chrona/app/ui/settings/SettingsViewModel.kt`, `SettingsScreen.kt` — Settings (SettingsRoute + real screen) — [P06]
- Modified: `app/src/main/kotlin/com/chrona/app/domain/model/UserPreferences.kt` (QuietHours + quietHours computed) — [P06]
- Modified: `app/src/main/kotlin/com/chrona/app/data/local/Entities.kt` (ReminderPresentationEntity, table + indices, priority column), `Daos.kt` (ReminderPresentationDao incl. countNonUrgentSurfacedOn), `ChronaDatabase.kt` (v3, MIGRATION_2_3), `EntityMappers.kt` — [P06]
- Modified: `app/src/main/kotlin/com/chrona/app/data/repository/RepositoryContracts.kt` (ReminderHistoryRepository, PreferencesRepository), `RoomRepositories.kt` (RoomReminderHistoryRepository) — [P06]
- Modified: `app/src/main/kotlin/com/chrona/app/di/AppContainer.kt` (wires prefs/reminder history/policy/use cases/applicationScope/presenters/deliver + MIGRATION_2_3) — [P06]
- Modified: `app/src/main/kotlin/com/chrona/app/navigation/ChronaNavHost.kt` (QUEST_CARD_ROUTE, SettingsRoute, Today entry) — [P06]
- Modified: `app/src/main/kotlin/com/chrona/app/MainActivity.kt` (schedules ReminderReconciliationWorker), `AndroidManifest.xml` (POST_NOTIFICATIONS, SYSTEM_ALERT_WINDOW, ReminderActionReceiver), `app/src/main/res/values/strings.xml` (channels/actions/settings) — [P06]
- Tests: `app/src/test/kotlin/.../domain/reminder/DeterministicReminderPolicyTest.kt` (20), `ReminderCandidateSelectorTest.kt` (10); `app/src/test/kotlin/.../usecase/ReminderUseCasesTest.kt` (5), `RecordReminderHistoryUseCaseTest.kt` (2), `ApplyReminderActionUseCaseTest.kt` (3), `DeliverRemindersUseCaseTest.kt` (4); `app/src/test/kotlin/.../data/RoomReminderHistoryRepositoryTest.kt` (4, Robolectric), `MigrationTest2To3Test.kt` (1) — [P06]
