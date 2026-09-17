# Chrona — Intelligent Adaptive Scheduling Engine

> Take a photo of your exam timetable. Chrona builds your entire study schedule, repairs it every day, and learns how you actually work.

## What is Chrona?

Chrona is the evolution of Neuravex — an AI-powered event extraction system. It adds intelligent scheduling, behavioral learning, and adaptive repair capabilities on top of Neuravex's multimodal event extraction.

## Features

### From Neuravex (Base)
- Upload photos, PDFs, or text descriptions of events
- AI extracts structured data: title, category, severity, complexity, prep hours, topics
- Automatic mindmap visualization
- Priority scoring and action recommendations

### New in Chrona v1.0
- **EDF Scheduler** — Earliest Deadline First algorithm that assigns tasks to your free time slots
- **Behavior Learning** — Learns when you're most productive (Meta-style EMA weight updates)
- **Smart Repair** — When conflicts happen, proposes 3 strategies: move, split, or defer
- **Study Block Generation** — Automatically creates study sessions from exam topics (killer feature)
- **Onboarding Wizard** — 5-question cold start that initializes your behavioral profile
- **Calendar View** — Real-time schedule display with complete/skip/reschedule actions
- **LangGraph Agent Pipeline** — Full agentic AI pipeline: ingestion → parsing → enrichment → scheduling → repair → explanation

## Tech Stack

| Layer | Technology | Cost |
|-------|-----------|------|
| Frontend | React + Vite + TailwindCSS | Free |
| Backend | Python FastAPI | Free |
| Production | Vercel Serverless | Free |
| AI | Google Gemini 2.5 Flash | Free tier |
| Database | Supabase PostgreSQL | Free tier |
| Cache | Upstash Redis | Free tier |
| Agents | LangGraph | Open source |
| Scheduling | EDF Algorithm | Pure Python |

**Total monthly cost: $0**

## Quick Start

### Prerequisites
- Python 3.10+
- Node.js 18+
- Supabase account (free)
- Google Gemini API key (free)

### Setup

```bash
# 1. Clone and install
git clone <repo-url>
cd neuravex_frontend
npm install
cd backend && pip install -r requirements.txt && cd ..

# 2. Configure environment
cp .env.example .env
# Edit .env with your API keys

# 3. Run database migration
# Paste supabase/migrations/003_chrona_schema.sql in Supabase SQL Editor

# 4. Start backend
cd backend && uvicorn server:app --port 8000 --reload

# 5. Start frontend (new terminal)
npm run dev
```

### Verify

```bash
curl http://localhost:8000/api/health
# {"status":"ok","service":"neuravex-backend","schema_version":"chrona-v1.0"}
```

## Project Structure

```
neuravex_frontend/
├── backend/
│   ├── server.py              # FastAPI app with all endpoints
│   ├── scheduling/            # EDF scheduler, conflict detection, repair
│   ├── behavior/              # Behavioral learning (EMA weights)
│   ├── agents/                # LangGraph pipeline nodes
│   ├── cache/                 # Redis abstraction with fallback
│   └── db/                    # Supabase client helpers
├── api/                       # Vercel serverless (production)
├── src/                       # React frontend
│   ├── components/            # UI components (Calendar, Repair, Onboarding)
│   ├── pages/                 # Route pages (Upload, Mindmap, Priority, Drafts)
│   └── lib/                   # Utilities (Supabase client, priority engine)
├── supabase/migrations/       # Database schema files
├── READMECHRONA.md            # Detailed backend integration guide
├── CHRONA_ARCHITECTURE.md     # Full architecture analysis
└── CLAUDE.md                  # AI agent workflow guide
```

## Documentation

| Document | Purpose |
|----------|---------|
| `READMECHRONA.md` | **Complete backend integration guide** — all modules, APIs, data flows, testing |
| `CHRONA_ARCHITECTURE.md` | Full architecture analysis and research rationale |
| `CLAUDE.md` | Quick reference for AI agents working on the codebase |
| `.env.example` | All required environment variables |

## Work Done (v1.0)

### Phase 0: Bug Fixes
- [x] Pydantic validation before Supabase writes
- [x] SHA-256 source hash deduplication
- [x] User auth context via Supabase JWT
- [x] Schema version on /api/health
- [x] Synced validation to Vercel serverless function
- [x] Fixed requirements.txt encoding issue

### Phase 1: Database Schema
- [x] Created `003_chrona_schema.sql` migration
- [x] Extended events table with user_id, source_hash
- [x] Added behavior_profiles, schedule_items, behavior_events tables
- [x] Added repair_proposals and onboarding_responses tables
- [x] Created performance indexes for conflict detection

### Phase 2: Backend Modules
- [x] EDF Scheduler (`scheduling/edf.py`)
- [x] Conflict Detection (`scheduling/conflict.py`)
- [x] Repair Engine (`scheduling/repair_engine.py`)
- [x] Behavior Profile with EMA learning (`behavior/profile.py`)
- [x] ML Predictor stub (`behavior/predictor.py`)
- [x] LangGraph Agent Graph (`agents/graph.py`)
- [x] Ingestion, Parser, Enrichment, Repair, Explanation nodes
- [x] Redis client with in-memory fallback (`cache/redis_client.py`)
- [x] Supabase client helper (`db/supabase_client.py`)

### Phase 3: API Endpoints
- [x] POST `/api/onboarding`
- [x] POST `/api/schedule/generate`
- [x] POST `/api/behavior/update`
- [x] GET `/api/repair/propose`
- [x] GET `/api/schedule/items`
- [x] GET `/api/behavior/profile`

### Phase 4: Frontend Components
- [x] BehaviorOnboarding.jsx — 5-step cold start wizard
- [x] CalendarView.jsx — Schedule display with actions
- [x] RepairPanel.jsx — Conflict repair proposal UI
- [x] Updated App.jsx with new routes and onboarding flow

### Phase 5-6: Environment & Dependencies
- [x] .env.example with all variables documented
- [x] Updated requirements.txt with Chrona dependencies
- [x] All __init__.py files for Python module structure

### Phase 7: Documentation
- [x] READMECHRONA.md — comprehensive backend integration guide
- [x] README.md — project overview and progress
- [x] Updated CLAUDE.md for Chrona architecture

## Competitive Advantage

| Feature | Chrona | Motion | Reclaim | Clockwise |
|---------|--------|--------|---------|-----------|
| Multi-modal ingestion (photo/PDF) | Yes | No | No | No |
| Auto study plan from timetable | Yes | No | No | No |
| Prep hours estimation via AI | Yes | No | No | No |
| Topic-level sub-task generation | Yes | No | No | No |
| Behavior learning | Yes | Yes | Yes | Partial |
| Explainable AI reasoning | Yes | No | No | No |
| Agent-based architecture | Yes | No | No | No |
| Free tier | Yes | $19/mo | Limited | Limited |

## License

Private — Hackathon Project

---

*Built with Neuravex + Chrona Architecture | All free-tier services | Total cost: $0*
