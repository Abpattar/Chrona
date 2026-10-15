# P06 — Quiet Quest Cards and Notification Delivery

## Objective
Implement the reminder policy, ordinary Android notification fallback and best-effort optional overlay.

## Prerequisites
P05 gate passed. Consult current API-36 and API-35 documentation in `TECHNICAL_REFERENCES.md`; do not assume background execution is unrestricted.

## Scope
- Reminder policy as a pure domain decision: eligible task, quiet hours, daily nonurgent cap (default test hypothesis 2, configurable), batching, cooldown/repeated-dismiss suppression, priority/urgency and user interruption preference.
- Notification channels and runtime permission explanation/denial handling.
- Optional SYSTEM_ALERT_WINDOW flow with clear rationale, explicit Settings handoff, visible on/off state and a compact card panel with Start, Done, Later, Dismiss.
- In-app compact panel fallback; tap on normal notification opens the corresponding compact task details when Android permits.
- No always-running overlay or background foreground service unless a documented, approved need and platform review justify it.

## Critical semantics
- Dismiss closes this card only and does not complete, delete or silently postpone the task.
- Done invokes the canonical completion use case.
- Later invokes explicit postpone action and requires an available replacement time or transparent unscheduled outcome.
- Start opens the appropriate focused task/work state.
- If permission is refused, overlay cannot start, OEM restricts it, or app is force-stopped, essential workflows and ordinary notification/in-app fallback continue.
- WorkManager is only for deferrable reconciliation, not guaranteed exact-time delivery.

## Tests
- Pure policy: cap reset/day boundary, quiet hours crossing midnight, urgent exceptions if defined, group related reminder candidates, repeated dismiss, postpone, timezone changes.
- Notification permission allowed/denied/revoked; channel disabled; overlay permission granted/denied/revoked; tap actions preserve semantics.
- Emulator API36 and physical moto g54 API35; test screen off, app background, reboot, battery restriction, permission settings return and foreground/background launches.
- Log exact observed outcomes and limitations.

## Acceptance
Quiet reminders and in-app task access work without overlay. Permission denial never blocks core functionality. Do not claim universal overlay availability. Next: P07.
