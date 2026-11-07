"""
Chrona Backend API Server — v2.0
==================================
Intelligent AI-driven personal time-management system backend.

Key changes from v1:
- Uses current google-genai SDK (google.genai) with gemini-3.8-flash
- Full natural-language chat endpoint with context memory
- Task decomposition agent for projects/goals
- Real schedule generation from user's context
- Proper timezone handling throughout
- Actual free-slot calculation from live schedule data
- Connected EDF scheduler + validator + repair engine pipeline

Run with: uvicorn server:app --port 8000 --reload
(from backend/ directory with venv activated)
"""
import os
import sys
import json
import hashlib
import traceback
import requests
from fastapi import FastAPI, HTTPException, Request, Depends, UploadFile, File, Form
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from datetime import datetime, timedelta, timezone, date
from typing import Optional, List, Dict, Any
from pydantic import BaseModel

# ==============================
# CONFIGURATION
# ==============================

from dotenv import load_dotenv
load_dotenv(os.path.join(os.path.dirname(__file__), '..', '.env'))

GEMINI_API_KEY    = os.getenv("GEMINI_API_KEY")
GROQ_API_KEY      = os.getenv("GROQ_API_KEY")
SUPABASE_URL      = os.getenv("SUPABASE_URL")
SUPABASE_ANON_KEY = os.getenv("SUPABASE_ANON_KEY")
SUPABASE_SERVICE_KEY = os.getenv("SUPABASE_SERVICE_KEY")
UPSTASH_REDIS_URL   = os.getenv("UPSTASH_REDIS_REST_URL")
UPSTASH_REDIS_TOKEN = os.getenv("UPSTASH_REDIS_REST_TOKEN")

# ---- AI Model Config ----
GEMINI_MODEL = "gemini-3.8-flash"
GROQ_MODEL   = "openai/gpt-oss-120b"

# ---- AI Client setup ----
gemini_client = None
if GEMINI_API_KEY and (GEMINI_API_KEY.startswith("AIzaSy") or not GEMINI_API_KEY.startswith("AQ.")):
    try:
        from google import genai as _genai
        gemini_client = _genai.Client(api_key=GEMINI_API_KEY)
        print("✅ Gemini (google-genai) initialized")
    except ImportError:
        # Fallback to old SDK
        try:
            import google.generativeai as _oldgenai
            _oldgenai.configure(api_key=GEMINI_API_KEY)
            gemini_client = _oldgenai
            print("⚠️ Using legacy google.generativeai SDK")
        except ImportError:
            print("❌ No Gemini SDK found")
else:
    print("ℹ️ Note: GEMINI_API_KEY is not a Google AI Studio key (starts with AIzaSy). Using Groq as primary LLM.")

groq_client = None
if GROQ_API_KEY:
    try:
        from groq import Groq
        groq_client = Groq(api_key=GROQ_API_KEY)
        print("✅ Groq initialized")
    except ImportError:
        print("⚠️ Groq not installed")

if not all([SUPABASE_URL, SUPABASE_ANON_KEY]):
    print("⚠️  WARNING: Supabase env vars missing")

# ==============================
# FASTAPI APP
# ==============================

app = FastAPI(title="Chrona Backend", version="2.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ==============================
# SUPABASE REST HELPERS (with in-memory resilience)
# ==============================

_in_memory_db: Dict[str, List[dict]] = {
    "schedule_items": [],
    "events": [],
    "behavior_profiles": [],
    "projects": [],
    "behavior_events": [],
    "repair_proposals": [],
    "onboarding_responses": [],
}

class MockResponse:
    def __init__(self, data=None, status_code=200):
        self._data = data if data is not None else []
        self.status_code = status_code
        self.ok = 200 <= status_code < 300
        self.text = json.dumps(self._data)
    def json(self):
        return self._data

def _sb_headers(service: bool = False) -> dict:
    key = SUPABASE_SERVICE_KEY if service else SUPABASE_ANON_KEY
    return {
        "apikey": key,
        "Authorization": f"Bearer {key}",
        "Content-Type": "application/json",
        "Prefer": "return=representation",
    }

def sb_get(table: str, params: dict = None, service: bool = False) -> Any:
    try:
        resp = requests.get(f"{SUPABASE_URL}/rest/v1/{table}", headers=_sb_headers(service), params=params, timeout=2)
        if resp.status_code == 200:
            return resp
    except Exception:
        pass
    # In-memory fallback
    items = _in_memory_db.get(table, [])
    # Basic user_id filter if present
    if params and "user_id" in params:
        uid = params["user_id"].replace("eq.", "")
        items = [i for i in items if i.get("user_id") == uid]
    return MockResponse(items, 200)

def sb_post(table: str, data: dict | list, service: bool = True) -> Any:
    try:
        resp = requests.post(f"{SUPABASE_URL}/rest/v1/{table}", headers=_sb_headers(service), json=data, timeout=2)
        if resp.status_code in [200, 201]:
            return resp
    except Exception:
        pass
    # In-memory store
    table_items = _in_memory_db.setdefault(table, [])
    data_list = data if isinstance(data, list) else [data]
    inserted = []
    import uuid
    for item in data_list:
        entry = dict(item)
        if "id" not in entry:
            entry["id"] = str(uuid.uuid4())
        table_items.append(entry)
        inserted.append(entry)
    return MockResponse(inserted, 201)

def sb_patch(table: str, data: dict, params: dict, service: bool = True) -> Any:
    try:
        resp = requests.patch(f"{SUPABASE_URL}/rest/v1/{table}", headers=_sb_headers(service), params=params, json=data, timeout=2)
        if resp.status_code in [200, 204]:
            return resp
    except Exception:
        pass
    # In-memory patch
    table_items = _in_memory_db.setdefault(table, [])
    item_id = params.get("id", "").replace("eq.", "")
    updated = []
    for item in table_items:
        if not item_id or item.get("id") == item_id:
            item.update(data)
            updated.append(item)
    return MockResponse(updated, 200)

def sb_delete(table: str, params: dict, service: bool = True) -> Any:
    try:
        resp = requests.delete(f"{SUPABASE_URL}/rest/v1/{table}", headers=_sb_headers(service), params=params, timeout=2)
        if resp.status_code in [200, 204]:
            return resp
    except Exception:
        pass
    # In-memory delete
    item_id = params.get("id", "").replace("eq.", "")
    if item_id:
        _in_memory_db[table] = [i for i in _in_memory_db.get(table, []) if i.get("id") != item_id]
    return MockResponse([], 200)

# ==============================
# TIME HELPERS
# ==============================

def _now_utc() -> datetime:
    return datetime.now(timezone.utc)

def _ensure_utc(dt: datetime) -> datetime:
    if dt is None:
        return None
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)

def _parse_dt(s: str) -> Optional[datetime]:
    if not s:
        return None
    try:
        dt = datetime.fromisoformat(str(s).replace("Z", "+00:00"))
        return _ensure_utc(dt)
    except (ValueError, TypeError):
        return None

# ==============================
# REDIS (Upstash REST)
# ==============================

_mem_cache: dict = {}  # in-memory fallback

async def cache_get(key: str) -> Optional[str]:
    if UPSTASH_REDIS_URL and UPSTASH_REDIS_TOKEN:
        try:
            resp = requests.get(
                f"{UPSTASH_REDIS_URL}/get/{key}",
                headers={"Authorization": f"Bearer {UPSTASH_REDIS_TOKEN}"},
                timeout=3
            )
            if resp.status_code == 200:
                res = resp.json().get("result")
                if res is not None:
                    return res
        except Exception:
            pass
    return _mem_cache.get(key)

async def cache_set(key: str, value: str, ex: int = 86400) -> None:
    if UPSTASH_REDIS_URL and UPSTASH_REDIS_TOKEN:
        try:
            requests.post(
                UPSTASH_REDIS_URL,
                headers={"Authorization": f"Bearer {UPSTASH_REDIS_TOKEN}", "Content-Type": "application/json"},
                json=["SET", key, str(value), "EX", ex],
                timeout=3
            )
        except Exception:
            pass
    _mem_cache[key] = value

async def cache_del(key: str) -> None:
    if UPSTASH_REDIS_URL and UPSTASH_REDIS_TOKEN:
        try:
            requests.post(
                UPSTASH_REDIS_URL,
                headers={"Authorization": f"Bearer {UPSTASH_REDIS_TOKEN}", "Content-Type": "application/json"},
                json=["DEL", key],
                timeout=3
            )
        except Exception:
            pass
    _mem_cache.pop(key, None)

def extract_text_from_pdf(pdf_bytes: bytes) -> str:
    """Extract human-readable text from PDF streams using Python standard library zlib."""
    import zlib
    import re
    chunks = []
    for m in re.finditer(rb"stream[\r\n]+(.*?)[\r\n]+endstream", pdf_bytes, re.DOTALL):
        try:
            decomp = zlib.decompress(m.group(1))
            for s in re.findall(rb"\((.*?)\)", decomp):
                decoded = s.decode("latin1", errors="ignore").strip()
                if len(decoded) > 1 and not re.match(r"^[\x00-\x1F]+$", decoded):
                    chunks.append(decoded)
        except Exception:
            pass
    if not chunks:
        raw_strings = re.findall(rb"[\x20-\x7E]{4,}", pdf_bytes)
        chunks = [s.decode("latin1", errors="ignore") for s in raw_strings if not s.startswith(b"/")]
    return " ".join(chunks)

