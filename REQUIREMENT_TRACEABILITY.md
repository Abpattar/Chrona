# Requirement Traceability Matrix

All entries start `PLANNED` unless marked otherwise; the planning document itself is not evidence that the behavior has been implemented. Mark PASS only when actual command/test/device evidence is recorded in `PROGRESS.md` or the final verification report.

| ID | Requirement; class/priority | Source | Owner / phase | Observable acceptance evidence |
|---|---|---|---|---|
| PRJ-001 | Native Android planner; confirmed Must | PRD §§1,18; owner | P01–P08 | `IN_PROGRESS` — debug APK builds; no device/emulator run yet |
| UX-001 | low-friction first task; confirmed Must | PRD §§3,8,15 | UI/P02 | `PASS (automated)` — typed task created with no account and no onboarding block. The "~1 minute" hypothesis is still unmeasured |
| TASK-001 | typed/voice capture, structured draft and correction; confirmed Must | PRD §§7,9; owner | P02/P03 | `PASS (automated)` — typed path (P02) plus the deterministic parser (38 cases), draft validation, review/correction and one focused clarification (P03). Device voice path NOT RUN |
| TASK-002 | stable identity/status lifecycle; confirmed Must | PRD §§9,19–20 | domain/data/P02 | `PASS (automated)` — 65 unit + 17 Room tests cover identity, transitions, idempotence and persistence. Device-level identity NOT RUN |
| TASK-003 | manual fixed commitments; confirmed Must | owner | P02/P04 | `PASS (automated)` — CRUD and overlap query tested (P02); the scheduler treats them as immovable hard constraints (`ScheduleEngineTest`, P04). Device run NOT RUN |
| REL-001 | typed relation semantics/integrity; confirmed Must | PRD §6 | P02/P05 | `PASS (automated)` — direction, self-edge, cycles, duplicate-triple rejection and cascade delete tested at use-case + Room level (`RelationshipUseCasesTest`, `RoomRelationshipRepositoryTest`); board UI is P05. Device run NOT RUN |
| REL-002 | hard dependency distinct from supportive relationship; confirmed Must | PRD §6.4 | P04/P05 | `PASS (automated)` — `DEPENDS_ON` orders and cycles are rejected by the engine (`ScheduleEngineTest`); a `SUGGESTED` dependency is stored dormant until confirmed (`RelationshipUseCasesTest`, `RelationPresentationTest`); SUPPORTS imposes no order |
| SCHED-001 | deterministic scheduler independent of AI/UI; confirmed Must | PRD §§7,10,19; owner | P04 | `PASS (automated)` — `DeterministicScheduleEngine` is pure (no Android/Room/parser imports); same input yields the same proposal id (`ScheduleEngineTest`, 21 cases) |
| SCHED-002 | fixed commitments never moved; confirmed Must | PRD §§3,10–11 | P04 | `PASS (automated)` — commitments and FIXED tasks are placed immovably or reported as conflicts; adversarial overlap fixtures in `ScheduleEngineTest` |
| SCHED-003 | deadlines/duration/windows/flexibility/splitting honored; confirmed Must | PRD §9–11 | P04 | `PASS (automated)` — deadline infeasibility, earliest/preferred windows, missing/invalid duration and split/min-session covered by `ScheduleEngineTest` |
| REC-001 | late start/overrun/delay/task addition/break recovery; confirmed Must | PRD §11; owner | P04 | `PASS (automated)` — `DeterministicRecoveryPlanner` returns a preview/diff/reasons; late wake-up, incoming task and commitment-overrun scenarios tested |
| REC-002 | no silent task drop; confirmed Must | PRD §§3,10–11 | P04 | `PASS (automated)` — every eligible task is placed or listed in `unplaced` with a reason; `overloaded` is stated; preview writes nothing (`ScheduleViewModelTest`) |
| REC-003 | explain moves, accept/edit/reject, safe undo; confirmed Must | PRD §§3,11,15 | P04 | `PASS (automated)` — `changes` explain movement; apply is transactional and undo is guarded against later edits (`RecoveryUseCasesTest`, `RoomScheduleRevisionRepositoryTest`). Device UI run NOT RUN |
| CARD-001 | optional overlay and notification/in-app fallback; confirmed Must | PRD §§12,18; owner | P06 | permission denied/revoked/unavailable surface tests |
| CARD-002 | Start/Done/Later/Dismiss distinct; confirmed Must | PRD §§12,15; owner | P06 | action integration tests |
| CARD-003 | dismiss never completes/deletes task; confirmed Must | PRD §9.3; owner | P06 | explicit regression test for overlay + notification dismissal |
| NOTIFY-001 | quiet hours, batching, configurable cap, repeat suppression; confirmed Must | PRD §§8,12; owner | P06 | deterministic simulated day/week; cap 2 remains hypothesis |
| VOICE-001 | tap-to-speak; no always-listening/raw audio retention; confirmed Must | PRD §7; owner | P03/P07 | `PARTIAL (automated)` — tap is the only entry; cancel/close/lifecycle release the recogniser (`AndroidSpeechRecognitionAdapterTest`, `VoiceCaptureViewModelTest`); no audio persisted. On-device permission flow on a real device NOT RUN |
| VOICE-002 | no silent off-device recognition; confirmed Must | owner | P03 | `PASS (automated) by construction` — the only recogniser created is the on-device one; no `INTERNET` permission; unavailable/denied → typing. Real-device network behaviour NOT RUN (unknown) |
| PRIV-001 | local-first, no account/cloud sync; confirmed Must | owner | P02/P07 | `IN_PROGRESS` — no network permission or networking dependency exists in the slice; airplane-mode test (AT-027) NOT RUN |
| PRIV-002 | no embedded provider secret; confirmed Must | PRD NFR-SEC-001; owner | P01/P07 | `PASS (P01 scope)` — no remote AI exists; source/manifest reviewed. Re-audit in P07 |
| PRIV-003 | data export/delete and lifecycle consistency; confirmed Must | PRD §§9,17; owner | P07 | export schema and deletion residual checks |
| PRIV-004 | data minimization, permission disclosure, redacted logs; confirmed Must | PRD §17 | P03/P07 | `PASS (automated, P02+P03 scope)` — the status log stores only machine reason codes; P03 requests only `RECORD_AUDIO` at point of use and persists no transcript |
| PLAT-001 | test moto g54 Android15; confirmed Must | owner | P03/P06/P07 | `NOT RUN` — no device attached in P03; record locale/on-device availability/network/permission flow on the device (OPEN-007) |
| PLAT-002 | Ubuntu/Windows parity, one repo/pinned toolchain; confirmed Must | PRD §18; owner | P01/P08 | `BLOCKED` — Ubuntu PASS (`scripts/verify.sh`); no Windows host available |
| UI-001 | Today/Schedule/Board/History/Settings; shared task records; confirmed Must | PRD §§5,15 | P01/P02/P04/P05/P07 | `PASS (automated, board scope)` — five destinations exist; Today reads the real repository; Schedule renders preview/apply/undo (P04); Board renders the same shared rows as canvas or list, with filters and link editing (P05). History content still P06/P07; device run BLOCKED |
| UI-002 | paper/pins/red thread and priority swatches; confirmed Must | PRD §6; owner | P01/P05 | `PASS (automated, board scope)` — paper/pin/palette tokens plus the pinned-note board, thin typed red threads, and label+icon+words per relationship (`RelationPresentationTest`); device look NOT RUN |
| ACC-001 | scalable text, semantics, reduced motion, board list; confirmed Must | PRD §§6.5,16 | P01/P05/P07 | `PARTIAL (automated)` — lint accessibility checks set to error; the board provides a list alternative over the same records, talkback content descriptions and no always-running animation (`BoardLayoutTest`, `BoardViewModelTest`). Live large-font/contrast/talkback review NOT RUN |
| RELIABILITY-001 | offline core, transactional mutations, safe errors/migrations; confirmed Must | PRD §§16,20 | all | `PASS (automated, P02+P04 scope)` — transactions, optimistic concurrency, idempotence and typed errors tested; the v1→v2 migration is tested (`MigrationTest1To2Test`). Offline still NOT RUN on a device |
| RESEARCH-001 | evaluation metrics and baseline; confirmed Must | PRD §§22–24; owner | P08 | reproducible calculations, scenarios, sample/denominators/limitations |
| RESEARCH-002 | ethics/consent check before participant study; confirmed Must | owner | P08 | written requirements/approval status; no study while unknown |
| URE-OPEN-001 | deadline/report/demo/rubric; Open Must resolve for submission | owner says unknown | P08 | confirm through official supervisor/department source |
| URE-OPEN-002 | allowed research method and consent requirements; Open Must before any participant study | owner/PRD | P08 | documented approval/consent or no participants |
| FUTURE-001 | remote LLM via secure backend proxy only if later needed; Deferred | owner | later | new owner-approved ADR, threat model and cost/privacy review |
| FUTURE-002 | calendar/sync/personalization; Deferred | owner/PRD | later | separate scope decision and design |

## Status vocabulary
`PLANNED`, `IN_PROGRESS`, `PASS`, `FAIL`, `BLOCKED`, `DEFERRED`, `NOT_APPLICABLE`. An implementation phase must not set PASS because code was generated; it needs actual verification.
