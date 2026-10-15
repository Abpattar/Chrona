# Implementation Phase Plan — Sequential, One Agent

Eight implementation phases are proposed for a two-credit project. These are future implementation phases, not completed work. Run them in order using a single OpenCode agent in one repo and stop at each gate.

| Phase | Objective | Gate |
|---|---|---|
| P01 Foundation and reproducibility | native Android shell, routes/theme, pinned toolchain, host scripts | clean clone sync/build/test/lint on Ubuntu + Windows and app launches in emulator |
| P02 Local data / vertical slice | Room schema, repository, task lifecycle/editor, Today list | actual typed task create→persist→reload→complete end-to-end flow; works offline |
| P03 Voice and deterministic parser | tap-to-speak adapter, parser contract, grammar, correction and fallback | typed flow always works; no silent off-device speech; device availability documented |
| P04 Scheduling and recovery | pure scheduler, constraints, explanation/diff, revision apply/undo | no fixed/dependency violations; all tasks accounted for; atomic updates and safe undo |
| P05 Board and relationships | paper notes, thread types, filters, zoom/pan, list alternative | stable shared TaskIds; thread semantics accurate; accessibility and performance checked |
| P06 Quest Cards/reminders | policy, cap, quiet hours, batching, notifications, optional overlay compact panel | denied/revoked permissions fall back; action semantics tested; moto g54 results recorded |
| P07 Privacy/resilience/accessibility | data export/delete, backup rules, migration/error/security audit | deletion/migration/accessibility/privacy regression tests pass or blocker approved |
| P08 URE evaluation and packaging | demo dataset, metric runs, clean builds, release artifact and final report | requirements traced to actual evidence; URE unknowns/ethics status honest |

## P01 — Foundation
Create native Kotlin/Compose shell, Gradle wrapper, manifest, minimal route scaffold, design tokens, `.editorconfig`, `.gitattributes`, `.gitignore`, scripts and README. Verify current official JDK/Studio/AGP/Gradle/Kotlin/KSP/Compose/Room compatibility and artifact availability. Build/test/lint clean clones on both hosts and launch API36 emulator. If matrix fails, fix decision + all references before proceeding.

## P02 — Local data and the first vertical slice
Implement stable IDs/types, task state transitions, Room DB/DAOs/converters, explicit migrations, repository/use cases, manual fixed event basics, task editor and Today/Schedule read views. Demonstrate type task → validate → save → navigate/reopen → complete → restart/reload. Test idempotence/offline/persistence/migrations. This early slice must not wait until final phase.

## P03 — Voice and parser
Implement deterministic parser for bounded common phrases, structured drafts, assumptions/confidence, correction/clarification, explicit tap-to-speak and supported on-device recognizer. Keep raw audio out of Chrona persistence. Tests cover reference date/time zone, ambiguity, invalid input, recognition unavailable, permission/cancel/lifecycle cleanup.

## P04 — Scheduling and recovery
Implement pure constraint engine and proposal/diff; fixed commitments; time windows/deadlines; splitting/minimum session; hard dependency DAG and cycles; overload/unplaced reasons; recovery preview, transaction, revision history/undo. Acceptance tests cover late start, overrun, break, task addition/cancellation, stale proposal, fault-injected DB write and time/date boundaries.

## P05 — Board and relationships
Add paper and red-thread visuals, relation editor and explanation, pan/zoom/filter/project focus and accessible list alternative. Shared TaskId only. Test dependency vs support semantics, persistence and relation integrity, TalkBack/scaled text/reduced motion and 100 tasks/200 relationships.

## P06 — Quest Cards/reminders
Pure decision policy; Quiet/Balanced/More Guidance; cap 2 default hypothesis editable; quiet hours, related batching and dismissal suppression; notification channels/permission/actions; optional overlay and compact panel; WorkManager reconciliation (non-exact). Test denied/revoked permissions, action identity/idempotency, crossing-midnight quiet hours, process death, Doze and actual phone behavior.

## P07 — Privacy and hardening
Privacy Centre/data inventory, export/delete, backup policy, permission revocation, safe logs/errors, migration audit, dependency/manifests/secrets, accessibility/reduced motion, offline recovery. Verify deletion includes all app-owned tables/preferences/revisions/history and pending work/surfaces. Do not claim independent DB encryption unless added and tested.

## P08 — URE evidence and final packaging
Run global suite, clean builds on both hosts, emulator and real device; prepare stable synthetic scenario dataset; calculate schedule/parser/reminder metrics against a basic baseline; produce final verification report. Confirm URE deadlines/report/demo/ethics details with institution. If unknown, do not recruit participants; mark study as not conducted/pending approval.

## Phase dependencies
P01 → P02 → P03 → P04 → P05 → P06 → P07 → P08. P04 can use typed/fake parser inputs if contracts are stable, but phase gates remain sequential. Never run phase prompts as independent codebases or agents.