# ==============================
# GEMINI AI HELPERS (google-genai SDK)
# ==============================

def _ai_complete(prompt: str, json_mode: bool = False, system: str = None) -> str:
    """Call AI with Gemini primary, Groq fallback. Returns text."""
    full_prompt = f"{system}\n\n{prompt}" if system else prompt

    if gemini_client:
        try:
            # Try new google-genai SDK
            if hasattr(gemini_client, 'models'):
                from google import genai as _genai
                resp = gemini_client.models.generate_content(
                    model=GEMINI_MODEL,
                    contents=full_prompt,
                    config=_genai.types.GenerateContentConfig(
                        response_mime_type="application/json" if json_mode else "text/plain",
                        temperature=0.1 if json_mode else 0.7,
                        max_output_tokens=8192,
                    )
                )
                return resp.text
            else:
                # Legacy google.generativeai SDK
                model = gemini_client.GenerativeModel(model_name="gemini-2.0-flash")
                config = {"temperature": 0.1 if json_mode else 0.7}
                if json_mode:
                    config["response_mime_type"] = "application/json"
                resp = model.generate_content(full_prompt, generation_config=config)
                return resp.text
        except Exception as e:
            print(f"⚠️ Gemini error: {e}")

    if groq_client:
        try:
            msgs = [{"role": "user", "content": full_prompt}]
            kw = {}
            if json_mode:
                kw["response_format"] = {"type": "json_object"}
            resp = groq_client.chat.completions.create(
                model=GROQ_MODEL,
                messages=msgs,
                temperature=0.1 if json_mode else 0.7,
                max_tokens=8192,
                **kw
            )
            return resp.choices[0].message.content
        except Exception as e:
            print(f"⚠️ Groq error: {e}")

    raise HTTPException(500, "No AI provider available")

def _ai_json(prompt: str, system: str = None) -> Any:
    """Call AI and parse JSON response."""
    text = _ai_complete(prompt, json_mode=True, system=system)
    # Strip markdown code fences if present
    text = text.strip()
    if text.startswith("```"):
        lines = text.split("\n")
        text = "\n".join(lines[1:-1])
    return json.loads(text)

# ==============================
# PYDANTIC MODELS
# ==============================

class AutoRescheduleRequest(BaseModel):
    user_id: str
    task_id: Optional[str] = None
    all_unplaced: bool = False

class AgentExecuteRequest(BaseModel):
    user_id: str
    task_id: str
    action: str = "study_guide"  # "research" | "study_guide" | "summary" | "checklist"
    instructions: Optional[str] = None

class OnboardingRequest(BaseModel):
    user_id: str
    productive_time: str  # morning, afternoon, evening, night
    work_type: str         # student, professional, freelancer, other
    task_preference: str   # one_long, multiple_short, flexible
    study_hours: int = 4
    wake_time: Optional[str] = "07:00"
    sleep_time: Optional[str] = "23:00"
    timezone: Optional[str] = "UTC"

class ChatRequest(BaseModel):
    user_id: str
    message: str
    session_id: Optional[str] = None
    context: Optional[dict] = None

class ScheduleGenerateRequest(BaseModel):
    user_id: str
    start_date: Optional[str] = None  # ISO date, defaults to today
    end_date: Optional[str] = None    # ISO date, defaults to 7 days out
    force: bool = False

class ScheduleAcceptRequest(BaseModel):
    user_id: str
    version: str
    scheduled_items: List[dict]

class TaskCreate(BaseModel):
    title: str
    description: Optional[str] = None
    duration_minutes: int = 60
    deadline: Optional[datetime] = None
    priority: int = 3
    flexibility: bool = True
    task_type: str = "task"  # task, fixed, study, workout, etc.
    recurrence: Optional[str] = None  # daily, weekly, etc.
    fixed_start: Optional[datetime] = None
    fixed_end: Optional[datetime] = None
    project_id: Optional[str] = None

class FixedCommitmentCreate(BaseModel):
    user_id: str
    title: str
    start_time: datetime
    end_time: datetime
    recurrence: Optional[str] = None  # daily, weekdays, weekly
    recurrence_days: Optional[List[int]] = None  # 0=Mon, 6=Sun

class BehaviorUpdateRequest(BaseModel):
    user_id: str
    task_id: str
    signal: str  # completed_on_time, skipped, delayed, rescheduled
    actual_start: Optional[str] = None
    actual_end: Optional[str] = None
    metadata: Optional[dict] = None

class RepairProposeRequest(BaseModel):
    user_id: str
    disrupted_task_id: str
    reason: Optional[str] = None  # e.g. "missed", "conflict", "user_requested"

class RepairApplyRequest(BaseModel):
    user_id: str
    proposal: dict  # from /api/repair/propose

class TaskCompleteRequest(BaseModel):
    user_id: str
    task_id: str
    actual_duration_minutes: Optional[int] = None
    note: Optional[str] = None

class ProjectCreate(BaseModel):
    user_id: str
    title: str
    description: Optional[str] = None
    deadline: Optional[datetime] = None
    priority: int = 3

# ==============================
# AUTH DEPENDENCY
# ==============================

async def get_user_id(request: Request) -> str:
    """
    Extract user_id from request.
    Priority: X-User-ID header > Authorization Bearer (future JWT) > default
    In production: validate JWT and extract sub claim.
    """
    uid = request.headers.get("X-User-ID")
    if uid:
        return uid
    # Default for single-user mode (dev/demo)
    return "00000000-0000-0000-0000-000000000001"

# ==============================
# BEHAVIOR PROFILE HELPERS
# ==============================

ARCHETYPES = {
    "student_balanced": {"morning": 0.5, "afternoon": 0.5, "evening": 0.5, "night": 0.5},
    "early_bird":       {"morning": 1.0, "afternoon": 0.6, "evening": 0.3, "night": 0.1},
    "night_owl":        {"morning": 0.2, "afternoon": 0.5, "evening": 0.8, "night": 1.0},
    "afternoon_focus":  {"morning": 0.3, "afternoon": 1.0, "evening": 0.6, "night": 0.2},
}
EMA_ALPHA = 0.3

async def get_behavior_profile(user_id: str) -> dict:
    cached = await cache_get(f"chrona:profile:{user_id}")
    if cached:
        try:
            return json.loads(cached)
        except Exception:
            pass

    resp = sb_get("behavior_profiles", {"user_id": f"eq.{user_id}", "limit": "1"})
    if resp.status_code == 200 and resp.json():
        profile = resp.json()[0]
        await cache_set(f"chrona:profile:{user_id}", json.dumps(profile))
        return profile

    # Cold start
    return {
        "user_id": user_id,
        "slot_weights": ARCHETYPES["student_balanced"].copy(),
        "type_weights": {},
        "avg_delay_min": 0.0,
        "sample_count": 0,
        "archetype": "student_balanced",
        "wake_time": "07:00",
        "sleep_time": "23:00",
        "study_hours": 4,
        "timezone": "UTC",
        "last_updated": _now_utc().isoformat(),
    }

async def update_behavior_profile(user_id: str, signal: str, slot: str = None, task_type: str = None) -> dict:
    profile = await get_behavior_profile(user_id)

    if slot and slot in profile["slot_weights"]:
        cur = profile["slot_weights"][slot]
        if signal == "completed_on_time":
            new_v = cur + EMA_ALPHA * (1.0 - cur)
        elif signal == "skipped":
            new_v = cur + EMA_ALPHA * (0.0 - cur)
        elif signal in ("delayed", "rescheduled"):
            new_v = cur + EMA_ALPHA * (0.4 - cur)
        else:
            new_v = cur
        profile["slot_weights"][slot] = round(max(0.05, min(1.0, new_v)), 4)

    if task_type:
        cur_type = profile["type_weights"].get(task_type, 0.5)
        if signal == "completed_on_time":
            profile["type_weights"][task_type] = round(cur_type + EMA_ALPHA * (1.0 - cur_type), 4)
        elif signal == "skipped":
            profile["type_weights"][task_type] = round(cur_type + EMA_ALPHA * (0.0 - cur_type), 4)

    profile["sample_count"] = profile.get("sample_count", 0) + 1
    profile["last_updated"] = _now_utc().isoformat()

    # Persist
    sb_patch("behavior_profiles",
             {"slot_weights": profile["slot_weights"],
              "type_weights": profile["type_weights"],
              "avg_delay_min": profile.get("avg_delay_min", 0.0),
              "sample_count": profile["sample_count"],
              "last_updated": profile["last_updated"]},
             params={"user_id": f"eq.{user_id}"})
    await cache_set(f"chrona:profile:{user_id}", json.dumps(profile))
    return profile

def _slot_for_hour(h: int) -> str:
    if 5 <= h < 12:  return "morning"
    if 12 <= h < 17: return "afternoon"
    if 17 <= h < 21: return "evening"
    return "night"

# ==============================
# SCHEDULE HELPERS
# ==============================

def get_user_schedule(user_id: str, start: datetime, end: datetime) -> List[dict]:
    """Fetch all schedule_items for user in [start, end)."""
    resp = sb_get("schedule_items", {
        "user_id": f"eq.{user_id}",
        "start_time": f"gte.{start.isoformat()}",
        "end_time": f"lte.{end.isoformat()}",
        "order": "start_time.asc",
        "status": "neq.cancelled",
    })
    if resp.status_code == 200:
        return resp.json() or []
    return []

