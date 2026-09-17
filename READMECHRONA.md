# CHRONA Backend Integration Guide

> **For: Backend developers, frontend integrators, and AI agent teams**
> **Version:** 1.0.0 | **Last Updated:** 2026-04-08
> **Base:** Neuravex Event Extraction + Chrona Adaptive Scheduling

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Folder Structure](#2-folder-structure)
3. [Module Reference](#3-module-reference)
4. [API Endpoints](#4-api-endpoints)
5. [Database Schema](#5-database-schema)
6. [LangGraph Agent Pipeline](#6-langgraph-agent-pipeline)
7. [Behavior Learning System](#7-behavior-learning-system)
8. [Redis Key Schema](#8-redis-key-schema)
9. [EDF Scheduler Deep Dive](#9-edf-scheduler-deep-dive)
10. [Repair Engine](#10-repair-engine)
11. [Frontend Integration Guide](#11-frontend-integration-guide)
12. [Adding New Task Types](#12-adding-new-task-types)
13. [Running Locally](#13-running-locally)
14. [Environment Variables](#14-environment-variables)
15. [Testing](#15-testing)

---

## 1. Architecture Overview

```
                    USER INPUT
                        |
            +-----------+-----------+
            |                       |
        Text/JSON              Image/PDF
            |                       |
            v                       v
    +-------+-------+     +--------+--------+
    | POST /api/    |     | POST /api/      |
    | process-event |     | process-event   |
    | (text path)   |     | (file path)     |
    +-------+-------+     +--------+--------+
            |                       |
            +-----------+-----------+
                        |
                        v
              +---------+----------+
              | GEMINI 2.5 FLASH   |
              | Extraction Engine  |
              | (EXTRACTION_PROMPT)|
              +--------+-----------+
                       |
                       v
              +--------+----------+
              | PYDANTIC VALIDATOR |  <--- Fix 1: No garbage in DB
              | ExtractedEvent     |
              +--------+----------+
                       |
              +--------+----------+
              | SOURCE HASH       |  <--- Fix 2: SHA-256 dedup
              | compute_source_   |
              | hash()            |
              +--------+----------+
                       |
                       v
              +--------+----------+
              | SUPABASE INSERT   |  <--- Fix 3: user_id injected
              | events table      |
              +-------------------+


        === CHRONA SCHEDULING PIPELINE ===

    +------------------+
    | POST /api/       |     Triggered after events exist
    | schedule/generate|     in the database
    +--------+---------+
             |
    +--------v---------+
    | BEHAVIOR PROFILE  |   Loaded from Redis (fast) or Supabase (fallback)
    | slot_weights {}   |   Morning/Afternoon/Evening/Night weights
    +--------+---------+
             |
    +--------v---------+
    | EDF SCHEDULER     |   Earliest Deadline First algorithm
    | (scheduling/     |   Assigns tasks to free time slots
    |  edf.py)         |   Behavior-weighted slot preference
    +--------+---------+
             |
    +--------v---------+
    | CONFLICT DETECT   |   SQL overlap query on schedule_items
    | (scheduling/     |   Index: idx_schedule_overlap
    |  conflict.py)    |
    +--------+---------+
             |
        +----+----+
        |         |
    conflict   no conflict
        |         |
        v         v
    +---+---+  +--+---+
    |REPAIR |  |INSERT|
    |ENGINE |  |INTO  |
    +---+---+  |SCHED |
        |      +------+
        v
    +---+----------+
    |REPAIR         |
    |PROPOSALS      |
    |3 strategies:  |
    | - Move today  |
    | - Split task  |
    | - Defer tmrw  |
    +---+----------+
        |
        v
    USER SELECTS → PATCH schedule_items
```

---

## 2. Folder Structure

```
neuravex_frontend/
├── backend/                          # Python FastAPI Backend
│   ├── server.py                     # Main app entry (all endpoints)
│   ├── requirements.txt              # Python dependencies
│   │
│   ├── scheduling/                   # Deterministic scheduling core
│   │   ├── __init__.py
│   │   ├── edf.py                    # EDF algorithm (Task, TimeSlot, EDFScheduler)
│   │   ├── conflict.py               # Overlap detection (detect_conflicts)
│   │   └── repair_engine.py          # Move/Split/Defer strategies (RepairEngine)
│   │
│   ├── behavior/                     # Behavioral learning system
│   │   ├── __init__.py
│   │   ├── profile.py                # BehaviorProfile + EMA weights + archetypes
│   │   └── predictor.py              # LightGBM stub (Phase 2 - post hackathon)
│   │
│   ├── agents/                       # LangGraph agent nodes
│   │   ├── __init__.py
│   │   ├── graph.py                  # Graph topology + run_pipeline() fallback
│   │   ├── ingestion.py              # Input classification node
│   │   ├── parser.py                 # Gemini extraction node
│   │   ├── enrichment.py             # key_topics → study blocks (killer feature)
│   │   ├── repair.py                 # Repair proposal generation node
│   │   └── explanation.py            # Natural language explanation node
│   │
│   ├── cache/                        # Redis abstraction
│   │   ├── __init__.py
│   │   └── redis_client.py           # Upstash Redis + in-memory fallback
│   │
│   └── db/                           # Database abstraction
│       ├── __init__.py
│       └── supabase_client.py        # Supabase client helper + REST wrapper
│
├── api/                              # Vercel Serverless Functions (production)
│   ├── health.js                     # Health check endpoint
│   └── process-event.js              # Event extraction (synced with server.py)
│
├── supabase/
│   └── migrations/
│       ├── 001_create_drafts_table.sql
│       ├── 002_archive_trigger.sql
│       └── 003_chrona_schema.sql     # Full Chrona schema (run in SQL editor)
│
├── src/                              # React Frontend
│   ├── App.jsx                       # Main app with routing + onboarding
│   ├── components/
│   │   ├── BehaviorOnboarding.jsx    # 5-step cold start wizard
│   │   ├── CalendarView.jsx          # Schedule display (for FullCalendar upgrade)
│   │   ├── RepairPanel.jsx           # Conflict repair proposal UI
│   │   ├── CategoryNode.jsx          # (existing) ReactFlow node
│   │   ├── EventNode.jsx             # (existing) ReactFlow node
│   │   ├── OpeningAnimation.jsx      # (existing) GSAP intro
│   │   └── PriorityCard.jsx          # (existing) Priority card
│   ├── pages/
│   │   ├── Upload.jsx                # (existing) Event upload
│   │   ├── Mindmap.jsx               # (existing) ReactFlow mindmap
│   │   ├── Priority.jsx              # (existing) Priority hub
│   │   └── Drafts.jsx                # (existing) Archived events
│   └── lib/
│       ├── supabase.js               # Supabase client init
│       └── priorityEngine.js         # (existing) Priority scoring
│
├── .env.example                      # All required env vars documented
├── CLAUDE.md                         # Updated project guide
├── CHRONA_ARCHITECTURE.md            # Full architecture analysis
└── READMECHRONA.md                   # THIS FILE
```

---

## 3. Module Reference

### 3.1 `scheduling/edf.py` — EDF Scheduler

**Classes:**

| Class | Purpose | Key Methods |
|-------|---------|-------------|
| `Task` | Represents a schedulable task | `urgency_score()`, `is_feasible()` |
| `TimeSlot` | A block of free time | `can_fit(task)`, `consume(task)`, `remaining_minutes()` |
| `Assignment` | A task assigned to a slot | `assigned_start`, `assigned_end` |
| `ScheduleResult` | Output of scheduling | `scheduled`, `impossible`, `deferred` |
| `EDFScheduler` | The scheduler | `schedule(tasks, slots, weights)` |

**Algorithm:**
1. Separate impossible tasks (urgency > 1.0)
2. Build min-heap ordered by (deadline, priority)
3. Sort slots by behavior weight (prefer user's productive times)
4. Greedily assign tasks to best-fitting slots
5. Unplaced feasible tasks go to `deferred`

**Urgency Score:** `duration_minutes / time_remaining_minutes`
- `< 0.5` → Safe, plenty of time
- `0.5 - 0.8` → Getting close, should schedule soon
- `0.8 - 1.0` → Urgent, barely feasible
- `> 1.0` → Impossible, not enough time

**Import:**
```python
from scheduling.edf import EDFScheduler, Task, TimeSlot
```

### 3.2 `scheduling/conflict.py` — Conflict Detection

**Functions:**

| Function | Signature | Returns |
|----------|-----------|---------|
| `detect_conflicts` | `(supabase_client, user_id, new_start, new_end, exclude_id=None)` | `list[dict]` |
| `compute_conflict_hash` | `(user_id, conflicts)` | `str` (MD5 hex) |

**SQL Logic:** `start_time < new_end AND end_time > new_start`
**Index Used:** `idx_schedule_overlap ON schedule_items (user_id, start_time, end_time)`

### 3.3 `scheduling/repair_engine.py` — Repair Engine

**Class: `RepairEngine`**

| Method | Purpose |
|--------|---------|
| `propose_repairs(disrupted_task, free_slots, supabase_client, user_id)` | Generate 2-3 repair proposals |

**Strategies:**

| # | Strategy | Risk | When Used |
|---|----------|------|-----------|
| 1 | `move_today` | Low | Free slot exists today that fits the task |
| 2 | `split` | Medium | Task >= 40min, 2+ free slots available |
| 3 | `defer_tomorrow` | Low-Critical | Deadline allows; becomes critical if deadline < 2 days |

**Safety Guard:** Never proposes a repair with urgency > 0.9

### 3.4 `behavior/profile.py` — Behavioral Learning

**Class: `BehaviorProfile`**

| Method | Purpose |
|--------|---------|
| `from_archetype(user_id, archetype)` | Cold start from predefined weights |
| `from_db_row(row)` | Reconstruct from Supabase/Redis |
| `update(task_type, sched_start, sched_end, actual_complete, confirmed_at)` | EMA weight update |
| `get_scheduling_weights()` | Get weights for EDF scheduler |
| `to_db_dict()` | Serialize for storage |

**Available Archetypes:**

| Archetype | Morning | Afternoon | Evening | Night |
|-----------|---------|-----------|---------|-------|
| `student_morning` | 0.75 | 0.55 | 0.40 | 0.25 |
| `student_night` | 0.25 | 0.45 | 0.65 | 0.75 |
| `student_balanced` | 0.50 | 0.55 | 0.55 | 0.40 |
| `developer` | 0.45 | 0.70 | 0.60 | 0.35 |
| `manager` | 0.70 | 0.50 | 0.40 | 0.20 |
| `freelancer` | 0.40 | 0.60 | 0.55 | 0.50 |

**Signal Strengths (EMA deltas):**

| Signal | Delta | Trigger |
|--------|-------|---------|
| `completed_on_time` | +0.12 | Finished within 15min grace window |
| `completed_late` | +0.04 | Finished, but after grace window |
| `skipped` | -0.18 | No actual_complete timestamp |
| `rescheduled` | 0.00 | Neutral — no reward, no penalty |
| `delayed_start` | -0.06 | Started late but completed |

### 3.5 `agents/graph.py` — LangGraph Pipeline

**Graph Topology:**
```
ingestion → parser → enrichment → scheduling → conflict_check
                                                    ↓         ↓
                                               (conflict)  (clear)
                                                    ↓         ↓
                                                 repair    explanation
                                                    ↓         ↓
                                                 explanation  ←┘
                                                    ↓
                                                   END
```

**Functions:**

| Function | Purpose |
|----------|---------|
| `build_chrona_graph(gemini, supabase, redis)` | Build LangGraph state machine |
| `run_pipeline(state, gemini, supabase, redis)` | Sequential fallback (no LangGraph needed) |

**State Schema (`ChronaState`):**

| Field | Type | Set By |
|-------|------|--------|
| `raw_input` | `str` | Caller |
| `input_bytes` | `bytes` | Caller |
| `input_type` | `str` | ingestion_node |
| `user_id` | `str` | Caller |
| `extracted_events` | `list[dict]` | parser_node |
| `enriched_tasks` | `list[dict]` | enrichment_node |
| `conflicts` | `list[dict]` | conflict_check_node |
| `repair_proposals` | `list[dict]` | repair_node |
| `behavior_weights` | `dict` | scheduling_node |
| `explanation` | `str` | explanation_node |
| `final_output` | `dict` | explanation_node |
| `errors` | `list[str]` | Any node |

### 3.6 `agents/enrichment.py` — The Killer Feature

**What it does:** Converts extracted events with `key_topics` + `estimated_prep_hours` into individual study blocks.

**Example:**
```
INPUT: "OS Final Exam" with topics=["scheduling","memory","deadlocks"], prep=12h
OUTPUT: [
  { title: "Study: scheduling",  duration: 90min, deadline: May 13 },
  { title: "Study: memory",      duration: 90min, deadline: May 13 },
  { title: "Study: deadlocks",   duration: 60min, deadline: May 14 },
  { title: "OS: Full Review",    duration: 60min, deadline: May 14 }
]
```

**Fallback:** If Gemini fails, creates one generic prep block per event.

### 3.7 `cache/redis_client.py` — Redis Abstraction

| Function | Returns |
|----------|---------|
| `get_redis_client()` | `redis.Redis` or `RedisFallback` |

**Fallback:** `RedisFallback` is an in-memory dict when Redis is unavailable. Not persistent.

---

## 4. API Endpoints

### Existing Endpoints (Neuravex)

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/api/health` | Health check + schema_version | No |
| POST | `/api/process-event` | Extract event from text/file via Gemini | Optional |
| POST | `/api/process-event-json` | Extract event from JSON body | Optional |

### New Endpoints (Chrona)

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/api/onboarding` | Save behavior profile from onboarding quiz | Optional* |
| POST | `/api/schedule/generate` | Run EDF scheduler on tasks + free slots | Optional* |
| POST | `/api/behavior/update` | Real-time behavioral signal (complete/skip) | Optional* |
| GET | `/api/repair/propose` | Get repair proposals for a task | Optional* |
| GET | `/api/schedule/items` | Get all schedule items for user | Optional* |
| GET | `/api/behavior/profile` | Get user's behavior profile | Optional* |

*Auth is optional during migration. Will become mandatory.

### Endpoint Details

#### POST `/api/onboarding`

```json
// REQUEST
{
  "archetype": "student_morning",
  "productive_time": "morning",
  "work_type": "student",
  "task_preference": "long_blocks",
  "study_hours": 6
}

// RESPONSE
{
  "status": "ok",
  "archetype": "student_morning",
  "profile": {
    "morning": 0.75,
    "afternoon": 0.55,
    "evening": 0.40,
    "night": 0.25
  },
  "available_archetypes": ["student_morning", "student_night", ...]
}
```

#### POST `/api/schedule/generate`

```json
// REQUEST
{
  "tasks": [
    {
      "id": "task-001",
      "title": "Study OS Chapter 1",
      "duration_minutes": 60,
      "deadline": "2026-04-15T09:00:00",
      "priority": 2,
      "task_type": "study_block",
      "event_id": "evt-001"
    }
  ],
  "free_slots": [
    {
      "start": "2026-04-10T09:00:00",
      "end": "2026-04-10T11:00:00"
    }
  ]
}

// RESPONSE
{
  "scheduled": 1,
  "assignments": [
    {
      "task_id": "task-001",
      "title": "Study OS Chapter 1",
      "start": "2026-04-10T09:00:00",
      "end": "2026-04-10T10:00:00",
      "task_type": "study_block"
    }
  ],
  "impossible": [],
  "deferred": []
}
```

#### POST `/api/behavior/update`

```json
// REQUEST — Mark task as completed
{
  "task_id": "uuid-of-schedule-item",
  "actual_complete": "2026-04-10T10:05:00",
  "confirmed_at": "2026-04-10T10:06:00"
}

// REQUEST — Mark task as skipped
{
  "task_id": "uuid-of-schedule-item",
  "actual_complete": null
}

// RESPONSE
{
  "signal": "completed_on_time",
  "updated_weights": {
    "morning": 0.762,
    "afternoon": 0.55,
    "evening": 0.40,
    "night": 0.25
  },
  "sample_count": 1
}
```

#### GET `/api/repair/propose?task_id=xxx`

```json
// RESPONSE
{
  "task_id": "uuid",
  "task_title": "Study OS",
  "proposals": [
    {
      "strategy": "move_today",
      "label": "Move to 03:00 PM today",
      "new_start": "2026-04-10T15:00:00",
      "new_end": "2026-04-10T16:00:00",
      "urgency_after": 0.35,
      "risk": "low"
    },
    {
      "strategy": "split",
      "label": "Split into 2 x 30-min blocks",
      "blocks": 2,
      "block_duration": 30,
      "urgency_after": 0.21,
      "risk": "medium",
      "note": "Cognitive load is higher with split sessions"
    },
    {
      "strategy": "defer_tomorrow",
      "label": "Move to tomorrow morning",
      "new_start": "2026-04-11T09:00:00",
      "new_end": "2026-04-11T10:00:00",
      "risk": "low",
      "urgency_after": 0.18,
      "warning": null
    }
  ]
}
```

---

## 5. Database Schema

### Tables

| Table | Purpose | Key Columns |
|-------|---------|-------------|
| `events` | Extracted events (Neuravex base) | title, category, severity_level, user_id, source_hash |
| `drafts` | Archived events (existing) | title, archived_at |
| `behavior_profiles` | Per-user EMA weights | slot_weights (JSONB), archetype, sample_count |
| `schedule_items` | Scheduled task blocks | start_time, end_time, status, priority, task_type |
| `behavior_events` | Append-only behavior log | event_type, actual_complete, metadata |
| `repair_proposals` | Repair history | proposals (JSONB), accepted, accepted_idx |
| `onboarding_responses` | Cold start answers | productive_time, work_type |

### Key Indexes

| Index | Table | Columns | Purpose |
|-------|-------|---------|---------|
| `idx_schedule_overlap` | schedule_items | (user_id, start_time, end_time) | Conflict detection |
| `idx_schedule_user_status` | schedule_items | (user_id, status) | Filtered queries |
| `idx_events_user_id` | events | (user_id) | User-scoped queries |
| `events_source_hash_unique` | events | (source_hash) | Deduplication |

### Migration File

**Location:** `supabase/migrations/003_chrona_schema.sql`

**How to run:**
1. Open Supabase Dashboard → SQL Editor
2. Click "New Query"
3. Paste the entire contents of `003_chrona_schema.sql`
4. Click "Run"
5. Verify: `SELECT table_name FROM information_schema.tables WHERE table_schema = 'public';`

---

## 6. LangGraph Agent Pipeline

### When to Use Which Function

| Scenario | Function to Call | File |
|----------|------------------|------|
| Full pipeline with LangGraph | `build_chrona_graph()` then `graph.invoke(state)` | `agents/graph.py` |
| Simple sequential pipeline | `run_pipeline(state, gemini, supabase, redis)` | `agents/graph.py` |
| Just extract events | `parser_node(state, gemini_client)` | `agents/parser.py` |
| Just generate study blocks | `enrichment_node(state, gemini_client)` | `agents/enrichment.py` |
| Just get repair proposals | `repair_node(state, gemini_client, redis)` | `agents/repair.py` |
| Just get explanation text | `explanation_node(state, gemini_client)` | `agents/explanation.py` |

### Node Dependencies

```
ingestion_node  — No external deps (pure routing logic)
parser_node     — Requires: google.generativeai (Gemini client)
enrichment_node — Requires: google.generativeai (Gemini client)
scheduling_node — Requires: Redis client, Supabase client, BehaviorProfile
conflict_check  — Requires: Supabase client
repair_node     — Requires: RepairEngine, optional Redis for caching
explanation_node— Requires: optional Gemini (has template fallback)
```

---

## 7. Behavior Learning System

### Update Flow

```
User taps "Complete" on CalendarView
        ↓
Frontend POSTs to /api/behavior/update
        ↓
Backend loads profile from Redis (fast)
        ↓
BehaviorProfile.update() applies EMA signal
        ↓
New weights written to Redis immediately
        ↓
Behavior event logged to Supabase (always)
        ↓
Profile synced to Supabase every 10 updates
        ↓
Next schedule/generate call uses updated weights
```

### Weight Update Formula

```
new_weight = old_weight + ALPHA * SIGNAL_STRENGTH[signal]
clamped to [0.1, 0.95]

ALPHA = 0.1 (learning rate)
```

### Slot Classification

| Hour Range | Slot Name |
|-----------|-----------|
| 5:00 - 11:59 | morning |
| 12:00 - 16:59 | afternoon |
| 17:00 - 20:59 | evening |
| 21:00 - 4:59 | night |

---

## 8. Redis Key Schema

| Key Pattern | Value | TTL | Purpose |
|-------------|-------|-----|---------|
| `chrona:profile:{user_id}` | BehaviorProfile JSON | 24h | Fast slot weight reads |
| `chrona:repair:{user_id}:{task_id}` | Repair proposals JSON | 5min | Cache expensive proposals |
| `chrona:lock:schedule:{user_id}` | "1" | 5s | Optimistic concurrency lock |
| `chrona:memory:{user_id}:{session}` | Agent state JSON | 1h | Agent conversation context |

**Free Tier:** Upstash 10K commands/day. Every Redis call has an in-memory fallback.

---

## 9. EDF Scheduler Deep Dive

### Input/Output Contract

```python
# INPUT
tasks: list[Task]        # Tasks with deadlines and durations
free_slots: list[TimeSlot]  # Available time blocks
behavior_weights: dict   # {"morning": 0.75, "afternoon": 0.5, ...}

# OUTPUT
ScheduleResult:
  .scheduled: list[Assignment]  # Successfully placed tasks
  .impossible: list[Task]       # Urgency > 1.0 (can't fit)
  .deferred: list[Task]         # Could fit but no slot available
```

### Extending the Scheduler

To add a new scheduling strategy:
1. Subclass `EDFScheduler`
2. Override `schedule()` method
3. Keep the same `ScheduleResult` return type
4. Register in `server.py` endpoint

---

## 10. Repair Engine

### Strategy Selection Logic

```
1. Find first free slot today that fits → propose "move_today"
2. If task >= 40 min AND 2+ slots → propose "split"
3. Calculate tomorrow urgency:
   - If urgency < 0.9 → propose "defer_tomorrow"
   - If urgency >= 0.9 → flag as "defer_impossible" (critical risk)
```

### Adding New Strategies

In `scheduling/repair_engine.py`:
1. Add your strategy to `propose_repairs()`
2. Include: `strategy`, `label`, `risk`, `urgency_after`
3. Respect `URGENCY_LIMIT = 0.9`
4. Update the RepairPanel frontend component to render it

---

## 11. Frontend Integration Guide

### API Calls Map

| Component | API Endpoint | When Called |
|-----------|-------------|-------------|
| `BehaviorOnboarding.jsx` | POST `/api/onboarding` | On quiz completion |
| `CalendarView.jsx` | GET `/api/schedule/items` | On mount + realtime |
| `CalendarView.jsx` | POST `/api/behavior/update` | On Complete/Skip buttons |
| `CalendarView.jsx` | GET `/api/repair/propose` | On Reschedule button |
| `RepairPanel.jsx` | GET `/api/repair/propose` | On mount (with task_id) |
| `Upload.jsx` (existing) | POST `/api/process-event` | On form submit |

### Supabase Realtime Setup

```javascript
// In CalendarView.jsx
const channel = supabase
    .channel('schedule-changes')
    .on('postgres_changes', {
        event: '*',
        schema: 'public',
        table: 'schedule_items',
    }, (payload) => {
        // Refresh schedule items
        fetchItems()
    })
    .subscribe()
```

**Note:** Requires enabling Realtime on `schedule_items` table in Supabase Dashboard.

---

## 12. Adding New Task Types

1. **Backend:** Add to `TYPE_COLORS` in `CalendarView.jsx`
2. **Scheduler:** The EDF scheduler is type-agnostic — any `task_type` string works
3. **Behavior:** `BehaviorProfile.update()` auto-creates `type_weights[new_type]`
4. **Frontend:** Add color mapping in `CalendarView.jsx` TYPE_COLORS
5. **Enrichment:** The enrichment agent only generates `study_block` and `revision` types; add new types to `ENRICHMENT_PROMPT`

---

## 13. Running Locally

### Prerequisites
- Python 3.10+
- Node.js 18+
- .env file with all variables (see .env.example)

### Start Backend

```bash
cd backend
pip install -r requirements.txt
uvicorn server:app --port 8000 --reload
```

### Start Frontend

```bash
npm install
npm run dev
```

### Verify

```bash
# Health check
curl http://localhost:8000/api/health

# Test onboarding
curl -X POST http://localhost:8000/api/onboarding \
  -H "Content-Type: application/json" \
  -d '{"archetype": "student_morning"}'

# Test schedule generation
curl -X POST http://localhost:8000/api/schedule/generate \
  -H "Content-Type: application/json" \
  -d '{
    "tasks": [{"id":"1","title":"Test","duration_minutes":60,"deadline":"2026-04-15T09:00:00"}],
    "free_slots": [{"start":"2026-04-10T09:00:00","end":"2026-04-10T11:00:00"}]
  }'
```

---

## 14. Environment Variables

| Variable | Required | Service | Purpose |
|----------|----------|---------|---------|
| `GEMINI_API_KEY` | Yes | Google AI | Gemini API access |
| `SUPABASE_URL` | Yes | Supabase | Database URL |
| `SUPABASE_ANON_KEY` | Yes | Supabase | Public anon key |
| `UPSTASH_REDIS_URL` | No* | Upstash | Redis host URL |
| `UPSTASH_REDIS_TOKEN` | No* | Upstash | Redis password |
| `SUPABASE_SERVICE_KEY` | No | Supabase | Server-side auth (future) |

*Redis is optional — system falls back to in-memory cache.

---

## 15. Testing

### Unit Tests (Self-Test Blocks)

Every Python module has `if __name__ == "__main__"` test blocks:

```bash
cd backend

# Test EDF Scheduler
python -c "from scheduling.edf import EDFScheduler; print('OK')"
python scheduling/edf.py

# Test Conflict Detection
python scheduling/conflict.py

# Test Repair Engine
python scheduling/repair_engine.py

# Test Behavior Profile
python behavior/profile.py

# Test Redis Client
python cache/redis_client.py

# Test Agent Pipeline (fallback mode)
python agents/graph.py
```

### Integration Tests (curl)

See [Running Locally](#13-running-locally) section.

### Frontend Verification

```bash
npm run dev
# Open http://localhost:5173
# 1. Complete onboarding (should show once)
# 2. Navigate to /schedule (should show empty)
# 3. Upload an event (existing flow)
# 4. Check /schedule for new items
```

---

## Quick Reference Card

```
┌─────────────────────────────────────────────────────────────┐
│  CHRONA QUICK REFERENCE                                      │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ONBOARD USER:     POST /api/onboarding                      │
│  EXTRACT EVENT:    POST /api/process-event                   │
│  SCHEDULE TASKS:   POST /api/schedule/generate               │
│  MARK COMPLETE:    POST /api/behavior/update (actual_complete)│
│  MARK SKIP:        POST /api/behavior/update (null)          │
│  GET REPAIRS:      GET  /api/repair/propose?task_id=xxx      │
│  GET SCHEDULE:     GET  /api/schedule/items                  │
│  GET PROFILE:      GET  /api/behavior/profile                │
│                                                              │
│  SCHEDULER:        scheduling/edf.py → EDFScheduler          │
│  CONFLICTS:        scheduling/conflict.py → detect_conflicts │
│  REPAIRS:          scheduling/repair_engine.py → RepairEngine│
│  BEHAVIOR:         behavior/profile.py → BehaviorProfile     │
│  AGENTS:           agents/graph.py → run_pipeline()          │
│  REDIS:            cache/redis_client.py → get_redis_client()│
│                                                              │
│  START BACKEND:    cd backend &&  │
│  START FRONTEND:   npm run dev             uvicorn server:app --reload                  │
│  RUN MIGRATION:    Paste 003_chrona_schema.sql in SQL editor │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

*Generated: 2026-04-08 | Chrona v1.0 | All free-tier services*
