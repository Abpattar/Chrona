# P05 — Paper Board, Red Threads and Relationship Graph

## Objective
Add the distinctive board while preserving one canonical task/event data store shared with Today/Schedule.

## Prerequisites
P04 gate passed. Use `APP_VISUAL_SPEC.md`, `SHARED_CONTRACT.md` and relation semantics in `DATA_MODEL_AND_INTERFACES.md`.

## Scope
- Board canvas/list representation of existing task/event records, priority notes (yellow low/side; blue important; green very important/critical), restrained pins/shadows/rotation and thin red-string links.
- Relationship selection reveals type and human-readable meaning. Different relation types use distinct label/text/icon, not color alone.
- Filters by project, date, status and priority; focused project/subgraph, search, pan/zoom and a fully usable accessible list alternative.
- UI state such as position may be persisted as view metadata separately from schedule/domain facts. Board rendering must not create duplicate tasks.
- Preserve links when task date changes.

## Required behaviors
- `DEPENDS_ON` semantics follow shared contract direction; prerequisite links have direction and cycles are prevented.
- Helpful relations such as supports/related/part-of do not create scheduler precedence.
- Suggested links are not introduced in MVP unless they are visibly presented for explicit acceptance; no silent consequential dependency creation.
- Use stable coordinates, simple drawing, bounded rendering work and avoid always-running animation.

## Tests
- Relationship CRUD/integrity, delete policy, graph cycle rules.
- UI tests for add/edit/select/filter and switching Board ↔ Today preserves task identity.
- Accessibility test for list alternative, talkback labels, contrast, large font, reduced motion.
- Starting performance fixture from source: 100 tasks and 200 relationships; measure/render and record actual results, don't invent timings.

## Acceptance
Board is both visually distinctive and usable with assistive technology. Relationship semantics are explicit; task records remain shared. Next: P06.
