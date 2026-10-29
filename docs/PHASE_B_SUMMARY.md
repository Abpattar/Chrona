# Phase B Completion: Scheduling Engine Fixes

**Branch:** `chrona-saner-parity`
**Date:** 2026-10-28

---

## Summary

Fixed all 8 scheduling correctness defects (P1-P8) from `CHRONA_AUDIT.md` using test-first approach. Created independent validator used by both scheduler and repair engine.

---

## Changes Made

### 1. `backend/scheduling/edf.py` — Core Scheduler

| Defect | Fix |
|--------|-----|
| **P1: Deadline respect** | Added `slot.end <= task.deadline` filter before assigning. Tasks that cannot fit before deadline go to `impossible`, never scheduled late. |
| **P2: Behavior weights** | Weights now only rank AMONG valid slots (those passing deadline check). High-weight slot past deadline is filtered out, not chosen. |
| **P3: Fixed commitments** | Added `fixed_start`/`fixed_end` to `Task` model. New `_subtract_fixed_items()` removes fixed commitments from free slots before scheduling. |
| **P5: Timezone handling** | All datetimes normalized to UTC via `_ensure_utc()`. `urgency_score()` accepts optional `from_time` for repair validation. No more naive/aware crashes. |
| **Docstring** | Updated to describe algorithm as "Greedy EDF-based heuristic" (not optimal per Liu & Layland). |

**New helper functions:**
- `_ensure_utc(dt)` — normalize to timezone-aware UTC
- `_now_utc()` — current time in UTC
- `_subtract_fixed_items(free_slots, fixed_items)` — remove fixed commitments from available time

### 2. `backend/scheduling/repair_engine.py` — Repair Engine

| Defect | Fix |
|--------|-----|
| **P6: Deadline validation** | Move proposals validated: `proposed_end <= deadline`. Invalid slots rejected. |
| **P7: Split returns times** | Split strategy now returns `block_assignments` with actual `start`/`end` times per block, validated against free slots. |
| **P8: Urgency from proposed time** | `urgency_after` calculated via `task.urgency_score(from_time=proposed_end)` not current time. |

**Validator integration:** Every proposal passes `validate_plan()` before being returned.

### 3. `backend/scheduling/validator.py` — Independent Validator (NEW)

Validates any plan (scheduler output, repair proposals, API input) against:
1. **No overlaps** within plan items
2. **No overlap** with fixed commitments
3. **Inside working windows** (at least one window contains each item)
4. **End <= deadline** (if deadline specified)
5. **Fixed items don't overlap** each other

Returns: `{"valid": bool, "errors": list[str]}`

Used by: scheduler (internal), repair_engine (every proposal), API endpoints (future).

### 4. Tests — `tests/test_scheduling_engine.py` + Fixtures

- 14 tests covering P1-P8 + validator cases
- JSON fixtures in `tests/fixtures/scheduling/test_fixtures.py` for cross-language validation (Kotlin later)
- All tests pass: **14 passed, 1 skipped**

---

## Verification

| Test Suite | Result |
|------------|--------|
| `pytest tests/test_scheduling_engine.py -v` | **14 passed, 1 skipped** |
| `python -m scheduling.edf` | OK |
| `python -m scheduling.repair_engine` | OK |
| `python -m scheduling.validator` | OK |
| `python -m behavior.profile` | OK |
| `python -m agents.graph/parser/enrichment/explanation` | All OK |

---

## Backward Compatibility

- Existing `Task` fields unchanged; new `fixed_start`/`fixed_end` are optional
- `schedule()` signature extended with optional `fixed_items` parameter
- `urgency_score()` now accepts optional `from_time` parameter
- Self-test in `edf.py` still passes (uses default behavior)

---

## Next: Phase C

Connect the fixed engine to real API endpoints and frontend:
1. Add Chrona routes to backend (`/api/schedule/generate`, `/api/onboarding`, etc.)
2. Implement Supabase persistence for `schedule_items`, `behavior_*`, `repair_proposals`
3. Connect orphaned React components (`CalendarView`, `RepairPanel`, `BehaviorOnboarding`, `Drafts`)
4. Remove false-success fallbacks in frontend
5. Add authentication / RLS

See `docs/BLOCKERS.md` for required credentials and infrastructure.