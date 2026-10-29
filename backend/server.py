"""
Neuravex + Chrona Backend API Server
Combines event extraction (Neuravex) with intelligent scheduling (Chrona).
Run with: uvicorn server:app --port 8000 --reload
"""
import os
import json
import tempfile
import hashlib
import requests
import google.generativeai as genai
from fastapi import FastAPI, HTTPException, Request, Depends
from fastapi.middleware.cors import CORSMiddleware
from datetime import datetime, timedelta, timezone
from typing import Optional, List
from pydantic import BaseModel

# ==============================
# CONFIGURATION
# ==============================

from dotenv import load_dotenv
load_dotenv('/home/neo/Codes/neuravex/.env')

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
GROQ_API_KEY = os.getenv("GROQ_API_KEY")
SUPABASE_URL = os.getenv("SUPABASE_URL")
SUPABASE_ANON_KEY = os.getenv("SUPABASE_ANON_KEY")
SUPABASE_SERVICE_KEY = os.getenv("SUPABASE_SERVICE_KEY")
UPSTASH_REDIS_URL = os.getenv("UPSTASH_REDIS_REST_URL")
UPSTASH_REDIS_TOKEN = os.getenv("UPSTASH_REDIS_REST_TOKEN")

if not all([SUPABASE_URL, SUPABASE_ANON_KEY, SUPABASE_SERVICE_KEY]):
    print("⚠️ WARNING: Missing necessary environment variables (.env)")

if not GEMINI_API_KEY and not GROQ_API_KEY:
    print("⚠️ WARNING: No AI API keys configured (GEMINI_API_KEY or GROQ_API_KEY)")

# Use available Gemini model (2.5-flash is deprecated)
GEMINI_MODEL = "gemini-flash-latest"
GROQ_MODEL = "llama-3.3-70b-versatile"

genai.configure(api_key=GEMINI_API_KEY) if GEMINI_API_KEY else None
groq_client = None
if GROQ_API_KEY:
    from groq import Groq
    groq_client = Groq(api_key=GROQ_API_KEY)

# ==============================
# FASTAPI APP
# ==============================

app = FastAPI(title="Neuravex + Chrona Backend", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ==============================
# SUPABASE CLIENTS
# ==============================

# Anon client (for user-scoped reads via PostgREST with RLS)
ANON_HEADERS = {
    "apikey": SUPABASE_ANON_KEY,
    "Authorization": f"Bearer {SUPABASE_ANON_KEY}",
    "Content-Type": "application/json",
}

# Service client (for admin writes bypassing RLS)
SERVICE_HEADERS = {
    "apikey": SUPABASE_SERVICE_KEY,
    "Authorization": f"Bearer {SUPABASE_SERVICE_KEY}",
    "Content-Type": "application/json",
    "Prefer": "return=representation",
}

def supabase_get(table: str, params: dict = None, headers: dict = ANON_HEADERS) -> requests.Response:
    url = f"{SUPABASE_URL}/rest/v1/{table}"
    return requests.get(url, headers=headers, params=params)

def supabase_post(table: str, data: dict, headers: dict = SERVICE_HEADERS) -> requests.Response:
    url = f"{SUPABASE_URL}/rest/v1/{table}"
    return requests.post(url, headers=headers, json=data)

def supabase_patch(table: str, data: dict, params: dict = None, headers: dict = SERVICE_HEADERS) -> requests.Response:
    url = f"{SUPABASE_URL}/rest/v1/{table}"
    return requests.patch(url, headers=headers, params=params, json=data)

def supabase_delete(table: str, params: dict = None, headers: dict = SERVICE_HEADERS) -> requests.Response:
    url = f"{SUPABASE_URL}/rest/v1/{table}"
    return requests.delete(url, headers=headers, params=params)

def _ensure_utc(dt: datetime) -> datetime:
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)

def _now_utc() -> datetime:
    return datetime.now(timezone.utc)

# ==============================
# PYDANTIC MODELS
# ==============================

class TaskCreate(BaseModel):
    id: Optional[str] = None
    title: str
    duration_minutes: int
    deadline: datetime
    priority: int = 3
    flexibility: bool = True
    task_type: str = "task"
    event_id: Optional[str] = None
    fixed_start: Optional[datetime] = None
    fixed_end: Optional[datetime] = None

