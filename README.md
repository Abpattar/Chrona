# Chrona — Intelligent Adaptive Scheduling Engine

> **Take a photo of your exam timetable. Chrona builds your entire study schedule, repairs it every day, and learns how you actually work.**

## What is Chrona?

Chrona is the evolution of Neuravex — an AI-powered event extraction system. It adds **intelligent scheduling**, **behavioral learning**, and **adaptive repair capabilities** on top of Neuravex's multimodal event extraction.

**Project Status: v1.0 Core Complete** — All backend modules, agent pipeline, database schema, and core frontend components implemented. Running locally with free-tier services ($0/month).

## Current Status

| Component | Status | Notes |
|-----------|--------|-------|
| **Event Extraction (Neuravex)** | ✅ Complete | Text, images, PDFs via Gemini Vision |
| **Agent Pipeline (LangGraph)** | ✅ Complete | ingestion → parser → enrichment → scheduling → repair → explanation |
| **EDF Scheduler** | ✅ Complete | Earliest Deadline First with behavior weights |
| **Behavior Learning** | ✅ Complete | EMA-based slot weights, onboarding cold-start |
| **Conflict Detection & Repair** | ✅ Complete | Move, split, defer strategies with explanations |
| **Study Block Generation** | ✅ Complete | Auto-generates 45-90min sessions from exam topics |
| **Database Schema** | ✅ Complete | Supabase migration `003_chrona_schema.sql` |
| **Frontend (React + Vite)** | ✅ Core | Calendar, Repair Panel, Onboarding Wizard |
| **Production Deploy** | 🔄 Ready | Vercel serverless functions configured |

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
| Frontend | React 18 + Vite + TailwindCSS | Free |
| Backend | Python 3.11 + FastAPI | Free |
| Production | Vercel Serverless Functions | Free tier |
| AI | Google Gemini 2.5 Flash / Flash-Lite | Free tier (20 req/day) |
| Database | Supabase PostgreSQL + RLS | Free tier (500MB) |
| Cache | Upstash Redis REST | Free tier (10K req/day) |
| Agents | LangGraph (open source) | Free |
| Scheduling | Pure Python EDF Algorithm | Free |

**Total monthly cost: $0** (all free tiers)

## Quick Start

### Prerequisites
- Python 3.10+
- Node.js 18+
- Supabase account (free at supabase.com)
- Google Gemini API key (free at ai.google.dev)

### Setup

```bash
# 1. Clone and install
git clone https://github.com/Abpattar/Chrona.git
cd Chrona
npm install
cd backend && pip install -r requirements.txt && cd ..

# 2. Configure environment
cp .env.example .env
# Edit .env with your API keys (see .env.example for all variables)

# 3. Run database migration
# Paste supabase/migrations/003_chrona_schema.sql in Supabase SQL Editor

# 4. Start backend
cd backend && uvicorn server:app --port 8000 --reload

# 5. Start frontend (new terminal)
npm run dev
```

### One-command dev start (both servers)
```bash
python3 run_servers.py
```

### Verify
```bash
curl http://localhost:8000/api/health
# {"status":"ok","service":"neuravex-chrona-backend"}

curl -X POST http://localhost:8000/api/schedule/generate \
  -H "X-User-ID: test-user" \
  -H "Content-Type: application/json" \
  -d '{"user_id": "test-user", "tasks": [{"title": "Study", "duration_minutes": 60, "deadline": "2026-10-30T15:00:00Z"}], "free_slots": [{"start": "2026-10-30T09:00:00Z", "end": "2026-10-30T12:00:00Z"}]}'
```

## Project Structure

```
Chrona/
├── backend/
│   ├── server.py                 # FastAPI app with all endpoints
│   ├── scheduling/               # EDF scheduler, conflict detection, repair
│   │   ├── edf.py               # Core EDF algorithm
│   │   ├── conflict.py          # Overlap detection
│   │   ├── repair_engine.py     # Move/split/defer strategies
│   │   └── validator.py         # Plan validation
│   ├── behavior/                 # Behavioral learning
│   │   ├── profile.py           # EMA weight updates, onboarding
│   │   └── predictor.py         # ML predictor stub (LightGBM)
│   ├── agents/                   # LangGraph pipeline nodes
│   │   ├── graph.py             # Pipeline orchestration
│   │   ├── ingestion.py         # Input type classification
│   │   ├── parser.py            # Gemini extraction (text/image/PDF)
│   │   ├── enrichment.py        # Study block generation from topics
│   │   ├── repair.py            # Natural language repair proposals
│   │   └── explanation.py       # Human-readable schedule explanations
│   ├── cache/                    # Redis abstraction with fallback
│   └── db/                       # Supabase client helpers
├── api/                          # Vercel serverless functions (production)
├── src/                          # React frontend
│   ├── components/               # CalendarView, RepairPanel, BehaviorOnboarding
│   ├── pages/                    # Upload, Mindmap, Priority, Drafts
│   └── lib/                      # Supabase client, priority engine
├── supabase/migrations/          # Database schema (003_chrona_schema.sql)
├── tests/                        # pytest + vitest
├── run_servers.py                # One-command dev server startup
├── .env.example                  # All required environment variables
└── docs/                         # Architecture & integration guides
```

