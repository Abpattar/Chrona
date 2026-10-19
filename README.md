# Chrona

**Chrona** is a local-first Android planner. It turns everyday task requests into structured
tasks, builds a feasible day plan, repairs that plan when the day actually changes, shows
task/event relationships as pinned paper notes joined by red threads, and offers brief
respectful task guidance instead of repeated reminder noise.

This repository is the implementation of the approved product in
[`MASTER_REQUIREMENTS.md`](MASTER_REQUIREMENTS.md), following the canonical contracts in
[`SHARED_CONTRACT.md`](SHARED_CONTRACT.md).

## Current status — P01–P06 complete, P07 next

| Phase | Scope | Status |
|---|---|---|
| P01 | Repository foundation, pinned toolchain, navigation shell | **DONE** — builds, tests and lints clean on Ubuntu |
| P02 | Local data + first end-to-end task slice | **DONE** — typed task → validate → Room → Today → complete |
| P03 | Voice input and deterministic parser | **DONE (automated)** — tap-to-speak, on-device-only recogniser, parser, review/correction; device availability NOT RUN |
| P04 | Scheduling and recovery engine | **DONE (automated)** — deterministic day preview, apply/undo, schema v2 + migration; device run NOT RUN |
| P05 | Board and relationships | **DONE (automated)** — pinned-note board, typed red threads, filters, list alternative, link editing; Compose run NOT RUN |
| P06 | Quest Cards and notifications | **DONE (automated)** — deterministic reminder policy, Room v3 history, DataStore settings, four Quest Card actions, notification + optional overlay + in-app surfaces, hourly reconciliation; notification/overlay/worker device checks NOT RUN |
| Cloud interpreter | Optional hosted task understanding | **DONE (automated, mocked)** — Supabase Edge Function with Groq → Gemini → OpenRouter failover; app client (off by default) falls back to the deterministic parser on any failure; live provider exercised locally with a real key via the app's own client; hosted Supabase deploy NOT RUN |
| Simulation | Multi-day planning and a reproducible 14-day life | **DONE** — `DeterministicMultiDayPlanner` over the existing engine; 142 task instances, 52 definitions, 58 distinct titles; load/reset/advance/run controls; Room v4 with reset isolated from user data; 40 scenario checks green; report generated from a real run |
| P07 | Privacy, resilience, accessibility | Not started |
| P08 | URE evaluation and final release | Not started |

**What works today (verified by automated tests):**

- The app builds, lints clean, and launches into Today.
- All six top-level destinations — Today, Schedule, Tasks, Board, History, Settings — open and
  show real state rather than placeholders.
- **Planning respects the user's configured hours**, keeps fixed commitments where they are,
  reports what it could not place and why, and applies a plan only when explicitly asked.
- **History is real**: applied schedule revisions and task status changes, read from the same
  tables production writes to.
- **A reproducible 14-day life simulation** with load, reset, advance-a-day and run-to-end, whose
  every number is counted from persisted state (see "Life simulation" below).
- **A real end-to-end slice:** type a task → it is validated in the domain layer → persisted in
  Room/SQLite → shown on Today → started, completed, postponed, skipped or archived → every
  change written to an audit trail. Everything works offline; there is no account and no
  backend, and the only network path is the optional, off-by-default cloud interpreter below.
- Manual fixed commitments can be stored and are queried by overlap.
- Task relationships enforce direction, reject self-links and detect cycles.
- Typed task creation validates inline and refuses to save an empty or malformed task.
- **Tap-to-speak, on-device only:** tap the microphone, speak a bounded English phrase (for
  example "study for GATE for two hours tomorrow"), review and correct the parse, answer one
  focused clarification when the parser is unsure, then save. The recogniser is the on-device
  one; if the device has none, or the microphone permission is denied, Chrona offers typing
  instead of switching to a possibly remote recogniser. No raw audio is stored.
- **Optional cloud interpreter, off by default:** when a build supplies a function URL, capture
  first tries Chrona's own Supabase Edge Function, which owns the provider keys and may use a
  hosted model to read a messy phrase. Any failure — offline, timeout, bad status, malformed or
  invalid draft — silently falls back to the deterministic parser, which stays the authority on
  whether something is a task. The app never holds a provider key and never calls an AI provider
  directly, and no task text leaves the device unless the owner configures it.
- **A deterministic day plan:** the Schedule tab builds a preview from your tasks and fixed
  commitments — what is planned, what moved, and what could not be placed (with a reason).
  Fixed commitments and FIXED tasks are never moved; buffers, dependencies, earliest windows and
  deadlines are honoured; a missing duration assumes 30 minutes and is called out as an
  assumption. You can **Apply** the plan (written atomically as a revision) and **Undo** it; a
  preview alone never writes anything. The engine is pure domain code — no AI, no network.
