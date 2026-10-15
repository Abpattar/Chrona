# Change Log

All notable changes to Chrona are recorded here. Entries state what actually happened; no
entry claims verification that did not occur (see `PROGRESS.md` for command-level evidence).

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