## Documentation

| Document | Purpose |
|----------|---------|
| `README.md` | This file — project overview, setup, status |
| `docs/READMECHRONA.md` | **Complete backend integration guide** — all modules, APIs, data flows, testing |
| `docs/CHRONA_ARCHITECTURE.md` | Full architecture analysis and research rationale |
| `docs/CLAUDE.md` | Quick reference for AI agents working on the codebase |
| `.env.example` | All required environment variables |

## How It Works

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  Ingestion  │────▶│   Parser    │────▶│ Enrichment  │
│  (type cls) │     │  (Gemini)   │     │ (study gen) │
└─────────────┘     └─────────────┘     └──────┬──────┘
                                               │
                    ┌─────────────┐     ┌───────▼───────┐
                    │ Explanation │◀────│   Repair     │
                    │  (natural   │     │ (move/split/ │
                    │   language) │     │   defer)     │
                    └──────┬──────┘     └──────────────┘
                           │
                    ┌──────▼──────┐
                    │ Scheduling  │
                    │  (EDF +     │
                    │  behavior)  │
                    └─────────────┘
```

## Future Roadmap (v1.1 - v2.0)

### v1.1 — Polish & Reliability (Next 2 weeks)
- [ ] **Fix Gemini quota limits** — Implement request batching, caching, and fallback to local LLM (Ollama)
- [ ] **End-to-end tests** — Full pipeline tests with mocked Gemini responses
- [ ] **Rate limiting & retry logic** — Exponential backoff for all external APIs
- [ ] **WebSocket support** — Real-time schedule updates on frontend
- [ ] **Drag-and-drop rescheduling** — Visual calendar editing
- [ ] **Notification system** — Email/push for upcoming tasks and conflicts
- [ ] **Timezone support** — Full IANA timezone handling

### v1.2 — Intelligence Layer (Month 2)
- [ ] **LightGBM Predictor** — Replace stub with actual ML model for task duration/completion prediction
- [ ] **Vector embeddings** — Semantic search for "similar past tasks"
- [ ] **RL-based slot optimizer** — Reinforcement learning for schedule quality
- [ ] **Multi-user support** — Team calendars, shared resources, family mode
- [ ] **Calendar sync** — Google Calendar / Outlook 2-way sync via CalDAV

### v1.3 — Platform Expansion (Month 3)
- [ ] **Mobile app** — React Native / Expo wrapper
- [ ] **Browser extension** — Capture events from any webpage
- [ ] **Email parsing** — Forward emails to extract events
- [ ] **Voice input** — Whisper integration for voice notes

### v2.0 — Autonomous Agent (Month 6)
- [ ] **Proactive scheduling** — "I notice you have exams in 2 weeks, generating prep plan now"
- [ ] **Life integration** — Sleep tracking (Oura/Apple Health), focus sessions (Forest/Be Focused)
- [ ] **Collaborative AI** — Multiple agents negotiating shared resources
- [ ] **Plugin marketplace** — Custom schedulers, domain-specific extractors (medical, legal, etc.)

## Known Limitations

| Issue | Workaround | Fix Target |
|-------|------------|------------|
| Gemini free tier: 20 req/day | Cache extracted events, batch requests | v1.1 |
| No local LLM fallback | Add Ollama support for offline mode | v1.1 |
| Single-user auth only | Supabase RLS ready, need multi-tenant UI | v1.2 |
| No calendar sync | CalDAV/Google Calendar API stubs exist | v1.2 |
| Frontend state management | Migrate to TanStack Query / Zustand | v1.1 |

## Competitive Advantage

| Feature | Chrona | Motion | Reclaim | Clockwise |
|---------|--------|--------|---------|-----------|
| Multi-modal ingestion (photo/PDF) | ✅ Yes | ❌ No | ❌ No | ❌ No |
| Auto study plan from timetable | ✅ Yes | ❌ No | ❌ No | ❌ No |
| Prep hours estimation via AI | ✅ Yes | ❌ No | ❌ No | ❌ No |
| Topic-level sub-task generation | ✅ Yes | ❌ No | ❌ No | ❌ No |
| Behavior learning (EMA) | ✅ Yes | ✅ Yes | ✅ Yes | Partial |
| Explainable AI reasoning | ✅ Yes | ❌ No | ❌ No | ❌ No |
| Agent-based architecture | ✅ Yes | ❌ No | ❌ No | ❌ No |
| **Free tier** | ✅ Yes | $19/mo | Limited | Limited |

## License

Private — Hackathon Project / Personal Portfolio

---

*Built with Neuravex + Chrona Architecture | All free-tier services | Total cost: $0*