# Chrona

**Chrona** is a local-first Android planner. It turns everyday task requests into structured
tasks, builds a feasible day plan, repairs that plan when the day actually changes, shows
task/event relationships as pinned paper notes joined by red threads, and offers brief
respectful task guidance instead of repeated reminder noise.

This repository is the implementation of the approved product in
[`MASTER_REQUIREMENTS.md`](MASTER_REQUIREMENTS.md), following the canonical contracts in
[`SHARED_CONTRACT.md`](SHARED_CONTRACT.md).

## Current status — P01, P02 and P03 complete, P04 next

| Phase | Scope | Status |
|---|---|---|
| P01 | Repository foundation, pinned toolchain, navigation shell | **DONE** — builds, tests and lints clean on Ubuntu |
| P02 | Local data + first end-to-end task slice | **DONE** — typed task → validate → Room → Today → complete |
| P03 | Voice input and deterministic parser | **DONE (automated)** — tap-to-speak, on-device-only recogniser, parser, review/correction; device availability NOT RUN |
| P04 | Scheduling and recovery engine | Not started |
| P05 | Board and relationships | Not started |
| P06 | Quest Cards and notifications | Not started |
| P07 | Privacy, resilience, accessibility | Not started |
| P08 | URE evaluation and final release | Not started |

**What works today (verified by automated tests):**

- The app builds, lints clean, and launches into Today.
- All five top-level destinations — Today, Schedule, Board, History, Settings — open with
  empty states that explain themselves.
- **A real end-to-end slice:** type a task → it is validated in the domain layer → persisted in
  Room/SQLite → shown on Today → started, completed, postponed, skipped or archived → every
  change written to an audit trail. Everything works offline; there is no account, no backend
  and no network permission.
- Manual fixed commitments can be stored and are queried by overlap.
- Task relationships enforce direction, reject self-links and detect cycles.
- Typed task creation validates inline and refuses to save an empty or malformed task.
- **Tap-to-speak, on-device only:** tap the microphone, speak a bounded English phrase (for
  example "study for GATE for two hours tomorrow"), review and correct the parse, answer one
  focused clarification when the parser is unsure, then save. The recogniser is the on-device
  one; if the device has none, or the microphone permission is denied, Chrona offers typing
  instead of switching to a possibly remote recogniser. No raw audio is stored.

**Not working yet:** the schedule generator, schedule recovery, the visual board, reminders and
notifications, and the Privacy Centre. They belong to P04–P07.

