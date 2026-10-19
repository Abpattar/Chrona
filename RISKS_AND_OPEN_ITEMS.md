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
| OPEN-007 | on-device speech languages and offline availability | measure on moto g54 in P03 | **PARTLY CLOSED (2026-10-18) — measurement taken on the real handset.** On the moto g54 5G (Android 15, API 35): `hasSystemFeature(MICROPHONE)=true`, `isRecognitionAvailable=true`, `isOnDeviceRecognitionAvailable=true`, and the app's own `AndroidSpeechRecognitionAdapter` reports `Available` once `RECORD_AUDIO` is granted — but a real session ends in `ERROR_LANGUAGE_UNAVAILABLE`, i.e. this handset has no `en-US` on-device pack. In the app a press-and-hold therefore lands in the plain-language failure state with typing one tap away (verified by screenshot). **Still open:** installing the language pack and hearing a real utterance end to end. No language/offline support is assumed; voice stays optional and typing always works. Code is guarded to API 31+ and uses the on-device recogniser only |
| OPEN-008 | exact integrated toolchain matrix and stable patches | P01 agent | pins remain proposed until clean builds pass |
| OPEN-009 | target SDK required by store on release date | re-check immediately before publishing | no store-compliance claim until checked |
| OPEN-010 | independent DB encryption/backup requirements | P07 privacy review | app sandbox + intentional backup policy; no independent encryption claim |
| OPEN-011 | actual overlay/background capability on moto g54 | P06 device testing | optional best-effort + notification/in-app fallback |
| OPEN-012 | retention period for task/revision/reminder history | owner/product decision if not specified | minimize retention; clear data/delete path |
| OPEN-013 | Windows host is not available in the current environment, so the Windows half of the P01 cross-OS gate (AT-033) cannot be run | owner supplies a Windows machine before P08 | gate stays BLOCKED; never report it as passed |
| OPEN-014 | emulator acceleration. The build user is not in the `kvm` group, but `/dev/kvm` is accessible via a filesystem ACL (`user:neo:rw-`). Verified 2026-10-17: the API 36 AVD `chrona_api36` boots hardware-accelerated in ~25s, installs the debug APK, and runs the app (`MainActivity` resumed). Updated 2026-10-18: a physical `moto g54 5G` (Android 15, API 35) was attached over adb and the instrumentation suite runs and passes on it | owner adds the user to the `kvm` group for portability | instrumentation runs on the device; notification, overlay and successful-capture checks remain NOT RUN |
| OPEN-015 | application licence has not been chosen | owner before any public distribution | no licence file; repository is not published as licensed work |
| OPEN-016 | "Chrona" trademark/domain availability unchecked; no approved reverse-DNS application ID | owner | `com.chrona.app` used as a documented provisional identity (ADR-025) |
| OPEN-017 | reminder batching groups by the task's `projectLabel` rather than the P05 relationship graph | revisit only if richer grouping is needed (P07+) | deliberate simplification; deterministic and unit-tested |
| OPEN-021 | simulation tasks polluting the user's own surfaces | measured 2026-10-19 on moto g54 | **CLOSED.** A 142-task simulation shares the `tasks` table with real work, and the first device run showed it burying the user's day (a lifecycle test counting 62 tasks where it expected 1). `TaskQuery.includeSimulated` now defaults to false, so Today, the schedule and reminders ignore simulation rows; the Tasks browser opts in and marks every row. Reset stays scoped by run id and reserved label | none; if a future surface needs simulation rows it must opt in explicitly |
| OPEN-018 | cloud task understanding is **partly verified**: on 2026-10-17 the app's own client (`CloudTaskUnderstandingProvider` + `UrlConnectionHttpTransport` + `TaskIntentWire`) parsed real drafts from a **local** Deno Edge Function with a live Gemini key (2 of 3 phrases; the third fell back on a transient provider error). Remaining gap: **no hosted Supabase deploy** — no Supabase CLI/project/credentials here, and hosted `verify_jwt` behavior is untested. The live provider also returned transient `429`/timeouts | owner supplies a Supabase project + at least one provider key, then runs a real deploy and one live capture | feature stays off by default; never report the **hosted** path as live-working until exercised |
| OPEN-019 | provider keys were pasted in plaintext during development and should be rotated; the observed Gemini key format (`AQ.…`) is non-standard (`AIza…` expected) | owner rotates keys and confirms credential format | keys are never committed (git-ignored `supabase/.env.local`); invalid credentials must not be treated as transient |

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
- **Device instrumentation — PARTLY CLEARED (2026-10-18).** A physical `moto g54 5G` (Android 15,
  API 35) was attached, and `./gradlew :app:connectedDebugAndroidTest` now runs and **passes all
  15 tests there** (`BoardSliceTest`, `TaskLifecycleSliceTest`, `NavigationShellTest`,
  `MainActivityLaunchTest`). The first run found 9 real device-only defects — most seriously, the
  Board could not be used on a phone at all (its selection panel, link editor and Refresh button
  measured 0×0) — all fixed and re-measured; see `PROGRESS.md` → "Device verification on real
  hardware". **Still NOT RUN:** notification posting and Quest Card actions, the overlay window and
  the reconciliation job (OPEN-011); large-font reflow, TalkBack order and live contrast on the new
  board sheet and voice panel; and a successful voice capture, because this handset has no `en-US`
  on-device language pack (OPEN-007). Claims about those remain blocked.
