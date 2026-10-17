# Risks and Open Items

## Open items — do not invent answers

| ID | Item | Target / responsible | Default until resolved |
|---|---|---|---|
| OPEN-001 | official URE deadline and review schedule | owner/supervisor before committing calendar plan | relative milestones only |
| OPEN-002 | report format/template, required chapters, word limit, references format | department/supervisor before P08 | prepare technical evidence, don't invent official format |
| OPEN-003 | demo duration/rubric, required APK/repository/files | supervisor before P08 | repeatable local demo; adapt when confirmed |
| OPEN-004 | required research method | supervisor before P08 | deterministic technical evaluation |
| OPEN-005 | approval/ethics/consent requirements for participants | institution before recruitment | no participant study while unknown |
| OPEN-006 | actual paper-board visual reference image | owner | written §6 in PDF only; image not separately included in current files |
| OPEN-007 | on-device speech languages and offline availability | measure on moto g54 in P03 | **still OPEN — NOT RUN in P03** (no device attached). No language/offline support is assumed; voice is optional and typing always works. Code is guarded to API 31+ and uses the on-device recogniser only |
| OPEN-008 | exact integrated toolchain matrix and stable patches | P01 agent | pins remain proposed until clean builds pass |
| OPEN-009 | target SDK required by store on release date | re-check immediately before publishing | no store-compliance claim until checked |
| OPEN-010 | independent DB encryption/backup requirements | P07 privacy review | app sandbox + intentional backup policy; no independent encryption claim |
| OPEN-011 | actual overlay/background capability on moto g54 | P06 device testing | optional best-effort + notification/in-app fallback |
| OPEN-012 | retention period for task/revision/reminder history | owner/product decision if not specified | minimize retention; clear data/delete path |
| OPEN-013 | Windows host is not available in the current environment, so the Windows half of the P01 cross-OS gate (AT-033) cannot be run | owner supplies a Windows machine before P08 | gate stays BLOCKED; never report it as passed |
| OPEN-014 | build user is not in the `kvm` group, so only software emulation is possible. Verified in P02: a `-no-accel` API 36 AVD boots and installs APKs, but `artd` finds no usable dex artifacts and `am start` returns `result code=-92` | owner adds the user to `kvm`, or supplies a device/Studio-managed emulator | instrumentation tests compile and are reported NOT RUN |
| OPEN-015 | application licence has not been chosen | owner before any public distribution | no licence file; repository is not published as licensed work |
| OPEN-016 | "Chrona" trademark/domain availability unchecked; no approved reverse-DNS application ID | owner | `com.chrona.app` used as a documented provisional identity (ADR-025) |
| OPEN-017 | reminder batching groups by the task's `projectLabel` rather than the P05 relationship graph | revisit only if richer grouping is needed (P07+) | deliberate simplification; deterministic and unit-tested |

## Resolved in P01

| ID | Item | Resolution |
|---|---|---|
| OPEN-008 | exact integrated toolchain matrix and stable patches | **RESOLVED for Ubuntu.** JDK 17, Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, KSP 2.2.21-2.0.5, Compose BOM 2026.06.00, Room 2.8.5, DataStore 1.2.1, WorkManager 2.12.0, Activity Compose 1.13.0, minSdk 26, compileSdk 36, targetSdk 36 all build clean. Navigation Compose moved 2.10.2 → **2.9.8** and Lifecycle pinned at **2.10.0** because the newer releases require compileSdk 37 / AGP 9.x (ADR-018a). Windows half remains OPEN-013 |