class FixedCommitmentCreate(BaseModel):
    title: str
    start_time: datetime
    end_time: datetime
    user_id: str

class ScheduleGenerateRequest(BaseModel):
    user_id: str
    tasks: List[TaskCreate]
    free_slots: List[dict]  # {start, end} ISO strings
    behavior_weights: Optional[dict] = None
    fixed_items: Optional[List[TaskCreate]] = None

class ScheduleAcceptRequest(BaseModel):
    user_id: str
    version: str
    scheduled_items: List[dict]  # [{task_id, start_time, end_time, title, task_type, priority, flexibility, deadline}]

class RepairProposeRequest(BaseModel):
    user_id: str
    disrupted_task: dict
    free_slots: List[dict]

class RepairApplyRequest(BaseModel):
    user_id: str
    proposal: dict

class BehaviorUpdateRequest(BaseModel):
    user_id: str
    task_id: str
    signal: str  # completed_on_time, skipped, delayed, rescheduled
    metadata: Optional[dict] = None

class OnboardingRequest(BaseModel):
    user_id: str
    productive_time: str
    work_type: str
    task_preference: str
    study_hours: int = 4

class CommandRequest(BaseModel):
    user_id: str
    command: str

# ==============================
# REDIS CACHING (Behavior Profiles)
# ==============================

async def redis_get(key: str) -> Optional[str]:
    if not UPSTASH_REDIS_URL or not UPSTASH_REDIS_TOKEN:
        return None
    try:
        resp = requests.get(
            f"{UPSTASH_REDIS_URL}/get/{key}",
            headers={"Authorization": f"Bearer {UPSTASH_REDIS_TOKEN}"}
        )
        if resp.status_code == 200:
            data = resp.json()
            # Upstash returns {"value": "stored_string", "ex": ttl}
            return data.get("value")
    except Exception:
        pass
    return None

async def redis_set(key: str, value: str, ex: int = 86400) -> bool:
    if not UPSTASH_REDIS_URL or not UPSTASH_REDIS_TOKEN:
        return False
    try:
        # Ensure value is stored as raw string (Upstash expects JSON in body)
        resp = requests.post(
            f"{UPSTASH_REDIS_URL}/set/{key}",
            headers={"Authorization": f"Bearer {UPSTASH_REDIS_TOKEN}", "Content-Type": "application/json"},
            json={"value": value, "ex": ex}
        )
        return resp.status_code == 200
    except Exception:
        return False

# ==============================
# BEHAVIOR PROFILE LOGIC
# ==============================

ARCHETYPES = {
    "student_balanced": {"morning": 0.5, "afternoon": 0.5, "evening": 0.5, "night": 0.5},
    "early_bird": {"morning": 1.0, "afternoon": 0.6, "evening": 0.3, "night": 0.1},
    "night_owl": {"morning": 0.2, "afternoon": 0.5, "evening": 0.8, "night": 1.0},
    "afternoon_focus": {"morning": 0.3, "afternoon": 1.0, "evening": 0.5, "night": 0.2},
}

EMA_ALPHA = 0.3  # smoothing factor

async def get_behavior_profile(user_id: str) -> dict:
    # Try Redis cache first
    cached = await redis_get(f"behavior:profile:{user_id}")
    if cached:
        try:
            return json.loads(cached)
        except Exception:
            pass
    
    # Fallback to Supabase
    resp = supabase_get("behavior_profiles", {"user_id": f"eq.{user_id}", "limit": 1})
    if resp.status_code == 200 and resp.json():
        profile = resp.json()[0]
        # Cache it
        await redis_set(f"behavior:profile:{user_id}", json.dumps(profile))
        return profile
    
    # Create default
    return {
        "user_id": user_id,
        "slot_weights": {"morning": 0.5, "afternoon": 0.5, "evening": 0.5, "night": 0.5},
        "type_weights": {},
        "avg_delay_min": 0.0,
        "sample_count": 0,
        "archetype": "student_balanced",
        "last_updated": _now_utc().isoformat()
    }

