# Chrona — Adaptive AI Time-Management Copilot

> **Drop your exam timetable, syllabus, or daily routine—or manage everything in natural language. Chrona extracts commitments with Gemini Vision, detects repeated tasks automatically, updates/reschedules/clears events directly from chat, and organizes your timeline into a 2D Pin-Board Mind Map and interactive 3D Card Deck.**

[![Live Demo](https://img.shields.io/badge/Live_Demo-Vercel-black?style=flat-square&logo=vercel)](https://neuravex-two.vercel.app)
[![Status](https://img.shields.io/badge/Status-v2.1_Live-emerald?style=flat-square)](https://github.com/Abpattar/Chrona)
[![Frontend](https://img.shields.io/badge/Frontend-React_18_+_Vite_+_Tailwind-sky?style=flat-square)](https://vitejs.dev/)
[![Serverless](https://img.shields.io/badge/Vercel_API-Node.js_Serverless-black?style=flat-square)](https://vercel.com/)
[![Backend](https://img.shields.io/badge/Local_Backend-FastAPI_+_Python_3.12-blue?style=flat-square)](https://fastapi.tiangolo.com/)
[![AI Engine](https://img.shields.io/badge/AI-Gemini_Multimodal_+_Groq_Pool-purple?style=flat-square)](https://ai.google.dev/)
[![License](https://img.shields.io/badge/License-MIT-slate?style=flat-square)](LICENSE)

🌐 **Live Vercel Application:** [https://neuravex-two.vercel.app](https://neuravex-two.vercel.app)

---

## 🌟 Key Features

### 1. ✧ Context-Aware Agentic Chat (`/chat`)
Chrona AI loads your live schedule and recent conversation history so you can perform **any** scheduling action in plain English:
- **Add Commitments:** Natural language task/event parsing with timezone-aware ISO scheduling.
- **Cancel & Reschedule In-Place:** Prompts like *"My exam on Nov 28 got cancelled, now it's on Dec 1"* update the existing event cleanly without leaving stale duplicates behind.
- **Bulk Clear & Range Actions:** Commands like *"Clear all tasks in December"*, *"Clear this month"*, or *"Remove all meetings tomorrow"* clear matching database rows in one shot.
- **Repeated-Task Protection:** Automatically detects exact and fuzzy repeats (including variants with/without course codes like `CS2205 Cloud Computing Exam` vs `Cloud Computing Exam`) and informs you instead of creating duplicates.

### 2. 📄 Multimodal Timetable & Document Extraction (`/chat`, `/week`)
- Drag-and-drop or attach (`📎`) timetable screenshots (PNG, JPG, JPEG, WebP), syllabi PDFs, or text/CSV files.
- Powered by Google Gemini Multimodal Vision with automatic model fallback (`gemini-flash-lite-latest`, `gemini-3-flash-preview`, `gemini-3.5-flash-lite`, `gemini-3.5-flash`) and client timezone normalization.
- Extracts titles, dates, start/end times, venues, priority, and movability in a single pass.

### 3. 📌 Pin-Board Mind Map & Week Grid (`/calendar`, `/week`)
- **Pin-Board Mind Map (`/calendar`):** Compact 2D wrapped pin-board grid built with ReactFlow—framed day columns with washi-tape headers, color-coded 3D push-pins (`Anchor`, `Priority`, `Fluid`, `Done`), dashed pin threads, search & status filters, drag-to-rearrange notes, one-click `Move` (auto-reschedule), `Done`, `💡 Explain`, and **`Clear Pins`**.
- **Week Grid (`/week`):** Clean 7-day calendar timeline view with quick AI command bar, file upload, and task actions.

### 4. 🃏 Interactive 3D Card Deck (`/dashboard`)
- Tactile 3D card stack (`Deck` mode) with bounded depth layering for any number of active tasks, smooth cycling, `Complete`, `Reschedule`, and `Defer` controls.
- Toggle to **Grid** mode for multi-card scanning and quick clear controls.
- Ambient glowing particle background inside a screen-fit `100dvh` layout.

### 5. 🧠 Dynamic AI Buffers & 3-Axis Stakes (Local FastAPI Engine)
- **3-Axis Stakes:** Classifies commitments as **Immovable** (exams, lectures, appointments), **Negotiable** (priority study/workouts), or **Fluid** (flexible tasks).
- **Dynamic Transition Buffers:** Reasons commute, prep, and cognitive decompression gaps without rigid hardcoded intervals (`backend/scheduling/transitions.py`).

---

## 🏗️ Architecture & Project Structure

```text
Chrona/
├── api/                              # Vercel Serverless Functions (Node.js)
│   ├── _lib/chrona.js                # Shared Gemini failover, Supabase helpers, operations engine & dedup
│   ├── chat.js                       # POST /api/chat (context-aware agentic chat + ops)
│   ├── chat/upload.js                # POST /api/chat/upload (multipart Gemini Vision/PDF extraction)
│   ├── context.js                    # GET  /api/context (today/week snapshot)
│   ├── events.js                     # GET  /api/events
│   ├── health.js                     # GET  /api/health
│   ├── process-event.js              # POST /api/process-event
│   ├── schedule/
│   │   ├── auto-reschedule.js        # POST /api/schedule/auto-reschedule
│   │   ├── clear.js                  # DELETE /api/schedule/clear
│   │   ├── items.js                  # GET  /api/schedule/items
│   │   └── items/[id].js             # PATCH/DELETE /api/schedule/items/:id
│   └── tasks/
│       ├── complete.js               # POST /api/tasks/complete
│       └── skip.js                   # POST /api/tasks/skip
├── backend/                          # Optional Local FastAPI + LangGraph Scheduling Engine
│   ├── server.py                     # FastAPI core application & EDF pipeline
│   ├── ai_router.py                  # Gemini Vision + Groq Key Pool router
│   ├── scheduling/                   # EDF, conflict detection, transitions, repair engine
│   ├── agents/                       # Parser, enrichment, explanation, repair agents
│   └── requirements.txt              # Python dependencies
├── src/
│   ├── components/
│   │   ├── CardStack.jsx             # Bounded 3D interactive card deck
│   │   ├── CalendarMindmap.jsx       # 2D Pin-Board Mind Map (ReactFlow)
│   │   ├── CalendarView.jsx          # 7-day Week Grid view
│   │   └── ParticleBackground.jsx    # Ambient glowing particle canvas
│   ├── pages/
│   │   ├── Dashboard.jsx             # Today view (Deck / Grid toggle)
│   │   ├── Chat.jsx                  # Chrona AI chat & multimodal upload
│   │   └── Calendar.jsx              # Mind Map & Week Grid wrapper
│   ├── lib/api.js                    # Unified API client (Vercel /api & local :8000)
│   └── App.jsx                       # Unified navigation & routing
├── supabase/migrations/              # PostgreSQL schema migrations
├── tests/                            # Pytest scheduling & E2E tests
├── run_servers.py                    # Local dual dev server launcher
└── vercel.json                       # Vercel Vite + /api routing config
```

---

## 🚀 Quick Start

### Prerequisites
- **Node.js**: v18+ (v20+ recommended)
- **Python**: 3.10+ (only required if running the local FastAPI backend)
- **Keys**: Google Gemini API key (`GEMINI_API_KEY`) and Supabase project (`SUPABASE_URL`, `SUPABASE_ANON_KEY`)

### 1. Clone & Install

```bash
git clone https://github.com/Abpattar/Chrona.git
cd Chrona

# Install frontend & Vercel serverless dependencies
npm install

# Optional: setup local Python FastAPI backend
cd backend
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
cd ..
```

### 2. Configure Environment Variables

Copy `.env.example` to `.env`:

```bash
cp .env.example .env
```

Set your credentials in `.env`:
```env
GEMINI_API_KEY=your_gemini_api_key_here
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your_supabase_anon_key

# Local frontend -> FastAPI backend (leave unset or /api on Vercel)
VITE_API_URL=http://localhost:8000

# Optional for local FastAPI Groq pool & Redis cache
GROQ_API_KEY=your_primary_groq_key
UPSTASH_REDIS_REST_URL=
UPSTASH_REDIS_REST_TOKEN=
```

### 3. Run Locally

- **With full local FastAPI + Vite (`:8000` + `:5173`):**
  ```bash
  python3 run_servers.py
  ```
- **Frontend only (pointing to local Vite dev server):**
  ```bash
  npm run dev
  ```

---

## 🌿 Branching & Collaboration Workflow

- `main` — Core full-stack & backend baseline.
- `ui` — Active frontend development branch (safe to work in `src/` without touching `api/` serverless handlers).

```bash
git fetch origin
git checkout ui
npm install
npm run dev
```

---

## 🌐 API Overview

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/health` | Service health check |
| `POST` | `/api/chat` | Context-aware chat: add, reschedule, cancel, complete, or clear tasks |
| `POST` | `/api/chat/upload` | Multipart timetable image / PDF / text extraction |
| `GET` | `/api/context` | Today, upcoming week, and summary stats |
| `GET` | `/api/schedule/items` | Deduplicated active scheduled items |
| `PATCH` / `DELETE` | `/api/schedule/items/:id` | Update or remove a specific item |
| `POST` | `/api/schedule/auto-reschedule` | Move a task (or unplaced tasks) to the next open slot |
| `DELETE` | `/api/schedule/clear` | Clear all scheduled items for the user |
| `POST` | `/api/tasks/complete` | Mark a task as completed |
| `POST` | `/api/tasks/skip` | Defer/skip a task |
| `GET` | `/api/events` | Fetch raw events from Supabase |

---

## 🧪 Testing & Build

```bash
# Frontend production build
npm run build

# Optional: backend scheduling engine tests
PYTHONPATH=backend backend/venv/bin/pytest tests/test_scheduling_engine.py
```

---

## 🚢 Deployment (Vercel)

```bash
npm run build
vercel --prod
```

Ensure `GEMINI_API_KEY`, `SUPABASE_URL`, and `SUPABASE_ANON_KEY` are configured in **Vercel → Project Settings → Environment Variables** for both Production and Preview environments.

---

## 📄 License

MIT License. Designed and developed with Chrona Adaptive AI.