def compute_free_slots(
    user_id: str,
    start: datetime,
    end: datetime,
    wake_time: str = "07:00",
    sleep_time: str = "23:00",
    buffer_minutes: int = 15,
) -> List[dict]:
    """
    Compute actual free time slots for a user between start and end.
    Uses their current schedule (fixed + flexible items) and wake/sleep times.
    Returns list of {start, end} dicts with ISO strings.
    """
    existing = get_user_schedule(user_id, start, end)

    # Build busy intervals
    busy = []
    for item in existing:
        s = _parse_dt(item.get("start_time"))
        e = _parse_dt(item.get("end_time"))
        if s and e:
            # Add buffer around non-fixed items
            buf = timedelta(minutes=buffer_minutes if item.get("flexibility", True) else 0)
            busy.append((s - buf, e + buf))

    # Merge overlapping busy slots
    busy.sort(key=lambda x: x[0])
    merged = []
    for b in busy:
        if merged and b[0] < merged[-1][1]:
            merged[-1] = (merged[-1][0], max(merged[-1][1], b[1]))
        else:
            merged.append(list(b))

    # Chop to wake/sleep windows per day
    free = []
    current = start
    while current.date() <= end.date():
        # Wake and sleep times for this day
        wh, wm = int(wake_time.split(":")[0]), int(wake_time.split(":")[1])
        sh, sm = int(sleep_time.split(":")[0]), int(sleep_time.split(":")[1])

        day_start = current.replace(hour=wh, minute=wm, second=0, microsecond=0)
        day_end   = current.replace(hour=sh, minute=sm, second=0, microsecond=0)

        # Clamp to overall window
        day_start = max(day_start, start)
        day_end   = min(day_end, end)

        if day_start < day_end:
            # Subtract busy intervals
            slots = [(day_start, day_end)]
            for b_start, b_end in merged:
                new_slots = []
                for s_s, s_e in slots:
                    if b_end <= s_s or b_start >= s_e:
                        new_slots.append((s_s, s_e))  # no overlap
                    else:
                        if s_s < b_start:
                            new_slots.append((s_s, b_start))
                        if b_end < s_e:
                            new_slots.append((b_end, s_e))
                slots = new_slots

            for s_s, s_e in slots:
                dur = (s_e - s_s).total_seconds() / 60
                if dur >= 30:  # only slots >= 30 min
                    free.append({
                        "start": s_s.isoformat(),
                        "end": s_e.isoformat(),
                        "duration_minutes": int(dur),
                    })

        current = current + timedelta(days=1)
        current = current.replace(hour=0, minute=0, second=0, microsecond=0)

    return free

# ==============================
# TASK DECOMPOSITION ENGINE
# ==============================

DECOMPOSE_SYSTEM = """You are an expert project planner and task decomposition engine for Chrona, an AI scheduling assistant.

When given a goal, project, or complex task, you break it down into realistic subtasks with concrete time estimates.

You MUST return a valid JSON object in this exact format:
{
  "project_title": "string",
  "total_estimated_hours": number,
  "subtasks": [
    {
      "title": "string",
      "description": "string",
      "duration_minutes": number,
      "priority": 1-4,
      "order": 1,
      "depends_on": [],
      "task_type": "study|work|research|writing|practice|review|other",
      "earliest_offset_days": 0,
      "completion_criteria": "string"
    }
  ],
  "recommendations": ["string"]
}

Rules:
- priority: 1=critical, 2=important, 3=normal, 4=optional
- depends_on: list of "order" numbers of prerequisite subtasks
- earliest_offset_days: minimum days from now before this subtask can start
- Keep subtasks concrete and actionable (not vague like "study")
- Estimate durations conservatively (better to overestimate)
- Include review/buffer subtasks for exams and deadlines
- Maximum 15 subtasks per project
"""

def decompose_goal(title: str, description: str, deadline: Optional[str],
                   context: dict = None) -> dict:
    """Use AI to decompose a goal into subtasks."""
    today = _now_utc().strftime("%Y-%m-%d")
    prompt = f"""
Today's date: {today}
Deadline: {deadline or "Not specified"}

Goal/Project: {title}
Description: {description or ""}
User context: {json.dumps(context or {})}

Break this down into realistic, actionable subtasks.
"""
    return _ai_json(prompt, system=DECOMPOSE_SYSTEM)

# ==============================
# CHRONA CHAT ENGINE
# ==============================

CHRONA_SYSTEM = """You are Chrona, an intelligent personal time-management and AI scheduling assistant.

Today's date is {today}.

You understand natural language about schedules, tasks, goals, classes, exams, workouts, and personal commitments.
You help users plan their time intelligently.

CRITICAL CONVERSATIONAL & EXTRACTION PRINCIPLES:
1. MULTI-TURN MEMORY & TASK UPDATES:
   - Always connect the user's latest message with the previous messages in the conversation!
   - If the user provides durations, adjustments, or prep time for a task mentioned in the recent dialogue (e.g., "it will be 45 minutes long", "I need 2 hours of prep beforehand", "change duration to 30 mins", "move it to 4 PM"):
     * DO NOT ASK FOR THE TITLE, DATE, OR EVENT NAME AGAIN! The title and date are already in the conversation history!
     * Update the existing task with the new duration/time, and extract any requested prep blocks (e.g. prep task before the presentation).
     * Return the updated tasks in the "tasks" array immediately.
2. SENSIBLE DEFAULTS OVER BLOCKING:
   - If a duration is not explicitly specified, USE INTELLIGENT DEFAULTS:
     * Exams: 120 minutes (2 hours)
     * Classes / Lectures: 60 minutes
     * Quizzes / Tests: 30 minutes
     * Assignments / Project work: 120 minutes (2 hours)
     * Workouts / Exercise / Practice: 60 minutes
     * General tasks: 60 minutes
   - NEVER leave duration_minutes as null! Always provide a realistic integer (minutes) so the task can be placed on the schedule.
   - If fixed_start is given (e.g. 2 PM) but fixed_end is missing, compute fixed_end = fixed_start + duration_minutes.
   - You can inform the user in your message: "I've set this for [X] hours based on typical defaults — feel free to adjust it anytime!"
3. ACTIONABLE EXTRACTIONS:
   - Extract tasks into the "tasks" array.
   - Only set clarification_needed if a truly essential detail is missing (e.g. user says "I have an exam" with no subject, date, or timeframe).
   - If user says "My exam is next Friday at 2 PM", that is completely sufficient! Extract it immediately with duration=120 min.

When extracting tasks/events, return valid JSON in this exact structure:
{
  "action": "add_tasks|add_fixed|generate_schedule|query|reschedule|decompose_project|mark_complete",
  "message": "Your friendly, conversational response explaining what you did or scheduled",
  "tasks": [
    {
      "title": "string",
      "duration_minutes": number,
      "deadline": "ISO string or null",
      "priority": 1-4,
      "flexibility": true/false,
      "task_type": "task|fixed|study|workout|practice|meeting|class|exam|assignment|quiz",
      "recurrence": "daily|weekdays|weekly|null",
      "recurrence_days": [0,1,2,3,4] or null,
      "fixed_start": "ISO string or null",
      "fixed_end": "ISO string or null",
      "is_project": false,
      "project_description": null
    }
  ],
  "clarification_needed": null,
  "schedule_trigger": false
}
"""

