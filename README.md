# Chrona — Adaptive AI Time-Management Copilot

> **Drop your exam timetable, syllabus, or daily routine. Chrona extracts all commitments, reasons dynamic travel & cognitive fatigue buffers with AI, locks external anchors, and auto-schedules your entire timeline into a spatial mind map and interactive 3D card deck.**

[![Status](https://img.shields.io/badge/Status-v2.0_Adaptive_Live-emerald?style=flat-square)](https://github.com/Abpattar/Chrona)
[![Frontend](https://img.shields.io/badge/Frontend-React_18_+_Vite_+_Tailwind-sky?style=flat-square)](https://vitejs.dev/)
[![Backend](https://img.shields.io/badge/Backend-FastAPI_+_Python_3.12-blue?style=flat-square)](https://fastapi.tiangolo.com/)
[![AI Engine](https://img.shields.io/badge/AI-Gemini_Vision_+_Groq_Pool-purple?style=flat-square)](https://ai.google.dev/)
[![License](https://img.shields.io/badge/License-MIT-slate?style=flat-square)](LICENSE)

---

## 🌟 Key Features

### 1. 🧠 Dynamic AI Transition Buffers (Zero Hardcoding)
Traditional schedulers force rigid 15-minute or 30-minute static blocks. Chrona uses a dedicated AI reasoning engine (`scheduling/transitions.py`) to calculate contextual buffers based on:
- **Travel & Commute:** Evaluates departure locations, arrival venues, and traffic realities.
- **Preparation & Getting Ready:** Detects pre-event requirements (formal attire, equipment setup, revision).
- **Cognitive Recovery:** Allocates decompression intervals between demanding high-focus blocks (e.g. 3-hour university exams, presentations).
- Every scheduled task carries a dynamic `transition_reason` rendered directly in the timeline.

### 2. ◈ 3-Axis Stakes System
Every schedule item is evaluated across three core dimensions:
- **Immovable (Ancient Anchor):** Fixed external events (university exams, lectures, flights, weddings, appointments) that can never be shifted automatically.
- **Negotiable (Sacred Vow):** High-priority personal commitments (deep-work study sessions, project milestones, workouts) that adapt around immovable anchors.
- **Fluid (Wandering Thought):** Flexible backlog focus blocks placed dynamically into open circadian windows.

### 3. 📄 Multimodal Timetable & Document Extraction
- Direct drag-and-drop or attachment (`📎`) of timetable images (PNG, JPG, JPEG) and syllabi PDFs.
- Powered by Google Gemini Flash Multimodal Vision with high-speed IPv4 routing and automatic day-first date normalization (`DD-MM-YYYY` and `hh.mm a/p`).
- Automatically extracts start time, end time, venue, subject code, and priority in a single pass.

### 4. 🗺️ Spatial Mind Map & Week Grid
- **Spatial Mind Map (`/calendar`):** Interactive ReactFlow canvas mapping your commitments, dependencies, and dynamic transition nodes with live zoom, pan, and filter controls.
- **Week Grid (`/week`):** Full-width, clean 7-day timeline view highlighting overlaps, travel buffers, and circadian energy levels.

### 5. 🃏 Interactive 3D Card Deck (`/dashboard`)
- Tactile 3D card deck (`Deck` mode) with gesture swipe, completion marks, deferral, and instant AI re-alignment.
- Toggle into **Grid** view for rapid scanning and bulk management.
- Ambient glowing particle background with screen-fit `100dvh` responsive viewport.

---

## 🏗️ Architecture & Tech Stack

```
Chrona/
├── backend/
│   ├── server.py                 # FastAPI core application, REST APIs, and database sync
│   ├── ai_router.py              # Dual-engine router (Gemini Vision + Groq Key Pool)
│   ├── scheduling/
│   │   ├── edf.py                # Earliest Deadline First (EDF) conflict resolver
│   │   ├── transitions.py        # AI contextual transition buffer reasoner
│   │   ├── conflict.py           # Overlap and gap evaluation engine
│   │   └── repair_engine.py      # Move / Split / Defer repair strategies
│   ├── agents/
│   │   ├── parser.py             # Multimodal schedule extractor & 3-axis stakes tagger
│   │   ├── enrichment.py         # Automatic study block generator from exam topics
│   │   └── explanation.py        # Natural language explainability engine
│   └── requirements.txt          # Python dependencies
├── src/
│   ├── components/
│   │   ├── CardStack.jsx         # 3D interactive stacked task cards
│   │   ├── CalendarMindmap.jsx   # Spatial mind map timeline (ReactFlow)
│   │   ├── CalendarView.jsx      # Week grid schedule view
│   │   └── ParticleBackground.jsx# Ambient glowing particle canvas
│   ├── pages/
│   │   ├── Dashboard.jsx         # Today focus view (Deck / Grid toggle)
│   │   ├── Chat.jsx              # Chrona AI conversational assistant & file upload
│   │   ├── Calendar.jsx          # Mind map & week grid router
│   │   └── Onboarding.jsx        # First-time behavioral initialization wizard
│   ├── lib/api.js                # Frontend API client and Supabase bindings
│   └── App.jsx                   # Single-row unified navigation and routing
├── tests/
│   └── test_scheduling_engine.py # Pytest test suite for EDF, stakes, and transitions
├── run_servers.py                # One-command dual dev server launcher
└── vercel.json                   # Vercel deployment configuration
```

---

## 🚀 Quick Start

### Prerequisites
- **Node.js**: v18+ (v20+ recommended)
- **Python**: 3.10+ (tested on Python 3.12)
- **API Keys**: Google Gemini API key and/or Groq API key

### 1. Clone & Install

```bash
git clone https://github.com/Abpattar/Chrona.git
cd Chrona

# Install frontend dependencies
npm install

# Setup backend environment
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

Add your API credentials:
```env
# Gemini API Key (Multimodal timetable parsing & vision)
GEMINI_API_KEY=your_gemini_api_key_here

# Groq API Key (Fast conversational AI & transition reasoning)
GROQ_API_KEY=your_groq_api_key_here

# Optional: Supabase configuration (defaults to local fallback storage)
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_KEY=your_supabase_anon_key
```

### 3. Launch Development Servers

Run both the FastAPI backend (`:8000`) and Vite frontend (`:5173`) with a single command:

```bash
python3 run_servers.py
```

- **Frontend:** [http://localhost:5173](http://localhost:5173)
- **Backend API Docs:** [http://localhost:8000/docs](http://localhost:8000/docs)

---

## 🧪 Testing & Verification

Run the test suite to verify the scheduling engine, transition calculations, and conflict resolution:

```bash
# Run backend engine tests
PYTHONPATH=backend backend/venv/bin/pytest tests/test_scheduling_engine.py

# Run frontend build check
npm run build
```

---

## 🌐 API Overview

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/chat` | Natural language chat and schedule modification |
| `POST` | `/api/chat/upload` | Multimodal timetable, syllabus, or invite card ingestion |
| `GET` | `/api/schedule/items` | Retrieves all scheduled items and transition metadata |
| `POST` | `/api/schedule/complete/{id}` | Marks a scheduled item as fulfilled |
| `POST` | `/api/schedule/auto-reschedule`| Re-balances tasks using EDF and behavioral weights |
| `GET` | `/api/context` | Live context snapshot for dashboard and active focus block |
| `DELETE` | `/api/schedule/clear` | Clears scheduled items |

---

## 🚢 Deployment

### Deploying Frontend to Vercel

```bash
npm run build
vercel --prod
```

`vercel.json` is pre-configured with rewrites for Vite single-page application routing.

---

## 📄 License

MIT License. Designed and developed with Chrona Adaptive AI.
