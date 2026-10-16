# Change Log

All notable changes to Chrona are recorded here. Entries state what actually happened; no
entry claims verification that did not occur (see `PROGRESS.md` for command-level evidence).

## 2026-10-16 — P03 Voice and structured task intent

### Added
- `domain/parse/TaskUnderstanding.kt` — the parser contract from `SHARED_CONTRACT.md`:
  `TaskUnderstandingProvider`, `TaskUnderstandingRequest`, `TaskIntentDraft` (with a new
  `fixedRange`), `RelationDraft` (always `SUGGESTED`), `ParseAssumption`,
  `ClarificationPrompt`/`ClarificationOption`, `ParseFailureReason` and the `Parsed`/
  `NeedsClarification`/`Unsupported` result family.
- `domain/parse/DeterministicTaskIntentParser.kt` and `ParseGrammar.kt` — a bounded,
  deterministic English grammar for dates, durations, priorities and fixed/flexible clues,
  with no clock, no Android and no I/O.
- `domain/parse/TaskIntentDraftValidator.kt` — validates a draft through the canonical
  `TaskValidator` before it can be persisted.
- `platform/speech/SpeechRecognitionAdapter.kt` and `AndroidSpeechRecognitionAdapter.kt` — a
  provider-neutral speech boundary whose only recogniser is the on-device one, guarded to
  API 31+, destroyed on release, with `EXTRA_PREFER_OFFLINE`. No audio is buffered or stored.
- `ui/voice/VoiceCaptureViewModel.kt` and `VoiceCaptureSheet.kt` — explicit capture state
  machine and screen: tap-to-speak, listening/cancel/retry, transcript review and correction,
  one focused clarification, and the typed fallback always available.
- `usecase/TaskIntentUseCases.kt` — `CreateTaskFromIntentUseCase` persists a reviewed draft
  through the existing `CreateTaskUseCase`.
- Tests: `DeterministicTaskIntentParserTest` (38), `CreateTaskFromIntentUseCaseTest` (8),
  `TaskIntentDraftValidatorTest` (2), `AndroidSpeechRecognitionAdapterTest` (11, Robolectric
  against the real speech classes), `VoiceCaptureViewModelTest` (11).

### Changed
- `AndroidManifest.xml` — adds only `android.permission.RECORD_AUDIO`; there is still no
  `INTERNET` permission and no networking dependency.
- `di/AppContainer.kt`, `ui/today/{TodayRoute,TodayScreen,TodayViewModel}.kt` — wire the
  speech adapter, parser and capture ViewModel, and open the voice sheet or typed editor.
- `ui/taskeditor/TaskEditorSheet.kt` — accepts prefill from a voice draft; `modifier` is now
  the first optional parameter to satisfy Compose lint.
- `SHARED_CONTRACT.md` — `TaskIntentDraft` gains `fixedRange`; clarification moves from a draft
  field to the `NeedsClarification` result (ADR-026).
- Data-disclosure docs (`DATA_MODEL_AND_INTERFACES.md`, `ERROR_HANDLING_AND_SECURITY.md`) and
  the structure/ownership docs updated to the implemented P03 paths and behaviour.

### Fixed during P03
- **API-31 speech calls were unguarded** on minSdk 26. `AndroidSpeechRecognitionAdapter` now
  checks `Build.VERSION.SDK_INT >= S` for `createOnDeviceSpeechRecognizer` and
  `isOnDeviceRecognitionAvailable`, and reports `OnDeviceUnavailable` below API 31 so those
  devices use typing. This was a real crash on API 26–30, not only a lint error.
- Compose `ModifierParameter` warning in `TaskEditorSheet`.

### Verification (P03)
Exact commands and per-test results are in `PROGRESS.md` → "P03". Summary:

| Layer | Cases | Result |
|---|---|---|
| JVM/Robolectric unit tests | 152 (70 new) | PASS, 0 failures |
| `scripts/verify.sh` (clean + assemble + unit tests + lint) | — | PASS |
| Android lint | — | PASS, 0 errors / 0 warnings |
| Compose instrumentation | — | compiles; NOT RUN (no usable runtime) |
| moto g54 on-device voice/availability (VOICE-001/002, OPEN-007) | — | NOT RUN — reported unknown, voice kept optional |

## 2026-10-15 — P02 Local data and first end-to-end task slice

### Added
- `domain/model/` — canonical value types from `SHARED_CONTRACT.md`: `TaskId`, `EventId`,
  `RelationId`, `RevisionId`, `Priority`, `TaskStatus`, `RelationType`, `Flexibility`,
  `TaskOrigin`, plus `Task`, `CalendarEvent`, `TaskRelationship`, `TimeWindow`/`TimeRange`,
  `UserPreferences`, and the `Outcome`/`Failure` families used instead of exceptions.
- `domain/task/TaskValidator` — field-level rules with plain-language reasons, returning all
  problems at once so a correction UI can show them together.
- `domain/task/TaskStatusMachine` — the only place a status may change. There is deliberately
  no transition a card dismissal could map onto.
- `domain/relation/RelationGraph` — canonical `DEPENDS_ON` direction, self-edge rejection,
  cycle detection and a deterministic topological order.
- `data/local/` — Room database v1 (tasks, calendar_events, task_relationships,
  task_status_log) with foreign keys, indices, exported schema at `app/schemas/` and
  **no** destructive-migration fallback.
- `data/local/EntityMappers` — the single boundary between storage rows and domain records.
- `data/repository/` — `TaskRepository`, `EventRepository`, `RelationshipRepository` and their
  Room implementations, all returning typed outcomes.