- **Room schema migration** — *resolved in P04*. Schema v2 adds `schedule_revisions` and
  `schedule_placements`; `MIGRATION_1_2` is committed and exercised end-to-end by
  `MigrationTest1To2Test` against a real SQLite v1 database. Both `1.json` and `2.json` are
  exported and committed. (Note: `MigrationTestHelper` cannot read exported schemas from JVM
  unit tests, so the test builds the v1 database from frozen DDL instead.) No destructive
  fallback is registered.

### Phase P06 — Reminders / Quest Cards

- **Runtime surface checks NOT RUN (OPEN-011, OPEN-014).** An API 36 emulator now boots, but
  notification posting/actions, the overlay window, and the hourly
  `ReminderReconciliationWorker` have not been observed. They are implemented
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

### Post-P06 — Cloud task understanding (ADR-037)

- **Live provider NOT re-exercised on 2026-10-19.** The scheduling, multi-day and simulation work
  touched no provider code and no credentials were available in that environment, so nothing new
  may be claimed about a live Groq/Gemini round trip. The evidence below remains the whole of it.
- **Local live integration RUN; hosted deploy NOT RUN (OPEN-018).** On 2026-10-17 a Deno runtime
  was installed and the Edge Function run locally with a live Gemini key; the app's **own** client
  (`CloudTaskUnderstandingProvider` + `UrlConnectionHttpTransport` + `TaskIntentWire`) parsed real
  drafts for 2 of 3 phrases (the third fell back on a transient provider error; the provider also
  returned transient `429`/timeouts). Still unverified: a **hosted** Supabase deploy and hosted
  `verify_jwt`. The feature stays off by default and the deterministic parser remains the authority.
- **Secrets to rotate (OPEN-019).** Provider keys were pasted in plaintext during development.
  They are not committed (git-ignored `supabase/.env.local`) and are never in the APK, but they
  should be rotated; the observed Gemini key format is non-standard and must be validated at
  deploy time. A bad credential is a configuration error, not a transient failure to retry.
- **Data leaves the device only when enabled.** With the feature off (the default) no code path
  opens a socket; when on, only the phrase (≤500 chars), the reference time and the zone are sent.
  This is the one network path in an otherwise local-first app and is recorded as a coordinated
  change (ADR-037); the README privacy section was updated to say so.

### Post-P06 — Voice-first task creation (multi-task utterances)

- **OPEN-020: no provider decomposes one utterance into several tasks (high).** A single
  recording can only ever produce **one** task today. `DeterministicTaskIntentParser` reads one
  clause, and the Edge Function returns one draft, so "I have a function on October 25th, and I
  need to start studying in two hours" becomes a single task with a merged title. The plumbing
  for the fix is in place and exercised by tests, but the split itself does not exist:
  - `TaskUnderstandingResult.Parsed` carries `List<TaskIntentDraft>`, so "N tasks" is the type,
    not a special case, and no caller can silently drop the extras.
  - `CreateTaskFromIntentUseCase.createAll` writes one independent record per draft and reports
    each outcome; the capture flow stores every task immediately and names them all back to the
    user in `VoiceUiState.Created`.
  - `TaskIntentWire.decodeDrafts` reads a `drafts: [...]` envelope (and still accepts the
    deployed function's single `draft`), refusing the whole response if any task in it is
    unusable.
  - **Remaining work:** teach `supabase/functions/understand-task` to emit `drafts` with one
    entry per independent task (prompt + schema change + gateway validation), and teach the
    deterministic parser to split coordinated utterances ("X on the 25th, and Y in two hours").
    Until then the multi-task path is wired but unreachable from real speech.
- **Per-task ambiguity is single-task only.** `NeedsClarification` carries one prompt for one
  draft, so the capture flow can hold back at most one task while asking. When decomposition
  lands, ambiguity has to become per-task so a clear task in the same utterance is stored
  immediately rather than waiting on an unrelated question.