**Nothing is complete that is not listed as complete.** [`PROGRESS.md`](PROGRESS.md) is the
authoritative record of what was actually run and what was actually verified.
[`CHANGELOG.md`](CHANGELOG.md) records every change. In particular, the Compose instrumentation
suite compiles but **has not been run** — see [Known limitations](#known-limitations-and-blocked-checks).

## Privacy and scope boundaries

- No account, no backend, no cloud sync, no remote AI. The core planner works offline.
- No provider secrets, API keys or paid services anywhere in source or the APK.
- On-device speech recognition only where Android reports it as available; typing is always a
  complete fallback and there is no silent switch to a possibly remote recognizer (P03).
- The optional Quest Card overlay is best-effort. Notifications and in-app surfaces remain
  proper alternatives (P06).
- Cloud backup and device transfer are opted out; a deliberate export path replaces them (P07).

## Stack

One Gradle module, Kotlin + Jetpack Compose, Room/SQLite, and a pure Kotlin domain core.
Every version is pinned in one place: [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

| Component | Pinned version |
|---|---|
| JDK | 17 |
| Gradle (wrapper) | 8.13 |
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 2.2.21 |
| KSP | 2.2.21-2.0.5 |
| Compose BOM | 2026.06.00 |
| Room | 2.8.5 |
| DataStore | 1.2.1 |
| WorkManager | 2.12.0 |
| Navigation Compose | 2.9.8 |
| Lifecycle | 2.10.0 |
| Activity Compose | 1.13.0 |
| minSdk / compileSdk / targetSdk | 26 / 36 / 36 |

Navigation Compose 2.10.x and Lifecycle 2.11.0 require `compileSdk 37` and AGP 9.x. Chrona
deliberately stays on the stable API-36 path, so the newest compatible releases are pinned.
See [`DECISIONS.md`](DECISIONS.md) ADR-018a.

## Build and test

### Prerequisites

- JDK 17
- Android SDK with **Platform 36** and **Build-Tools 36.0.0**
- `local.properties` with `sdk.dir=/path/to/Android/Sdk` (untracked, never committed)

See [`CROSS_OS_SETUP_AND_TOOLCHAIN.md`](CROSS_OS_SETUP_AND_TOOLCHAIN.md) for full
Ubuntu and Windows setup.

### Ubuntu / Linux

```bash
./scripts/verify.sh                          # clean + assemble + unit tests + lint
./scripts/verify.sh --with-instrumentation   # also runs connectedDebugAndroidTest
```

### Windows (PowerShell)

```powershell
.\scripts\verify.ps1
.\scripts\verify.ps1 -WithInstrumentation
```

### Raw Gradle commands

```bash
./gradlew --version
./gradlew --no-daemon clean assembleDebug testDebugUnitTest lintDebug
./gradlew --no-daemon assembleDebugAndroidTest   # compiles the instrumentation suite
./gradlew --no-daemon connectedDebugAndroidTest   # requires a running emulator/device
```

Always use the committed wrapper. Never rely on a globally installed Gradle.

## Repository layout

```text
.
├── app/                          Single application module
│   └── src/main/kotlin/com/chrona/app/
│       ├── ChronaApplication.kt  Entry point
│       ├── MainActivity.kt       Single-activity Compose host
│       ├── navigation/           Route registry + navigation shell
│       ├── domain/               Pure Kotlin: models, task rules, relations, parser
│       ├── data/                 Room database, DAOs, mappers, repositories
│       ├── usecase/              Orchestration layer (incl. task-intent create)
│       ├── platform/             Speech, and later notifications/overlay/work
│       ├── privacy/              Data inventory, export, delete (P07+)
│       └── ui/                   Compose design system and screens (incl. ui/voice)
├── gradle/libs.versions.toml     The single source of dependency versions
├── scripts/verify.sh|.ps1        Equivalent verification entry points per OS
├── opencode/                     Master execution prompt + phase prompts P01–P08
└── *.md                          Canonical planning, contract and evidence documents
```

Layering rules are enforced by review, not by build plugins: UI calls use cases, never DAOs;
the domain core imports no Android, Room or network code.

## Key documents

| Document | Purpose |
|---|---|
| [`PROGRESS.md`](PROGRESS.md) | What was actually run, verified, blocked or skipped |
| [`CHANGELOG.md`](CHANGELOG.md) | Chronological change record |
| [`DECISIONS.md`](DECISIONS.md) | Approved decisions and coordinated changes |
| [`SHARED_CONTRACT.md`](SHARED_CONTRACT.md) | Authoritative cross-module behavior |
| [`SHARED_SYMBOL_REGISTRY.md`](SHARED_SYMBOL_REGISTRY.md) | Canonical symbol index and status |
| [`REQUIREMENT_TRACEABILITY.md`](REQUIREMENT_TRACEABILITY.md) | Requirement → test evidence map |
| [`GLOBAL_ACCEPTANCE_TESTS.md`](GLOBAL_ACCEPTANCE_TESTS.md) | Acceptance scenarios AT-001…AT-036 |
| [`opencode/OPENCODE_MASTER_EXECUTION_PROMPT.md`](opencode/OPENCODE_MASTER_EXECUTION_PROMPT.md) | Agent execution rules and phase gate template |

## Known limitations and blocked checks

- **Windows host not verified.** The cross-OS gate needs a clean clone build on the Windows
  machine. `scripts/verify.ps1` is written but has never been executed. Marked BLOCKED, not
  passed.
- **Compose instrumentation has NOT been run.** An API 36 AVD was created and does boot on this
  host, and both APKs install — but the build user is not in the `kvm` group, so only software
  emulation is available; `artd` reports "no usable artifacts" and `am start` is rejected with
  `result code=-92`. The instrumentation suites compile and will run unchanged once a
  `kvm`-capable host or the moto g54 is available.
- **Physical device (moto g54, Android 15) untested** — required before P03/P06/P07 claims.
  In particular, on-device speech availability, the permission flow and observed network
  behaviour are NOT RUN (OPEN-007); voice stays optional and typing is always complete.
- **Schema migration untested**, because there is only one schema version yet. Room schema v1
  is exported to `app/schemas/` so the migration test has a real starting point at version 2.
- **Offline is true by construction, not measured.** No networking permission and no networking
  dependency exist, but the airplane-mode acceptance test has not been executed on a device.
- Release build is unminified until a shrink/obfuscation audit exists.
- No licence file has been chosen yet.

## License

Not specified by the owner yet. Add one before any public release.

## Provisional name

"Chrona" is a provisional product name. Trademark and domain availability have not been
checked.