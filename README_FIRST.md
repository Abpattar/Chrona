# Chrona Development Pack — Start Here

**Pack version:** 1.1 · **Prepared:** 15 October 2026 · **Status:** Implementation has started.
P01 (foundation) is complete on Ubuntu; P02 complete; P03 complete; P04 complete; P05 complete (all automated checks). Device/voice availability NOT RUN.

## Purpose
This is the architecture and sequential OpenCode execution pack for Chrona, based on the two supplied PDFs and the product decisions confirmed by the owner on 15 October 2026. It targets one OpenCode coding agent, one repository, and sequential phases. The pack is now co-located with the application source it specifies: `app/` holds the real Android code, and this directory of documents is the contract that code must satisfy.

## Read in this order
1. `MASTER_REQUIREMENTS.md` — approved scope, priorities, assumptions and exclusions.
2. `MASTER_ARCHITECTURE.md` — system context, chosen stack, module boundaries, data flows and trade-offs.
3. `DECISIONS.md` — owner-approved choices and proposed technical decisions.
4. `SHARED_CONTRACT.md`, `DATA_MODEL_AND_INTERFACES.md`, `SHARED_SYMBOL_REGISTRY.md` — canonical interfaces, data semantics and shared names.
5. `PROJECT_DIRECTORY_TREE.md`, `FILE_RESPONSIBILITY_AND_PHASE_MAP.md`, `MODULE_DEPENDENCY_MAP.md` — repository structure and ownership.
6. `REQUIREMENT_TRACEABILITY.md`, `GLOBAL_ACCEPTANCE_TESTS.md`, `TESTING_AND_VERIFICATION_GUIDE.md` — how requirements will be verified.
7. `IMPLEMENTATION_PHASE_PLAN.md` — P01–P08 sequence and gates.
8. `opencode/OPENCODE_MASTER_EXECUTION_PROMPT.md`, then phase prompts `PHASE_01`–`PHASE_08` in order.
9. `CROSS_OS_SETUP_AND_TOOLCHAIN.md`, `ERROR_HANDLING_AND_SECURITY.md`, `APP_VISUAL_SPEC.md`, `URE_RESEARCH_AND_EVALUATION_PLAN.md`, `RISKS_AND_OPEN_ITEMS.md`, `TECHNICAL_REFERENCES.md`.

## Confirmed defaults
- Native Android using Kotlin + Jetpack Compose, one Git repository, one coding agent executing sequentially.
- Local-first Room/SQLite storage; no required account, server, cloud sync or paid LLM in MVP.
- Deterministic scheduling/feasibility independent from parser, speech recognition, UI and remote AI.
- Explicit tap-to-speak; use on-device speech recognition only where platform support is confirmed. If unavailable, typed fallback; never silently switch to a potentially remote recognition service.
- Manual fixed commitments; calendar integrations deferred.
- Optional Quest Card overlay; notification and in-app fallback remain usable.
- Quiet reminders; two non-urgent cards/day is a configurable initial research hypothesis, not an immutable global rule.
- Ubuntu and Windows use the same repo, Gradle wrapper, pinned dependencies and equivalent commands.
- Physical device: Motorola moto g54, Android 15/API 35 as owner-reported; emulator plus real-device checks.

## Verified toolchain — P01 result
The proposal below was checked against published artifacts and one coordinate failed. Chrona builds on `compileSdk 36` / AGP 8.13.2 and therefore pins **Navigation Compose 2.9.8** and **Lifecycle 2.10.0**: Navigation 2.10.x and Lifecycle 2.11.0 require `compileSdk 37` and AGP 9.x, which would drag the project onto the preview platform line this project deliberately avoids. Full evidence is in `DECISIONS.md` (ADR-018a) and `PROGRESS.md`. The rest of the matrix — JDK 17, AGP 8.13.2, Gradle 8.13, Kotlin 2.2.21, KSP 2.2.21-2.0.5, Compose BOM 2026.06.00, Room 2.8.5, DataStore 1.2.1, WorkManager 2.12.0, Activity Compose 1.13.0, `minSdk 26`, `compileSdk 36`, `targetSdk 36` — built clean on Ubuntu 24.04.5. The Windows clean-clone check and the API 36 emulator launch are still **BLOCKED**; see `PROGRESS.md`. Re-check Play target policy before release.

## Status and limitations
- Application source exists under `app/`. P01 delivers a navigation shell, theme and empty states. P02 delivers a real end-to-end typed task slice with Room. P03 delivers tap-to-speak (on-device-only), a deterministic parser, review/correction and the typed fallback. P04 delivers the deterministic scheduling/recovery engine, a preview/apply/undo Schedule surface, and the Room v1→v2 migration. P05 delivers the pinned-note Board over the shared task rows, typed red threads, filters, a list alternative and relationship editing validated against `RelationGraph`.
- `scripts/verify.sh` passes on Ubuntu: clean build, unit tests and lint. `scripts/verify.ps1`
  has never been executed because no Windows host was available.
- Instrumentation tests compile but have not been run: `/dev/kvm` is not accessible to the
  build user, so no emulator could be started.
- The board reference image was not present as a separate file among the available attachments; only the two PDFs were available. The visual spec follows the detailed written direction and yellow/blue/green priority treatment in PRD §6, pp. 6–7. Add the original image when available.
- URE deadline, report format, demo requirements, ethics/consent requirements and institutional rubric remain `OPEN — CONFIRM`. Do not fabricate them.
- Chrona is a provisional name; trademark/domain availability is not checked.
- No licence file has been chosen yet.

## How implementation proceeded
1. The pack files were placed at the repository root (this was done in P01), including `source-references/`.
2. One OpenCode agent/session works in this repository.
3. `opencode/OPENCODE_MASTER_EXECUTION_PROMPT.md`; execute phase prompts in order.
4. Do not request the next phase until the previous one has an evidence-backed record in `PROGRESS.md`. A gate with a blocked mandatory check stays open — it does not become passed because the sequence expects to continue.
5. If the toolchain proves incompatible, update the decision register, toolchain docs, prompts, and tests consistently in one change before continuing. (This happened once, in P01: see ADR-018a.)

## Canonical-source rule
`SHARED_CONTRACT.md` is authoritative for cross-module behavior; `DATA_MODEL_AND_INTERFACES.md` expands persistence schema; `SHARED_SYMBOL_REGISTRY.md` is the symbol index; `DECISIONS.md` is the decision register; `REQUIREMENT_TRACEABILITY.md` is the requirement-to-test map. Summaries elsewhere must not redefine them.
