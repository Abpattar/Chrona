# Change Control

## Owner approval required
Request explicit owner approval before changing data-egress behavior, introducing any paid service/backend/cloud/sync/account, persisting raw audio/transcripts, defaulting to potentially off-device voice recognition, changing fixed-commitment or dependency semantics, making overlay mandatory, changing export/delete/backup behavior materially, adding large/ongoing-cost dependencies, collecting participant data without institutional rules clarified, or expanding MVP enough to jeopardize the two-credit project.

## Required material change record
Include ID/date; new evidence/reason; previous and new decision; alternatives; affected requirement IDs; affected contracts/symbols/files/phase prompts/tests; compatibility/migration effects; privacy/security/cost effects; verification plan; approval request/result.

## Shared-contract change protocol
1. Explain why the existing contract is insufficient.
2. Propose exact schema/signature/behavior change.
3. Identify consumers/tests by stable symbol ID.
4. Update `SHARED_CONTRACT.md`, `DATA_MODEL_AND_INTERFACES.md` where relevant, registry, dependency/file map, prompts and tests consistently.
5. Change all affected implementations/call sites in one coherent change.
6. Run relevant tests and the phase gate.
7. Record the change in `CHANGELOG.md` and `PROGRESS.md`; material architecture decisions also go into `DECISIONS.md`.

Do not create parallel incompatible interfaces. Ordinary internal refactoring needs no bureaucracy unless it alters observable behavior or contracts.
