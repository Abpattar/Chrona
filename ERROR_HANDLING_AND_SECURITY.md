# Error Handling, Security and Privacy Design

## Assets and trust boundaries
Assets include task titles/details, fixed-event times, deadlines, task relationships, revisions, preferences, export files, transient transcript text and notification content. Boundaries include user/recognizer text → parser, use cases → Room, app → Android speech provider, notification/overlay system APIs, and a future remote AI proxy (not in MVP).

Main risks: misparsed date causing bad plan; schedule infeasibility hidden; dependency cycle; stale recovery overwriting edits; DB migration/data loss; duplicated notification action; dismiss incorrectly completes task; private text in logs/notifications; permission denied/revoked; overlay or background limitation; unnoticed third-party speech data transfer; client secret leak; OS build drift; participant research without valid consent/approval.

## Required controls
- No backend or provider key in MVP. Keep local-only config ignored. Do source, dependency, manifest and APK scans at release; scans are evidence, not proof of absolute absence.
- Use app-private internal storage and Android sandbox. Ask only for permissions tied to active features; no broad contacts/storage/calendar scopes.
- Define Android backup/data-extraction policy consciously. Default to no unintended backup/transfer of private task DB until owner accepts behavior; keep deliberate export. Describe actual protections honestly.
- Do not claim independent SQLite encryption unless actual encryption is implemented and tested. If needed, evaluate a maintained encrypted SQLite option in a new ADR because it impacts dependencies, migration, backup, performance and device support.
- No raw audio persisted by Chrona. Explicit, visible tap-to-speak with cancel/retry and lifecycle cleanup. No background mic or always-listening.
- `createOnDeviceSpeechRecognizer` only after platform availability check. Generic system recognition may send audio off-device, so never use it as an unannounced fallback. Prefer typing if on-device unavailable; off-device choice requires clear, upfront disclosure and explicit user action.
  - **P03 implementation:** `AndroidSpeechRecognitionAdapter` consults only
    `SpeechRecognizer.isOnDeviceRecognitionAvailable` and creates only the on-device recogniser,
    both guarded to API 31+ (below that, typing). It requests only `RECORD_AUDIO`, at the point
    of use, and adds no `INTERNET` permission. The transcript goes straight to the local parser;
    nothing is buffered or written, and the recogniser is destroyed on release.
- Treat any parse/model output as untrusted: validate title/duration/dates/relationships/status and hard-dependency confirmation. Parser never applies schedule.
  - **P05 implementation:** relationship writes go through `AddRelationshipUseCase`/
    `UpdateRelationshipUseCase`/`ConfirmRelationshipUseCase`, which validate self-edges,
    duplicate `(source, target, type)` triples and hard-dependency cycles against `RelationGraph`
    before any write. A suggested `DEPENDS_ON` is stored with `confirmedAt = null` and does not
    constrain order until confirmed. Because Room `@Upsert` silently swallows the unique-index
    violation, the duplicate check is explicit so a write never reports success without storing
    (see "Failure behavior").
- Use Room DAO binding/parameterized queries, domain validation, integrity constraints and explicit schema migration.
- Apply scheduling revision + assignment changes in a transaction and compare expected revision. Notification actions must be idempotent and use unambiguous action identities.
  - **P04 implementation:** `RoomScheduleRevisionRepository.current/apply/undo` run inside
    `@Transaction` methods; `apply` compares the expected basis revision and the per-task
    `version`s and returns a typed `ApplyRecoveryResult` without writing on mismatch. `undo`
    refuses when a newer revision exists or a task was edited after the plan. Reason codes
    `SCHEDULE_APPLIED`/`SCHEDULE_UNDONE` are recorded instead of free text.
- Logs should contain stable reason codes and minimal diagnostic metadata, never raw task description, audio transcript, secret or full serialized user record.
- Deletion includes app-owned records/preferences/history/exports as appropriate, scheduled WorkManager work and app-managed reminder state. Explain that Chrona cannot delete content processed independently by a speech provider.
- Permission requests happen near point of use with rationale; deny/revoke states are normal paths. Overlay never blocks core task planning.
- Never claim legal/standard compliance absent suitable evidence.

## Future remote AI — not approved for MVP
Before any remote LLM: obtain owner-approved ADR; specify first-party backend/proxy, authentication, authorization, TLS, provider-side secrets, rate/cost limits, request timeouts, schema validation, retention/training policy, data minimization, off-device disclosure, logs/redaction, account/deletion behavior, offline fallback, tests without live credentials, hosting/cost budget and failure risk. The APK must never contain vendor provider secrets. Do not introduce paid infrastructure without approval.

## Platform caveats
Notifications can be denied/muted/delayed. WorkManager is deferrable, not exact-second scheduling. Overlay can be denied/revoked or unavailable in some contexts. Background foreground services are constrained; microphone access is a while-in-use permission. Do not bypass restrictions by hiding foreground activity. On failure preserve tasks and use in-app/notification fallback.

## Risk controls
| Risk | Impact | Mitigation | Verification |
|---|---|---|---|
| Parser misreads task | bad schedule | assumptions, correction, typed validation | parser fixtures and correction path |
| Overloaded day hidden | trust loss | unplaced tasks and conflict explanation | overloaded-day acceptance cases |
| Stale schedule overwrites edits | data loss | revision compare + transaction | stale-version/fault-injection tests |
| Migration loses user data | high | explicit migrations; no destructive fallback | `MigrationTest1To2Test` (v1→v2, real SQLite); extend for every new version |
| Voice transferred remotely unintentionally | high | on-device path only by default; no silent system fallback | availability/source audit + real device |
| Reminder fatigue | medium | caps/quiet hours/batching/suppression | seven-day deterministic reminder fixture |
| Overlay fails on OEM | medium | optional overlay and normal fallback | moto g54 Android15 device checks |
| Secrets leak | high | no remote provider in MVP; secret scans | repository and APK inspection |
| URE ethics issue | high | ask institution before recruiting | documented status before study |

## Failure behavior
Never convert failure into fake success. Preserve existing schedule if a plan/revision fails. Show “not applied” when write fails; allow a manual retry where safe. Bound retries and do not auto-repeat irreversible actions. Record non-sensitive codes and the next safe step.
