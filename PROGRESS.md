# Chrona — Progress and Resume Record

**Status:** P01 complete. P02 in progress.
**Last updated:** 15 October 2026

## Status vocabulary

- **Planned** — specified, not implemented.
- **Implemented** — code exists in the working tree.
- **Tested** — a named test/command has actually run.
- **Verified** — acceptance criteria were checked and evidence recorded.
- **Blocked** — cannot safely proceed; explain why and next action.
- **Deferred** — explicitly out of this phase/MVP, with rationale.

---

## P01 — Repository Foundation and Cross-OS Build Gate

**Phase gate: PARTIALLY PASSED.** The reproducible skeleton builds, tests and lints clean on
Ubuntu. The cross-OS half of the gate and the emulator launch are BLOCKED with recorded
reasons, exactly as `PHASE_01_FOUNDATION.md` requires ("Do not mark cross-OS gate passed if
only one host is checked"). P02 is safe to proceed because nothing in it depends on the
Windows host or the emulator.

### Environment actually used (Ubuntu host)

| Item | Value |
|---|---|
| OS | Ubuntu 24.04.5 LTS, kernel 6.14.0-37-generic, x86_64 |
| JDK | Eclipse Temurin 17.0.20.1+1 (installed to `~/local/jdk`, no sudo available) |
| Android cmdline-tools | 15859902 |
| Android SDK Platform | android-36 (platform-36_r02) |
| Build-Tools | 36.0.0 |
| platform-tools / adb | 37.0.1 |
| Gradle wrapper | 8.13 (Launcher JVM 17.0.20.1) |
| AGP / Kotlin / KSP | 8.13.2 / 2.2.21 / 2.2.21-2.0.5 |
| Gradle cache | `~/.gradle` (local, not committed) |
| Git | 2.43.0 |

### Files added

- Build: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`,
  `gradle/libs.versions.toml`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat`,
  `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/lint.xml`
- Hygiene: `.gitignore`, `.gitattributes`, `.editorconfig`
- Scripts: `scripts/verify.sh`, `scripts/verify.ps1`
- App: `app/src/main/AndroidManifest.xml`, `res/**`,
  `ChronaApplication.kt`, `MainActivity.kt`,
  `navigation/ChronaDestination.kt`, `navigation/ChronaNavHost.kt`,
  `ui/design/{Color,Type,Theme,Components}.kt`,
  `ui/{today,schedule,board,history,settings}/*Screen.kt`
- Tests: `app/src/test/kotlin/com/chrona/app/navigation/ChronaDestinationTest.kt`,
  `app/src/androidTest/kotlin/com/chrona/app/MainActivityLaunchTest.kt`,
  `app/src/androidTest/kotlin/com/chrona/app/navigation/NavigationShellTest.kt`

Files modified by this phase: `README.md`, `PROGRESS.md`, `CHANGELOG.md`, `DECISIONS.md`,
`CROSS_OS_SETUP_AND_TOOLCHAIN.md`, `SHARED_SYMBOL_REGISTRY.md`,
`REQUIREMENT_TRACEABILITY.md`, `README_FIRST.md`, `PROJECT_DIRECTORY_TREE.md`,
`pack-metadata.json`, `implementation-guides/EXECUTION_CHECKLIST.md`.

No pack source file was deleted. The pack was moved from `Chrona_Development_Pack/` to the
repository root, as `README_FIRST.md` §"How to begin implementation" step 1 requires.

### Commands actually executed and their results

All commands were run from the repository root with
`JAVA_HOME=<temurin-17> ANDROID_HOME=<sdk>` and an untracked `local.properties`.

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew --version` | PASS — Gradle 8.13, Launcher JVM 17.0.20.1, OS Linux 6.14.0-37 |
| 2 | `./gradlew --no-daemon :app:assembleDebug` (with the originally proposed pins) | **FAIL** — `checkDebugAarMetadata`: 6 AAR metadata errors; see toolchain finding below |
| 3 | `./gradlew --no-daemon :app:assembleDebug` (with revised pins) | PASS |
| 4 | `./gradlew --no-daemon :app:lintDebug` | FAIL on 1 lint error, then PASS |
| 5 | `./scripts/verify.sh` | PASS — `clean assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest` |

Verification script output summary:

```
==> java -version
==> ./gradlew --version
==> clean assembleDebug testDebugUnitTest lintDebug
==> SKIPPED connectedDebugAndroidTest (pass --with-instrumentation to run it)
==> assembleDebugAndroidTest
BUILD SUCCESSFUL
OK: Chrona verification passed
```

### Tests added and executed

| Test | Layer | Result |
|---|---|---|
| `ChronaDestinationTest` — 5 destinations exist, routes unique, labels resolve, Today is start destination | JVM unit | **PASS** — 4 tests, 0 failures, 0 errors, 0 skipped |
| `MainActivityLaunchTest` — app launches on Today, 5 bottom-bar destinations | Compose instrumentation | **NOT RUN** (no emulator/device; APK compiles) |
| `NavigationShellTest` — every destination opens, empty states render | Compose instrumentation | **NOT RUN** (no emulator/device; APK compiles) |

`./gradlew assembleDebugAndroidTest` compiles both instrumentation classes successfully, so
the suite is real and will run as soon as a device is attached. Reporting them as passed
would be false.

### Build / lint / static analysis status

- Debug assembly: PASS, APK produced at `app/build/outputs/apk/debug/app-debug.apk`.
- Lint (`lintDebug`): PASS — 0 errors, 0 warnings after configuration.
  Accessibility and localisation checks are promoted to `error` in `app/lint.xml` because
  ACC-001 is a confirmed MUST.
- Kotlin compile: PASS — no errors, no warnings in the final run.
- Dependency-outdated notices: configured `informational` on purpose; Chrona pins an
  API-36 matrix and moving past it needs an owner-approved decision, not a silent bump.
- No static analysis tool beyond Android Lint is configured. Not run: detekt/ktlint, because
  adding a second style linter now would duplicate Lint and produce conflicting reports.

### Toolchain finding — the proposed matrix was incompatible

`PHASE_01_FOUNDATION.md` step 1 requires checking exact compatibility. The proposed pins
build, but **Navigation Compose 2.10.2 does not**.

Failure from command #2 (verbatim essentials):

```
Dependency 'androidx.navigation:navigation-compose-android:2.10.2' requires libraries and
applications that depend on it to compile against version 37 or later of the Android APIs.
:app is currently compiled against android-36.
Dependency 'androidx.navigation:navigation-compose-android:2.10.2' requires Android Gradle
plugin 9.1.0 or higher.
Dependency 'androidx.lifecycle:lifecycle-viewmodel-compose-android:2.11.0' requires ...
version 37 or later ...
Dependency 'androidx.lifecycle:lifecycle-runtime-compose-android:2.11.0' requires ...
```

Verified directly from the published AAR metadata on `dl.google.com`:

| Artifact | Version | `minCompileSdk` |
|---|---|---|
| `navigation-compose-android` | 2.9.7 | 35 |
| `navigation-compose-android` | 2.9.8 | **35** |
| `navigation-compose-android` | 2.10.0 / 2.10.1 / 2.10.2 | 37 |
| `lifecycle-viewmodel-compose-android` | 2.10.0 | **35** |
| `lifecycle-runtime-compose-android` | 2.10.0 | **35** |
| `lifecycle-viewmodel-compose-android` | 2.11.0 | 37 |
| `lifecycle-runtime-compose-android` | 2.11.0 | 37 |

**Decision (recorded as ADR-018a):** keep `compileSdk 36` and AGP 8.13.2, and pin
Navigation Compose **2.9.8** and Lifecycle **2.10.0** — the newest releases that stay on the
stable API-36 path. Navigation 2.9.8 itself depends on Lifecycle 2.9.0, so the explicit
2.10.0 pin is a clean upgrade with no conflict. Adopting the newer AndroidX line instead would
force compileSdk 37 / AGP 9.x, which `CROSS_OS_SETUP_AND_TOOLCHAIN.md` explicitly discourages
("Never use a preview SDK purely to be newest"). Chrona is a two-credit demonstrator;
API-36 stability is worth more than the newest navigation library.

Everything else in the proposed matrix resolved and built unchanged: AGP 8.13.2, Gradle 8.13,
Kotlin 2.2.21, KSP 2.2.21-2.0.5, Compose BOM 2026.06.00, Room 2.8.5, DataStore 1.2.1,
WorkManager 2.12.0, Activity Compose 1.13.0, minSdk 26.

### Other P01 observations

- JDK 17 is verified here. The build host also has JDK 21; AGP 8.13 supports both, but only
  JDK 17 is recorded as the pin because that is what was actually built with.
- AGP 8.13.2 emits: `SDK processing ... understands SDK XML versions up to 3 but an SDK XML
  file of version 4 was encountered`. Harmless — it comes from the newer cmdline-tools. Not an
  error and it does not affect the build.
- The Gradle wrapper JAR checksum matches the checksum published by
  `services.gradle.org` for Gradle 8.13 (`81a82aae…ae45f`), and
  `distributionSha256Sum` is pinned in `gradle-wrapper.properties`.
- `allowBackup="false"` plus a `data_extraction_rules.xml` that excludes every domain: task
  data is not silently copied to cloud backup or device transfer.

### Requirements addressed in P01

| ID | Addressed | Evidence |
|---|---|---|
| PRJ-001 | Implemented (shell only; not Verified on a device) | `assembleDebug` PASS |
| UI-001 | Implemented (five destinations, one route registry) | `ChronaDestinationTest` PASS |
| UI-002 | Implemented (palette + pinned-note primitives) | Design review; full board is P05 |
| ACC-001 | Partially (lint accessibility checks escalated to error) | `lintDebug` PASS |
| PRIV-002 | Verified for P01 (no secrets; none exist yet) | Source and manifest review |
| PLAT-002 | **BLOCKED** — Ubuntu only | See below |
| PRIV-001 | Partially (backup/transfer opted out) | `data_extraction_rules.xml` |

Deferred to later phases: PRJ-001 and UI-001 are not `PASS` because no device or emulator has
run the app yet.

### Known limitations, blockers and risks

1. **Windows host check — BLOCKED.** No Windows machine is available in this environment, so
   `AT-033` and the Windows half of the P01 gate cannot be run. `scripts/verify.ps1` is
   written and reviewed but **unexecuted**. Before P08 this must be run on the Windows PC from
   a fresh clone and recorded here.
2. **Emulator launch — BLOCKED.** `/dev/kvm` exists but is `root:kvm` mode 0660 and the build
   user is not in the `kvm` group; `cat /dev/kvm` fails. Hardware-accelerated emulation is
   unavailable and `-accel off` for an x86_64 API 36 image is not viable. Needed action: add
   the build user to the `kvm` group, or run instrumentation on the moto g54 or a
   Studio-managed emulator.
3. **Physical device — NOT RUN.** moto g54 / Android 15 evidence starts in P03.
4. **Release build is unminified** (`isMinifyEnabled = false`). This is a deliberate,
   reversible simplification, recorded in `RISKS_AND_OPEN_ITEMS.md`.
5. **Provisional application identity.** `com.chrona.app` / "Chrona" were chosen because no
   approved reverse-DNS ID exists. Renaming the product remains an owner decision; trademark
   availability is unchecked.
6. **No licence file.** Must be added before any public distribution.
7. **URE institutional items remain `OPEN — CONFIRM`** (deadline, report format, demo rubric,
   ethics/consent). No participant recruitment is permitted until they are resolved.

### Exact next phase

**P02 — Local Data and First End-to-End Task Slice.** Prerequisite P01 gate: satisfied for
Ubuntu-local work; the Windows and emulator sub-checks stay BLOCKED and must be cleared before
P08.

---

## Phase status summary

- [x] Source requirements PDF included unchanged.
- [x] Universal Software Development Master Prompt V2 included unchanged.
- [x] Architecture pack, contracts, symbol registry, traceability, test plan, risk register.
- [x] Sequential OpenCode phase prompts prepared.
- [x] Git repository created; development pack placed at the repository root.
- [x] P01 — skeleton, pinned toolchain, navigation shell, theme, empty states (Ubuntu).
- [ ] P01 Windows clean-clone build/test/lint — BLOCKED (no Windows host available).
- [ ] P01 API 36 emulator launch — BLOCKED (no `/dev/kvm` access for the build user).
- [ ] P02 — Room schema, repositories, task lifecycle, Today list, task editor.
- [ ] P03 — voice adapter and deterministic parser.
- [ ] P04 — scheduler and recovery.
- [ ] P05 — board and relationships.
- [ ] P06 — Quest Cards and reminders.
- [ ] P07 — privacy, resilience, accessibility.
- [ ] P08 — URE evaluation and final release.

## Resume instruction

Inspect the actual repository and read `README_FIRST.md`, `MASTER_REQUIREMENTS.md`,
`MASTER_ARCHITECTURE.md`, `DECISIONS.md`, `SHARED_CONTRACT.md`,
`SHARED_SYMBOL_REGISTRY.md`, `MODULE_DEPENDENCY_MAP.md`, `IMPLEMENTATION_PHASE_PLAN.md`,
this file, and the current phase prompt. Do not assume the repository state. Update this
record only with commands actually run and evidence observed.