- `usecase/` — create, list, edit, start, complete, postpone, skip, archive, restore, delete,
  and manual fixed-commitment create/list/delete.
- `di/AppContainer` — manual composition root with injectable clock and ID generator.
- `ui/taskeditor/TaskEditorSheet` — typed creation: title, details, duration, due date,
  priority. Typing is the complete first path; P03 voice will enter here.
- `ui/today/` — Today list reading the same repository as every other surface, with
  start/done/later actions, explicit priority and status text, and a fixed-commitment section.
- `ui/design/PriorityStyle` — yellow/blue/green note accents always paired with a text label.

### Changed
- `MainActivity` now builds the UI from `AppContainer`; `ChronaApplication` owns it lazily.
- `ChronaNavHost` takes the container and hosts a real Today surface.
- `app/build.gradle.kts` exports Room schemas for migration testing.

### Fixed during P02
- **Idempotence.** Completing an already-completed task bumped the version and wrote a second
  History row. Now short-circuited in the use case (the domain rule) and again inside the
  repository transaction (the write boundary P06 handlers reach directly).
- **Domain row mismatch.** Title trimming happened only in the storage mapper, so the in-memory
  record and the stored row disagreed. Trimming now happens at the use-case boundary.
- **Room could not process `TaskRelationshipEntity`** because domain mappers shared the entity
  file. Mapping moved to `data/local/EntityMappers.kt`.
- **Task listing had no total order.** Replaced with an explicit total `ORDER BY` plus a
  repository-side limit.
- **`RelationshipRepository.delete` deleted by source task**, not by relation id. Added a
  dedicated `deleteById` query.

### Verification (P02)
Exact commands and per-test results are in `PROGRESS.md` → "P02". Summary:

| Layer | Cases | Result |
|---|---|---|
| JVM unit (validators, state machine, relations, use cases) | 65 | PASS |
| Room integration (real SQLite via Robolectric) | 17 | PASS |
| `scripts/verify.sh` (clean + assemble + unit tests + lint) | — | PASS |
| Android lint | — | PASS, 0 errors / 0 warnings |
| Compose instrumentation | 9 | NOT RUN — emulator boots but `am start` is rejected |

## 2026-10-15 — P01 Repository foundation and cross-OS build gate

### Added
- Gradle wrapper 8.13 with `distributionSha256Sum` pinned and a wrapper JAR matching the
  checksum published by `services.gradle.org`.
- `gradle/libs.versions.toml` as the single source of every dependency version.
- Root/app Gradle Kotlin DSL build files, `gradle.properties`, `app/lint.xml`,
  `app/proguard-rules.pro`.
- `.gitignore`, `.gitattributes`, `.editorconfig`.
- `scripts/verify.sh` and `scripts/verify.ps1` — equivalent task lists, non-zero exit on
  failure.
- Minimal Android app: manifest, adaptive launcher icon, `data_extraction_rules.xml`,
  `ChronaApplication`, `MainActivity`, `ui/design/{Color,Type,Theme,Components}.kt`.
- `navigation/ChronaDestination.kt` (canonical route registry) and
  `navigation/ChronaNavHost.kt` (five top-level destinations, single-instance back stack).
- `ui/{today,schedule,board,history,settings}` screens with clear empty states.
- JVM unit test `ChronaDestinationTest`; instrumentation tests `MainActivityLaunchTest` and
  `NavigationShellTest`.
- Development pack moved from `Chrona_Development_Pack/` to the repository root, as
  `README_FIRST.md` requires.

### Changed
- `CROSS_OS_SETUP_AND_TOOLCHAIN.md`, `DECISIONS.md`, `SHARED_SYMBOL_REGISTRY.md`,
  `REQUIREMENT_TRACEABILITY.md`, `PROJECT_DIRECTORY_TREE.md`, `README_FIRST.md`,
  `pack-metadata.json`, `implementation-guides/EXECUTION_CHECKLIST.md`, `README.md`,
  `PROGRESS.md` — updated to reflect a real, verified repository.

### Toolchain decision
- **ADR-018a:** Navigation Compose 2.10.2 and Lifecycle 2.11.0 require `compileSdk 37` and
  AGP 9.x and therefore break the pinned API-36 matrix. Pinned Navigation Compose **2.9.8**
  and Lifecycle **2.10.0** instead — the newest releases compatible with compileSdk 36 /
  AGP 8.13.2. See `DECISIONS.md` for the full record and `PROGRESS.md` for the captured
  build failure and AAR metadata evidence.
- ADR-016, ADR-017 and ADR-018 move from `PROPOSED — P01 VERIFY` to `VERIFIED (Ubuntu)` with
  the caveat that the Windows host and emulator checks are still blocked.

### Not done in P01
- Windows clean-clone build/test/lint — BLOCKED, no Windows host available.
- API 36 emulator launch — BLOCKED, `/dev/kvm` is not readable by the build user.
- No application feature beyond the navigation shell. No backend, no remote AI, no secrets.

## 2026-10-15 — Architecture pack created (planning only)

- Incorporated the approved Phase 0 decisions: local-first storage, no required account/cloud,
  no paid external AI requirement, modular task parser, deterministic scheduler, optional
  overlay with notification/in-app fallbacks, manual fixed events, Ubuntu/Windows parity,
  moto g54 Android 15 device testing, and URE unknowns left open.
- Added proposed architecture, canonical interface contracts, symbol registry, data model,
  directory and file responsibility maps, eight implementation phase prompts, testing and
  acceptance guidance, privacy/security risks, research/evaluation plan, and technical
  references.
- No application source code was implemented.
- No project build or test command was run; version compatibility was gated on Phase P01.