async def update_behavior_profile(user_id: str, signal: str, task_duration: int = 60, metadata: dict = None) -> dict:
    profile = await get_behavior_profile(user_id)
    
    # Determine time slot of the task (approximate from metadata or use current time)
    slot = "morning"  # default
    if metadata and "slot" in metadata:
        slot = metadata["slot"]
    
    # EMA update for the relevant slot
    current = profile["slot_weights"].get(slot, 0.5)
    if signal == "completed_on_time":
        new_val = current + EMA_ALPHA * (1.0 - current)
    elif signal == "skipped":
        new_val = current + EMA_ALPHA * (0.0 - current)
    elif signal == "delayed":
        new_val = current + EMA_ALPHA * (0.5 - current)
    else:
        new_val = current
    
    profile["slot_weights"][slot] = max(0.1, min(1.0, new_val))
    profile["sample_count"] += 1
    profile["last_updated"] = _now_utc().isoformat()
    
    # Persist to Supabase
    supabase_patch("behavior_profiles", {
        "slot_weights": profile["slot_weights"],
        "sample_count": profile["sample_count"],
        "last_updated": profile["last_updated"]
    }, params={"user_id": f"eq.{user_id}"})
    
    # Update cache
    await redis_set(f"behavior:profile:{user_id}", json.dumps(profile))
    
    return profile

# ==============================
# GEMINI EXTRACTION (Enhanced for Chrona)
# ==============================

CHRONA_EXTRACTION_PROMPT = """
You are an event and task extraction engine for a personal scheduling assistant.

Analyze the input and extract ALL tasks/events with rich metadata.

Return JSON array of objects in this exact format:
[
  {
    "title": "",
    "category": "",           // exam, hackathon, assignment, meeting, personal, reminder, study, workout, etc.
    "venue": "",
    "event_datetime": "",     // ISO format: YYYY-MM-DDTHH:MM:SS
    "duration_minutes": 60,   // estimated duration
    "priority": 3,            // 1=critical, 2=important, 3=normal, 4=optional
    "flexibility": true,      // can this be moved?
    "task_type": "task",      // task, fixed, study_block, prep_block
    "key_topics": [],         // extracted topics/keywords
    "is_fixed": false,        // explicit user-specified time?
    "fixed_start": null,      // if is_fixed: ISO datetime
    "fixed_end": null,
    "earliest_start": null,   // optional: user says "not before X"
    "prep_hours": 0           // for exams/events: prep time needed
  }
]

Rules:
- category: exam, hackathon, assignment, meeting, personal, reminder, study, workout, errand, etc.
- Convert relative times using today's date (%s):
  "today" → today's date
  "tomorrow" → tomorrow's date
- Convert datetime to ISO format: YYYY-MM-DDTHH:MM:SS
- If user says "at 3pm" or "by Friday", capture as fixed_start/fixed_end or deadline
- If user says "need 3 hours for assignment", set duration_minutes=180, prep_hours=3
- Extract key_topics from the text
- Return ONLY valid JSON array.
- If a field is missing, use sensible defaults or null.
""" % datetime.now().strftime("%Y-%m-%d")

def extract_with_gemini(text: str) -> list:
    """Extract events using Gemini with Groq fallback."""
    # Try Gemini first
    if GEMINI_API_KEY:
        try:
            model = genai.GenerativeModel(model_name=GEMINI_MODEL)
            prompt = f"{CHRONA_EXTRACTION_PROMPT}\n\nInput:\n{text}"
            response = model.generate_content(
                prompt,
                generation_config={"response_mime_type": "application/json"},
            )
            return json.loads(response.text)
        except Exception as e:
            print(f"⚠️ Gemini extraction failed: {e}, trying Groq...")
    
    # Fallback to Groq
    if groq_client:
        try:
            prompt = f"{CHRONA_EXTRACTION_PROMPT}\n\nInput:\n{text}"
            response = groq_client.chat.completions.create(
                model=GROQ_MODEL,
                messages=[{"role": "user", "content": prompt}],
                response_format={"type": "json_object"},
                temperature=0.1,
                max_tokens=4096,
            )
            return json.loads(response.choices[0].message.content)
        except Exception as e:
            print(f"⚠️ Groq extraction failed: {e}")
    
    raise Exception("Both Gemini and Groq extraction failed")

