# P07 — Privacy, Resilience, Accessibility and Cross-OS Hardening

## Objective
Complete local data controls and harden the app for demonstrable use without expanding it into a cloud platform.

## Prerequisites
P06 gate passed; core user journey is runnable.

## Scope
- Privacy centre showing what is stored and which Android permissions are granted; explain voice input path truthfully.
- Data export in a documented, versioned format and full deletion path; make destructive action explicit and define whether app preferences are retained/reset.
- Room migration tests, atomic writes, error states, backup policy decision and recovery from database/operation failures.
- Safe logging, no raw voice archive, secrets audit, least permissions, dependency/security scan, release manifest audit.
- Accessibility pass: TalkBack, scalable text, color-independent labels, tap targets, reduced motion and list alternative.
- Ubuntu and Windows clean-clone build/test repeat; lock dependencies and document SDK setup.
- No external calendar, cloud sync, LLM, analytics or crash service added.

## Tests
- Export/delete/restart; export malformed/corrupted import refusal if import is offered.
- Database migration from every prior schema in test history; transaction rollback and interrupted operation behavior.
- Permission revoked while app is running; unavailable recognizer and denied notification path.
- Secrets scan, manifest review, dependency audit and safe-log assertions.
- Offline mode for every core workflow.
- Physical moto g54 Android15 and API36 emulator smoke/regression, plus cross-OS builds.

## Acceptance
Privacy statements match measured behavior; data lifecycle is understandable; core use works offline; known platform gaps are recorded. Next: P08.
