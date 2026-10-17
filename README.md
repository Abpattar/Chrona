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
| P07 | Privacy, resilience, accessibility | Not started |
| P08 | URE evaluation and final release | Not started |

**What works today (verified by automated tests):**

- The app builds, lints clean, and launches into Today.
- All five top-level destinations — Today, Schedule, Board, History, Settings — open with
  empty states that explain themselves.
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
- **Compose instrumentation has NOT been run.** An API 36 AVD was created and does boot on this
  host, and both APKs install — but the build user is not in the `kvm` group, so only software
  emulation is available; `artd` reports "no usable artifacts" and `am start` is rejected with
  `result code=-92`. The instrumentation suites compile and will run unchanged once a
  `kvm`-capable host or the moto g54 is available.
- **Physical device (moto g54, Android 15) untested** — required before P03/P04/P06/P07 claims.
  In particular, on-device speech availability, the permission flow and observed network
  behaviour are NOT RUN (OPEN-007); voice stays optional and typing is always complete. The P04
  schedule preview/apply/undo has only been exercised at the JVM/Room layer.
- **Schema migration** — v1→v2 is now tested (`MigrationTest1To2Test`) against a real SQLite v1
  database; both `1.json` and `2.json` are exported. Future versions must extend this test.
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