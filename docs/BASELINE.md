# Baseline Verification Report

**Repository:** https://github.com/Apu-g/neuravex.git
**Branch:** `chrona-saner-parity` (created from `main` at commit `1ff37c9`)
**Date:** 2026-10-28
**Node Version:** v24.21.0 (via nvm)
**Python Version:** 3.12 (system), venv at `backend/venv`

---

## Phase A: Baseline (Complete ✓)

### 1. Requirements Fix

**Original `backend/requirements.txt` (UTF-16 corrupted)**
```
fastapi==0.115.0^M$
uvicorn==0.32.0^M$
python-multipart==0.0.12^M$
google-generativeai==0.8.3^M$
requests==2.32.3^M$
p^@y^@t^@h^@o^@n^@-^@d^@o^@t^@e^@n^@v^@=^@=^@1^@.^@0^@.^@1^@^M^@$
^@
```

**Fixed `backend/requirements.txt` (UTF-8, LF)**
```text
fastapi==0.115.0
uvicorn==0.32.0
python-multipart==0.0.12
google-generativeai==0.8.3
requests==2.32.3
python-dotenv==1.0.1
supabase==2.14.0
redis==5.2.0
langgraph==0.2.0
```

**Added `backend/requirements-dev.txt`**
```text
pytest==8.3.4
pytest-asyncio==0.24.0
httpx==0.28.1
```

### 2. Frontend Build

- `npm ci` — **SUCCESS** (182 packages, 12 vulnerabilities)
- `npm run build` — **SUCCESS** (2.21s, 655 kB bundle warning)

### 3. Python Self-Tests (All Pass)

| Module | Status |
|--------|--------|
| `scheduling.edf` | PASS |
| `scheduling.conflict` | PASS |
| `scheduling.repair_engine` | PASS |
| `behavior.profile` | PASS (deprecation warning for `utcnow()`) |
| `agents.graph` | PASS (fallback) |
| `agents.parser` | PASS (mock) |
| `agents.enrichment` | PASS (fallback, 90-min cap) |
| `agents.explanation` | PASS (mock) |

### 4. Known Defects Confirmed

| ID | Defect | Verified |
|----|--------|----------|
| P1 | Scheduled past deadline | Yes |
| P2 | Night preference over earlier slot | Yes |
| P3 | No fixed_start/earliest_start | Yes |
| P5 | Timezone crash | Yes |
| P6 | Repair past deadline | Yes |
| P7 | Split returns sizes only | Yes |
| P8 | urgency_after from now | Yes |
| Fallbacks | 4 false-success patterns | Yes |

### 5. Missing Components (Phase C)

- Orphaned React components (CalendarView, RepairPanel, BehaviorOnboarding, Drafts)
- Chrona API routes missing
- No Supabase writes to Chrona tables
- No auth/RLS
- No tests/CI

---

## Phase B: Scheduling Engine Fixes (Complete ✓)

**Date:** 2026-10-28

### Changes

| File | Defects Fixed |
|------|---------------|
| `backend/scheduling/edf.py` | P1, P2, P3, P5 + docstring |
| `backend/scheduling/repair_engine.py` | P6, P7, P8 |
| `backend/scheduling/validator.py` | NEW — independent validator |

### Defect Fixes

| Defect | Fix |
|--------|-----|
| **P1** | Slot filter: `slot.end <= task.deadline` before assignment |
| **P2** | Weights only rank VALID slots (deadline-filtered first) |
| **P3** | `Task.fixed_start`/`fixed_end` + `_subtract_fixed_items()` |
| **P5** | All datetimes UTC-normalized; `urgency_score(from_time=...)` |
| **P6** | Move proposals validated: `proposed_end <= deadline` |
| **P7** | Split returns `block_assignments` with start/end times |
| **P8** | `urgency_after` from proposed time, not current time |

### Validator

New `validate_plan(plan, fixed_items, windows)` checks:
1. No overlaps in plan
2. No overlap with fixed items
3. Inside working windows
4. End <= deadline
5. Fixed items don't overlap

Used by scheduler, repair_engine, future API endpoints.

### Test Results

| Test Suite | Result |
|------------|--------|
| `pytest tests/test_scheduling_engine.py -v` | **14 passed, 1 skipped** |
| `python -m scheduling.edf` | OK |
| `python -m scheduling.repair_engine` | OK |
| `python -m scheduling.validator` | OK |
| All behavior/agents self-tests | OK |

### JSON Fixtures

Created in `tests/fixtures/scheduling/test_fixtures.py` for cross-language validation (Kotlin later).

---

## Current Status

- **Phase A:** ✓ Complete
- **Phase B:** ✓ Complete — All P1-P8 defects fixed, validator created, tests passing
- **Phase C:** Next — Connect engine to API endpoints and frontend
- **Phase D:** Blocked on credentials (see `docs/BLOCKERS.md`)

---

## Environment / Blockers (unchanged)

| Blocker | Status |
|---------|--------|
| GEMINI_API_KEY | Missing |
| SUPABASE_URL / ANON_KEY / SERVICE_KEY | Missing |
| UPSTASH_REDIS_URL / TOKEN | Missing |
| Supabase migrations applied | Not verified |
| Vercel Python runtime | Not checked |