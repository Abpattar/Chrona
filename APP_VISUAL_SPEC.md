# Chrona Visual and Interaction Specification

## Design intent
Calm, practical, tactile, modern — a personal planning/investigation board, not a cartoon game and not a generic AI-chat screen. The written visual direction in the supplied requirements PDF §6, pp. 6–7 describes a warm cork/paper texture, pinned note cards, slight rotations, subtle shadows, metal pin details and thin red string connecting related items. Avoid exaggerated 3D, busy decoration, low contrast or making text hard to scan.

**Reference image status:** no standalone reference image was available among the supplied attachment files at pack preparation. This document follows the written description and priority-color mapping. Add the owner’s exact image to the repository when available; use it as inspiration rather than copying it literally.

## Surfaces
- **Today:** immediate answer to “What should I do next?”, next task, short day summary, remaining tasks, schedule preview, visible typed/voice capture, completion/postpone/recovery actions. Avoid a dashboard crowded with statistics.
- **Schedule:** day-first timeline, fixed commitments, flexible tasks, collisions/deadlines and recovery entry point. Make the difference between a deadline and preferred time clear.
- **Board:** tactile notes and red relationship threads; pan/zoom/filter/focus/search where feasible. The same underlying task ID is used. Lines sit behind/around cards where possible, never cover text or action controls.
- **History:** completed, postponed, skipped, moved tasks and schedule revisions; plain-language reasons and safe undo where valid.
- **Quest Card:** small temporary action surface; compact task panel (Start, Done, Later, Dismiss) instead of full-app launch for every action. Overlay is optional; notification and in-app surfaces are proper alternatives.
- **Task editor/recovery panel:** parser assumptions, editable fields, fixed blocks, affected tasks, change reasons, what cannot fit and any user choice required.
- **Settings/Privacy Centre:** reminder style/cap/quiet hours, overlay/permission state, voice processing notice, reduced-motion preference, data inventory/export/delete.

## Priority note treatments
| Priority | Paper treatment | Non-color cue required |
|---|---|---|
| LOW / side task | soft yellow | text label and/or priority icon |
| IMPORTANT / high priority | soft blue | text label and/or priority icon |
| CRITICAL / very important | soft green | clear marker/label and deadline/context when useful |
| Completed/archived | desaturated or folded-corner treatment | explicit status text/icon |

Priority is an explicit field, never inferred only from color. A red thread means a relationship, not same priority or same timing.

## Relationship thread semantics
Types: `DEPENDS_ON`, `SUPPORTS`, `PART_OF_PROJECT`, `SAME_EVENT`, `RELATED_REFERENCE`. Selecting a thread reveals its type and explanation. Only a confirmed hard dependency (`DEPENDS_ON`) affects precedence. A suggestion is visibly marked and can be accepted, edited or rejected; no silently-created consequential dependency.

## Accessibility/motion
- Scalable text and contrast over textures; labels/icons for color; meaningful TalkBack semantics.
- Clear focus/selection and usable touch targets; task/link interaction also available in a list/tree alternative.
- Reduced motion; no perpetual animation or expensive continuous re-rendering. Paper rotation/shadows are restrained.
- Board actions work without requiring precise Canvas tapping alone.

## Visual acceptance
1. Tactile paper/cork feel with subtle shadows/pins/red strings; not cartoonish/overly 3D.
2. Yellow/blue/green mapping matches the requirements and remains explicitly labeled.
3. Cards readable at selection/zoom/text scaling.
4. Threads don't obstruct text/controls as far as practical and reveal relation reason.
5. Today/Schedule/Board edits reflect the same stable task identity.
6. Quest Card actions are accessible without overlay permission.
