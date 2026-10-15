# Global Acceptance Tests

The following are future acceptance tests, not results. Map tests to automated test names during implementation and record real outcomes.

| ID | Setup / action | Required result | Requirement / layer |
|---|---|---|---|
| AT-001 | Fresh install without account/permissions; add typed task | First task persists locally without onboarding block | UX-001 / UI+Room |
| AT-002 | Fixed reference clock; input “Study for GATE for two hours tomorrow” | Draft includes suitable title, ~2h duration and date or visible assumption; user can edit | TASK-001 / parser |
| AT-003 | Ambiguous target like “finish it later” | One concise clarification, no wrong task mutation | TASK-001 / parser+UI |
| AT-004 | On-device speech supported; tap mic, speak, cancel | visible state, parse flow, cancel cleans up recognizer, no raw audio stored | VOICE-001 / device |
| AT-005 | On-device recognizer unavailable | typed fallback; no silent external provider path | VOICE-002 / device |
| AT-006 | Notification permission denied | task/planning still work; in-app surface works; no fake delivery success | CARD-001 / platform |
| AT-007 | Overlay permission denied/revoked | no overlay; notification/in-app fallback; UI reports disabled state | CARD-001 / device |
| AT-008 | Dismiss from overlay/notification | surface closes; task status/completedAt unchanged | CARD-003 / integration |
| AT-009 | Fixed class 10:00–12:00 and overlapping flexible task | no active task placed over class | SCHED-002 / domain |
| AT-010 | A depends on B; C supports A | A follows B; C adds no hard order alone | REL-002 / domain |
| AT-011 | dependency cycle A→B→A | invalid/cycle warning; no infinite loop or silent schedule | REL-001 / domain |
| AT-012 | Active durations exceed day capacity | feasible subset + every unplaced task with reason and overload explanation | REC-002 / domain/UI |
| AT-013 | deadline already infeasible | explicit infeasibility; not represented as feasible after deadline | SCHED-003 / domain |
| AT-014 | Split task but only intervals shorter than minimum session | no unusable fragment; reason unplaced | SCHED-003 / domain |
| AT-015 | late start or commitment overrun | fixed commitments preserved; moved tasks explained; preview first | REC-001/003 |
| AT-016 | Edit task after proposal creation, then apply stale proposal | reject/recompute; newer edit preserved | REC-003 / integration |
| AT-017 | Inject DB failure while applying recovery | assignments/revision roll back atomically; say not applied | REC-003 / Room |
| AT-018 | Undo valid applied revision with no conflicting later edits | prior schedule restored and history updated | REC-003 / integration |
| AT-019 | Undo after newer constraints/edit | don't overwrite newer work; explain and offer new recovery | REC-003 / domain |
| AT-020 | Nonurgent reminder falls within quiet hours | suppress/defer according to policy | NOTIFY-001 / unit |
| AT-021 | Two nonurgent cards already surfaced today | subsequent item batched/suppressed/deferred per configurable policy; cap isn't hard-coded universal rule | NOTIFY-001 / unit |
| AT-022 | Several related items eligible together | meaningful batch where reasonable; no tasks disappear | NOTIFY-001 / unit |
| AT-023 | same task repeatedly dismissed | repeat nagging suppressed per policy and user's mode | NOTIFY-001 / unit |
| AT-024 | move task between Board and Schedule | same TaskId and relation IDs remain intact | UI-001/002 |
| AT-025 | 100 tasks and 200 relations | board remains usable by measured budget; list alternative works | ACC-001 / perf |
| AT-026 | large font/TalkBack/reduced motion | meaningful labels, focus/navigation and non-canvas task access; color not sole indicator | ACC-001 / accessibility |
| AT-027 | airplane mode after installation | task CRUD, plan, recovery and history remain usable | PRIV-001 / integration |
| AT-028 | export representative DB | versioned valid export; no raw audio; schema inventory accurate | PRIV-003 / integration |
| AT-029 | app has data, scheduled work and reminders | delete removes app-managed records/preferences, work and notification state under app control; clean next startup | PRIV-003 / integration |
| AT-030 | scan source/manifests/APK | no provider secret; networking dependency justified; no hidden remote AI | PRIV-002 / static audit |
| AT-031 | migrate every supported old DB schema | preserve valid tasks, relationships and revisions; no destructive fallback | RELIABILITY-001 / migration |
| AT-032 | fresh clone on Ubuntu | documented sync/build/test/lint completes and recorded | PLAT-002 |
| AT-033 | fresh clone on Windows | equivalent Gradle tasks work; no case/path/line-ending drift | PLAT-002 |
| AT-034 | moto g54 Android 15 with battery controls | behavior documented from actual device; no promise of exact timing/universal overlay | PLAT-001 |
| AT-035 | mic, notification and overlay permissions denied | essential task/plan flows still work | privacy/permission fallback |
| AT-036 | human study considered; ethics unknown | recruitment not started until official institution requirements/approvals are resolved | RESEARCH-002 |