async def process_chat_message(user_id: str, message: str, session_id: str = None, context: dict = None) -> dict:
    """
    Process a natural language message with persistent multi-turn memory and context merging.
    """
    today = _now_utc().strftime("%Y-%m-%d %A")

    # Multi-turn conversation history cache keys
    session_key = session_id or "default"
    history_cache_key = f"chrona:history:{user_id}:{session_key}"
    draft_cache_key = f"chrona:draft:{user_id}:{session_key}"
    recent_tasks_cache_key = f"chrona:recent_tasks:{user_id}:{session_key}"

    # Load recent conversation history
    history_json = await cache_get(history_cache_key)
    history_turns = []
    if history_json:
        try:
            history_turns = json.loads(history_json)
        except Exception:
            history_turns = []

    # Load recent active tasks discussed in this session
    recent_tasks_json = await cache_get(recent_tasks_cache_key)
    recent_tasks = []
    if recent_tasks_json:
        try:
            recent_tasks = json.loads(recent_tasks_json)
        except Exception:
            recent_tasks = []

    # Load any pending/draft task from previous turn
    pending_draft_json = await cache_get(draft_cache_key)
    pending_draft = None
    if pending_draft_json:
        try:
            pending_draft = json.loads(pending_draft_json)
        except Exception:
            pending_draft = None

    # Get user's existing schedule context
    week_start = _now_utc().replace(hour=0, minute=0, second=0, microsecond=0)
    week_end = week_start + timedelta(days=14)
    existing = get_user_schedule(user_id, week_start, week_end)
    schedule_summary = f"User has {len(existing)} scheduled items in the next 2 weeks."
    if existing[:5]:
        schedule_summary += " Recent: " + ", ".join([f"{e['title']} at {e.get('start_time','?')[:16]}" for e in existing[:5]])

    # Build dialogue history string
    history_str = ""
    if history_turns:
        history_str = "Recent Conversation History:\n"
        for turn in history_turns[-6:]:
            role_label = turn.get("role", "user").capitalize()
            history_str += f"{role_label}: {turn.get('content', '')}\n"

    recent_tasks_str = ""
    if recent_tasks:
        recent_tasks_str = "ACTIVE TASKS CURRENTLY IN DISCUSSION:\n"
        for rt in recent_tasks[-4:]:
            recent_tasks_str += f"- Title: \"{rt.get('title')}\", Scheduled: {rt.get('fixed_start') or rt.get('start_time')}, Duration: {rt.get('duration_minutes')}m, Deadline: {rt.get('deadline')}\n"
        recent_tasks_str += "\nCRITICAL RULE: If the user provides durations, prep time, or adjustments (e.g. 'it will be 45 minutes long', '2 hours prep beforehand', 'make it 30 mins'), they are modifying the active task above! DO NOT ASK FOR TITLE OR DATE AGAIN! Update the active task and/or add the prep block immediately in the 'tasks' array!\n"

    pending_draft_str = ""
    if pending_draft:
        pending_draft_str = (
            f"\nUnfinished / Pending Task from previous message: {json.dumps(pending_draft)}\n"
            f"Note: If the user's latest message provides details answering this pending task, "
            f"MERGE them to finalize and extract the task now!"
        )

    system = CHRONA_SYSTEM.replace("{today}", today)
    prompt = f"""
{history_str}
{recent_tasks_str}
{pending_draft_str}

User's current schedule context: {schedule_summary}
User's latest message: "{message}"

Extract any tasks/commitments and respond helpfully. Return valid JSON.
"""

    result = _ai_json(prompt, system=system)

    # Post-process extracted tasks to guarantee no null duration
    extracted_tasks = result.get("tasks") or []
    for t in extracted_tasks:
        if not t.get("duration_minutes"):
            ttype = (t.get("task_type") or "").lower()
            if "exam" in ttype or "exam" in t.get("title", "").lower():
                t["duration_minutes"] = 120
            elif "quiz" in ttype or "test" in ttype:
                t["duration_minutes"] = 30
            elif "project" in ttype or "assignment" in ttype:
                t["duration_minutes"] = 120
            else:
                t["duration_minutes"] = 60

        if t.get("fixed_start") and not t.get("fixed_end"):
            s_dt = _parse_dt(t["fixed_start"])
            if s_dt:
                t["fixed_end"] = (s_dt + timedelta(minutes=int(t["duration_minutes"]))).isoformat()

    result["tasks"] = extracted_tasks

    # Memory update: Track active recent tasks
    if extracted_tasks:
        await cache_del(draft_cache_key)
        # Update recent tasks
        recent_tasks.extend(extracted_tasks)
        await cache_set(recent_tasks_cache_key, json.dumps(recent_tasks[-6:]), ex=7200)
    elif result.get("clarification_needed"):
        # Save pending draft
        await cache_set(draft_cache_key, json.dumps({
            "user_prompt": message,
            "clarification_needed": result.get("clarification_needed"),
            "assistant_response": result.get("message", "")
        }), ex=3600)

    # Save to conversation history
    history_turns.append({"role": "user", "content": message})
    history_turns.append({"role": "assistant", "content": result.get("message", "")})
    await cache_set(history_cache_key, json.dumps(history_turns[-10:]), ex=7200)

    return result

# ==============================
# SCHEDULE GENERATION ENGINE
# ==============================

SCHEDULE_SYSTEM = """You are a scheduling optimization engine for Chrona.

Given a list of tasks and available time slots, create an optimal schedule.

You MUST return JSON in this format:
{
  "scheduled": [
    {
      "task_id": "string",
      "title": "string",
      "start_time": "ISO datetime",
      "end_time": "ISO datetime",
      "task_type": "string",
      "priority": number,
      "flexibility": boolean,
      "reasoning": "Brief explanation"
    }
  ],
  "unscheduled": [
    {"task_id": "string", "title": "string", "reason": "Why it couldn't be scheduled"}
  ],
  "summary": "Brief overall summary of scheduling decisions",
  "warnings": ["Any important warnings for the user"]
}

Rules:
- Never overlap two tasks
- Respect fixed commitments (task_type: fixed)
- Schedule high priority tasks in preferred time slots
- Leave buffer between tasks (at least 10 minutes)
- Respect deadlines - tasks must end before their deadline
- Split long tasks across multiple days if needed
- Do NOT schedule tasks outside the available free slots
- Consider the user's productivity weights (morning/afternoon/evening/night)
"""

def generate_schedule_with_ai(
    tasks: List[dict],
    free_slots: List[dict],
    behavior_weights: dict,
    user_context: dict
) -> dict:
    """Use AI to generate an optimized schedule from tasks and free slots."""
    prompt = f"""
Today: {_now_utc().strftime('%Y-%m-%d %H:%M UTC')}
User productivity preferences: {json.dumps(behavior_weights)}
User context: {json.dumps(user_context)}

Tasks to schedule:
{json.dumps(tasks, indent=2)}

Available free time slots:
{json.dumps(free_slots, indent=2)}

Create the optimal schedule. Be realistic about time estimates.
"""
    return _ai_json(prompt, system=SCHEDULE_SYSTEM)

# ==============================
# IMPORT SCHEDULING MODULES
# ==============================

try:
    from scheduling.edf import EDFScheduler, Task, TimeSlot
    from scheduling.repair_engine import RepairEngine
    from scheduling.validator import validate_plan
    from scheduling.conflict import detect_conflicts
    _scheduling_available = True
    print("✅ Scheduling modules loaded")
except ImportError as e:
    print(f"⚠️ Scheduling modules not available: {e}")
    _scheduling_available = False

# ==============================
# API ENDPOINTS
# ==============================

@app.get("/api/health")
async def health_check():
    return {
        "status": "ok",
        "service": "chrona-backend",
        "version": "2.0.0",
        "ai": "gemini" if gemini_client else ("groq" if groq_client else "none"),
        "scheduling": _scheduling_available,
        "timestamp": _now_utc().isoformat(),
    }

# ---- ONBOARDING ----

@app.post("/api/onboarding")
async def onboarding(request: OnboardingRequest, user_id: str = Depends(get_user_id)):
    """Complete onboarding and initialize the user's behavior profile."""
    if request.user_id != user_id:
        raise HTTPException(403, "User mismatch")

    archetype_map = {
        "morning": "early_bird",
        "afternoon": "afternoon_focus",
        "evening": "afternoon_focus",
        "night": "night_owl",
    }
    archetype = archetype_map.get(request.productive_time, "student_balanced")

    profile = {
        "user_id": user_id,
        "slot_weights": ARCHETYPES[archetype].copy(),
        "type_weights": {},
        "avg_delay_min": 0.0,
        "sample_count": 0,
        "archetype": archetype,
        "wake_time": request.wake_time or "07:00",
        "sleep_time": request.sleep_time or "23:00",
        "study_hours": request.study_hours,
        "timezone": request.timezone or "UTC",
        "last_updated": _now_utc().isoformat(),
    }

    # Upsert profile
    sb_post("behavior_profiles", profile)

    # Save onboarding response
    onb_data = {
        "user_id": user_id,
        "productive_time": request.productive_time,
        "work_type": request.work_type,
        "task_preference": request.task_preference,
        "study_hours": request.study_hours,
    }
    sb_post("onboarding_responses", onb_data)

    await cache_set(f"chrona:profile:{user_id}", json.dumps(profile))
    return {"success": True, "profile": profile}

# ---- CHAT / NATURAL LANGUAGE ----

@app.post("/api/chat")
async def chat(request: ChatRequest, user_id: str = Depends(get_user_id)):
    """
    Main Chrona conversational endpoint.
    Processes natural language and returns structured response + extracted tasks.
    """
    if request.user_id != user_id:
        raise HTTPException(403, "User mismatch")

    result = await process_chat_message(
        user_id, request.message, request.session_id, request.context
    )

    # Auto-process extracted tasks if present
    inserted_tasks = []
    if result.get("tasks"):
        for task_data in result["tasks"]:
            if task_data.get("is_project"):
                # Decompose project
                try:
                    decomp = decompose_goal(
                        task_data["title"],
                        task_data.get("project_description", ""),
                        task_data.get("deadline"),
                        context={"user_id": user_id}
                    )
                    result["decomposition"] = decomp
                    for sub in decomp.get("subtasks", []):
                        sub_item = {
                            "user_id": user_id,
                            "title": sub["title"],
                            "task_type": sub.get("task_type", "task"),
                            "duration_minutes": sub.get("duration_minutes", 60),
                            "priority": sub.get("priority", 3),
                            "flexibility": True,
                            "status": "pending",
                            "deadline": task_data.get("deadline"),
                        }
                        resp = sb_post("schedule_items", sub_item)
                        if resp.status_code in [200, 201]:
                            inserted_tasks.extend(resp.json() if isinstance(resp.json(), list) else [resp.json()])
                except Exception as e:
                    print(f"⚠️ Decomposition error: {e}")
            else:
                # Deduplication & Context Modification: Check for existing commitment
                dur = task_data.get("duration_minutes", 60)
                item = {
                    "user_id": user_id,
                    "title": task_data.get("title", "Untitled"),
                    "task_type": task_data.get("task_type", "task"),
                    "duration_minutes": dur,
                    "priority": task_data.get("priority", 3),
                    "flexibility": task_data.get("flexibility", True),
                    "status": "pending",
                }
                if task_data.get("fixed_start") and task_data.get("fixed_end"):
                    item["start_time"] = task_data["fixed_start"]
                    item["end_time"] = task_data["fixed_end"]
                    item["flexibility"] = False
                    item["status"] = "scheduled"
                if task_data.get("deadline"):
                    item["deadline"] = task_data["deadline"]

                # Check if this commitment already exists to update instead of duplicate
                existing_resp = sb_get("schedule_items", {"user_id": f"eq.{user_id}", "status": "neq.cancelled"})
                existing_list = existing_resp.json() if existing_resp.status_code == 200 else []
                match = None
                t_lower = (task_data.get("title") or "").strip().lower()
                for ex in existing_list:
                    ex_title = (ex.get("title") or "").strip().lower()
                    if ex_title == t_lower or (t_lower and (t_lower in ex_title or ex_title in t_lower)):
                        match = ex
                        break

                if match:
                    sb_patch("schedule_items", item, params={"id": f"eq.{match['id']}", "user_id": f"eq.{user_id}"})
                    match.update(item)
                    inserted_tasks.append(match)
                else:
                    resp = sb_post("schedule_items", item)
                    if resp.status_code in [200, 201]:
                        try:
                            inserted_tasks.extend(resp.json() if isinstance(resp.json(), list) else [resp.json()])
                        except Exception:
                            pass

    return {
        "message": result.get("message", "I understand. Let me help you with that."),
        "action": result.get("action"),
        "tasks_extracted": result.get("tasks", []),
        "tasks_saved": inserted_tasks,
        "clarification_needed": result.get("clarification_needed"),
        "schedule_trigger": result.get("schedule_trigger", False),
        "decomposition": result.get("decomposition"),
    }