## Main risks and mitigations
| Risk | Impact | Mitigation |
|---|---|---|
| Scope exceeds two credits | incomplete demo | early P02 vertical slice, freeze enhancements, strict phase gates |
| Ubuntu/Windows build drift | contributor blocked on one machine | wrapper/pins, clean clone build on both hosts in P01 |
| Scheduler overpacks day | user loses trust | buffers, explicit overload/unplaced reasons, minimum session, explained changes |
| Parser misreads time/task | bad schedule | narrow parser, assumptions/correction, fixed-time test fixtures |
| Voice sends information off-device unexpectedly | privacy mismatch | on-device path only by default; capability check, no silent fallback, explicit disclosure |
| Overlay unsupported/unreliable | cards absent or delayed | optional overlay, notifications/in-app fallback, real device checks |
| Reminder fatigue | notifications disabled | quiet mode, cap hypothesis, batching, quiet hours, repeat suppression |
| Board relationship clutter | unreadable view | filters/project focus, pan/zoom, list alternative (`BoardFilter`, board↔list over the same records); note canvas zoom bounded 0.5–2.5 |
| P05 board pan is unbounded | user pans the canvas away from the notes with no reset-to-fit | minor; noted in PROGRESS P05 limitations, candidate refinement in P06/P07 |
| Incomplete local delete | data/privacy issue | inventory all tables/preferences/jobs/notifications/exports; residual-state tests |
| Unknown URE research permissions | ethics/compliance problem | clarify institutional requirements before recruitment |
| Unsupported novelty/IP claim | weak research integrity | current literature/competitor work, measured results and limits |
| Unminified release build (ADR-024) | larger APK, no obfuscation | acceptable for a demonstrator; revisit before any real distribution |
| AndroidX auto-upgrade pressure | accidental move onto an unvalidated platform line | lint "newer version available" is `informational`; version changes require a DECISIONS.md entry |
| P04 undo is deliberately one-step and refuses if any task changed after the plan | user cannot undo across later edits | conservative by design; re-running recovery produces a fresh proposal. Documented in DECISIONS.md |

## Release-blocking conditions
- P01 clean build/test on both OSes is not passed or is falsely reported.
- Any fixed commitment/dependency/revision atomicity test fails.
- Voice could transfer audio off-device without clear disclosure and explicit choice.
- Provider API key/secret is in APK/client.
- Core task management fails when overlay, notifications or microphone permissions are denied.
- Human participant work begins before applicable institution requirements and consent are confirmed.

## Currently open blockers
- **Windows clean-clone build/test/lint** — not run (OPEN-013). Blocking for P08 and for any
  honest "cross-OS parity" claim.
- **Emulator / physical-device instrumentation** — not run (OPEN-014). Blocking for any claim
  about runtime behaviour, permissions, notifications, overlay or speech recognition. In P03
  this also leaves VOICE-001/002 and OPEN-007 (on-device availability) unmeasured; voice is
  kept optional and reported as unknown. In P05 the Compose board slice tests
  (`BoardSliceTest`, updated `NavigationShellTest`) compile but are NOT RUN, so the board's
  talkback order, live contrast and large-font reflow are asserted by construction only.
- **Room schema migration** — *resolved in P04*. Schema v2 adds `schedule_revisions` and
  `schedule_placements`; `MIGRATION_1_2` is committed and exercised end-to-end by
  `MigrationTest1To2Test` against a real SQLite v1 database. Both `1.json` and `2.json` are
  exported and committed. (Note: `MigrationTestHelper` cannot read exported schemas from JVM
  unit tests, so the test builds the v1 database from frozen DDL instead.) No destructive
  fallback is registered.

### Phase P06 — Reminders / Quest Cards

- **Runtime surface checks NOT RUN (OPEN-011, OPEN-014).** No emulator/device is available
  (`adb devices` empty), so notification posting/actions, the overlay window, and the hourly
  `ReminderReconciliationWorker` are unobserved. They are implemented
  (`NotificationPresenter`, `ReminderActionReceiver`, `OverlayPresenter`,
  `ReminderReconciliationWorker`, `POST_NOTIFICATIONS`, `SYSTEM_ALERT_WINDOW`) and unit-tested
  through the `ReminderPresenter` boundary only. Action: run on a device to verify channels,
  tag/id replacement, action intents, overlay grant/revoke, DND handling and reconciliation; then
  record the result against OPEN-011.
- **Overlay remains best-effort (OPEN-011).** Opt-in and permission-gated
  (`Settings.canDrawOverlays`, `TYPE_APPLICATION_OVERLAY`); it may not appear in every
  OEM/lockscreen context, and a failure returns `null` so delivery falls back to the notification
  or the in-app card. No foreground service is used.
- **OPEN-017 (low): `relationGroup` = task's `projectLabel`.** Batching groups by project rather
  than the P05 relationship graph — a deliberate simplification; revisit only if richer grouping
  is actually needed.
- **Resolved in P06 (documented deviation): `priority` column on
  `reminder_presentation_history`.** Added so the non-urgent daily cap counts dispatched cards and
  survives later task edits/deletions. `MIGRATION_2_3` adds it; entity, DAO and mappers updated;
  recorded in `CHANGELOG.md` and `DATA_MODEL_AND_INTERFACES.md`. No destructive fallback.
- **Reminder policy correctness.** `DeterministicReminderPolicyTest` (20) covers suppression,
  batching, the per-card non-urgent cap, card urgency and grouping; the policy stays pure. No open
  risk beyond the unmeasured runtime surfaces above.
