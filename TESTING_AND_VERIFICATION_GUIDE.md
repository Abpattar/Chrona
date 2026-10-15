# Testing and Verification Guide

## Principles
- Verify behavior the user needs, not only screen loading or non-null return values.
- Domain logic gets pure unit tests with injected clock/time zone and deterministic IDs.
- Use synthetic fixtures/fake parser/provider/platform adapters. Unit tests must not invoke live LLMs, real speech networks, production endpoints or personal user data.
- Every fixed defect gets a regression test; cross-layer defects need an integration test.
- A phase gate is supported by real command/test/device evidence, not by generated code or an agent saying it should work.

## Test layers
1. **Pure unit:** parser validation, task state machine, relation directions/cycle detection, scheduling constraints/tie-breaks, recovery diff/undo rules, reminder policy.
2. **Persistence integration:** Room DB/DAO, foreign keys, schema migrations, atomic revision application, export/delete, transaction failure and duplicate work.
3. **Contract tests:** parser result schema, JSON export version, repository results/errors, notification action IDs, task-action meanings.
4. **Compose UI:** first task, correction, Today/Schedule, recovery preview/apply/undo, board relation selection, permissions, settings/export/delete and accessibility semantics.
5. **Emulator end-to-end:** launch → create typed task → persist → build plan → simulate recovery → apply/undo → inspect history; notification/action fallback as supported.
6. **Physical Android device:** moto g54 Android 15; on-device speech availability and language, permission requests, notification channels, overlay grant/deny/revoke, process lifecycle, Doze/background and install/reopen.
7. **Cross-host reproducibility:** clean clone on Ubuntu and Windows; same JDK/Gradle/SDK pins; clean build, unit tests and Lint. Capture resolved dependency output if mismatch arises.
8. **Security/privacy:** key scans, manifest/exported components, dependencies, backup rules, logging/redaction, deletion residual state, permission revocation, migration failures, no raw audio persistence, traffic audit if any library opens network sockets.
9. **Performance:** measure scheduler on disclosed fixture/device, Today local action p50/p95 where measurable, board fixture 100 tasks/200 edges, and battery/redraw behavior.
10. **URE:** deterministic scenarios and baseline comparison first. Human-participant activities only after institutional approval, consent and data rules have been confirmed.

## Planned command examples (must be confirmed in P01)
```bash
java -version
./gradlew --version
./gradlew --no-daemon clean assembleDebug testDebugUnitTest lintDebug
./gradlew --no-daemon connectedDebugAndroidTest
```
Windows:
```powershell
java -version
.\gradlew.bat --version
.\gradlew.bat --no-daemon clean assembleDebug testDebugUnitTest lintDebug
.\gradlew.bat --no-daemon connectedDebugAndroidTest
```
These are planned commands, not results. P01 must validate Gradle task names, run them, and document results separately by host. Do not state “pass” if command was not executed. Add `scripts/verify.sh` and `scripts/verify.ps1` as equivalent wrappers which return nonzero on failure.

## Deterministic scheduler fixture
Use injected clock + explicit timezone and fixture containing: a fixed class, a deadline, two tasks with `DEPENDS_ON`, a separate support relation, a flexible workout, a splittable study task with minimum session, and deliberately insufficient available time. Expected invariants: zero fixed overlap, dependency order preserved, supportive link not blocking, every active task placed or explicitly unplaced, no below-minimum split and a reason for every changed/unplaced task.

## Evidence per test/gate
Record ID, git commit, host OS/version, JDK/Studio/SDK/Gradle/AGP/Kotlin versions, emulator/device/API, exact command/action, expected/actual outcomes, totals/failures/skips, redacted logs/screenshots, status PASS/FAIL/BLOCKED/SKIPPED, limitation and next action. Do not save private task transcripts or secrets in evidence.