@app.post("/api/chat/upload")
async def chat_upload(
    message: Optional[str] = Form(""),
    file: Optional[UploadFile] = File(None),
    user_id: str = Depends(get_user_id),
    session_id: Optional[str] = Form(None)
):
    """
    Unified Chat + Upload endpoint.
    Accepts text message, uploaded timetable/syllabus/document, or both.
    Extracts events/commitments, auto-plans them, and returns conversational response.
    """
    file_text = ""
    file_name = ""
    if file:
        file_name = file.filename or "uploaded_file"
        try:
            content = await file.read()
            if file_name.lower().endswith(".pdf") or content.startswith(b"%PDF"):
                file_text = extract_text_from_pdf(content)
            else:
                try:
                    file_text = content.decode("utf-8")
                except UnicodeDecodeError:
                    file_text = extract_text_from_pdf(content)
        except Exception as e:
            print(f"⚠️ Error reading file: {e}")

    full_user_input = message.strip() if message else ""
    if file_name:
        attachment_context = f"\n[Document Extracted Content ({file_name})]:\n{file_text[:4000]}"
        full_user_input = f"{full_user_input}\n{attachment_context}".strip()

    if not full_user_input:
        full_user_input = "Please analyze the uploaded document and extract my schedule commitments and deadlines."

    result = await process_chat_message(user_id, full_user_input, session_id=session_id)

    inserted_tasks = []
    if result.get("tasks"):
        for task_data in result["tasks"]:
            dur = int(task_data.get("duration_minutes") or 60)
            item = {
                "user_id": user_id,
                "title": task_data.get("title", "Untitled"),
                "task_type": task_data.get("task_type", "task"),
                "duration_minutes": dur,
                "priority": task_data.get("priority", 3),
                "flexibility": task_data.get("flexibility", True),
                "status": "pending",
            }
            if task_data.get("fixed_start"):
                item["start_time"] = task_data["fixed_start"]
                if task_data.get("fixed_end"):
                    item["end_time"] = task_data["fixed_end"]
                else:
                    s_dt = _parse_dt(task_data["fixed_start"])
                    if s_dt:
                        item["end_time"] = (s_dt + timedelta(minutes=dur)).isoformat()
                item["flexibility"] = False
                item["status"] = "scheduled"

            if task_data.get("deadline"):
                item["deadline"] = task_data["deadline"]

            resp = sb_post("schedule_items", item)
            if resp.status_code in [200, 201]:
                try:
                    inserted_tasks.extend(resp.json() if isinstance(resp.json(), list) else [resp.json()])
                except Exception:
                    pass

    return {
        "message": result.get("message", "I analyzed your document and extracted the relevant tasks."),
        "action": result.get("action"),
        "tasks_extracted": result.get("tasks", []),
        "tasks_saved": inserted_tasks,
        "file_name": file_name,
        "clarification_needed": result.get("clarification_needed"),
        "schedule_trigger": result.get("schedule_trigger", False),
        "decomposition": result.get("decomposition"),
    }

# ---- PROCESS EVENT (legacy + new) ----

@app.post("/api/process-event")
async def process_event(request: Request, user_id: str = Depends(get_user_id)):
    """
    Process an event from text, PDF, or image.
    Accepts: application/json OR multipart/form-data.
    """
    content_type = request.headers.get("content-type", "")
    text = ""
    file_bytes = None
    file_name = ""

    if "application/json" in content_type:
        body = await request.json()
        text = body.get("text", "").strip()
        if not text:
            raise HTTPException(400, "No text provided")
    elif "multipart/form-data" in content_type:
        form = await request.form()
        text = str(form.get("text", "")).strip()
        file = form.get("file")
        if file and hasattr(file, "read"):
            file_bytes = await file.read()
            file_name = getattr(file, "filename", "")
    else:
        try:
            body = await request.json()
            text = body.get("text", "").strip()
        except Exception:
            raise HTTPException(400, "Unsupported content type")

    if not text and not file_bytes:
        raise HTTPException(400, "No text or file provided")

    today = _now_utc().strftime("%Y-%m-%d")
    if file_bytes:
        extracted_doc_text = ""
        if file_name.lower().endswith(".pdf") or file_bytes.startswith(b"%PDF"):
            extracted_doc_text = extract_text_from_pdf(file_bytes)
        else:
            try:
                extracted_doc_text = file_bytes.decode("utf-8")
            except UnicodeDecodeError:
                extracted_doc_text = extract_text_from_pdf(file_bytes)

        text = f"Document content ({file_name}):\n{extracted_doc_text[:4000]}\n\nUser Notes: {text}".strip()

    system = f"""You are an expert event extraction engine for Chrona. Today is {today}.

Extract ALL tasks/events/classes/exams from the input text. Return a JSON ARRAY of objects:
[{{
  "title": "string",
  "category": "exam|assignment|meeting|class|workout|study|personal|other",
  "task_type": "task|fixed|study|exam|assignment|class|workout",
  "event_datetime": "ISO datetime or null",
  "duration_minutes": number (default 60, exams default 120),
  "priority": 1-4,
  "flexibility": true/false,
  "is_fixed": false,
  "fixed_start": "ISO datetime or null",
  "fixed_end": "ISO datetime or null",
  "deadline": "ISO datetime or null",
  "recurrence": "daily|weekdays|weekly|null",
  "recurrence_days": [0-6] or null,
  "key_topics": ["string"],
  "prep_hours": 0,
  "venue": "string or null"
}}]

RULES:
- Always assign a sensible integer duration_minutes (default 60, exams default 120, quizzes 30)
- flexibility=false for classes, exams, meetings with specific times
- flexibility=true for study sessions, workouts, assignments
- Use actual ISO dates. Today={today}
- Return ONLY a valid JSON array, no markdown
"""
    try:
        extracted = _ai_json(text, system=system)
        if not isinstance(extracted, list):
            extracted = [extracted]
    except Exception as e:
        raise HTTPException(500, f"Extraction failed: {e}")

    # Save to events table AND schedule_items table
    results = []
    for item in extracted:
        item["user_id"] = user_id
        item["source_hash"] = hashlib.sha256(
            f"{user_id}:{item.get('title','')}:{item.get('event_datetime','')}".encode()
        ).hexdigest()[:32]
        resp = sb_post("events", item)
        if resp.status_code in [200, 201]:
            try:
                results.extend(resp.json() if isinstance(resp.json(), list) else [resp.json()])
            except Exception:
                pass

        # Also populate schedule_items so Mind Map displays it immediately!
        dur = int(item.get("duration_minutes") or 60)
        s_item = {
            "user_id": user_id,
            "title": item.get("title", "Untitled"),
            "task_type": item.get("task_type", "task"),
            "duration_minutes": dur,
            "priority": item.get("priority", 3),
            "flexibility": item.get("flexibility", True),
            "status": "scheduled" if (item.get("fixed_start") or item.get("event_datetime")) else "pending",
        }
        start_val = item.get("fixed_start") or item.get("event_datetime")
        if start_val:
            s_item["start_time"] = start_val
            s_dt = _parse_dt(start_val)
            if s_dt:
                s_item["end_time"] = (s_dt + timedelta(minutes=dur)).isoformat()
        if item.get("deadline"):
            s_item["deadline"] = item.get("deadline")

        sb_post("schedule_items", s_item)

    return {"success": True, "events": results, "count": len(results)}

# ---- TASK DECOMPOSITION ----

