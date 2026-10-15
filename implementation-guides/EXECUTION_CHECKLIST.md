# Chrona Execution Checklist

## Before P01
- [x] Create/choose one repository and commit this pack.
- [x] Record Git version, Ubuntu/Windows versions, installed JDK and SDK.
      (Windows host was not available — recorded as OPEN-013, gate stays BLOCKED.)
- [ ] Confirm the current URE deadline, report format, demo needs and supervisor requirements.
- [x] Verify official toolchain release/compatibility links in `TECHNICAL_REFERENCES.md`
      (resolved with one correction — see DECISIONS.md ADR-018a).
- [x] Reconfirm app namespace/application ID (provisional `com.chrona.app` — ADR-025).
- [x] Create/confirm Gradle wrapper, settings, build files, version catalog.
- [x] `.gitignore`, lint/format configuration, `scripts/verify.sh` / `verify.ps1`.
- [x] Navigation shell for Today, Schedule, Board, History, Settings.
- [ ] Ubuntu clean clone `clean assembleDebug testDebugUnitTest lintDebug` — **PASS**.
- [ ] Windows clean clone equivalent — **BLOCKED**, no Windows host available.
- [ ] Launch app on an API 36 emulator — **BLOCKED**, `/dev/kvm` not accessible to build user.

## Every phase
- [ ] Read master prompt, current phase prompt, progress and affected contracts.
- [ ] Inspect repository state and preserve working code.
- [ ] Implement only phase scope.
- [ ] Add tests before calling behavior complete.
- [ ] Execute commands and preserve exact outcomes.
- [ ] Update traceability/progress/changelog/decisions.
- [ ] Check privacy, accessibility, offline behavior and errors.
- [ ] Stop at gate if a mandatory check fails.

## Before final demo
- [ ] Fresh checkout clean build on Ubuntu.
- [ ] Fresh checkout clean build on Windows. (BLOCKED today — no Windows host.)
- [ ] API36 emulator smoke suite. (BLOCKED today — no accelerated emulator available.)
- [ ] moto g54 Android15 physical test.
- [ ] Overlay grant/deny/revoke and notification grant/deny/revoke scenarios.
- [ ] On-device speech available/unavailable; no silent remote fallback.
- [ ] Recovery preserves fixed events and all unscheduled tasks.
- [ ] Board has accessible list alternative.
- [ ] Export and deletion verified.
- [ ] Research ethics/consent status resolved before participants.
- [ ] Demo APK artifact checksum and version recorded.
- [ ] A licence file exists (OPEN-015).