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
| OPEN-007 | on-device speech languages and offline availability | measure on moto g54 in P03 | no language/offline support assumed |
| OPEN-008 | exact integrated toolchain matrix and stable patches | P01 agent | pins remain proposed until clean builds pass |
| OPEN-009 | target SDK required by store on release date | re-check immediately before publishing | no store-compliance claim until checked |
| OPEN-010 | independent DB encryption/backup requirements | P07 privacy review | app sandbox + intentional backup policy; no independent encryption claim |
| OPEN-011 | actual overlay/background capability on moto g54 | P06 device testing | optional best-effort + notification/in-app fallback |
| OPEN-012 | retention period for task/revision/reminder history | owner/product decision if not specified | minimize retention; clear data/delete path |
| OPEN-013 | Windows host is not available in the current environment, so the Windows half of the P01 cross-OS gate (AT-033) cannot be run | owner supplies a Windows machine before P08 | gate stays BLOCKED; never report it as passed |
| OPEN-014 | build user is not in the `kvm` group, so no accelerated emulator can run | owner adds the user to `kvm`, or supplies a device/Studio-managed emulator | instrumentation tests compile and are reported NOT RUN |
| OPEN-015 | application licence has not been chosen | owner before any public distribution | no licence file; repository is not published as licensed work |
| OPEN-016 | "Chrona" trademark/domain availability unchecked; no approved reverse-DNS application ID | owner | `com.chrona.app` used as a documented provisional identity (ADR-025) |

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
| Board relationship clutter | unreadable view | filters/project focus, pan/zoom, list alternative |
| Incomplete local delete | data/privacy issue | inventory all tables/preferences/jobs/notifications/exports; residual-state tests |
| Unknown URE research permissions | ethics/compliance problem | clarify institutional requirements before recruitment |
| Unsupported novelty/IP claim | weak research integrity | current literature/competitor work, measured results and limits |
| Unminified release build (ADR-024) | larger APK, no obfuscation | acceptable for a demonstrator; revisit before any real distribution |
| AndroidX auto-upgrade pressure | accidental move onto an unvalidated platform line | lint "newer version available" is `informational`; version changes require a DECISIONS.md entry |

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
  about runtime behaviour, permissions, notifications, overlay or speech recognition.