# ==============================
# AGENT PIPELINE (handles text, images, PDFs)
# ==============================

from agents.graph import run_pipeline
from agents.ingestion import ingestion_node
from agents.parser import parser_node
from agents.enrichment import enrichment_node

# ==============================
# SCHEDULER INTEGRATION
# ==============================

from scheduling.edf import EDFScheduler, Task, TimeSlot
from scheduling.repair_engine import RepairEngine
from scheduling.validator import validate_plan

# ==============================
# AUTH DEPENDENCY (simple user_id from header for now)
# ==============================

async def get_user_id(request: Request) -> str:
    # In production, validate JWT and extract user_id
    # For now, accept from header or use a default for single-user mode
    user_id = request.headers.get("X-User-ID")
    if not user_id:
        # Single-user fallback - use a consistent UUID
        return "00000000-0000-0000-0000-000000000001"
    return user_id

# ==============================
# API ENDPOINTS
# ==============================

@app.get("/api/health")
async def health_check():
    return {"status": "ok", "service": "neuravex-chrona-backend"}

# ---- NEURAVEX: Event Extraction ----

@app.post("/api/process-event")
async def process_event(
    request: Request,
    user_id: str = Depends(get_user_id),
):
    """Process an event/task from text or file upload using Chrona agent pipeline.
    Accepts either multipart/form-data (file + text) or application/json ({text: "..."}).
    Handles text, images (JPEG/PNG), and PDFs using Gemini vision."""
    try:
        content_type = request.headers.get("content-type", "")

        # Prepare initial state for the agent pipeline
        initial_state = {
            "raw_input": None,
            "input_bytes": None,
            "input_type": "",
            "user_id": user_id,
            "extracted_events": [],
            "extraction_error": None,
            "enriched_tasks": [],
            "schedule_result": None,
            "conflicts": [],
            "repair_proposals": [],
            "accepted_repair": None,
            "behavior_weights": {},
            "explanation": "",
            "final_output": None,
            "errors": [],
        }

        if "application/json" in content_type:
            # JSON body (text-only)
            body = await request.json()
            text = body.get("text", "").strip()
            if not text:
                raise HTTPException(status_code=400, detail="No text provided")
            initial_state["raw_input"] = text

        elif "multipart/form-data" in content_type:
            # FormData (file + optional text)
            form = await request.form()
            file = form.get("file")
            text = form.get("text", "").strip() if form.get("text") else ""

            if file and hasattr(file, "filename") and file.filename:
                content = await file.read()
                initial_state["input_bytes"] = content
                # Also include filename as context
                if text:
                    initial_state["raw_input"] = f"File: {file.filename}. Context: {text}"
                else:
                    initial_state["raw_input"] = f"File: {file.filename}"
            elif text:
                initial_state["raw_input"] = text
            else:
                raise HTTPException(status_code=400, detail="No text or file provided")
        else:
            raise HTTPException(status_code=400, detail="Unsupported content type")

        # Run the agent pipeline (ingestion -> parser -> enrichment)
        # This properly handles images, PDFs, and text using Gemini vision
        final_state = run_pipeline(initial_state, genai, None, None)

        # Check for errors
        if final_state.get("errors"):
            for err in final_state["errors"]:
                print(f"⚠️ Pipeline error: {err}")

        extracted_data = final_state.get("extracted_events", [])
        if not extracted_data:
            raise HTTPException(status_code=400, detail="No events extracted from input")

        # Insert each extracted event/task
        results = []
        for item in extracted_data:
            item["user_id"] = user_id
            item["source_hash"] = hashlib.sha256(f"{user_id}:{item.get('title','')}:{item.get('event_datetime','')}".encode()).hexdigest()[:32]
            resp = supabase_post("events", item)
            if resp.status_code in [200, 201]:
                results.append(resp.json()[0])
            else:
                print(f"❌ Insert failed: {resp.text}")

        return {"success": True, "events": results, "explanation": final_state.get("explanation", "")}

    except HTTPException:
        raise
    except Exception as e:
        print(f"❌ Error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

# ---- CHRONA: Scheduling ----

@app.post("/api/schedule/generate")
async def schedule_generate(request: ScheduleGenerateRequest, user_id: str = Depends(get_user_id)):
    """Generate a proposed schedule using EDF + validator. Returns plan with version token."""
    if request.user_id != user_id:
        raise HTTPException(status_code=403, detail="User mismatch")

    # Load behavior profile
    profile = await get_behavior_profile(user_id)
    weights = request.behavior_weights or profile["slot_weights"]

    # Convert tasks
    tasks = []
    for t in request.tasks:
        task = Task(
            id=t.id or f"task-{len(tasks)}",
            title=t.title,
            duration_minutes=t.duration_minutes,
            deadline=_ensure_utc(t.deadline),
            priority=t.priority,
            flexibility=t.flexibility,
            task_type=t.task_type,
            event_id=t.event_id,
            fixed_start=_ensure_utc(t.fixed_start) if t.fixed_start else None,
            fixed_end=_ensure_utc(t.fixed_end) if t.fixed_end else None,
        )
        tasks.append(task)

    # Convert fixed items
    fixed_items = []
    if request.fixed_items:
        for t in request.fixed_items:
            task = Task(
                id=t.id or f"fixed-{len(fixed_items)}",
                title=t.title,
                duration_minutes=t.duration_minutes,
                deadline=_ensure_utc(t.deadline),
                priority=t.priority,
                flexibility=t.flexibility,
                task_type=t.task_type,
                event_id=t.event_id,
                fixed_start=_ensure_utc(t.fixed_start) if t.fixed_start else None,
                fixed_end=_ensure_utc(t.fixed_end) if t.fixed_end else None,
            )
            fixed_items.append(task)

    # Convert free slots
    free_slots = []
    for s in request.free_slots:
        free_slots.append(TimeSlot(
            start=_ensure_utc(datetime.fromisoformat(s["start"])),
            end=_ensure_utc(datetime.fromisoformat(s["end"]))
        ))

    # Run scheduler
    scheduler = EDFScheduler()
    result = scheduler.schedule(tasks, free_slots, weights, fixed_items)

    # Build proposed plan
    proposed = []
    for a in result.scheduled:
        proposed.append({
            "task_id": a.task.id,
            "title": a.task.title,
            "start_time": a.assigned_start.isoformat(),
            "end_time": a.assigned_end.isoformat(),
            "task_type": a.task.task_type,
            "priority": a.task.priority,
            "flexibility": a.task.flexibility,
            "deadline": a.task.deadline.isoformat() if a.task.deadline else None,
        })

    # Version token for optimistic locking
    version = hashlib.sha256(f"{user_id}:{datetime.now().isoformat()}".encode()).hexdigest()[:16]

    return {
        "success": True,
        "version": version,
        "proposed": proposed,
        "impossible": [{"task_id": t.id, "title": t.title, "reason": "deadline passed or no valid slot"} for t in result.impossible],
        "deferred": [{"task_id": t.id, "title": t.title} for t in result.deferred],
    }

@app.post("/api/schedule/accept")
async def schedule_accept(request: ScheduleAcceptRequest, user_id: str = Depends(get_user_id)):
    """Re-validate and commit the accepted plan atomically."""
    if request.user_id != user_id:
        raise HTTPException(status_code=403, detail="User mismatch")

    # Re-validate against current database state
    plan = []
    for item in request.scheduled_items:
        plan.append({
            "start": item["start_time"],
            "end": item["end_time"],
            "task_id": item.get("task_id", "unknown"),
            "deadline": item.get("deadline"),
        })

    fixed_items = []  # Load current fixed items for this user
    # For now, skip fixed items re-validation (would need to load from DB)
    windows = []  # Working windows - could be from user profile

    validation = validate_plan(plan, fixed_items, windows)
    if not validation["valid"]:
        raise HTTPException(status_code=409, detail={"message": "Plan validation failed", "errors": validation["errors"]})

    # Check version freshness (simplified - in production, compare with stored version)
    # For now, just commit

    # Insert schedule_items
    inserted = []
    for item in request.scheduled_items:
        data = {
            "user_id": user_id,
            "event_id": item.get("event_id"),
            "title": item["title"],
            "task_type": item.get("task_type", "task"),
            "start_time": item["start_time"],
            "end_time": item["end_time"],
            "deadline": item.get("deadline"),
            "priority": item.get("priority", 3),
            "flexibility": item.get("flexibility", True),
            "status": "scheduled",
        }
        resp = supabase_post("schedule_items", data)
        if resp.status_code in [200, 201]:
            inserted.append(resp.json()[0])
        else:
            print(f"❌ Insert failed: {resp.text}")

    return {"success": True, "inserted": inserted, "message": "Schedule committed"}

@app.get("/api/schedule/items")
async def schedule_items(user_id: str = Depends(get_user_id)):
    """Get persisted schedule items for the user."""
    resp = supabase_get("schedule_items", {"user_id": f"eq.{user_id}", "order": "start_time.asc"})
    if resp.status_code == 200:
        return {"items": resp.json()}
    raise HTTPException(status_code=500, detail=resp.text)

@app.post("/api/schedule/fixed")
async def schedule_fixed_create(item: FixedCommitmentCreate, user_id: str = Depends(get_user_id)):
    """Create a fixed commitment."""
    if item.user_id != user_id:
        raise HTTPException(status_code=403, detail="User mismatch")
    
    data = {
        "user_id": user_id,
        "title": item.title,
        "start_time": item.start_time.isoformat(),
        "end_time": item.end_time.isoformat(),
    }
    resp = supabase_post("schedule_items", {**data, "task_type": "fixed", "status": "fixed", "flexibility": False})
    if resp.status_code in [200, 201]:
        return {"success": True, "item": resp.json()[0]}
    raise HTTPException(status_code=500, detail=resp.text)

@app.delete("/api/schedule/fixed")
async def schedule_fixed_delete(item_id: str, user_id: str = Depends(get_user_id)):
    """Delete a fixed commitment."""
    resp = supabase_delete("schedule_items", {"id": f"eq.{item_id}", "user_id": f"eq.{user_id}", "task_type": "eq.fixed"})
    if resp.status_code in [200, 204]:
        return {"success": True}
    raise HTTPException(status_code=500, detail=resp.text)

# ---- CHRONA: Repair ----

@app.post("/api/repair/propose")
async def repair_propose(request: RepairProposeRequest, user_id: str = Depends(get_user_id)):
    """Generate repair proposals for a disrupted task."""
    if request.user_id != user_id:
        raise HTTPException(status_code=403, detail="User mismatch")
    
    engine = RepairEngine()
    proposals = engine.propose_repairs(request.disrupted_task, request.free_slots)
    return {"proposals": proposals}

@app.post("/api/repair/apply")
async def repair_apply(request: RepairApplyRequest, user_id: str = Depends(get_user_id)):
    """Apply a repair proposal (validates and persists)."""
    if request.user_id != user_id:
        raise HTTPException(status_code=403, detail="User mismatch")
    
    proposal = request.proposal
    
    # Validate the proposal
    plan = [{
        "start": proposal["new_start"],
        "end": proposal["new_end"],
        "task_id": proposal.get("task_id", "unknown"),
    }]
    validation = validate_plan(plan, [], [])
    if not validation["valid"]:
        raise HTTPException(status_code=409, detail={"message": "Invalid repair", "errors": validation["errors"]})
    
    # Update the schedule_item
    data = {
        "start_time": proposal["new_start"],
        "end_time": proposal["new_end"],
        "status": "rescheduled",
    }
    resp = supabase_patch("schedule_items", data, params={"id": f"eq.{proposal.get('task_id')}", "user_id": f"eq.{user_id}"})
    if resp.status_code in [200, 204]:
        # Log repair
        supabase_post("repair_proposals", {
            "user_id": user_id,
            "conflict_hash": proposal.get("conflict_hash"),
            "proposals": json.dumps([proposal]),
            "accepted": True,
            "accepted_idx": 0,
        })
        return {"success": True, "message": "Repair applied"}
    raise HTTPException(status_code=500, detail=resp.text)

# ---- CHRONA: Behavior ----

@app.post("/api/behavior/update")
async def behavior_update(request: BehaviorUpdateRequest, user_id: str = Depends(get_user_id)):
    """Update behavior profile from a task signal."""
    if request.user_id != user_id:
        raise HTTPException(status_code=403, detail="User mismatch")
    
    profile = await update_behavior_profile(user_id, request.signal, metadata=request.metadata)
    
    # Log behavior event
    supabase_post("behavior_events", {
        "user_id": user_id,
        "task_id": request.task_id,
        "event_type": request.signal,
        "metadata": request.metadata or {},
        "logged_at": _now_utc().isoformat(),
    })
    
    return {"success": True, "profile": profile}

@app.get("/api/behavior/profile")
async def behavior_profile(user_id: str = Depends(get_user_id)):
    """Get current behavior profile."""
    profile = await get_behavior_profile(user_id)
    return {"profile": profile}

@app.post("/api/onboarding")
async def onboarding(request: OnboardingRequest, user_id: str = Depends(get_user_id)):
    """Complete onboarding and initialize behavior profile."""
    if request.user_id != user_id:
        raise HTTPException(status_code=403, detail="User mismatch")
    
    # Determine archetype
    archetype = "student_balanced"
    if request.productive_time == "morning":
        archetype = "early_bird"
    elif request.productive_time == "night":
        archetype = "night_owl"
    elif request.productive_time == "afternoon":
        archetype = "afternoon_focus"
    
    profile = {
        "user_id": user_id,
        "slot_weights": ARCHETYPES[archetype],
        "type_weights": {},
        "avg_delay_min": 0.0,
        "sample_count": 0,
        "archetype": archetype,
        "last_updated": _now_utc().isoformat(),
    }
    
    # Persist
    supabase_post("behavior_profiles", profile)
    supabase_post("onboarding_responses", request.dict())
    
    # Cache
    await redis_set(f"behavior:profile:{user_id}", json.dumps(profile))
    
    return {"success": True, "profile": profile}

# ---- CHRONA: Command ----

@app.post("/api/command")
async def command(request: CommandRequest, user_id: str = Depends(get_user_id)):
    """Natural language → validated scheduling operation."""
    if request.user_id != user_id:
        raise HTTPException(status_code=403, detail="User mismatch")
    
    # For now, use Gemini to interpret the command
    model = genai.GenerativeModel(model_name=GEMINI_MODEL)
    prompt = f"""
Interpret this scheduling command and return a JSON operation:

Command: "{request.command}"

Return ONE of these operations:
1. {{"operation": "move_task", "task_id": "...", "new_start": "ISO", "new_end": "ISO"}}
2. {{"operation": "split_task", "task_id": "...", "blocks": [{{"start": "ISO", "end": "ISO"}}, ...]}}
3. {{"operation": "defer_task", "task_id": "...", "new_start": "ISO", "new_end": "ISO"}}
4. {{"operation": "add_fixed", "title": "...", "start": "ISO", "end": "ISO"}}
5. {{"operation": "delete_fixed", "task_id": "..."}}
6. {{"operation": "generate_schedule"}}

Only return the JSON object. Use current time context for relative references.
"""
    response = model.generate_content(prompt, generation_config={"response_mime_type": "application/json"})
    operation = json.loads(response.text)
    
    return {"interpreted": operation, "requires_confirmation": True}

# ---- NEURAVEX: Original endpoints (kept for compatibility) ----

@app.get("/api/health")
async def health_check():
    return {"status": "ok", "service": "neuravex-chrona-backend"}

@app.post("/api/process-event-json")
async def process_event_json(request: Request):
    body = await request.json()
    text = body.get("text", "")
    if not text.strip():
        raise HTTPException(status_code=400, detail="No text provided")
    try:
        extracted_data = extract_with_gemini(text.strip())
        # Insert first event
        if extracted_data:
            item = extracted_data[0]
            item["user_id"] = "00000000-0000-0000-0000-000000000001"  # fallback
            resp = supabase_post("events", item)
            if resp.status_code in [200, 201]:
                return {"success": True, "event": resp.json()[0]}
        raise HTTPException(status_code=500, detail="Insert failed")
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)