- **A paper board of your tasks:** the Board tab lays your tasks out as pinned notes joined by
  thin red threads. You can filter by status, priority, project, text search or a due date, and
  switch to a plain **list** showing the same records. Selecting a note lets you add, change,
  confirm or remove a typed relationship. A confirmed `DEPENDS_ON` constrains order and is drawn
  boldly; a suggested dependency stays dormant until you confirm it; a cycle, self-link or
  duplicate link is refused with a plain reason. Nothing is drawn between tasks that are hidden
  by a filter.

**Not working yet:** the Privacy Centre. It belongs to P07.

**Nothing is complete that is not listed as complete.** [`PROGRESS.md`](PROGRESS.md) is the
authoritative record of what was actually run and what was actually verified.
[`CHANGELOG.md`](CHANGELOG.md) records every change. In particular, the Compose instrumentation
suite compiles but **has not been run** — see [Known limitations](#known-limitations-and-blocked-checks).

## Privacy and scope boundaries

- No account, no backend, no cloud sync. The core planner works offline and never touches the
  network.
- Remote AI is an opt-in build configuration, off by default. When enabled the app calls Chrona's
  own Supabase Edge Function (which owns the provider keys); a hosted model only reads a phrase
  to propose a task draft, and the deterministic parser remains the authority. Any failure falls
  back to it silently. The app never holds a provider key: no secret, API key or paid service is
  in source or the APK.
- Task text leaves the device only when the owner turns the cloud interpreter on.
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

### Physical device (Android Debug Bridge)

Any attached device or emulator works; `ANDROID_SERIAL` picks one when several are connected.

```bash
adb devices -l                      # confirm the device is visible and authorised
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Whole suite on the device
ANDROID_SERIAL=<serial> ./gradlew connectedDebugAndroidTest

# One class, or one method, while iterating
ANDROID_SERIAL=<serial> ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.chrona.app.SimulationFlowTest

# Launch and look at it
adb shell am start -n com.chrona.app/.MainActivity
adb exec-out screencap -p > screen.png

adb shell pm clear com.chrona.app   # wipes the local database; ask the user first
```

The instrumentation suite writes its results to
`app/build/reports/androidTests/connected/debug/index.html` and its XML to
`app/build/outputs/androidTest-results/connected/`.

Last verified on a **moto g54 5G, Android 15 (API 35)**: 30 tests, 0 failures.

### Raw Gradle commands

```bash
./gradlew --version
./gradlew --no-daemon clean assembleDebug testDebugUnitTest lintDebug
./gradlew --no-daemon assembleDebugAndroidTest   # compiles the instrumentation suite
./gradlew --no-daemon connectedDebugAndroidTest   # requires a running emulator/device
```

Always use the committed wrapper. Never rely on a globally installed Gradle.

## Optional cloud interpreter

Off by default. A stock build contains no URL, no key and no network path; the deterministic
parser handles everything. To enable it:

1. Deploy [`supabase/functions/understand-task`](supabase/functions/understand-task) with the
   Supabase CLI (`supabase functions deploy understand-task`), setting at least one provider key
   as a function secret — `GROQ_API_KEY`, `GEMINI_API_KEY` or `OPENROUTER_API_KEY` (plus the
   optional `*_MODEL` overrides). See `supabase/.env.local.example` for the full list.
2. Build the app with the function URL and the **anon** key:

```bash
JAVA_HOME=/path/to/jdk-17 ./gradlew assembleDebug \
  -Pchrona.cloudEnabled=true \
  -Pchrona.cloudFunctionUrl=https://<project>.supabase.co/functions/v1/understand-task \
  -Pchrona.cloudAnonKey=<supabase-anon-key>
```

The app calls only that function; it never holds a provider key and never calls an AI provider
directly. Only the phrase, the reference time and the time zone are sent. Any failure — offline,
timeout, non-2xx status, malformed or rule-breaking draft — silently falls back to the
deterministic parser, which remains the authority on whether something is a task. The gateway's
core logic and the app's wire mapper are covered by automated tests.

For local development the function can be run with Deno
(`deno run --allow-net --allow-env --env-file=supabase/.env.local
supabase/functions/understand-task/index.ts`) and reached from a debug build over HTTP — debug
builds permit cleartext **only** for `localhost`/`127.0.0.1`/`10.0.2.2`
(`app/src/debug/res/xml/network_security_config.xml`). On 2026-10-17 the app's own client parsed
real drafts from such a local function with a live Gemini key. A **hosted** Supabase deploy has
**NOT RUN** (no credentials or project in this environment) — see `RISKS_AND_OPEN_ITEMS.md` and
`PROGRESS.md`.

## Life simulation

Chrona ships a reproducible 14-day simulated life so the planner can be exercised against an
evolving world rather than a single day. It is built from **52 task definitions expanding to 142
task instances** (58 distinct titles) across college, flute practice, daily routines, family life
and personal work, with deadlines, dependencies, fixed commitments, mid-run arrivals, missed
sessions, unexpected interruptions, reduced-availability days and deliberately overloaded days.

The rules that matter:

- The clock is **injected**. Nothing reads the device time, so the same inputs always produce the
  same plan. Replay is exact.
- Simulation tasks are **ordinary task rows** marked `TaskOrigin.SIMULATION`. They obey the same
  validation, persistence and history rules as real work, which is what makes them worth testing.
  They are excluded from Today, the schedule and reminders by default; the **Tasks** browser shows
  them and marks every row as simulated.
- **Reset only ever deletes simulation records**, filtered by the run id and the reserved label.
  User tasks are untouched.
- Nothing is silently dropped. After every day the task pool is reconciled against the plan: each
  open task is either placed or reported with a reason, and a step that cannot satisfy that fails
  loudly.

Use it in the app: **Today → Simulation**. Load the scenario, then Advance day (or Run to end), and
watch the counts, the unplaceable work and the change history update. The screen states the
processing mode it actually used, and it deliberately reports **no** host test run until one is
imported — it cannot execute Gradle instrumentation on the phone, and does not pretend otherwise.

Regenerate the report (runs every scenario for real and writes the results):

```bash
./gradlew testDebugUnitTest --tests '*SimulationReportGenerator*'
# -> docs/SIMULATION_REPORT.md and docs/SIMULATION_REPORT.json
```

See [`docs/SIMULATION_REPORT.md`](docs/SIMULATION_REPORT.md) for the latest generated results and
[`DECISIONS.md`](DECISIONS.md) ADR-040 for the design and the alternatives rejected.

## Repository layout

```text
.
├── app/                          Single application module
│   └── src/main/kotlin/com/chrona/app/
│       ├── ChronaApplication.kt  Entry point
│       ├── MainActivity.kt       Single-activity Compose host
│       ├── navigation/           Route registry + navigation shell
│       ├── domain/               Pure Kotlin: models, task rules, relations, parser, scheduling, recovery
│       ├── data/                 Room database (v2), DAOs, mappers, repositories, migrations
│       ├── usecase/              Orchestration layer (incl. task-intent create, apply/undo recovery)
│       ├── platform/             Speech, notifications, overlay, work, and the optional cloud client
│       ├── privacy/              Data inventory, export, delete (P07+)
│       └── ui/                   Compose design system and screens (incl. ui/voice, ui/schedule)
├── gradle/libs.versions.toml     The single source of dependency versions
├── scripts/verify.sh|.ps1        Equivalent verification entry points per OS
├── supabase/functions/understand-task/  Optional Edge Function (Deno) for cloud task understanding
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

## Known limitations and blocked checks

- **Windows host not verified.** The cross-OS gate needs a clean clone build on the Windows
  machine. `scripts/verify.ps1` is written but has never been executed. Marked BLOCKED, not
  passed.
- **Instrumentation runs on a real device (2026-10-19).** `./gradlew :app:connectedDebugAndroidTest`
  passes **30 tests, 0 failures** on the attached `moto g54 5G` (Android 15, API 35). Two device
  runs have now happened (2026-10-18 and 2026-10-19); each found real defects that host tests
  could not — a header that collapsed to one word per line on a 393dp screen, two screens that
  never loaded because they had no initial refresh, and a database key that silently deleted every
  previous simulated day. See `CHANGELOG.md` and `PROGRESS.md`. An API 36 AVD also boots on this
  host through a filesystem ACL on `/dev/kvm`, but the phone is the primary target.
- **Physical device (moto g54, Android 15) — partially checked.** Speech capability is measured:
  the on-device recogniser is present and the app's adapter reports `Available`, but this handset
  has no `en-US` on-device language pack, so a real session ends in `ERROR_LANGUAGE_UNAVAILABLE`
  and the app offers typing (OPEN-007). A successful voice capture end to end is still NOT
  measured. Notification posting, Quest Card actions and the overlay are still NOT RUN
  (OPEN-011), and large-font/TalkBack passes are still by construction only.
- **Schema migration** — v1→v2 and v2→v3 are tested against real SQLite databases, and v3→v4
  (`MigrationTest3To4Test`) likewise: it seeds a v3 database with real task, reminder and revision
  rows and asserts every one survives the upgrade. All four schema JSONs are exported. Future
  versions must extend this test.
- **Live AI provider NOT re-tested (2026-10-19).** No Groq or Gemini credentials are configured in
  this environment, so the cloud path was **not** exercised against a live provider during this
  work. The gateway tests, the wire-mapper tests and the deterministic-fallback tests all pass,
  and the app runs fully offline; what remains unverified is the hosted Supabase deploy and a live
  provider round trip (OPEN-018).
- **Offline is true by construction, not measured.** The app declares `INTERNET` only because the
  optional cloud interpreter can be enabled at build time; with it off (the default) no code path
  opens a socket. No networking dependency exists. The airplane-mode acceptance test has not been
  executed on a device.
- Release build is unminified until a shrink/obfuscation audit exists.
- No licence file has been chosen yet.

## License

Not specified by the owner yet. Add one before any public release.

## Provisional name

"Chrona" is a provisional product name. Trademark and domain availability have not been
checked.