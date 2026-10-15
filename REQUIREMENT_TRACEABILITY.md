# Requirement Traceability Matrix

All entries start `PLANNED` unless marked otherwise; the planning document itself is not evidence that the behavior has been implemented. Mark PASS only when actual command/test/device evidence is recorded in `PROGRESS.md` or the final verification report.

| ID | Requirement; class/priority | Source | Owner / phase | Observable acceptance evidence |
|---|---|---|---|---|
| PRJ-001 | Native Android planner; confirmed Must | PRD §§1,18; owner | P01–P08 | `IN_PROGRESS` — debug APK builds; no device/emulator run yet |
| UX-001 | low-friction first task; confirmed Must | PRD §§3,8,15 | UI/P02 | `PLANNED` (P02) |
| TASK-001 | typed/voice capture, structured draft and correction; confirmed Must | PRD §§7,9; owner | P02/P03 | `IN_PROGRESS` — typed editor in P02; parser in P03 |
| TASK-002 | stable identity/status lifecycle; confirmed Must | PRD §§9,19–20 | domain/data/P02 | `IN_PROGRESS` — TaskId routed in P01; model/state machine in P02 |
| TASK-003 | manual fixed commitments; confirmed Must | owner | P02/P04 | event CRUD and fixed-block schedule tests |
| REL-001 | typed relation semantics/integrity; confirmed Must | PRD §6 | P02/P05 | relation direction, cycles, delete integrity tests |
| REL-002 | hard dependency distinct from supportive relationship; confirmed Must | PRD §6.4 | P04/P05 | SUPPORTS doesn't impose order; DEPENDS_ON does |
| SCHED-001 | deterministic scheduler independent of AI/UI; confirmed Must | PRD §§7,10,19; owner | P04 | pure unit/invariant tests; no Android/Room/parser imports |
| SCHED-002 | fixed commitments never moved; confirmed Must | PRD §§3,10–11 | P04 | adversarial overlap/recovery scenarios with zero violations |
| SCHED-003 | deadlines/duration/windows/flexibility/splitting honored; confirmed Must | PRD §9–11 | P04 | constraint fixture matrix |
| REC-001 | late start/overrun/delay/task addition/break recovery; confirmed Must | PRD §11; owner | P04 | each input returns preview/diff/reasons |
| REC-002 | no silent task drop; confirmed Must | PRD §§3,10–11 | P04 | every active task scheduled or explicit unplaced reason |
| REC-003 | explain moves, accept/edit/reject, safe undo; confirmed Must | PRD §§3,11,15 | P04 | UI tests + atomicity/stale proposal tests |
| CARD-001 | optional overlay and notification/in-app fallback; confirmed Must | PRD §§12,18; owner | P06 | permission denied/revoked/unavailable surface tests |
| CARD-002 | Start/Done/Later/Dismiss distinct; confirmed Must | PRD §§12,15; owner | P06 | action integration tests |
| CARD-003 | dismiss never completes/deletes task; confirmed Must | PRD §9.3; owner | P06 | explicit regression test for overlay + notification dismissal |
| NOTIFY-001 | quiet hours, batching, configurable cap, repeat suppression; confirmed Must | PRD §§8,12; owner | P06 | deterministic simulated day/week; cap 2 remains hypothesis |
| VOICE-001 | tap-to-speak; no always-listening/raw audio retention; confirmed Must | PRD §7; owner | P03/P07 | permission/source audit, cancellation/lifecycle tests and data audit |
| VOICE-002 | no silent off-device recognition; confirmed Must | owner | P03 | unsupported on-device path leads to typing or disclosed explicit choice |
| PRIV-001 | local-first, no account/cloud sync; confirmed Must | owner | P02/P07 | `IN_PROGRESS` — backup/transfer disabled (ADR-023); airplane-mode test in P02 |
| PRIV-002 | no embedded provider secret; confirmed Must | PRD NFR-SEC-001; owner | P01/P07 | `PASS (P01 scope)` — no remote AI exists; source/manifest reviewed. Re-audit in P07 |
| PRIV-003 | data export/delete and lifecycle consistency; confirmed Must | PRD §§9,17; owner | P07 | export schema and deletion residual checks |
| PRIV-004 | data minimization, permission disclosure, redacted logs; confirmed Must | PRD §17 | P03/P07 | source/log review, permission denied/revoked tests |
| PLAT-001 | test moto g54 Android15; confirmed Must | owner | P03/P06/P07 | actual model/API/build settings and results captured |
| PLAT-002 | Ubuntu/Windows parity, one repo/pinned toolchain; confirmed Must | PRD §18; owner | P01/P08 | `BLOCKED` — Ubuntu PASS (`scripts/verify.sh`); no Windows host available |
| UI-001 | Today/Schedule/Board/History/Settings; shared task records; confirmed Must | PRD §§5,15 | P01/P02/P04/P05/P07 | `IN_PROGRESS` — all five destinations exist (P01); device run BLOCKED |
| UI-002 | paper/pins/red thread and priority swatches; confirmed Must | PRD §6; owner | P01/P05 | `IN_PROGRESS` — paper/pin/palette tokens implemented; board is P05 |
| ACC-001 | scalable text, semantics, reduced motion, board list; confirmed Must | PRD §§6.5,16 | P01/P05/P07 | `IN_PROGRESS` — lint accessibility checks set to error; device review blocked |
| RELIABILITY-001 | offline core, transactional mutations, safe errors/migrations; confirmed Must | PRD §§16,20 | all | offline, fault-injection, migration and reopen tests |
| RESEARCH-001 | evaluation metrics and baseline; confirmed Must | PRD §§22–24; owner | P08 | reproducible calculations, scenarios, sample/denominators/limitations |
| RESEARCH-002 | ethics/consent check before participant study; confirmed Must | owner | P08 | written requirements/approval status; no study while unknown |
| URE-OPEN-001 | deadline/report/demo/rubric; Open Must resolve for submission | owner says unknown | P08 | confirm through official supervisor/department source |
| URE-OPEN-002 | allowed research method and consent requirements; Open Must before any participant study | owner/PRD | P08 | documented approval/consent or no participants |
| FUTURE-001 | remote LLM via secure backend proxy only if later needed; Deferred | owner | later | new owner-approved ADR, threat model and cost/privacy review |
| FUTURE-002 | calendar/sync/personalization; Deferred | owner/PRD | later | separate scope decision and design |

## Status vocabulary
`PLANNED`, `IN_PROGRESS`, `PASS`, `FAIL`, `BLOCKED`, `DEFERRED`, `NOT_APPLICABLE`. An implementation phase must not set PASS because code was generated; it needs actual verification.