@app.post("/api/decompose")
async def decompose(
    project: ProjectCreate,
    user_id: str = Depends(get_user_id)
):
    """Decompose a project/goal into subtasks using AI."""
    if project.user_id != user_id:
        raise HTTPException(403, "User mismatch")

    deadline_str = project.deadline.isoformat() if project.deadline else None
    result = decompose_goal(project.title, project.description or "", deadline_str)

    # Save project
    proj_data = {
        "user_id": user_id,
        "title": project.title,
        "description": project.description,
        "deadline": deadline_str,
        "priority": project.priority,
        "status": "active",
        "created_at": _now_utc().isoformat(),
    }
    proj_resp = sb_post("projects", proj_data)
    project_id = None
    if proj_resp.status_code in [200, 201]:
        try:
            project_id = proj_resp.json()[0]["id"] if isinstance(proj_resp.json(), list) else proj_resp.json().get("id")
        except Exception:
            pass

    # Save subtasks as pending schedule items
    saved_subtasks = []
    for sub in result.get("subtasks", []):
        sub_data = {
            "user_id": user_id,
            "title": sub["title"],
            "task_type": sub.get("task_type", "task"),
            "priority": sub.get("priority", 3),
            "flexibility": True,
            "status": "pending",
            "deadline": deadline_str,
        }
        if project_id:
            sub_data["project_id"] = project_id
        resp = sb_post("schedule_items", sub_data)
        if resp.status_code in [200, 201]:
            try:
                saved_subtasks.extend(resp.json() if isinstance(resp.json(), list) else [resp.json()])
            except Exception:
                pass

    return {
        "success": True,
        "project_id": project_id,
        "decomposition": result,
        "subtasks_saved": len(saved_subtasks),
    }

# ---- SCHEDULE GENERATION ----

@app.post("/api/schedule/generate")
async def schedule_generate(request: ScheduleGenerateRequest, user_id: str = Depends(get_user_id)):
    """
    Generate an intelligent schedule for the user.
    Uses EDF scheduler + AI optimization for realistic placement.
    """
    if request.user_id != user_id:
        raise HTTPException(403, "User mismatch")

    # Date range
    now = _now_utc()
    start = _parse_dt(request.start_date + "T00:00:00+00:00") if request.start_date else now.replace(hour=0, minute=0, second=0)
    end   = _parse_dt(request.end_date + "T23:59:59+00:00") if request.end_date else start + timedelta(days=7)

    # Get behavior profile
    profile = await get_behavior_profile(user_id)
    weights = profile.get("slot_weights", ARCHETYPES["student_balanced"])
    wake    = profile.get("wake_time", "07:00")
    sleep   = profile.get("sleep_time", "23:00")

    # Get pending tasks
    tasks_resp = sb_get("schedule_items", {
        "user_id": f"eq.{user_id}",
        "status": "in.(pending,scheduled)",
        "order": "priority.asc,deadline.asc.nullslast",
    })
    all_items = tasks_resp.json() if tasks_resp.status_code == 200 else []

    # Separate fixed vs flexible
    fixed_items = [i for i in all_items if i.get("task_type") == "fixed" or not i.get("flexibility", True)]
    flex_tasks  = [i for i in all_items if i.get("flexibility", True) and i.get("task_type") != "fixed"]

    # Compute free slots
    free_slots = compute_free_slots(user_id, start, end, wake, sleep)

    if _scheduling_available:
        # Build Task objects for EDF
        edf_tasks = []
        for t in flex_tasks:
            deadline = _parse_dt(t.get("deadline")) or (end)
            edf_tasks.append(Task(
                id=t["id"],
                title=t["title"],
                duration_minutes=int(t.get("duration_minutes") or 60),
                deadline=deadline,
                priority=int(t.get("priority") or 3),
                flexibility=True,
                task_type=t.get("task_type", "task"),
            ))

        edf_fixed = []
        for f in fixed_items:
            if f.get("start_time") and f.get("end_time"):
                edf_fixed.append(Task(
                    id=f["id"],
                    title=f["title"],
                    duration_minutes=int((_parse_dt(f["end_time"]) - _parse_dt(f["start_time"])).total_seconds() / 60),
                    deadline=_parse_dt(f.get("deadline")) or end,
                    priority=1,
                    flexibility=False,
                    fixed_start=_parse_dt(f["start_time"]),
                    fixed_end=_parse_dt(f["end_time"]),
                ))

        edf_slots = [
            TimeSlot(start=_parse_dt(s["start"]), end=_parse_dt(s["end"]))
            for s in free_slots
        ]

        scheduler = EDFScheduler()
        result = scheduler.schedule(edf_tasks, edf_slots, weights, edf_fixed)

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
                "deadline": a.task.deadline.isoformat(),
            })

        impossible = [{"task_id": t.id, "title": t.title, "reason": "Deadline passed or insufficient time"} for t in result.impossible]
        deferred   = [{"task_id": t.id, "title": t.title} for t in result.deferred]

        # Auto-reschedule tasks that were impossible or deferred
        auto_rescheduled = []
        if result.impossible or result.deferred:
            unplaced = list(result.impossible) + list(result.deferred)
            for t in unplaced:
                # Find available slot with remaining time, or search ahead
                assigned_slot = None
                for s in edf_slots:
                    if s.remaining_minutes() >= min(t.duration_minutes, 30):
                        assigned_slot = s
                        break

                if assigned_slot:
                    t_start = assigned_slot.consume(t)
                    t_dur = min(t.duration_minutes, max(30, int((assigned_slot.end - t_start).total_seconds() / 60)))
                    t_end = t_start + timedelta(minutes=t_dur)
                else:
                    # Place in earliest feasible window on next day
                    latest_end = max([a.assigned_end for a in result.scheduled] + [now])
                    t_start = (latest_end + timedelta(hours=2)).replace(minute=0, second=0)
                    t_end = t_start + timedelta(minutes=t.duration_minutes)

                resched_item = {
                    "task_id": t.id,
                    "title": t.title,
                    "start_time": t_start.isoformat(),
                    "end_time": t_end.isoformat(),
                    "task_type": t.task_type,
                    "priority": t.priority,
                    "flexibility": t.flexibility,
                    "deadline": max(t.deadline, t_end + timedelta(days=1)).isoformat(),
                    "auto_rescheduled": True,
                    "reschedule_reason": "Originally unplaceable/impossible; automatically rescheduled to next available focus block."
                }
                proposed.append(resched_item)
                auto_rescheduled.append(resched_item)
    else:
        # AI-only fallback
        tasks_for_ai = [{"id": t["id"], "title": t["title"],
                         "duration_minutes": t.get("duration_minutes", 60),
                         "deadline": t.get("deadline"), "priority": t.get("priority", 3)} for t in flex_tasks]
        ai_result = generate_schedule_with_ai(tasks_for_ai, free_slots[:20], weights, {"user_id": user_id})
        proposed  = ai_result.get("scheduled", [])
        impossible = []
        deferred = ai_result.get("unscheduled", [])
        auto_rescheduled = []

    # Version token
    version = hashlib.sha256(f"{user_id}:{_now_utc().isoformat()}".encode()).hexdigest()[:16]

    return {
        "success": True,
        "version": version,
        "proposed": proposed,
        "impossible": impossible,
        "deferred": deferred,
        "auto_rescheduled": auto_rescheduled,
        "free_slots_count": len(free_slots),
        "tasks_considered": len(flex_tasks),
    }

@app.post("/api/schedule/auto-reschedule")
async def schedule_auto_reschedule(request: AutoRescheduleRequest, user_id: str = Depends(get_user_id)):
    """Automatically find and apply the next best free slot for impossible/missed/overdue tasks."""
    if request.user_id != user_id:
        raise HTTPException(403, "User mismatch")

    now = _now_utc()
    profile = await get_behavior_profile(user_id)
    wake = profile.get("wake_time", "07:00")
    sleep = profile.get("sleep_time", "23:00")

    target_tasks = []
    if request.task_id:
        resp = sb_get("schedule_items", {"id": f"eq.{request.task_id}", "user_id": f"eq.{user_id}"})
        if resp.status_code == 200 and resp.json():
            target_tasks = resp.json()
    elif request.all_unplaced:
        resp = sb_get("schedule_items", {
            "user_id": f"eq.{user_id}",
            "status": "in.(pending,skipped,impossible)",
            "limit": "20",
        })
        if resp.status_code == 200 and resp.json():
            target_tasks = resp.json()

    if not target_tasks:
        raise HTTPException(404, "No tasks found to reschedule")

    slots = compute_free_slots(user_id, now, now + timedelta(days=14), wake, sleep)
    rescheduled_items = []

    for task in target_tasks:
        dur = int(task.get("duration_minutes") or 60)
        chosen_slot = None
        for s in slots:
            s_start = _parse_dt(s["start"])
            s_end = _parse_dt(s["end"])
            if s_start and s_end and s_start > now:
                avail_min = (s_end - s_start).total_seconds() / 60
                if avail_min >= min(dur, 30):
                    chosen_slot = (s_start, min(s_end, s_start + timedelta(minutes=dur)))
                    s["start"] = (s_start + timedelta(minutes=dur + 15)).isoformat()
                    break

        if not chosen_slot:
            tomorrow = (now + timedelta(days=1)).replace(hour=10, minute=0, second=0, microsecond=0)
            chosen_slot = (tomorrow, tomorrow + timedelta(minutes=dur))

        new_start, new_end = chosen_slot
        update_data = {
            "start_time": new_start.isoformat(),
            "end_time": new_end.isoformat(),
            "status": "rescheduled",
        }
        sb_patch("schedule_items", update_data, params={"id": f"eq.{task['id']}", "user_id": f"eq.{user_id}"})
        rescheduled_items.append({
            "task_id": task["id"],
            "title": task.get("title", "Untitled"),
            "new_start": new_start.isoformat(),
            "new_end": new_end.isoformat(),
            "auto_rescheduled": True,
        })

    return {
        "success": True,
        "count": len(rescheduled_items),
        "rescheduled": rescheduled_items,
        "message": f"Successfully auto-rescheduled {len(rescheduled_items)} task(s) to optimal free slots."
    }

