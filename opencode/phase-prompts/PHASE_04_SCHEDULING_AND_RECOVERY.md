# P04 — Deterministic Scheduling and Recovery

## Objective
Implement the core Chrona value proposition: a feasible schedule that can be previewed, explained, adjusted and safely undone.

## Prerequisites
P03 gate passed; local records and parser contracts stable. Use `SHARED_CONTRACT.md`, `DATA_MODEL_AND_INTERFACES.md`, `GLOBAL_ACCEPTANCE_TESTS.md`.

## Scope
- Pure Kotlin scheduling domain module: deterministic inputs, constraints and output; inject `Clock` and other environment-dependent values.
- Fixed events/commitments, working windows, deadlines, earliest/preferred windows, duration, priority, split/minimum session, dependency graph and recovery buffers.
- Recovery engine returns proposed change set and explanation reasons before apply.
- Apply revisions transactionally with history/change reason and safe undo/redo rules; stale-plan detection prevents applying a proposal to changed inputs.
- UI preview with affected tasks, tasks moved, tasks not scheduled and reasons. Never silently discard.
- Basic overload state: explicitly say the day cannot fit everything; allow user choices/alternatives rather than asserting the plan is feasible.

## Algorithm requirements
Prefer a straightforward, explainable deterministic heuristic before optimization complexity. Hard constraints must be enforced; soft preferences may be scored. Fixed commitments are never moved. Hard dependency cycles are rejected. Non-blocking relationships never impose ordering. Sort tie-breaking deterministically. If no fit exists, return unplaced reason(s); never truncate silently.

## Required tests
- Fixed event overlap prevention, deadline infeasibility, travel-free time windows (travel isn't part of MVP), dependency ordering/cycle detection, priorities, split rules, buffers, ties/determinism, timezones/DST boundary strategy, all-day/unknown duration, missed task, late wake-up, class overrun, additional incoming task.
- Preview leaves persistent data unchanged; apply is transactional; undo restores only if revision is current and safe; stale proposal is rejected.
- Scenario tests are data-fixture based and assert both placements and explanations.
- Demonstrate the user cannot be blamed or punished for overload.

## Acceptance
The same input produces the same proposal. Fixed commitments survive unchanged. Every task remains represented; scheduled/unplaced outcomes have explanations. Recovery never auto-completes skipped/dismissed tasks. Update progress and scheduling limitations. Next: P05.
