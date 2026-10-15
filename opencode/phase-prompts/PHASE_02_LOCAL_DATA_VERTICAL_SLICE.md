# P02 — Local Data and First End-to-End Task Slice

## Objective
Deliver the earliest end-to-end working path: create typed task → validate → persist in Room/SQLite → display on Today → edit/complete → survive app restart.

## Prerequisites
P01 gate passed. Read current `PROGRESS.md`; inspect exact schema/build. Use canonical contracts `CON-TASK`, `CON-STORE`, `CON-STATE`, `CON-EXPORT`.

## Scope
- Domain task/event/relationship models and value types as specified in `DATA_MODEL_AND_INTERFACES.md`.
- Room entities, DAO, database, explicit repository interface/implementation, migrations strategy and deterministic test database.
- Use cases for create/list/edit/mark-completed and explicit postponement/status updates.
- Today list and task editor with typed creation as the initial end-to-end path; stable IDs shared across screens.
- Persist basic manual fixed commitments even if the full scheduler arrives in P04.
- Small Settings/Privacy placeholder only as required for architecture; no cloud accounts.

## Required behavior
Invalid records return typed validation errors. Task state change and related history/audit record are atomic where required. A dismiss or UI navigation never changes task status. Restarting the process does not lose accepted changes. Export/delete may be minimally scaffolded but must not be represented as complete before P07.

## Tests
- Unit: validators, status transition rules, stable identity.
- Room DAO/repository integration tests and restart/persistence tests.
- Duplicate action/idempotence checks where applicable.
- UI test: create task, reopen app/state, mark complete.
- Confirm no DAO access from composables and no network dependence for this flow.

## Acceptance
A runnable end-to-end slice exists early, demonstrably crosses UI/use case/domain/repository/Room, with test results and a scripted manual demo. Update progress and traceability. Next: P03.