@app.post("/api/schedule/accept")
async def schedule_accept(request: ScheduleAcceptRequest, user_id: str = Depends(get_user_id)):
    """Accept and persist a proposed schedule."""
    if request.user_id != user_id:
        raise HTTPException(403, "User mismatch")

    # Validate before committing
    plan = []
    for item in request.scheduled_items:
        s = _parse_dt(item.get("start_time"))
        e = _parse_dt(item.get("end_time"))
        if s and e:
            plan.append({
                "start": item["start_time"],
                "end": item["end_time"],
                "task_id": item.get("task_id", "unknown"),
                "deadline": item.get("deadline"),
            })

    if _scheduling_available and plan:
        validation = validate_plan(plan, [], [])
        if not validation["valid"]:
            raise HTTPException(409, {"message": "Schedule validation failed", "errors": validation["errors"]})

    # Persist
    inserted = []
    for item in request.scheduled_items:
        # Update existing item if task_id exists
        task_id = item.get("task_id")
        if task_id and not task_id.startswith("new-"):
            resp = sb_patch("schedule_items", {
                "start_time": item["start_time"],
                "end_time": item["end_time"],
                "status": "scheduled",
            }, params={"id": f"eq.{task_id}", "user_id": f"eq.{user_id}"})
            if resp.status_code in [200, 204]:
                inserted.append({"id": task_id, **item})
        else:
            # Insert new
            data = {
                "user_id": user_id,
                "title": item.get("title", "Untitled"),
                "task_type": item.get("task_type", "task"),
                "start_time": item.get("start_time"),
                "end_time": item.get("end_time"),
                "deadline": item.get("deadline"),
                "priority": item.get("priority", 3),
                "flexibility": item.get("flexibility", True),
                "status": "scheduled",
            }
            resp = sb_post("schedule_items", data)
            if resp.status_code in [200, 201]:
                try:
                    inserted.extend(resp.json() if isinstance(resp.json(), list) else [resp.json()])
                except Exception:
                    pass

    return {"success": True, "committed": len(inserted), "message": "Schedule accepted and saved"}

@app.get("/api/schedule/items")
async def schedule_items(
    user_id: str = Depends(get_user_id),
    start: Optional[str] = None,
    end: Optional[str] = None,
    status: Optional[str] = None,
):
    """Get schedule items for the user."""
    params = {"user_id": f"eq.{user_id}", "order": "start_time.asc"}
    if start:
        params["start_time"] = f"gte.{start}"
    if end:
        params["end_time"] = f"lte.{end}"
    if status:
        params["status"] = f"eq.{status}"
    else:
        params["status"] = "neq.cancelled"

    resp = sb_get("schedule_items", params)
    if resp.status_code == 200:
        return {"items": resp.json() or [], "count": len(resp.json() or [])}
    raise HTTPException(500, resp.text)

@app.post("/api/schedule/fixed")
async def schedule_fixed_create(item: FixedCommitmentCreate, user_id: str = Depends(get_user_id)):
    """Create a fixed commitment (class, meeting, etc.)."""
    if item.user_id != user_id:
        raise HTTPException(403, "User mismatch")

    data = {
        "user_id": user_id,
        "title": item.title,
        "start_time": item.start_time.isoformat(),
        "end_time": item.end_time.isoformat(),
        "task_type": "fixed",
        "flexibility": False,
        "status": "scheduled",
        "priority": 1,
        "recurrence": item.recurrence,
    }
    resp = sb_post("schedule_items", data)
    if resp.status_code in [200, 201]:
        return {"success": True, "item": resp.json()[0] if isinstance(resp.json(), list) else resp.json()}
    raise HTTPException(500, resp.text)

@app.delete("/api/schedule/fixed/{item_id}")
async def schedule_fixed_delete(item_id: str, user_id: str = Depends(get_user_id)):
    """Delete a fixed commitment."""
    resp = sb_delete("schedule_items", {"id": f"eq.{item_id}", "user_id": f"eq.{user_id}"})
    if resp.status_code in [200, 204]:
        return {"success": True}
    raise HTTPException(500, resp.text)

@app.patch("/api/schedule/items/{item_id}")
async def update_schedule_item(item_id: str, body: dict, user_id: str = Depends(get_user_id)):
    """Update a schedule item."""
    allowed = {"title", "start_time", "end_time", "status", "priority", "completion_note", "actual_start", "actual_end"}
    update_data = {k: v for k, v in body.items() if k in allowed}
    resp = sb_patch("schedule_items", update_data, params={"id": f"eq.{item_id}", "user_id": f"eq.{user_id}"})
    if resp.status_code in [200, 204]:
        return {"success": True}
    raise HTTPException(500, resp.text)

# ---- TASK COMPLETION ----

@app.post("/api/tasks/complete")
async def complete_task(request: TaskCompleteRequest, user_id: str = Depends(get_user_id)):
    """Mark a task as complete and update behavior profile."""
    if request.user_id != user_id:
        raise HTTPException(403, "User mismatch")

    now = _now_utc()

    # Get the task
    resp = sb_get("schedule_items", {"id": f"eq.{request.task_id}", "user_id": f"eq.{user_id}"})
    if resp.status_code != 200 or not resp.json():
        raise HTTPException(404, "Task not found")
    task = resp.json()[0]

    # Update task status
    update = {
        "status": "completed",
        "actual_end": now.isoformat(),
        "completion_note": request.note,
    }
    sb_patch("schedule_items", update, params={"id": f"eq.{request.task_id}", "user_id": f"eq.{user_id}"})

    # Determine if completed on time
    sched_end = _parse_dt(task.get("end_time"))
    signal = "completed_on_time" if (sched_end and now <= sched_end + timedelta(minutes=30)) else "delayed"
    slot = _slot_for_hour(_parse_dt(task.get("start_time")).hour if task.get("start_time") else now.hour)

    # Update behavior
    profile = await update_behavior_profile(user_id, signal, slot=slot, task_type=task.get("task_type"))

    # Log behavior event
    sb_post("behavior_events", {
        "user_id": user_id,
        "task_id": request.task_id,
        "event_type": signal,
        "scheduled_start": task.get("start_time"),
        "scheduled_end": task.get("end_time"),
        "actual_complete": now.isoformat(),
        "metadata": {"slot": slot, "note": request.note},
        "logged_at": now.isoformat(),
    })

    return {"success": True, "signal": signal, "profile_updated": True}

@app.post("/api/tasks/skip")
async def skip_task(request: TaskCompleteRequest, user_id: str = Depends(get_user_id)):
    """Mark a task as skipped and automatically reschedule it to the next viable slot."""
    if request.user_id != user_id:
        raise HTTPException(403, "User mismatch")

    # Get the task
    resp = sb_get("schedule_items", {"id": f"eq.{request.task_id}", "user_id": f"eq.{user_id}"})
    if resp.status_code != 200 or not resp.json():
        raise HTTPException(404, "Task not found")
    task = resp.json()[0]

    now = _now_utc()
    profile = await get_behavior_profile(user_id)
    wake = profile.get("wake_time", "07:00")
    sleep = profile.get("sleep_time", "23:00")
    dur = int(task.get("duration_minutes") or 60)

    # Find the next viable free slot to auto-reschedule
    slots = compute_free_slots(user_id, now, now + timedelta(days=7), wake, sleep)
    new_start = None
    new_end = None
    for s in slots:
        s_start = _parse_dt(s["start"])
        s_end = _parse_dt(s["end"])
        if s_start and s_end and s_start > now:
            avail = (s_end - s_start).total_seconds() / 60
            if avail >= min(dur, 30):
                new_start = s_start
                new_end = min(s_end, s_start + timedelta(minutes=dur))
                break

    if not new_start:
        tomorrow = (now + timedelta(days=1)).replace(hour=14, minute=0, second=0, microsecond=0)
        new_start = tomorrow
        new_end = tomorrow + timedelta(minutes=dur)

    # Update task with auto-rescheduled times
    sb_patch("schedule_items", {
        "status": "rescheduled",
        "start_time": new_start.isoformat(),
        "end_time": new_end.isoformat(),
        "completion_note": f"Auto-rescheduled after skip: {request.note or 'missed'}",
    }, params={"id": f"eq.{request.task_id}", "user_id": f"eq.{user_id}"})

    slot = _slot_for_hour(_parse_dt(task.get("start_time")).hour if task.get("start_time") else now.hour)
    await update_behavior_profile(user_id, "skipped", slot=slot, task_type=task.get("task_type"))

    sb_post("behavior_events", {
        "user_id": user_id,
        "task_id": request.task_id,
        "event_type": "skipped",
        "scheduled_start": task.get("start_time"),
        "scheduled_end": task.get("end_time"),
        "metadata": {"reason": request.note, "auto_rescheduled_to": new_start.isoformat()},
        "logged_at": now.isoformat(),
    })

    return {
        "success": True,
        "task_status": "rescheduled",
        "auto_rescheduled": True,
        "new_start": new_start.isoformat(),
        "new_end": new_end.isoformat(),
        "message": f"Task automatically rescheduled to {new_start.strftime('%a, %b %d at %I:%M %p')}",
    }

