# P01 — Repository Foundation and Cross-OS Build Gate

## Objective
Create a clean, runnable native Android skeleton with navigation/theme foundations and a validated pinned toolchain. Do not implement the full product in this phase.

## Prerequisites
Read the full canonical pack and inspect repository/Git state. There is no assumed existing app code. `TECHNICAL_REFERENCES.md` records candidate versions; verify exact compatibility now.

## Scope
- Create/confirm Gradle wrapper, settings, root/module build files, version catalog (single canonical source), `.gitignore`, lint/format configuration and baseline CI if practical.
- One `app` module; Kotlin + Compose; minimum API proposed 26, compile/target API 36 unless verification reveals a material issue requiring approval.
- Minimal app entry point, navigation shell for Today, Schedule, Board, History, Settings, app theme, basic accessible components.
- Include setup documentation for Ubuntu and Windows. No backend, no domain feature implementation, no paid APIs.
- Add an application identity/package namespace as a documented provisional name only if no approved reverse-DNS ID exists; ask before changing the product name, never assume trademark availability.

## Required behavior
A debug app launches to Today/Home and navigation shells open without crash. Empty states are clear and meaningful. The code compiles with no preview SDK or alpha dependency unless explicitly approved. Use no hard-coded machine paths.

## Toolchain gate
1. Verify JDK/Gradle/AGP/Kotlin/KSP/Compose/Room compatibility from official release/compatibility docs.
2. Use matching wrapper/scripts and stable artifact versions.
3. Run clean build on Ubuntu and Windows, or explicitly record which host is unavailable. Do not mark cross-OS gate passed if only one host is checked.
4. Install/use API 36 emulator for target behavior and preserve moto g54 Android 15 as the physical-device matrix.

## Tests/commands
- Wrapper `--version`; `clean`; debug `assemble`; unit test task; lint/static checks available in configured build.
- Fresh clone check on both OSes if both are available.
- Launch app on emulator; basic navigation smoke test.
- Check no secrets have entered code/repository.
- Record exact commands and results; do not report not-run tests as passed.

## Gate criteria
- Reproducible skeleton build succeeds on both Ubuntu and Windows or gate remains blocked with exact host blocker.
- Pinned versions, wrapper and setup commands documented.
- Navigation shell launches, no fatal logs.
- No core feature claimed complete prematurely.

## Update
Create/update `README.md`, `PROGRESS.md`, `CHANGELOG.md`, `DECISIONS.md` if the stack must change. Next: P02.