# ---- REPAIR ----

@app.post("/api/repair/propose")
async def repair_propose(request: RepairProposeRequest, user_id: str = Depends(get_user_id)):
    """Generate repair proposals for a disrupted task."""
    if request.user_id != user_id:
        raise HTTPException(403, "User mismatch")

    # Get the disrupted task
    resp = sb_get("schedule_items", {"id": f"eq.{request.disrupted_task_id}", "user_id": f"eq.{user_id}"})
    if resp.status_code != 200 or not resp.json():
        raise HTTPException(404, "Task not found")
    task = resp.json()[0]

    # Get actual free slots from the user's real schedule
    profile = await get_behavior_profile(user_id)
    now = _now_utc()
    deadline = _parse_dt(task.get("deadline")) or (now + timedelta(days=7))
    free_slots = compute_free_slots(
        user_id, now, deadline,
        profile.get("wake_time", "07:00"),
        profile.get("sleep_time", "23:00"),
    )

    disrupted = {
        "id": task["id"],
        "title": task["title"],
        "duration_minutes": task.get("duration_minutes", 60),
        "deadline": task.get("deadline"),
        "priority": task.get("priority", 3),
        "flexibility": task.get("flexibility", True),
    }

    if _scheduling_available:
        engine = RepairEngine()
        proposals = engine.propose_repairs(disrupted, free_slots)
    else:
        # AI-based repair
        repair_prompt = f"""
Task: {json.dumps(disrupted)}
Free slots: {json.dumps(free_slots[:10])}
Reason for disruption: {request.reason or "unknown"}

Propose 3 repair strategies. Return JSON:
{{"proposals": [{{"strategy": "move|split|defer", "label": "string", "description": "string",
   "new_start": "ISO", "new_end": "ISO", "risk": "low|medium|critical",
   "urgency_after": 0.5}}]}}
"""
        result = _ai_json(repair_prompt)
        proposals = result.get("proposals", [])

    # Cache proposals
    await cache_set(
        f"chrona:repair:{user_id}:{request.disrupted_task_id}",
        json.dumps(proposals),
        ex=600
    )

    return {"proposals": proposals, "task": task, "free_slots_found": len(free_slots)}

@app.post("/api/repair/apply")
async def repair_apply(request: RepairApplyRequest, user_id: str = Depends(get_user_id)):
    """Apply a selected repair proposal."""
    if request.user_id != user_id:
        raise HTTPException(403, "User mismatch")

    proposal = request.proposal
    task_id = proposal.get("task_id") or proposal.get("id")

    if not task_id:
        raise HTTPException(400, "task_id required in proposal")

    new_start = proposal.get("new_start")
    new_end   = proposal.get("new_end")

    if not new_start or not new_end:
        raise HTTPException(400, "new_start and new_end required")

    # Validate
    if _scheduling_available:
        validation = validate_plan([{
            "start": new_start,
            "end": new_end,
            "task_id": task_id,
        }], [], [])
        if not validation["valid"]:
            raise HTTPException(409, {"message": "Invalid repair", "errors": validation["errors"]})

    # Apply
    resp = sb_patch("schedule_items", {
        "start_time": new_start,
        "end_time": new_end,
        "status": "rescheduled",
    }, params={"id": f"eq.{task_id}", "user_id": f"eq.{user_id}"})

    if resp.status_code in [200, 204]:
        # Log
        sb_post("repair_proposals", {
            "user_id": user_id,
            "conflict_hash": proposal.get("conflict_hash"),
            "proposals": json.dumps([proposal]),
            "accepted": True,
            "accepted_idx": 0,
            "created_at": _now_utc().isoformat(),
        })
        # Update behavior
        await update_behavior_profile(user_id, "rescheduled")
        return {"success": True, "message": "Repair applied successfully"}
    raise HTTPException(500, resp.text)

# ---- BEHAVIOR ----

@app.get("/api/behavior/profile")
async def behavior_profile_get(user_id: str = Depends(get_user_id)):
    profile = await get_behavior_profile(user_id)
    return {"profile": profile}

@app.post("/api/behavior/update")
async def behavior_update(request: BehaviorUpdateRequest, user_id: str = Depends(get_user_id)):
    if request.user_id != user_id:
        raise HTTPException(403, "User mismatch")

    slot = None
    if request.actual_start:
        dt = _parse_dt(request.actual_start)
        if dt:
            slot = _slot_for_hour(dt.hour)

    profile = await update_behavior_profile(user_id, request.signal, slot=slot)

    sb_post("behavior_events", {
        "user_id": user_id,
        "task_id": request.task_id,
        "event_type": request.signal,
        "metadata": request.metadata or {},
        "logged_at": _now_utc().isoformat(),
    })

    return {"success": True, "profile": profile}

# ---- CONTEXT / MEMORY ----

@app.get("/api/context")
async def get_user_context(user_id: str = Depends(get_user_id)):
    """Get user's full context: profile, upcoming schedule, pending tasks."""
    profile = await get_behavior_profile(user_id)

    now = _now_utc()
    today_start = now.replace(hour=0, minute=0, second=0)
    week_end    = today_start + timedelta(days=7)

    # Today's schedule
    today_items = get_user_schedule(user_id, today_start, today_start + timedelta(days=1))

    # This week
    week_items = get_user_schedule(user_id, today_start, week_end)

    # Pending tasks
    pending_resp = sb_get("schedule_items", {
        "user_id": f"eq.{user_id}",
        "status": "eq.pending",
        "order": "priority.asc,deadline.asc.nullslast",
        "limit": "20",
    })
    pending = pending_resp.json() if pending_resp.status_code == 200 else []

    # Overdue
    overdue_resp = sb_get("schedule_items", {
        "user_id": f"eq.{user_id}",
        "status": "in.(scheduled,pending)",
        "end_time": f"lt.{now.isoformat()}",
        "order": "end_time.asc",
        "limit": "10",
    })
    overdue = overdue_resp.json() if overdue_resp.status_code == 200 else []

    # Upcoming deadlines
    upcoming_resp = sb_get("schedule_items", {
        "user_id": f"eq.{user_id}",
        "deadline": f"gte.{now.isoformat()}",
        "deadline": f"lte.{week_end.isoformat()}",
        "status": "neq.completed",
        "order": "deadline.asc",
        "limit": "10",
    })
    upcoming_deadlines = upcoming_resp.json() if upcoming_resp.status_code == 200 else []

    return {
        "profile": profile,
        "today": today_items,
        "this_week": week_items,
        "pending_tasks": pending,
        "overdue": overdue,
        "upcoming_deadlines": upcoming_deadlines,
        "stats": {
            "today_count": len(today_items),
            "week_count": len(week_items),
            "pending_count": len(pending),
            "overdue_count": len(overdue),
        }
    }

# ---- FREE SLOTS ----

@app.get("/api/schedule/free-slots")
async def free_slots(
    user_id: str = Depends(get_user_id),
    start: Optional[str] = None,
    end: Optional[str] = None,
):
    """Get available free time slots for scheduling."""
    profile = await get_behavior_profile(user_id)
    now = _now_utc()
    s = _parse_dt(start + "T00:00:00+00:00") if start else now
    e = _parse_dt(end + "T23:59:59+00:00") if end else now + timedelta(days=7)
    slots = compute_free_slots(
        user_id, s, e,
        profile.get("wake_time", "07:00"),
        profile.get("sleep_time", "23:00"),
    )
    return {"slots": slots, "count": len(slots)}

# ---- PROJECTS ----

@app.get("/api/projects")
async def list_projects(user_id: str = Depends(get_user_id)):
    resp = sb_get("projects", {"user_id": f"eq.{user_id}", "status": "neq.cancelled", "order": "created_at.desc"})
    if resp.status_code == 200:
        return {"projects": resp.json() or []}
    return {"projects": []}

# ---- EVENTS (original Neuravex) ----

@app.get("/api/events")
async def list_events(user_id: str = Depends(get_user_id)):
    resp = sb_get("events", {"user_id": f"eq.{user_id}", "order": "event_datetime.asc"})
    if resp.status_code == 200:
        return {"events": resp.json() or []}
    return {"events": []}

# ---- PROCESS COMMAND (legacy compat) ----

@app.post("/api/command")
async def command(request: Request, user_id: str = Depends(get_user_id)):
    """Natural language command interpretation (backward compat)."""
    body = await request.json()
    message = body.get("command", body.get("message", ""))
    if not message:
        raise HTTPException(400, "command or message required")

    result = await process_chat_message(user_id, message)
    return {"success": True, **result}

# ==============================
# STARTUP
# ==============================

@app.on_event("startup")
async def startup():
    print("🚀 Chrona Backend v2.0 starting...")
    print(f"   Supabase: {'✅' if SUPABASE_URL else '❌'}")
    print(f"   Gemini:   {'✅' if gemini_client else '❌'}")
    print(f"   Groq:     {'✅' if groq_client else '❌'}")
    print(f"   Redis:    {'✅' if UPSTASH_REDIS_URL else '⚡ memory fallback'}")
    print(f"   EDF:      {'✅' if _scheduling_available else '⚠️ AI fallback'}")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000, reload=True)
