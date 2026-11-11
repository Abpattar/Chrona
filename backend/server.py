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
import re
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

def _eval_in_memory_filter(item: dict, key: str, val: Any) -> bool:
    target = str(val)
    op = "eq"
    if "." in target:
        prefix = target.split(".", 1)[0]
        if prefix in ["eq", "neq", "in", "lt", "lte", "gt", "gte"]:
            op = prefix
            target = target.split(".", 1)[1]

    item_val = item.get(key)
    if op == "eq":
        return str(item_val or "") == target
    elif op == "neq":
        return str(item_val or "") != target
    elif op == "in":
        allowed = [x.strip() for x in target.strip("()").split(",")]
        return str(item_val or "") in allowed

    if item_val is None:
        return False

    # Check datetime range comparison
    is_dt_target = False
    target_dt = None
    try:
        if isinstance(target, str) and ("T" in target or (len(target) == 10 and "-" in target)):
            target_dt = _parse_dt(target)
            is_dt_target = target_dt is not None
    except Exception:
        pass

    if is_dt_target and target_dt is not None:
        item_dt = _parse_dt(item_val)
        if not item_dt:
            return False
        if op == "lt": return item_dt < target_dt
        elif op == "lte": return item_dt <= target_dt
        elif op == "gt": return item_dt > target_dt
        elif op == "gte": return item_dt >= target_dt
        return True

    # Numeric or string range comparison
    try:
        n_val = float(item_val)
        n_tgt = float(target)
        if op == "lt": return n_val < n_tgt
        elif op == "lte": return n_val <= n_tgt
        elif op == "gt": return n_val > n_tgt
        elif op == "gte": return n_val >= n_tgt
    except (ValueError, TypeError):
        s_val = str(item_val)
        if op == "lt": return s_val < target
        elif op == "lte": return s_val <= target
        elif op == "gt": return s_val > target
        elif op == "gte": return s_val >= target

    return True

def sb_get(table: str, params: dict = None, service: bool = False) -> Any:
    try:
        resp = requests.get(f"{SUPABASE_URL}/rest/v1/{table}", headers=_sb_headers(service), params=params, timeout=2)
        if resp.status_code == 200:
            return resp
    except Exception:
        pass
    # In-memory fallback
    items = list(_in_memory_db.get(table, []))
    if params:
        limit_val = None
        order_val = None
        for k, v in params.items():
            if k == "limit":
                try:
                    limit_val = int(v)
                except Exception:
                    pass
                continue
            if k == "order":
                order_val = str(v)
                continue

            items = [it for it in items if _eval_in_memory_filter(it, k, v)]

        if order_val:
            reverse = "desc" in order_val.lower()
            order_col = order_val.split(".")[0].split(",")[0]
            try:
                items.sort(key=lambda x: str(x.get(order_col) or ""), reverse=reverse)
            except Exception:
                pass

        if limit_val is not None and limit_val > 0:
            items = items[:limit_val]

    return MockResponse(items, 200)

def sb_post(table: str, data: dict | list, service: bool = True) -> Any:
    # Reject dummy phantom tasks
    if table == "schedule_items":
        raw_items = data if isinstance(data, list) else [data]
        valid_post = []
        for it in raw_items:
            t_clean = (it.get("title") or "").strip().lower()
            if t_clean and t_clean not in ["pending task", "untitled", "task", "undefined", "null"]:
                valid_post.append(it)
        if not valid_post:
            return MockResponse([], 201)
        data = valid_post if isinstance(data, list) else valid_post[0]

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
# TIME & TIMEZONE HELPERS
# ==============================

try:
    from zoneinfo import ZoneInfo
except ImportError:
    ZoneInfo = None

def _now_utc() -> datetime:
    return datetime.now(timezone.utc)

def _get_default_tz() -> timezone:
    try:
        sys_tz = datetime.now().astimezone().tzinfo
        if sys_tz:
            return sys_tz
    except Exception:
        pass
    return timezone.utc

def get_user_timezone(req: Optional[Request] = None, context: Optional[dict] = None) -> timezone:
    """
    Extract user's timezone from HTTP headers, request context, or default to system local tz.
    """
    if req:
        tz_name = req.headers.get("x-timezone")
        if tz_name and ZoneInfo:
            try:
                return ZoneInfo(tz_name)
            except Exception:
                pass
        offset_val = req.headers.get("x-timezone-offset")
        if offset_val:
            try:
                return timezone(timedelta(minutes=int(offset_val)))
            except Exception:
                pass

    if context and isinstance(context, dict):
        tz_name = context.get("timezone")
        if tz_name and ZoneInfo:
            try:
                return ZoneInfo(tz_name)
            except Exception:
                pass
        offset_val = context.get("timezone_offset")
        if offset_val is not None:
            try:
                return timezone(timedelta(minutes=int(offset_val)))
            except Exception:
                pass

    return _get_default_tz()

def _ensure_utc(dt: Optional[datetime], user_tz: Optional[timezone] = None) -> Optional[datetime]:
    if dt is None:
        return None
    if dt.tzinfo is None:
        tz = user_tz or _get_default_tz()
        dt = dt.replace(tzinfo=tz)
    return dt.astimezone(timezone.utc)

def _parse_dt(s: Any, user_tz: Optional[timezone] = None) -> Optional[datetime]:
    if not s:
        return None
    if isinstance(s, datetime):
        return _ensure_utc(s, user_tz)
    try:
        s_clean = str(s).strip()
        if s_clean.endswith("Z"):
            s_clean = s_clean[:-1] + "+00:00"
        dt = datetime.fromisoformat(s_clean)
        return _ensure_utc(dt, user_tz)
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
    timezone: Optional[str] = None
    timezone_offset: Optional[str] = None
    client_time: Optional[str] = None

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
    wake_time: str = "08:00",
    sleep_time: str = "22:30",
    buffer_minutes: int = 15,
    user_tz: Optional[timezone] = None,
) -> List[dict]:
    """
    Compute actual free time slots for a user between start and end.
    Uses their current schedule (fixed + flexible items) and wake/sleep times.
    All wake/sleep day intervals are calculated in the user's local timezone (e.g. Asia/Kolkata)
    to prevent tasks from ever being scheduled during human sleep hours (10:30 PM to 08:00 AM).
    Returns list of {start, end} dicts with ISO strings.
    """
    existing = get_user_schedule(user_id, start, end)
    tz = user_tz or _get_default_tz()

    # Build busy intervals
    busy = []
    for item in existing:
        s = _parse_dt(item.get("start_time"), user_tz)
        e = _parse_dt(item.get("end_time"), user_tz)
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

    # Chop to wake/sleep windows per day in user local time
    free = []
    start_local = _ensure_utc(start).astimezone(tz)
    end_local = _ensure_utc(end).astimezone(tz)

    current_date = start_local.date()
    end_date = end_local.date()

    wh, wm = int(wake_time.split(":")[0]), int(wake_time.split(":")[1])
    sh, sm = int(sleep_time.split(":")[0]), int(sleep_time.split(":")[1])

    while current_date <= end_date:
        day_start_local = datetime(current_date.year, current_date.month, current_date.day, wh, wm, 0, tzinfo=tz)
        day_end_local   = datetime(current_date.year, current_date.month, current_date.day, sh, sm, 0, tzinfo=tz)

        day_start_utc = day_start_local.astimezone(timezone.utc)
        day_end_utc   = day_end_local.astimezone(timezone.utc)

        # Clamp to overall query window
        day_start = max(day_start_utc, _ensure_utc(start))
        day_end   = min(day_end_utc, _ensure_utc(end))

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

        current_date += timedelta(days=1)

    return free

# ==============================
# INTELLIGENT TASK INFERENCE, COGNITIVE RECOVERY & CONFLICT RESOLUTION ENGINE
# ==============================

def calculate_recovery_buffer_minutes(preceding_task: dict, succeeding_task: dict = None) -> int:
    """
    Calculate dynamic cognitive recovery and transition buffer based on task exertion.
    High exertion / stressful tasks (exams, interviews, presentations) require 30-45m recovery.
    Moderate exertion (gym, workouts, lectures) require 20-30m.
    Casual / low effort tasks require 10-15m.
    """
    p_title = (preceding_task.get("title") or "").lower()
    p_type = (preceding_task.get("task_type") or "").lower()

    # Differentiate actual exams from self-study sessions
    study_kws = ["study", "prep", "prepare", "revision", "revise", "homework", "reading"]
    p_is_study = any(k in p_title for k in study_kws) or p_type == "study"

    # High stress / cognitive exhaustion from actual test, interview, presentation, or surgery
    stressful_keywords = ["exam", "midterm", "final", "test", "quiz", "interview", "presentation", "defense", "surgery", "oral"]
    is_stressful = not p_is_study and (any(k in p_title for k in stressful_keywords) or p_type in ["exam", "quiz"])

    # Physical exhaustion / workouts
    workout_keywords = ["gym", "workout", "exercise", "run", "running", "swimming", "sports"]
    is_workout = any(k in p_title for k in workout_keywords) or p_type == "workout"

    # Deep focus
    focus_keywords = ["lecture", "class", "coding", "project", "assignment", "lab", "study"]
    is_focus = any(k in p_title for k in focus_keywords) or p_type in ["class", "lecture", "study"] or p_is_study

    if is_stressful:
        buffer_min = 35  # 30-45 min relaxation after stressful exam
    elif is_workout:
        buffer_min = 30  # shower & cooldown
    elif is_focus:
        buffer_min = 20
    else:
        buffer_min = 15

    # If succeeding task is social or travel-heavy, ensure at least 30m after exam
    if succeeding_task:
        s_title = (succeeding_task.get("title") or "").lower()
        if ("dinner" in s_title or "lunch" in s_title or "party" in s_title) and is_stressful:
            buffer_min = max(buffer_min, 30)

    return buffer_min

def check_upcoming_exam_context(subject_or_title: str, user_text: str = "", existing_items: list = None) -> dict:
    """
    Check if the user has an upcoming actual exam related to this study task,
    either mentioned in conversation or scheduled in existing items.
    Returns: {"has_upcoming_exam": bool, "exam_title": Optional[str], "reason": str}
    """
    import re
    s_title = (subject_or_title or "").lower()
    u_text = (user_text or "").lower()

    # Academic subjects
    subjects = [
        "math", "mathematics", "calculus", "algebra", "geometry", "physics",
        "chemistry", "biology", "history", "english", "literature", "computer science",
        "cs", "os", "operating systems", "algorithms", "dsa", "database", "sql",
        "economics", "microeconomics", "macroeconomics", "statistics", "stats",
        "science", "philosophy", "psychology", "sociology"
    ]
    matched_subjects = [sub for sub in subjects if re.search(r'\b' + re.escape(sub) + r'\b', s_title)]

    # 1. Check user utterance / conversation text for upcoming exam mentions
    exam_mention = re.search(r'\b(?:have|got|upcoming|scheduled)?\s*(?:an?|[a-z]+)?\s*exam\s+(?:on|in|next|tomorrow|this|at)\b', u_text)
    if exam_mention:
        return {
            "has_upcoming_exam": True,
            "exam_title": exam_mention.group(0),
            "reason": f"Upcoming exam mentioned in conversation: '{exam_mention.group(0)}'"
        }

    # 2. Check existing items in database for actual exams (task_type == 'exam' and not study)
    if existing_items:
        study_kws = ["study", "prep", "prepare", "revision", "revise", "homework", "reading"]
        for it in existing_items:
            it_title = (it.get("title") or "").lower()
            it_type = (it.get("task_type") or "").lower()
            it_is_study = any(k in it_title for k in study_kws) or it_type == "study"
            is_actual_exam = not it_is_study and (it_type in ["exam", "quiz"] or any(k in it_title for k in ["exam", "midterm", "final", "quiz", "test"]))
            if is_actual_exam:
                if matched_subjects:
                    if any(sub in it_title for sub in matched_subjects):
                        return {
                            "has_upcoming_exam": True,
                            "exam_title": it.get("title"),
                            "reason": f"Upcoming scheduled exam: '{it.get('title')}'"
                        }
                else:
                    return {
                        "has_upcoming_exam": True,
                        "exam_title": it.get("title"),
                        "reason": f"Upcoming scheduled exam: '{it.get('title')}'"
                    }

    return {
        "has_upcoming_exam": False,
        "exam_title": None,
        "reason": "No upcoming exam found in schedule or conversation"
    }

def infer_task_properties(task_dict: dict, user_text: str = "", existing_items: list = None) -> dict:
    """
    Intelligently infer task type, priority, flexibility, and LOCUS OF CONTROL based on description and context.
    
    LOCUS OF CONTROL HIERARCHY:
    1. External commitments (Not in user's control):
       - Family functions, weddings, ceremonies, receptions, anniversaries, parties
       - Medical & formal appointments (doctor, dentist, clinic, therapy, surgery, interview, court)
       - Public ticketed events (movies, concerts, theater, flights, trains)
       - Live college classes & labs
       - Actual scheduled exams
       -> controllability: "external", flexibility: False.
       These are FIXED ANCHORS that can NEVER be moved by personal tasks!

    2. Personal tasks (In user's control):
       - Study sessions, exam prep, homework, revision
       - Fitness, workouts, gym, running, instrument practice
       - Chores, cleaning, cooking
       - Casual meals & social hangouts (dinner, lunch, coffee)
       -> controllability: "personal", flexibility: True.
       These tasks are in the user's control and yield to external events.

    EXAM-AWARE STUDY PRIORITY:
    - Study sessions for an upcoming exam (present in schedule or mentioned) elevate to Priority 2 (High).
    - Study sessions without an upcoming exam default to Priority 3 (Normal).
    """
    title = (task_dict.get("title") or "").strip().lower()
    task_type = (task_dict.get("task_type") or "").strip().lower()
    combined_text = f"{title} {task_dict.get('description', '')} {user_text}".lower()

    # Explicit user importance check
    explicit_high_prio = any(
        kw in combined_text for kw in [
            "very important", "extremely important", "high priority", "top priority",
            "highest priority", "super important", "crucial", "must do", "urgent",
            "p1", "priority 1", "priority 2", "cannot miss"
        ]
    )

    # Explicit strict time check (e.g. user insists on that exact time or no decompression break)
    strict_time_keywords = [
        "at that time only", "at this time only", "strictly at", "only at that time",
        "very important at that time", "important at that time", "must be at that time",
        "only at", "exactly at", "no break", "skip break", "skip decompression",
        "without break", "don't need a break", "no decompression", "keep strictly",
        "keep at that time"
    ]
    is_strict_time = any(kw in combined_text for kw in strict_time_keywords)

    # 1. Study / Revision / Self-prep
    study_keywords = [
        "study", "revision", "revise", "homework", "prep", "prepare", "preparation",
        "review", "self-study", "reading", "practice problems"
    ]
    is_study = any(k in title for k in study_keywords) or task_type in ["study", "study_block", "homework", "revision"]

    # 2. Family / Social Obligation / Ceremonies (External / Uncontrollable)
    family_keywords = [
        "family", "function", "wedding", "reception", "ceremony", "anniversary",
        "gathering", "reunion", "baby shower", "puja", "pooja", "festival",
        "birthday party", "celebration", "relatives"
    ]
    is_family = any(k in title for k in family_keywords) or task_type == "family"

    # 3. Formal Appointments (External / Uncontrollable)
    appointment_keywords = [
        "doctor", "dentist", "appointment", "clinic", "therapy", "hospital",
        "checkup", "surgery", "consultation", "interview", "court", "hearing",
        "embassy", "visa"
    ]
    is_appointment = any(k in title for k in appointment_keywords) or task_type == "appointment"

    # 4. Ticketed / Public Scheduled Events (External / Uncontrollable)
    ticketed_keywords = [
        "movie", "cinema", "film", "theater", "theatre", "concert", "show",
        "match", "flight", "train", "bus"
    ]
    is_ticketed = any(k in title for k in ticketed_keywords) or task_type == "movie"

    # 5. Live Classes / Lectures / Labs (External / Uncontrollable)
    class_keywords = ["lecture", "class", "lab", "seminar", "college", "school"]
    is_class = (any(k in title for k in class_keywords) or task_type in ["class", "lecture"]) and not is_study

    # 6. Actual Exams / Tests (External / Uncontrollable)
    exam_keywords = ["exam", "midterm", "final", "quiz", "test"]
    is_actual_exam = (any(k in title for k in exam_keywords) or task_type in ["exam", "quiz"]) and not is_study

    # 7. Workouts / Fitness (Personal / Controllable)
    workout_keywords = [
        "gym", "workout", "exercise", "run", "running", "yoga", "swimming",
        "flute", "practice", "piano", "guitar"
    ]
    is_workout = any(k in title for k in workout_keywords) or task_type == "workout"

    # 8. Casual Social / Meals (Personal / Flexible)
    social_keywords = [
        "dinner", "lunch", "breakfast", "brunch", "coffee", "drinks",
        "hang out", "hangout", "friends", "meet friends", "eating", "restaurant", "club"
    ]
    is_social = any(k in title for k in social_keywords) and not is_family

    res = dict(task_dict)

    if task_dict.get("strict_time") or task_dict.get("skip_recovery_buffer") or is_strict_time:
        res["strict_time"] = True
        res["skip_recovery_buffer"] = True

    # Determine Controllability, Task Type, Priority, and Flexibility
    if is_actual_exam:
        res["controllability"] = "external"
        res["task_type"] = "exam"
        res["priority"] = 1
        res["flexibility"] = False
    elif is_family:
        res["controllability"] = "external"
        res["task_type"] = "family"
        res["priority"] = 2
        res["flexibility"] = False
    elif is_appointment:
        res["controllability"] = "external"
        res["task_type"] = "appointment"
        res["priority"] = 1 if any(k in title for k in ["surgery", "court", "hearing", "interview"]) else 2
        res["flexibility"] = False
    elif is_ticketed:
        res["controllability"] = "external"
        res["task_type"] = "movie" if any(k in title for k in ["movie", "cinema", "film", "theater", "theatre"]) else "fixed"
        res["priority"] = 1
        res["flexibility"] = False
    elif is_class:
        res["controllability"] = "external"
        res["task_type"] = "class"
        res["priority"] = 1
        res["flexibility"] = False
    elif is_study:
        res["controllability"] = "personal"
        res["task_type"] = "study"
        res["flexibility"] = True
        exam_ctx = check_upcoming_exam_context(title, user_text=user_text, existing_items=existing_items)
        if explicit_high_prio:
            res["priority"] = 2
        elif exam_ctx["has_upcoming_exam"]:
            res["priority"] = 2  # Urgent exam preparation
            res["exam_context"] = exam_ctx["reason"]
        else:
            res["priority"] = 3  # Normal self-study
    elif is_workout:
        res["controllability"] = "personal"
        res["task_type"] = "workout"
        res["priority"] = 2 if explicit_high_prio else 3
        res["flexibility"] = True
    elif is_social:
        res["controllability"] = "personal"
        res["task_type"] = "social"
        res["priority"] = 2 if explicit_high_prio else 3
        res["flexibility"] = True
    else:
        # Default fallback
        res["controllability"] = task_dict.get("controllability") or ("external" if task_type == "fixed" or not task_dict.get("flexibility", True) else "personal")
        res["task_type"] = res.get("task_type") or "task"
        res["priority"] = 2 if explicit_high_prio else int(res.get("priority") or 3)
        if "flexibility" not in res:
            res["flexibility"] = (res["controllability"] == "personal")

    return res

def check_collision_or_gap_issue(it1: dict, it2: dict, existing_items: list = None) -> tuple:
    """
    Check if two tasks collide or have an insufficient cognitive recovery gap.
    Returns (has_conflict, anchor, loser, gap_minutes).
    
    CONTROL & PRIORITY RULES:
    1. External (uncontrollable) events (Family functions, Appointments, Exams, Movies, Classes)
       ALWAYS anchor over Personal (controllable) tasks (Study, Workout, Chores, Casual Social).
       The personal task MUST yield and reschedule.
    2. If both share the same controllability (e.g. both external, or both personal):
       - Inflexible tasks (flexibility: False) anchor over flexible tasks.
       - Lower priority number wins (P1 Critical > P2 High > P3 Normal).
    """
    s1 = _parse_dt(it1.get("start_time"))
    e1 = _parse_dt(it1.get("end_time"))
    s2 = _parse_dt(it2.get("start_time"))
    e2 = _parse_dt(it2.get("end_time"))
    if not (s1 and e1 and s2 and e2):
        return False, None, None, 0

    prop1 = infer_task_properties(it1, existing_items=existing_items)
    prop2 = infer_task_properties(it2, existing_items=existing_items)

    ctrl1 = prop1.get("controllability", "personal")
    ctrl2 = prop2.get("controllability", "personal")

    # 1. CORE PRINCIPLE: External (uncontrollable) events ALWAYS anchor over personal (controllable) tasks.
    if ctrl1 == "external" and ctrl2 == "personal":
        anchor, loser = it1, it2
        anchor_prop, loser_prop = prop1, prop2
    elif ctrl1 == "personal" and ctrl2 == "external":
        anchor, loser = it2, it1
        anchor_prop, loser_prop = prop2, prop1
    # 2. Same controllability: check flexibility
    elif not prop1.get("flexibility", True) and prop2.get("flexibility", True):
        anchor, loser = it1, it2
        anchor_prop, loser_prop = prop1, prop2
    elif prop1.get("flexibility", True) and not prop2.get("flexibility", True):
        anchor, loser = it2, it1
        anchor_prop, loser_prop = prop2, prop1
    else:
        # 3. Same controllability & flexibility: compare priority
        score1 = (prop1.get("priority", 3), 0 if not prop1.get("flexibility", True) else 1)
        score2 = (prop2.get("priority", 3), 0 if not prop2.get("flexibility", True) else 1)
        if score1 <= score2:
            anchor, loser = it1, it2
            anchor_prop, loser_prop = prop1, prop2
        else:
            anchor, loser = it2, it1
            anchor_prop, loser_prop = prop2, prop1

    # 1. Direct Overlap: simultaneous physical presence clash
    if s1 < e2 and e1 > s2:
        return True, anchor, loser, 0

    # 2. Decompression gap check:
    # If the subordinate/flexible task was explicitly requested with strict time / skip break,
    # honor user choice and do not flag an adjacent gap conflict!
    if loser.get("strict_time") or loser.get("skip_recovery_buffer") or loser_prop.get("strict_time") or loser_prop.get("skip_recovery_buffer"):
        return False, None, None, 0

    # 3. Check recovery gap between adjacent tasks (e.g. Exam ends at 9:00 PM, Dinner starts at 9:00 PM)
    if e1 <= s2:
        gap = (s2 - e1).total_seconds() / 60
        req_buf = calculate_recovery_buffer_minutes(it1, it2)
        if gap < req_buf:
            return True, anchor, loser, int(req_buf - gap)
    elif e2 <= s1:
        gap = (s2 - e1).total_seconds() / 60
        req_buf = calculate_recovery_buffer_minutes(it1, it2)
        if gap < req_buf:
            return True, anchor, loser, int(req_buf - gap)
    elif e2 <= s1:
        gap = (s1 - e2).total_seconds() / 60
        req_buf = calculate_recovery_buffer_minutes(it2, it1)
        if gap < req_buf:
            return True, anchor, loser, int(req_buf - gap)

    return False, None, None, 0

    return False, None, None, 0

def find_optimal_reschedule_slot(to_reschedule: dict, anchor: dict, all_items: list, wake_time: str = "08:00", sleep_time: str = "22:30", user_tz: Optional[timezone] = None) -> tuple:
    """
    Find optimal slot for to_reschedule enforcing dynamic cognitive buffers, circadian human habits, and sane sleep boundaries.
    1. Sleep hours: NO event scheduled outside wake/sleep window (default 08:00 to 22:30 local).
    2. Uses today's remaining hours (e.g. pre-movie gap) before ever moving to subsequent days.
    3. Respects circadian meal windows (Dinner in evening 18:30-20:30, Lunch in afternoon 12:00-14:00).
    4. Day-of-week matching: 'every Sunday' / 'Sunday' strictly stays on Sunday.
    Returns (start_dt, end_dt, reason_note).
    """
    import re
    tz = user_tz or _get_default_tz()
    now_local = datetime.now(tz)
    now_utc = now_local.astimezone(timezone.utc)
    dur = int(to_reschedule.get("duration_minutes") or 60)

    anchor_start = _parse_dt(anchor.get("start_time"), user_tz) if anchor else None
    anchor_end = _parse_dt(anchor.get("end_time"), user_tz) if anchor else None

    # Determine target date
    title_lower = (to_reschedule.get("title") or "").lower()
    day_match = re.search(r'\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b', title_lower)
    day_names = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday']

    if day_match:
        target_idx = day_names.index(day_match.group(1))
        diff = (target_idx - now_local.weekday()) % 7
        target_date = now_local.date() + timedelta(days=diff)
    elif anchor_start:
        target_date = anchor_start.astimezone(tz).date()
    else:
        target_date = now_local.date()

    wh, wm = [int(x) for x in wake_time.split(":")]
    sh, sm = [int(x) for x in sleep_time.split(":")]
    wake_local = datetime(target_date.year, target_date.month, target_date.day, wh, wm, 0, tzinfo=tz)
    sleep_local = datetime(target_date.year, target_date.month, target_date.day, sh, sm, 0, tzinfo=tz)
    wake_dt = wake_local.astimezone(timezone.utc)
    sleep_dt = sleep_local.astimezone(timezone.utc)

    if not anchor_start or not anchor_end:
        # Fallback to evening focus hour on target date
        c_s_local = datetime(target_date.year, target_date.month, target_date.day, 18, 30, 0, tzinfo=tz)
        if c_s_local.astimezone(timezone.utc) < now_utc:
            c_s_local = datetime(target_date.year, target_date.month, target_date.day, 10, 0, 0, tzinfo=tz) + timedelta(days=1)
        c_s = c_s_local.astimezone(timezone.utc)
        return c_s, c_s + timedelta(minutes=dur), "Placed in available daytime hours."

    # Calculate dynamic recovery buffers
    post_anchor_buffer = calculate_recovery_buffer_minutes(anchor, to_reschedule)
    pre_anchor_buffer = calculate_recovery_buffer_minutes(to_reschedule, anchor)

    def is_slot_free(c_start, c_end):
        for it in all_items:
            if it.get("id") and to_reschedule.get("id") and it.get("id") == to_reschedule.get("id"):
                continue
            if (it.get("title") or "").strip().lower() == (to_reschedule.get("title") or "").strip().lower():
                continue

            it_s = _parse_dt(it.get("start_time"), user_tz)
            it_e = _parse_dt(it.get("end_time"), user_tz)
            if it_s and it_e:
                is_anchor = False
                if anchor:
                    if it.get("id") and anchor.get("id") and it.get("id") == anchor.get("id"):
                        is_anchor = True
                    elif (it.get("title") or "").strip().lower() == (anchor.get("title") or "").strip().lower():
                        is_anchor = True

                if is_anchor:
                    if c_start < it_e and c_end > it_s:
                        return False
                else:
                    buf = timedelta(minutes=10)
                    if c_start < (it_e + buf) and c_end > (it_s - buf):
                        return False
        return True

    def is_candidate_valid(c_start, c_end, is_today=False):
        if c_end > sleep_dt or c_start < wake_dt:
            return False  # Strict human sleep window violation
        if is_today and c_start < now_utc:
            return False  # Cannot schedule in the past
        return is_slot_free(c_start, c_end)

    is_today = (target_date == now_local.date())
    is_dinner = "dinner" in title_lower
    is_lunch = "lunch" in title_lower

    # Strict Time Override: Place immediately after anchor without decompression break
    skip_recovery = (
        to_reschedule.get("strict_time") or
        to_reschedule.get("skip_recovery_buffer") or
        infer_task_properties(to_reschedule).get("strict_time")
    )
    if skip_recovery:
        post_start = anchor_end
        post_end = post_start + timedelta(minutes=dur)
        if is_slot_free(post_start, post_end):
            time_display = post_start.astimezone(tz).strftime('%I:%M %p')
            note = f"Placed strictly at {time_display} right after '{anchor.get('title')}' as requested (decompression break skipped)."
            return post_start, post_end, note

    # Circadian Meal Prioritization:
    # If the task is Dinner, humans prefer 18:30 - 20:30.
    # If the anchor is an 8:00 PM Movie / Exam, having dinner BEFORE the anchor is the natural human choice!
    if is_dinner:
        # Check pre-anchor dinner gap (e.g. 18:30 - 19:30 before 20:00 Movie)
        pre_end = anchor_start - timedelta(minutes=max(pre_anchor_buffer, 15))
        pre_start = pre_end - timedelta(minutes=dur)
        if pre_start.minute not in [0, 30]:
            pre_start = pre_start.replace(minute=30 if pre_start.minute >= 30 else 0, second=0, microsecond=0)
            pre_end = pre_start + timedelta(minutes=dur)
        if (pre_end + timedelta(minutes=15)) <= anchor_start and is_candidate_valid(pre_start, pre_end, is_today):
            s_disp = pre_start.astimezone(tz).strftime('%I:%M %p')
            note = f"Scheduled Dinner at {s_disp} before '{anchor.get('title')}' with transition breathing room."
            return pre_start, pre_end, note

        # Check post-anchor dinner if anchor ends early enough before bedtime
        post_start = anchor_end + timedelta(minutes=post_anchor_buffer)
        rem = post_start.minute % 5
        if rem != 0:
            post_start += timedelta(minutes=(5 - rem))
        post_start = post_start.replace(second=0, microsecond=0)
        post_end = post_start + timedelta(minutes=dur)
        if is_candidate_valid(post_start, post_end, is_today):
            note = f"Added a {post_anchor_buffer}-minute decompression break after '{anchor.get('title')}' so you don't feel rushed."
            return post_start, post_end, note

    # Focus / Study Prioritization before Evening Commitments:
    # If the anchor is an evening event starting at 19:00 or later (Family function, Party, Dinner, Movie),
    # studying BEFORE the event (e.g. 18:30 - 19:30) is the natural human choice over studying late at night!
    is_study_task = ("study" in title_lower or "prep" in title_lower or "revision" in title_lower or "homework" in title_lower)
    anchor_hour = anchor_start.astimezone(tz).hour if anchor_start else None

    if is_study_task and anchor_hour and anchor_hour >= 19:
        # Check pre-anchor study slot first (e.g. 18:30 - 19:30 before 20:00 Family function)
        pre_end = anchor_start - timedelta(minutes=max(pre_anchor_buffer, 20))
        pre_start = pre_end - timedelta(minutes=dur)
        if pre_start.minute not in [0, 30]:
            pre_start = pre_start.replace(minute=30 if pre_start.minute >= 30 else 0, second=0, microsecond=0)
            pre_end = pre_start + timedelta(minutes=dur)
        if (pre_end + timedelta(minutes=20)) <= anchor_start and is_candidate_valid(pre_start, pre_end, is_today):
            s_disp = pre_start.astimezone(tz).strftime('%I:%M %p')
            note = f"Scheduled Study session at {s_disp} before '{anchor.get('title')}' so you are focused and relaxed for the evening."
            return pre_start, pre_end, note

    # Strategy 1: Post-anchor slot on target day (with stress recovery buffer)
    post_start = anchor_end + timedelta(minutes=post_anchor_buffer)
    rem = post_start.minute % 5
    if rem != 0:
        post_start += timedelta(minutes=(5 - rem))
    post_start = post_start.replace(second=0, microsecond=0)
    post_end = post_start + timedelta(minutes=dur)

    if is_candidate_valid(post_start, post_end, is_today):
        note = f"Added a {post_anchor_buffer}-minute decompression break after '{anchor.get('title')}' so you don't feel rushed."
        return post_start, post_end, note

    # Strategy 2: Pre-anchor slot on target day (giving breathing room before anchor)
    pre_end = anchor_start - timedelta(minutes=pre_anchor_buffer)
    pre_start = pre_end - timedelta(minutes=dur)
    if pre_start.minute not in [0, 30]:
        pre_start = pre_start.replace(minute=0 if pre_start.minute < 30 else 30, second=0, microsecond=0)
        pre_end = pre_start + timedelta(minutes=dur)

    if (pre_end + timedelta(minutes=pre_anchor_buffer)) <= anchor_start and is_candidate_valid(pre_start, pre_end, is_today):
        note = f"Scheduled before '{anchor.get('title')}' with a {pre_anchor_buffer}-minute transition buffer."
        return pre_start, pre_end, note

    orig_s = _parse_dt(to_reschedule.get("start_time"), user_tz) or _parse_dt(to_reschedule.get("fixed_start"), user_tz)
    orig_local = orig_s.astimezone(tz) if orig_s else None
    orig_hour = orig_local.hour if orig_local else None
    orig_minute = orig_local.minute if orig_local else 0

    # Strategy 3: Scan other optimal windows on the target day matching the nature of the task
    is_evening = ("date" in title_lower or "drinks" in title_lower or "party" in title_lower or is_dinner or (orig_hour is not None and orig_hour >= 19))
    if is_evening:
        # Evening social events only belong in the evening (18:00 - 20:30), never at 11 AM or 4 PM!
        day_scan_hours = [19, 20, 18]
    elif is_lunch:
        day_scan_hours = [12, 13, 14]
    else:
        day_scan_hours = [19, 18, 17, 16, 15, 14, 11, 10, 20]

    for hour in day_scan_hours:
        c_s_local = datetime(target_date.year, target_date.month, target_date.day, hour, 0, tzinfo=tz)
        c_s = c_s_local.astimezone(timezone.utc)
        c_e = c_s + timedelta(minutes=dur)
        if is_candidate_valid(c_s, c_e, is_today):
            time_str = c_s_local.strftime('%I:%M %p')
            note = f"Placed at {time_str} in an open focus window on {c_s_local.strftime('%A')} with plenty of breathing room."
            return c_s, c_e, note

    # Strategy 4: If target day is packed, spillover to subsequent days (Day + 1 to Day + 14)
    # Strictly in daytime waking hours, prioritizing the user's requested hour!
    candidate_hours = []
    if orig_hour is not None:
        candidate_hours.append(orig_hour)
        if orig_hour >= 18:
            # Evening events (Dinner, Date, Drinks, Movie, etc.)
            for h in [orig_hour - 1, orig_hour + 1, 20, 19, 21, 18]:
                if h not in candidate_hours and 17 <= h <= 21:
                    candidate_hours.append(h)
        elif 11 <= orig_hour <= 14:
            # Midday / Lunch
            for h in [12, 13, 14, 11]:
                if h not in candidate_hours:
                    candidate_hours.append(h)
        else:
            for h in [orig_hour + 1, orig_hour - 1, 11, 14, 15, 16, 17]:
                if h not in candidate_hours and 8 <= h <= 21:
                    candidate_hours.append(h)
    elif is_dinner:
        candidate_hours = [19, 20, 18, 21]
    elif is_lunch:
        candidate_hours = [13, 12, 14]
    else:
        candidate_hours = [11, 10, 14, 15, 16, 17, 18]

    for day_offset in range(1, 15):
        next_day = target_date + timedelta(days=day_offset)
        # If task has day of week (e.g. Sunday), only check matching day of week
        if day_match and next_day.weekday() != target_idx:
            continue

        for h in candidate_hours:
            m = orig_minute if h == orig_hour else 0
            c_s_local = datetime(next_day.year, next_day.month, next_day.day, h, m, tzinfo=tz)
            c_s = c_s_local.astimezone(timezone.utc)
            c_e = c_s + timedelta(minutes=dur)
            n_sleep = datetime(next_day.year, next_day.month, next_day.day, sh, sm, 0, tzinfo=tz).astimezone(timezone.utc)
            n_wake = datetime(next_day.year, next_day.month, next_day.day, wh, wm, 0, tzinfo=tz).astimezone(timezone.utc)
            if c_s >= n_wake and c_e <= n_sleep and is_slot_free(c_s, c_e):
                time_str = c_s_local.strftime('%A, %b %d at %I:%M %p')
                note = f"Your day around '{anchor.get('title')}' is packed. Rescheduled to {time_str} at your requested time."
                return c_s, c_e, note

    # Sane daytime fallback (tomorrow at requested hour or dinner time, never at night!)
    fallback_day = target_date + timedelta(days=1)
    fb_hour = orig_hour if (orig_hour and 8 <= orig_hour <= 21) else (19 if is_dinner else (13 if is_lunch else 10))
    fb_min = orig_minute if fb_hour == orig_hour else 0
    fallback_s_local = datetime(fallback_day.year, fallback_day.month, fallback_day.day, fb_hour, fb_min, 0, tzinfo=tz)
    fallback_s = fallback_s_local.astimezone(timezone.utc)
    return fallback_s, fallback_s + timedelta(minutes=dur), f"Rescheduled to {fallback_s_local.strftime('%A at %I:%M %p')}."

def resolve_and_save_schedule_item(user_id: str, task_data: dict, in_flight_items: list, user_text: str = "", user_tz: Optional[timezone] = None) -> tuple:
    """
    Save or update a task item while automatically detecting collisions
    and insufficient recovery gaps, rescheduling flexible tasks to optimal non-overlapping slots.
    Enforces day-of-week integrity and daytime wake/sleep boundaries.
    Respects LOCUS OF CONTROL: External commitments anchor firmly, personal tasks yield.
    Returns (saved_item, optional_explanation_note).
    """
    tz = user_tz or _get_default_tz()

    # Pre-fetch existing schedule to provide accurate exam context & collision tracking
    existing_resp = sb_get("schedule_items", {
        "user_id": f"eq.{user_id}",
        "status": "neq.cancelled",
    })
    existing_list = existing_resp.json() if existing_resp.status_code == 200 else []
    combined_existing = list(existing_list) + list(in_flight_items)

    inferred = infer_task_properties(task_data, user_text=user_text, existing_items=combined_existing)
    dur = int(inferred.get("duration_minutes") or 60)
    t_clean = (inferred.get("title") or "").strip()
    if not t_clean or t_clean.lower() in ["pending task", "untitled", "task", "undefined", "null"]:
        return None, None

    item = {
        "user_id": user_id,
        "title": t_clean,
        "task_type": inferred.get("task_type", "task"),
        "duration_minutes": dur,
        "priority": inferred.get("priority", 3),
        "flexibility": inferred.get("flexibility", True),
        "controllability": inferred.get("controllability", "personal"),
        "status": "pending",
    }
    if inferred.get("strict_time") or inferred.get("skip_recovery_buffer"):
        item["strict_time"] = True
        item["skip_recovery_buffer"] = True

    if task_data.get("deadline"):
        item["deadline"] = task_data["deadline"]

    raw_start = task_data.get("fixed_start") or task_data.get("start_time")
    if raw_start:
        s_dt = _parse_dt(raw_start, user_tz)
        if s_dt:
            item["start_time"] = s_dt.isoformat()
            raw_end = task_data.get("fixed_end") or task_data.get("end_time")
            e_dt = _parse_dt(raw_end, user_tz) if raw_end else None
            if not e_dt or e_dt <= s_dt:
                e_dt = s_dt + timedelta(minutes=dur)
            item["end_time"] = e_dt.isoformat()
            item["status"] = "scheduled"

    # Day-of-week and daytime safeguard:
    # E.g. 'a flute class every Sunday' MUST be placed on Sunday during waking hours!
    if item.get("start_time"):
        t_title_lower = (item.get("title") or "").lower()
        day_names = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday']
        cur_dt = _parse_dt(item["start_time"], user_tz).astimezone(tz)
        for d_idx, d_name in enumerate(day_names):
            if d_name in t_title_lower:
                if cur_dt.weekday() != d_idx:
                    diff = (d_idx - cur_dt.weekday()) % 7
                    if diff == 0:
                        diff = 7
                    cur_dt = cur_dt + timedelta(days=diff)
                break

        # Sane daytime clamp: no activity at 1:00 AM or 10:30 PM unless strict
        if not item.get("strict_time") and (cur_dt.hour >= 22 or cur_dt.hour < 8):
            f_h = 19 if "dinner" in t_title_lower else (13 if "lunch" in t_title_lower else 11)
            cur_dt = cur_dt.replace(hour=f_h, minute=0, second=0, microsecond=0)

        item["start_time"] = cur_dt.astimezone(timezone.utc).isoformat()
        item["end_time"] = (cur_dt + timedelta(minutes=dur)).astimezone(timezone.utc).isoformat()

    reschedule_note = None

    # Check for collisions or insufficient recovery gaps with existing scheduled items or items in this batch
    if item.get("start_time") and item.get("end_time"):
        combined = list(combined_existing)

        for other in combined:
            if (other.get("title") or "").strip().lower() == item["title"].strip().lower():
                continue

            has_conflict, anchor, loser, gap_deficit = check_collision_or_gap_issue(item, other, existing_items=combined)
            if has_conflict:
                orig_s_fmt = _parse_dt(loser.get("start_time"), user_tz).astimezone(tz).strftime("%I:%M %p")
                new_s, new_e, slot_note = find_optimal_reschedule_slot(loser, anchor, combined, user_tz=user_tz)
                new_s_fmt = new_s.astimezone(tz).strftime("%I:%M %p")
                new_e_fmt = new_e.astimezone(tz).strftime("%I:%M %p")

                a_prop = infer_task_properties(anchor, existing_items=combined)
                l_prop = infer_task_properties(loser, existing_items=combined)

                if a_prop.get("controllability") == "external" and l_prop.get("controllability") == "personal":
                    ctrl_msg = f"'{anchor.get('title')}' is a fixed external commitment (not in your control), while '{loser.get('title')}' is in your personal control."
                    reason = f"Auto-rescheduled from {orig_s_fmt} to {new_s_fmt}: {ctrl_msg} {slot_note}"
                    note_msg = (
                        f"⚡ Auto-Rescheduled: '{loser.get('title')}' was adjusted from {orig_s_fmt} to {new_s_fmt} – {new_e_fmt}. "
                        f"'{anchor.get('title')}' is an external commitment (not in your control), so '{loser.get('title')}' was shifted to an optimal focus slot."
                    )
                else:
                    reason = f"Auto-rescheduled from {orig_s_fmt} to {new_s_fmt}: {slot_note}"
                    note_msg = f"⚡ Auto-Rescheduled: '{loser.get('title')}' was adjusted from {orig_s_fmt} to {new_s_fmt} – {new_e_fmt}. {slot_note}"

                if loser == item:
                    item["start_time"] = new_s.isoformat()
                    item["end_time"] = new_e.isoformat()
                    item["status"] = "rescheduled"
                    item["auto_rescheduled"] = True
                    item["flexibility"] = True
                    item["completion_note"] = reason
                    reschedule_note = note_msg
                else:
                    loser_prop = infer_task_properties(other, existing_items=combined)
                    patch_data = {
                        "start_time": new_s.isoformat(),
                        "end_time": new_e.isoformat(),
                        "status": "rescheduled",
                        "auto_rescheduled": True,
                        "task_type": loser_prop["task_type"],
                        "priority": loser_prop["priority"],
                        "flexibility": True,
                        "controllability": loser_prop.get("controllability", "personal"),
                        "completion_note": reason,
                    }
                    if other.get("id"):
                        sb_patch("schedule_items", patch_data, params={"id": f"eq.{other['id']}", "user_id": f"eq.{user_id}"})
                    other.update(patch_data)
                    reschedule_note = note_msg

    if item.get("strict_time") and not reschedule_note:
        s_dt = _parse_dt(item.get("start_time"))
        reschedule_note = f"⚡ Strict Schedule: '{item['title']}' was placed strictly at {s_dt.strftime('%I:%M %p')} as requested (decompression break skipped)."

    # Deduplication check
    existing_resp = sb_get("schedule_items", {"user_id": f"eq.{user_id}", "status": "neq.cancelled"})
    existing_list = existing_resp.json() if existing_resp.status_code == 200 else []
    match = None
    t_lower = (item.get("title") or "").strip().lower()
    for ex in existing_list:
        ex_title = (ex.get("title") or "").strip().lower()
        if ex_title == t_lower or (t_lower and (t_lower in ex_title or ex_title in t_lower)):
            match = ex
            break

    saved = None
    if match:
        sb_patch("schedule_items", item, params={"id": f"eq.{match['id']}", "user_id": f"eq.{user_id}"})
        match.update(item)
        saved = match
    else:
        resp = sb_post("schedule_items", item)
        if resp.status_code in [200, 201]:
            try:
                res_json = resp.json()
                saved = res_json[0] if isinstance(res_json, list) else res_json
            except Exception:
                saved = item
        else:
            saved = item

    return saved, reschedule_note

def auto_repair_existing_conflicts(user_id: str, user_tz: Optional[timezone] = None) -> list:
    """
    Scan all active scheduled items for the user, detect any time overlaps or zero-minute gaps,
    sanitize corrupted timestamps, and automatically reschedule lower-priority/flexible items with recovery buffers.
    """
    tz = user_tz or _get_default_tz()
    resp = sb_get("schedule_items", {
        "user_id": f"eq.{user_id}",
        "status": "neq.cancelled",
        "order": "start_time.asc"
    })
    if resp.status_code != 200 or not resp.json():
        return []

    scheduled_items = [it for it in resp.json() if it.get("start_time")]

    # Purge any dummy phantom tasks
    valid_items = []
    for it in scheduled_items:
        t_clean = (it.get("title") or "").strip().lower()
        if not t_clean or t_clean in ["pending task", "untitled", "task", "undefined", "null"]:
            if it.get("id"):
                sb_delete("schedule_items", params={"id": f"eq.{it['id']}", "user_id": f"eq.{user_id}"})
            continue
        valid_items.append(it)
    scheduled_items = valid_items

    # 1. Sanitize naive, missing, or inverted timestamps, and correct night-time anomalies
    for it in scheduled_items:
        s_dt = _parse_dt(it.get("start_time"), user_tz)
        dur = int(it.get("duration_minutes") or 60)
        e_dt = _parse_dt(it.get("end_time"), user_tz)
        needs_patch = False
        patch = {}
        if s_dt and (not e_dt or e_dt <= s_dt):
            e_dt = s_dt + timedelta(minutes=dur)
            patch["end_time"] = e_dt.isoformat()
            needs_patch = True

        # Check day-of-week integrity (e.g. 'flute class every Sunday' placed on Saturday)
        t_title = (it.get("title") or "").lower()
        if s_dt and "sunday" in t_title and s_dt.astimezone(tz).weekday() != 6:
            diff = (6 - s_dt.astimezone(tz).weekday()) % 7
            if diff == 0:
                diff = 7
            s_dt = s_dt + timedelta(days=diff)
            e_dt = s_dt + timedelta(minutes=dur)
            patch["start_time"] = s_dt.isoformat()
            patch["end_time"] = e_dt.isoformat()
            needs_patch = True

        # Clamp middle-of-the-night tasks (e.g. 1:00 AM or 2:15 AM) into sane focus hours
        if s_dt and not it.get("strict_time") and (s_dt.astimezone(tz).hour >= 22 or s_dt.astimezone(tz).hour < 8):
            f_h = 20 if ("date" in t_title or "party" in t_title or "drinks" in t_title) else (19 if "dinner" in t_title else (13 if "lunch" in t_title else 11))
            s_dt = s_dt.astimezone(tz).replace(hour=f_h, minute=0, second=0, microsecond=0).astimezone(timezone.utc)
            e_dt = s_dt + timedelta(minutes=dur)
            patch["start_time"] = s_dt.isoformat()
            patch["end_time"] = e_dt.isoformat()
            needs_patch = True

        if s_dt and it.get("start_time") != s_dt.isoformat():
            patch["start_time"] = s_dt.isoformat()
            needs_patch = True
        if e_dt and it.get("end_time") != e_dt.isoformat():
            patch["end_time"] = e_dt.isoformat()
            needs_patch = True
        if needs_patch and it.get("id"):
            sb_patch("schedule_items", patch, params={"id": f"eq.{it['id']}", "user_id": f"eq.{user_id}"})
            it.update(patch)

    repaired = []

    for i in range(len(scheduled_items)):
        for j in range(i + 1, len(scheduled_items)):
            it1 = scheduled_items[i]
            it2 = scheduled_items[j]

            has_conflict, anchor, loser, gap_deficit = check_collision_or_gap_issue(it1, it2)
            if has_conflict:
                loser_prop = infer_task_properties(loser)
                new_s, new_e, slot_note = find_optimal_reschedule_slot(loser, anchor, scheduled_items, user_tz=user_tz)
                orig_str = _parse_dt(loser.get("start_time"), user_tz).astimezone(tz).strftime("%I:%M %p")
                new_s_str = new_s.astimezone(tz).strftime("%I:%M %p")
                new_e_str = new_e.astimezone(tz).strftime("%I:%M %p")

                reason = f"Auto-rescheduled from {orig_str} to {new_s_str}: {slot_note}"

                patch_data = {
                    "start_time": new_s.isoformat(),
                    "end_time": new_e.isoformat(),
                    "status": "rescheduled",
                    "auto_rescheduled": True,
                    "task_type": loser_prop["task_type"],
                    "priority": loser_prop["priority"],
                    "flexibility": True,
                    "completion_note": reason,
                }

                sb_patch("schedule_items", patch_data, params={"id": f"eq.{loser['id']}", "user_id": f"eq.{user_id}"})
                loser.update(patch_data)
                repaired.append({
                    "task_id": loser.get("id"),
                    "title": loser.get("title"),
                    "rescheduled_to": f"{new_s_str} – {new_e_str}",
                    "reason": reason
                })

    return repaired

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

def extract_context_hints(text: str) -> dict:
    """Extract quick hints like 'at 9 PM', 'Sunday 8pm', 'for 2 hours', '8 to 10 PM', 'Math exam' from user utterance."""
    import re
    t_lower = text.lower()
    hints = {}

    # 1. Time range check e.g. '8 to 10 PM', '8-10 PM', '8:00 to 10:00 PM', '8 and ends at 9'
    range_m = re.search(r'\b(\d{1,2}(?::\d{2})?)\s*(?:am|pm)?\s*(?:to|-|until|and\s+ends\s+at)\s*(\d{1,2}(?::\d{2})?)\s*(am|pm)\b', t_lower)
    if range_m:
        s_part, e_part, ampm = range_m.groups()
        hints["time_str"] = f"{s_part} {ampm.upper()}"
        try:
            s_hr = int(s_part.split(":")[0])
            s_mn = int(s_part.split(":")[1]) if ":" in s_part else 0
            e_hr = int(e_part.split(":")[0])
            e_mn = int(e_part.split(":")[1]) if ":" in e_part else 0
            if ampm == "pm" and e_hr < 12:
                e_hr += 12
                if s_hr < 12:
                    s_hr += 12
            diff_m = (e_hr * 60 + e_mn) - (s_hr * 60 + s_mn)
            if diff_m > 0:
                hints["duration_minutes"] = diff_m
        except Exception:
            pass
    else:
        time_m = re.search(r'\b(?:at\s+)?(\d{1,2}(?::\d{2})?\s*(?:am|pm))\b', t_lower)
        if time_m:
            hints["time_str"] = time_m.group(1).upper()

    day_m = re.search(r'\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday|today|tomorrow)\b', t_lower)
    if day_m:
        hints["day_str"] = day_m.group(1).capitalize()

    if "duration_minutes" not in hints:
        dur_h = re.search(r'(\d+)\s*(?:hours?|hrs?|h)\b', t_lower)
        if dur_h:
            hints["duration_minutes"] = int(dur_h.group(1)) * 60
        else:
            dur_m = re.search(r'(\d+)\s*(?:minutes?|mins?|m)\b', t_lower)
            if dur_m:
                hints["duration_minutes"] = int(dur_m.group(1))

    # Look for task title
    # Pattern A: explicit intent words
    title_m = re.search(r'\b(?:schedule|add|have|plan|for|about|task is|event is|it is for|it\'s for|its for)\s+([a-zA-Z0-9\s]{3,35}?)(?:\s+at|\s+on|\s+tomorrow|\s+next|\s+for|\.|\?|$)', text, re.IGNORECASE)
    if title_m:
        cand = title_m.group(1).strip()
        cand = re.sub(r'^(my|the|a|an)\s+', '', cand, flags=re.IGNORECASE).strip()
        if cand.lower() not in ["it", "that", "this", "something", "a task", "me", "my schedule"]:
            hints["title"] = cand

    # Pattern B: standalone domain titles e.g. "Math exam", "Dinner with friends", "Chemistry quiz", "Family function"
    if "title" not in hints:
        keywords = ["exam", "test", "quiz", "midterm", "final", "dinner", "lunch", "movie", "cinema", "workout", "gym", "flute", "piano", "class", "lecture", "function", "wedding", "appointment", "study", "reception"]
        for kw in keywords:
            if kw in t_lower:
                m_kw = re.search(rf'([a-zA-Z0-9\s]{{2,25}}?\b{kw}\b[a-zA-Z0-9\s]{{0,15}})', text, re.IGNORECASE)
                if m_kw:
                    cand = m_kw.group(1).strip()
                    cand = re.sub(r'^(for|about|my|the|a|an)\s+', '', cand, flags=re.IGNORECASE).strip()
                    cand = re.sub(r'\s+(at|on|tomorrow|next).*$', '', cand, flags=re.IGNORECASE).strip()
                    if cand and len(cand) >= 3:
                        hints["title"] = cand
                        break

    return hints

def compute_datetime_from_day_and_time(day_str: str, time_str: str, user_tz: Optional[timezone] = None) -> datetime:
    """Compute offset-aware UTC datetime given day name and time string in user's timezone (e.g. 'Sunday', '6 PM')."""
    import re
    tz = user_tz or _get_default_tz()
    now_local = datetime.now(tz)
    hr, mn = 18, 0  # default 6 PM
    if time_str:
        m = re.search(r'(\d{1,2})(?::(\d{2}))?\s*(am|pm)?', time_str.lower())
        if m:
            hr = int(m.group(1))
            mn = int(m.group(2) or 0)
            ampm = m.group(3)
            if ampm == 'pm' and hr < 12:
                hr += 12
            elif ampm == 'am' and hr == 12:
                hr = 0

    days = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday']
    target_date = now_local.date()
    if day_str and day_str.lower() in days:
        target_idx = days.index(day_str.lower())
        current_idx = now_local.weekday()
        diff = (target_idx - current_idx) % 7
        if diff == 0 and hr < now_local.hour:
            diff = 7
        target_date = now_local.date() + timedelta(days=diff)
    elif day_str and day_str.lower() == 'tomorrow':
        target_date = now_local.date() + timedelta(days=1)

    local_dt = datetime(target_date.year, target_date.month, target_date.day, hr, mn, 0, tzinfo=tz)
    return local_dt.astimezone(timezone.utc)

CHRONA_SYSTEM = """You are Chrona, an intelligent personal time-management and AI scheduling assistant.

Today's date is {today}.

You understand natural language about schedules, tasks, goals, classes, exams, workouts, and personal commitments.
You help users plan their time intelligently.

CRITICAL CONVERSATIONAL & EXTRACTION PRINCIPLES:
1. MULTI-TURN MEMORY & TASK UPDATES:
   - Always connect the user's latest message with the previous messages in the conversation!
   - If the user provides durations, times, or answers questions for a task mentioned previously (e.g. "at 9 PM", "for 2 hours", "on Sunday", "make it 30 mins"):
     * DO NOT ASK FOR THE TITLE, DATE, OR EVENT NAME AGAIN! The title and date are already in the conversation history!
     * Combine the title and the newly provided details to extract the finalized task in the "tasks" array immediately!
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
4. COGNITIVE RECOVERY & DECOMPRESSION BREAKS (CRITICAL):
   - Exhausting / stressful tasks (Exams, tests, midterms, finals, interviews) require a 35-minute cognitive recovery break by default!
   - If a succeeding task (such as Dinner with friends or Gym) is scheduled right after an exam (e.g. Exam ends at 9:00 PM and Dinner is at 9:00 PM), Chrona MUST by default insert a 35-minute decompression break and reschedule Dinner to 9:35 PM so the user doesn't burn out!
   - STRICT TIME EXCEPTION: If the user explicitly mentions that the succeeding event is "very important at that time only", "strictly at that time", "only at that time", or asks to skip the decompression break:
     * Place the event at that exact requested time (9:00 PM) without waiting for the break!
     * Explain: "Since you specified this is very important at that time only, I have placed Dinner with friends strictly at 9:00 PM with no decompression break after your Math exam. Would you like to keep this strictly at 9:00 PM or should I add the 35-minute recovery break?"
   - DIRECT SIMULTANEOUS CLASH (e.g. Exam 8-10 PM and Dinner 8-9 PM): A user cannot attend dinner while sitting in an exam room. Since the exam is Priority 1 fixed, if the user insisted on strict placement, place it immediately after the exam (10:00 PM with zero wait) rather than the impossible 8:00 PM!
5. TIMETABLE & SYLLABUS EXTRACTION:
   - When extracted text from an uploaded timetable or syllabus image/document is provided:
     * Extract ALL classes, lectures, labs, exams, and assignments.
     * Keep college lectures and exams as fixed (priority 1), and workouts/study as flexible (priority 3).
6. RESCHEDULING & MOVING EXISTING TASKS (CRITICAL):
   - When the user asks to move, reschedule, postpone, or change the time/date of an existing task (e.g. "move the date to 9pm on sunday", "reschedule math exam to 10am"):
   - You MUST include the updated task in the "tasks" array!
   - Set fixed_start to the new requested ISO time (e.g. Sunday 9:00 PM), fixed_end to fixed_start + duration, and set action to "reschedule".
   - NEVER leave "tasks" empty when the user tells you to move or reschedule a task! If "tasks" is empty, the database cannot be updated!
7. LOCUS OF CONTROL & TASK PRIORITIZATION (CRITICAL):
   - EXTERNAL COMMITMENTS (NOT in user's control):
     Events where the timing is governed externally by third parties, organizations, or tickets (Family functions, weddings, ceremonies, doctor appointments, interviews, live classes/lectures, formal exams, movies/shows, flights).
     * The user CANNOT move or alter the timing of a family function or wedding to accommodate personal study.
     * These commitments have controllability: "external" and flexibility: false. Their time is FIXED.
   - PERSONAL TASKS (IN user's control):
     Self-managed, personal activities where the user chooses when to do them (Study sessions, exam prep, homework, workouts, gym, reading, personal chores).
     * The user CAN control and shift their study or workout time to an optimal free slot.
     * These tasks have controllability: "personal" and flexibility: true.
   - COLLISION PRINCIPLE:
     When an external uncontrollable commitment conflicts with a personal task (e.g. "Family function at 8 PM" and "Study for math exam at 8 PM"):
     * The EXTERNAL event is the IMMOVABLE ANCHOR. It stays at 8:00 PM.
     * The PERSONAL task is the one that gets RESCHEDULED to an optimal free slot (e.g. earlier daytime focus window before the event, or next available slot).
   - CONTEXTUAL EXAM-AWARE STUDY PRIORITY:
     * Differentiate between an ACTUAL EXAM (task_type: "exam", priority: 1, controllability: "external") and STUDYING FOR AN EXAM (task_type: "study", controllability: "personal").
     * If the user has an upcoming exam in their schedule or mentions an upcoming exam, study sessions for that exam have elevated priority (Priority 2, High/Important), but remain personal/controllable (yielding to immovable external events, but prioritized over low-priority personal tasks like gaming/workouts).
     * If there is no upcoming exam, self-study is standard priority (Priority 3, Normal).

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
      "controllability": "external|personal",
      "task_type": "task|fixed|study|workout|practice|meeting|class|exam|assignment|quiz|family|appointment|movie",
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

async def process_chat_message(user_id: str, message: str, session_id: str = None, context: dict = None, user_tz: Optional[timezone] = None) -> dict:
    """
    Process a natural language message with persistent multi-turn memory and context merging.
    All date and time calculations are grounded in the user's local timezone.
    """
    tz = user_tz or _get_default_tz()
    now_local = datetime.now(tz)
    today = now_local.strftime("%Y-%m-%d %A")
    user_current_time = now_local.strftime("%I:%M %p")

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

    # Merge accumulated draft context across turns
    draft_dict = {}
    if pending_draft:
        if isinstance(pending_draft, dict):
            draft_dict = dict(pending_draft)
        elif isinstance(pending_draft, str):
            try:
                draft_dict = json.loads(pending_draft)
            except Exception:
                draft_dict = {"title": pending_draft}

    current_hints = extract_context_hints(message)
    for k, v in current_hints.items():
        if v:
            draft_dict[k] = v

    # If title is missing, search backwards in history turns
    if not draft_dict.get("title"):
        for turn in reversed(history_turns):
            if turn.get("role") == "user":
                h_prev = extract_context_hints(turn.get("content", ""))
                if h_prev.get("title"):
                    draft_dict["title"] = h_prev["title"]
                    break

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

    # Check move / reschedule intent for an existing task
    is_move_cmd = bool(re.search(r'\b(move|reschedule|shift|postpone|push|change\s+time|set\s+time)\b', message.lower()))
    if is_move_cmd:
        all_user_items = sb_get("schedule_items", {"user_id": f"eq.{user_id}", "status": "neq.cancelled"}).json() or []
        sorted_existing = sorted(all_user_items, key=lambda x: len(x.get("title") or ""), reverse=True)
        matched_item = None
        msg_lower = message.lower()
        for ex in sorted_existing:
            ex_title = (ex.get("title") or "").strip().lower()
            if not ex_title or ex_title in ["pending task", "untitled", "task"]:
                continue
            pattern = r'\b' + re.escape(ex_title) + r'\b'
            if re.search(pattern, msg_lower):
                matched_item = ex
                break

        if matched_item:
            t_day = current_hints.get("day_str") or "Sunday"
            t_time = current_hints.get("time_str") or "9:00 PM"
            target_start = compute_datetime_from_day_and_time(t_day, t_time, user_tz)
            dur = int(matched_item.get("duration_minutes") or 60)
            target_end = target_start + timedelta(minutes=dur)

            patch_data = {
                "start_time": target_start.isoformat(),
                "end_time": target_end.isoformat(),
                "status": "scheduled",
                "strict_time": True,
                "auto_rescheduled": False,
                "completion_note": f"Moved to {target_start.astimezone(tz).strftime('%A, %b %d at %I:%M %p')} as requested."
            }
            sb_patch("schedule_items", patch_data, params={"id": f"eq.{matched_item['id']}", "user_id": f"eq.{user_id}"})
            matched_item.update(patch_data)

            time_display = target_start.astimezone(tz).strftime('%A, %b %d at %I:%M %p')
            resp_msg = f"I've moved '{matched_item.get('title')}' to {time_display}."

            await cache_del(draft_cache_key)
            history_turns.append({"role": "user", "content": message})
            history_turns.append({"role": "assistant", "content": resp_msg})
            await cache_set(history_cache_key, json.dumps(history_turns[-10:]), ex=7200)

            return {
                "action": "reschedule",
                "message": resp_msg,
                "tasks": [matched_item],
                "clarification_needed": None,
                "schedule_trigger": True
            }

    pending_draft_str = ""
    clean_draft_title = (draft_dict.get("title") or "").strip()
    if clean_draft_title and clean_draft_title.lower() not in ["pending task", "untitled", "task"]:
        pending_draft_str = (
            f"\nACCUMULATED TASK IN PROGRESS ACROSS TURNS:\n"
            f"- Event Title: \"{clean_draft_title}\"\n"
            f"- Scheduled Time/Day: \"{draft_dict.get('day_str', '')} {draft_dict.get('time_str', '')}\"\n"
            f"- Target Duration: {draft_dict.get('duration_minutes', 60)} minutes\n"
            f"MANDATORY INSTRUCTION: You now have the accumulated details for this task. "
            f"Extract this task immediately into the 'tasks' array! "
            f"DO NOT ASK what task this is for! DO NOT ASK for date or time again!"
        )

    system = CHRONA_SYSTEM.replace("{today}", today)
    system += f"\nUser's current local time: {user_current_time} ({today}, Timezone: {tz})."
    system += "\nCRITICAL: Interpret all relative times (e.g. '6 PM', '5 PM', 'noon', 'tonight') in the user's local timezone. Provide ISO strings (e.g. YYYY-MM-DDTHH:MM:SS) representing the requested local time!"
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

        if t.get("fixed_start"):
            s_dt = _parse_dt(t["fixed_start"], user_tz)
            if s_dt:
                t["fixed_start"] = s_dt.isoformat()
                dur_m = int(t.get("duration_minutes") or 60)
                raw_e = t.get("fixed_end")
                e_dt = _parse_dt(raw_e, user_tz) if raw_e else None
                if not e_dt or e_dt <= s_dt:
                    e_dt = s_dt + timedelta(minutes=dur_m)
                t["fixed_end"] = e_dt.isoformat()

    # Deterministic fallback: If user provided title & time across turns but LLM hesitated
    if not extracted_tasks and draft_dict.get("title") and (draft_dict.get("time_str") or current_hints.get("time_str")):
        t_title = draft_dict["title"]
        t_time = draft_dict.get("time_str") or current_hints.get("time_str")
        t_day = draft_dict.get("day_str") or current_hints.get("day_str") or "Sunday"
        dt_start = compute_datetime_from_day_and_time(t_day, t_time, user_tz)
        dur = int(draft_dict.get("duration_minutes") or 60)
        t_prop = infer_task_properties({"title": t_title})

        synth_task = {
            "title": t_title,
            "duration_minutes": dur,
            "task_type": t_prop.get("task_type", "task"),
            "priority": t_prop.get("priority", 3),
            "flexibility": t_prop.get("flexibility", True),
            "fixed_start": dt_start.isoformat(),
            "fixed_end": (dt_start + timedelta(minutes=dur)).isoformat(),
        }
        extracted_tasks = [synth_task]
        result["tasks"] = extracted_tasks
        result["clarification_needed"] = None
        result["message"] = f"Understood! I have scheduled '{t_title}' for {t_day} at {t_time}."

    # Check strict time override in user message
    strict_keywords = [
        "at that time only", "at this time only", "strictly at", "only at that time",
        "very important at that time", "important at that time", "no break", "skip break",
        "skip decompression", "without break", "don't need a break", "no decompression"
    ]
    if any(k in message.lower() for k in strict_keywords):
        for t in extracted_tasks:
            t["strict_time"] = True
            t["skip_recovery_buffer"] = True
            t["priority"] = 2

    result["tasks"] = extracted_tasks

    # Memory update: Track active recent tasks and draft
    if extracted_tasks:
        await cache_del(draft_cache_key)
        recent_tasks.extend(extracted_tasks)
        await cache_set(recent_tasks_cache_key, json.dumps(recent_tasks[-6:]), ex=7200)
    elif result.get("clarification_needed") or draft_dict.get("title"):
        draft_dict["last_question"] = result.get("message", "")
        draft_dict["clarification_needed"] = result.get("clarification_needed")
        await cache_set(draft_cache_key, json.dumps(draft_dict), ex=3600)

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
async def chat(request: ChatRequest, req: Request, user_id: str = Depends(get_user_id)):
    """
    Main Chrona conversational endpoint.
    Processes natural language and returns structured response + extracted tasks.
    """
    if request.user_id != user_id:
        raise HTTPException(403, "User mismatch")

    user_tz = get_user_timezone(req=req, context=request.context or {"timezone": request.timezone, "timezone_offset": request.timezone_offset})

    result = await process_chat_message(
        user_id, request.message, request.session_id, request.context, user_tz=user_tz
    )

    # Auto-process extracted tasks if present
    inserted_tasks = []
    reschedule_notes = []
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
                saved_item, conflict_note = resolve_and_save_schedule_item(user_id, task_data, inserted_tasks, user_text=request.message, user_tz=user_tz)
                if saved_item:
                    inserted_tasks.append(saved_item)
                if conflict_note:
                    reschedule_notes.append(conflict_note)

    final_msg = result.get("message", "I understand. Let me help you with that.")
    if reschedule_notes:
        final_msg = final_msg.strip() + "\n\n" + "\n".join(reschedule_notes)

    # Contextual quick actions for decompression break overrides
    quick_actions = []
    for t in inserted_tasks:
        if t.get("auto_rescheduled") and "decompression" in (t.get("completion_note") or "").lower():
            s_dt = _parse_dt(t.get("start_time"), user_tz)
            time_display = s_dt.astimezone(user_tz).strftime('%I:%M %p') if s_dt else ''
            quick_actions.append({
                "label": f"⚡ Keep '{t.get('title')}' strictly (Skip break)",
                "task_id": t.get("id"),
                "task_title": t.get("title"),
                "target_time": t.get("start_time"),
                "skip_break": True,
                "action": "override_break",
                "variant": "warning"
            })
            quick_actions.append({
                "label": f"🧘 Keep 35m Decompression Break ({time_display})",
                "action": "dismiss",
                "variant": "primary"
            })
        elif t.get("strict_time") or t.get("skip_recovery_buffer"):
            quick_actions.append({
                "label": f"🧘 Add 35m Decompression Break for '{t.get('title')}'",
                "task_id": t.get("id"),
                "task_title": t.get("title"),
                "skip_break": False,
                "action": "override_break",
                "variant": "primary"
            })
            quick_actions.append({
                "label": "✓ Keep strictly at requested time (No break)",
                "action": "dismiss",
                "variant": "secondary"
            })

    return {
        "message": final_msg,
        "action": result.get("action"),
        "tasks_extracted": result.get("tasks", []),
        "tasks_saved": inserted_tasks,
        "clarification_needed": result.get("clarification_needed"),
        "schedule_trigger": result.get("schedule_trigger", False),
        "decomposition": result.get("decomposition"),
        "quick_actions": quick_actions,
    }

@app.post("/api/chat/upload")
async def chat_upload(
    req: Request,
    message: Optional[str] = Form(""),
    file: Optional[UploadFile] = File(None),
    extracted_text: Optional[str] = Form(None),
    user_id: str = Depends(get_user_id),
    session_id: Optional[str] = Form(None)
):
    user_tz = get_user_timezone(req)
    """
    Unified Chat + Upload endpoint.
    Accepts text message, uploaded timetable/syllabus/document, or OCR extracted text.
    Extracts events/commitments, auto-plans them, detects collisions, and returns response.
    """
    file_text = ""
    file_name = ""
    is_image = False
    if file:
        file_name = file.filename or "uploaded_file"
        is_image = file_name.lower().endswith((".jpg", ".jpeg", ".png", ".webp", ".bmp"))
        try:
            content = await file.read()
            if extracted_text and extracted_text.strip():
                file_text = extracted_text.strip()
            elif is_image:
                # Server-side OCR execution fallback via node tesseract
                try:
                    import tempfile, subprocess
                    suffix = os.path.splitext(file_name)[1] or ".jpg"
                    with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tmp:
                        tmp.write(content)
                        tmp_path = tmp.name
                    res = subprocess.run(
                        ["node", "scripts/ocr_extract.mjs", tmp_path],
                        capture_output=True,
                        text=True,
                        timeout=15
                    )
                    if res.returncode == 0 and res.stdout.strip():
                        file_text = res.stdout.strip()
                    if os.path.exists(tmp_path):
                        os.unlink(tmp_path)
                except Exception as e:
                    print(f"⚠️ Server OCR fallback notice: {e}")
            elif file_name.lower().endswith(".pdf") or content.startswith(b"%PDF"):
                file_text = extract_text_from_pdf(content)
            else:
                try:
                    file_text = content.decode("utf-8")
                except UnicodeDecodeError:
                    file_text = extract_text_from_pdf(content)
        except Exception as e:
            print(f"⚠️ Error reading file: {e}")
    elif extracted_text and extracted_text.strip():
        file_text = extracted_text.strip()

    full_user_input = message.strip() if message else ""
    if file_name and file_text:
        attachment_context = f"\n[Timetable / Syllabus OCR Extracted Content ({file_name})]:\n{file_text[:5000]}"
        full_user_input = f"{full_user_input}\n{attachment_context}".strip()
    elif is_image and not file_text and not full_user_input:
        session_key = session_id or "default"
        await cache_del(f"chrona:draft:{user_id}:{session_key}")
        return {
            "message": (
                f"I received your uploaded image '{file_name}'. I wasn't able to automatically detect legible text in this image. "
                f"Could you please type or paste your key schedule commitments (e.g. 'Math exam Sunday 8-9 PM, Dinner at 9 PM')? "
                f"I'll immediately organize your schedule with conflict resolution and decompression breaks!"
            ),
            "action": "clarification",
            "tasks_extracted": [],
            "tasks_saved": [],
            "file_name": file_name,
            "clarification_needed": None,
            "schedule_trigger": False,
            "decomposition": None,
            "quick_actions": [],
        }

    if not full_user_input:
        full_user_input = "Please analyze the uploaded document and extract my schedule commitments and deadlines."

    result = await process_chat_message(user_id, full_user_input, session_id=session_id, user_tz=user_tz)

    inserted_tasks = []
    reschedule_notes = []
    if result.get("tasks"):
        for task_data in result["tasks"]:
            saved_item, conflict_note = resolve_and_save_schedule_item(user_id, task_data, inserted_tasks, user_text=full_user_input, user_tz=user_tz)
            if saved_item:
                inserted_tasks.append(saved_item)
            if conflict_note:
                reschedule_notes.append(conflict_note)

    final_msg = result.get("message", "I analyzed your document and extracted the relevant tasks.")
    if is_image and not file_text and not inserted_tasks:
        final_msg = (
            f"I received your uploaded image '{file_name}'. I wasn't able to automatically detect legible text in this image. "
            f"Could you please type or paste your key schedule commitments (e.g. 'Math exam Sunday 8-9 PM, Dinner at 9 PM')? "
            f"I'll immediately organize your schedule with conflict resolution and decompression breaks!"
        )
        result["clarification_needed"] = None
    elif reschedule_notes:
        final_msg = final_msg.strip() + "\n\n" + "\n".join(reschedule_notes)

    quick_actions = []
    for t in inserted_tasks:
        if t.get("auto_rescheduled") and "decompression" in (t.get("completion_note") or "").lower():
            s_dt = _parse_dt(t.get("start_time"), user_tz)
            time_display = s_dt.astimezone(user_tz).strftime('%I:%M %p') if s_dt else ''
            quick_actions.append({
                "label": f"⚡ Keep '{t.get('title')}' strictly (Skip break)",
                "task_id": t.get("id"),
                "task_title": t.get("title"),
                "target_time": t.get("start_time"),
                "skip_break": True,
                "action": "override_break",
                "variant": "warning"
            })
            quick_actions.append({
                "label": f"🧘 Keep 35m Decompression Break ({time_display})",
                "action": "dismiss",
                "variant": "primary"
            })
        elif t.get("strict_time") or t.get("skip_recovery_buffer"):
            quick_actions.append({
                "label": f"🧘 Add 35m Decompression Break for '{t.get('title')}'",
                "task_id": t.get("id"),
                "task_title": t.get("title"),
                "skip_break": False,
                "action": "override_break",
                "variant": "primary"
            })
            quick_actions.append({
                "label": "✓ Keep strictly at requested time (No break)",
                "action": "dismiss",
                "variant": "secondary"
            })

    return {
        "message": final_msg,
        "action": result.get("action"),
        "tasks_extracted": result.get("tasks", []),
        "tasks_saved": inserted_tasks,
        "file_name": file_name,
        "clarification_needed": result.get("clarification_needed"),
        "schedule_trigger": result.get("schedule_trigger", False),
        "decomposition": result.get("decomposition"),
        "quick_actions": quick_actions,
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
async def schedule_auto_reschedule(request: AutoRescheduleRequest, req: Request, user_id: str = Depends(get_user_id)):
    """Automatically find and apply the next best free slot for impossible/missed/overdue tasks and resolve conflicts."""
    if request.user_id != user_id:
        raise HTTPException(403, "User mismatch")

    user_tz = get_user_timezone(req)
    tz = user_tz or _get_default_tz()
    now_local = datetime.now(tz)
    now = now_local.astimezone(timezone.utc)
    profile = await get_behavior_profile(user_id)
    wake = profile.get("wake_time", "08:00")
    sleep = profile.get("sleep_time", "22:30")

    # If rescheduling ALL unplaced, run repair.
    # But if user requested a specific task_id, ONLY reschedule that specific task!
    conflicts_repaired = []
    if not request.task_id:
        conflicts_repaired = auto_repair_existing_conflicts(user_id, user_tz=user_tz)

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

    if not target_tasks and not conflicts_repaired:
        raise HTTPException(404, "No tasks found to reschedule")

    slots = compute_free_slots(user_id, now, now + timedelta(days=14), wake, sleep, user_tz=user_tz)
    rescheduled_items = []

    for cr in conflicts_repaired:
        rescheduled_items.append({
            "task_id": cr.get("task_id"),
            "title": cr.get("title", "Untitled"),
            "rescheduled_to": cr.get("rescheduled_to"),
            "auto_rescheduled": True,
            "reason": cr.get("reason"),
        })

    for task in target_tasks:
        dur = int(task.get("duration_minutes") or 60)
        t_title = (task.get("title") or "").lower()
        is_dinner = "dinner" in t_title
        is_lunch = "lunch" in t_title

        chosen_slot = None
        # Check meal circadian windows first
        if is_dinner:
            for d_off in range(0, 14):
                chk_d = now_local.date() + timedelta(days=d_off)
                for hr, mn in [(18, 30), (19, 0), (19, 30), (20, 0)]:
                    s_loc = datetime(chk_d.year, chk_d.month, chk_d.day, hr, mn, 0, tzinfo=tz)
                    s_utc = s_loc.astimezone(timezone.utc)
                    e_utc = s_utc + timedelta(minutes=dur)
                    if s_utc >= now:
                        for fs in slots:
                            fs_s = _parse_dt(fs["start"])
                            fs_e = _parse_dt(fs["end"])
                            if fs_s and fs_e and fs_s <= s_utc and fs_e >= e_utc:
                                chosen_slot = (s_utc, e_utc)
                                break
                    if chosen_slot:
                        break
                if chosen_slot:
                    break
        elif is_lunch:
            for d_off in range(0, 14):
                chk_d = now_local.date() + timedelta(days=d_off)
                for hr, mn in [(12, 30), (13, 0), (13, 30)]:
                    s_loc = datetime(chk_d.year, chk_d.month, chk_d.day, hr, mn, 0, tzinfo=tz)
                    s_utc = s_loc.astimezone(timezone.utc)
                    e_utc = s_utc + timedelta(minutes=dur)
                    if s_utc >= now:
                        for fs in slots:
                            fs_s = _parse_dt(fs["start"])
                            fs_e = _parse_dt(fs["end"])
                            if fs_s and fs_e and fs_s <= s_utc and fs_e >= e_utc:
                                chosen_slot = (s_utc, e_utc)
                                break
                    if chosen_slot:
                        break
                if chosen_slot:
                    break

        if not chosen_slot:
            for s in slots:
                s_start = _parse_dt(s["start"])
                s_end = _parse_dt(s["end"])
                if s_start and s_end and s_start >= now:
                    avail_min = (s_end - s_start).total_seconds() / 60
                    if avail_min >= min(dur, 30):
                        chosen_slot = (s_start, min(s_end, s_start + timedelta(minutes=dur)))
                        s["start"] = (s_start + timedelta(minutes=dur + 15)).isoformat()
                        break

        if not chosen_slot:
            tom = now_local.date() + timedelta(days=1)
            f_h = 19 if is_dinner else (12 if is_lunch else 10)
            tom_dt_local = datetime(tom.year, tom.month, tom.day, f_h, 0, 0, tzinfo=tz)
            tom_s = tom_dt_local.astimezone(timezone.utc)
            chosen_slot = (tom_s, tom_s + timedelta(minutes=dur))

        new_start, new_end = chosen_slot
        s_disp = new_start.astimezone(tz).strftime('%a, %b %d at %I:%M %p')
        e_disp = new_end.astimezone(tz).strftime('%I:%M %p')
        reason_msg = f"Auto-rescheduled to {s_disp} – {e_disp} in available daytime hours."
        update_data = {
            "start_time": new_start.isoformat(),
            "end_time": new_end.isoformat(),
            "status": "rescheduled",
            "auto_rescheduled": True,
            "flexibility": True,
            "completion_note": reason_msg,
        }
        sb_patch("schedule_items", update_data, params={"id": f"eq.{task['id']}", "user_id": f"eq.{user_id}"})
        rescheduled_items.append({
            "task_id": task["id"],
            "title": task.get("title", "Untitled"),
            "new_start": new_start.isoformat(),
            "new_end": new_end.isoformat(),
            "auto_rescheduled": True,
            "reason": reason_msg,
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
    req: Request,
    user_id: str = Depends(get_user_id),
    start: Optional[str] = None,
    end: Optional[str] = None,
    status: Optional[str] = None,
):
    """Get schedule items for the user with automatic conflict detection and repair."""
    user_tz = get_user_timezone(req)
    try:
        auto_repair_existing_conflicts(user_id, user_tz=user_tz)
    except Exception as e:
        print(f"⚠️ Conflict auto-repair warning: {e}")

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

@app.post("/api/schedule/resolve-conflicts")
async def schedule_resolve_conflicts(req: Request, user_id: str = Depends(get_user_id)):
    """Detect and auto-reschedule all conflicting overlapping tasks for user."""
    user_tz = get_user_timezone(req)
    repaired = auto_repair_existing_conflicts(user_id, user_tz=user_tz)
    return {
        "success": True,
        "repaired_count": len(repaired),
        "repaired": repaired,
        "message": f"Successfully resolved {len(repaired)} schedule conflict(s)." if repaired else "No schedule conflicts detected."
    }

class OverrideBreakRequest(BaseModel):
    user_id: Optional[str] = "00000000-0000-0000-0000-000000000001"
    task_id: Optional[str] = None
    task_title: Optional[str] = None
    target_time: Optional[str] = None
    skip_break: bool = True

@app.post("/api/schedule/override-break")
async def schedule_override_break(req: OverrideBreakRequest, request: Request, user_id: str = Depends(get_user_id)):
    """
    Override or restore decompression break for a specific task.
    If skip_break=True: places task strictly at target_time without decompression buffer.
    If skip_break=False: restores decompression break after preceding stressful task.
    """
    user_tz = get_user_timezone(request)
    tz = user_tz or _get_default_tz()

    target_item = None
    resp = sb_get("schedule_items", {"user_id": f"eq.{user_id}", "status": "neq.cancelled"})
    items = resp.json() if resp.status_code == 200 else []

    if req.task_id:
        for it in items:
            if it.get("id") == req.task_id:
                target_item = it
                break

    if not target_item and req.task_title:
        t_clean = req.task_title.strip().lower()
        for it in items:
            it_t = (it.get("title") or "").strip().lower()
            if t_clean in it_t or it_t in t_clean:
                target_item = it
                break

    if not target_item:
        raise HTTPException(404, "Task not found")

    dur = int(target_item.get("duration_minutes") or 60)

    if req.skip_break:
        new_start = _parse_dt(req.target_time, user_tz) if req.target_time else _parse_dt(target_item.get("start_time"), user_tz)
        if not new_start:
            now_local = datetime.now(tz)
            new_start = now_local.replace(hour=21, minute=0, second=0, microsecond=0).astimezone(timezone.utc)

        new_end = new_start + timedelta(minutes=dur)
        time_display = new_start.astimezone(tz).strftime('%I:%M %p')
        patch_data = {
            "start_time": new_start.isoformat(),
            "end_time": new_end.isoformat(),
            "strict_time": True,
            "skip_recovery_buffer": True,
            "status": "scheduled",
            "completion_note": f"Strict placement by user request: Decompression break skipped (scheduled at {time_display})."
        }
        sb_patch("schedule_items", patch_data, params={"id": f"eq.{target_item['id']}", "user_id": f"eq.{user_id}"})
        target_item.update(patch_data)
        return {
            "success": True,
            "item": target_item,
            "message": f"Updated! '{target_item['title']}' is now scheduled strictly at {time_display} with no decompression break."
        }
    else:
        # Restore decompression break
        anchor = None
        t_start = _parse_dt(target_item.get("start_time"), user_tz)
        for other in items:
            if other.get("id") == target_item.get("id"):
                continue
            o_end = _parse_dt(other.get("end_time"), user_tz)
            if o_end and t_start and (t_start - o_end).total_seconds() >= -1800 and (t_start - o_end).total_seconds() <= 7200:
                anchor = other
                break

        if anchor:
            new_start, new_end, note = find_optimal_reschedule_slot(target_item, anchor, items, user_tz=user_tz)
        else:
            new_start = t_start + timedelta(minutes=35) if t_start else _now_utc()
            new_end = new_start + timedelta(minutes=dur)
            note = "Decompression break of 35m restored."

        start_str = new_start.astimezone(tz).strftime('%I:%M %p')
        end_str = new_end.astimezone(tz).strftime('%I:%M %p')
        patch_data = {
            "start_time": new_start.isoformat(),
            "end_time": new_end.isoformat(),
            "strict_time": False,
            "skip_recovery_buffer": False,
            "status": "rescheduled",
            "completion_note": note,
        }
        sb_patch("schedule_items", patch_data, params={"id": f"eq.{target_item['id']}", "user_id": f"eq.{user_id}"})
        target_item.update(patch_data)
        return {
            "success": True,
            "item": target_item,
            "message": f"Decompression break restored! '{target_item['title']}' moved to {start_str} – {end_str}."
        }

@app.post("/api/schedule/fixed")
async def schedule_fixed_create(item: FixedCommitmentCreate, req: Request, user_id: str = Depends(get_user_id)):
    """Create a commitment with intelligent inference and conflict resolution."""
    if item.user_id != user_id:
        raise HTTPException(403, "User mismatch")

    user_tz = get_user_timezone(req)
    dur = int((item.end_time - item.start_time).total_seconds() / 60)
    task_data = {
        "title": item.title,
        "fixed_start": item.start_time.isoformat(),
        "fixed_end": item.end_time.isoformat(),
        "start_time": item.start_time.isoformat(),
        "end_time": item.end_time.isoformat(),
        "duration_minutes": dur,
        "recurrence": item.recurrence,
    }
    saved_item, conflict_note = resolve_and_save_schedule_item(user_id, task_data, [], user_text=item.title, user_tz=user_tz)
    return {
        "success": True,
        "item": saved_item,
        "reschedule_note": conflict_note
    }

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
async def get_user_context(req: Request, user_id: str = Depends(get_user_id)):
    """Get user's full context: profile, upcoming schedule, pending tasks."""
    profile = await get_behavior_profile(user_id)
    user_tz = get_user_timezone(req)

    now = _now_utc()
    now_local = now.astimezone(user_tz)
    today_start_local = now_local.replace(hour=0, minute=0, second=0, microsecond=0)
    today_end_local = today_start_local + timedelta(days=1)
    week_end_local = today_start_local + timedelta(days=7)

    today_start = today_start_local.astimezone(timezone.utc)
    today_end = today_end_local.astimezone(timezone.utc)
    week_end = week_end_local.astimezone(timezone.utc)

    # Fetch all user items to build consistent, disjoint partitions
    all_resp = sb_get("schedule_items", {
        "user_id": f"eq.{user_id}",
        "status": "neq.cancelled",
    })
    all_items = all_resp.json() if all_resp.status_code == 200 else []

    # Filter out any phantom dummy tasks
    all_items = [
        it for it in all_items
        if (it.get("title") or "").strip().lower() not in ["pending task", "untitled", "task", "undefined", "null"]
    ]

    # Today's schedule: items scheduled on today's local date
    today_items = []
    for it in all_items:
        s = _parse_dt(it.get("start_time"), user_tz)
        if s and today_start <= s < today_end and it.get("status") in ["scheduled", "rescheduled", "completed"]:
            today_items.append(it)
    today_items.sort(key=lambda x: _parse_dt(x.get("start_time"), user_tz) or now)

    # This week's schedule
    week_items = []
    for it in all_items:
        s = _parse_dt(it.get("start_time"), user_tz)
        if s and today_start <= s < week_end and it.get("status") in ["scheduled", "rescheduled", "completed"]:
            week_items.append(it)
    week_items.sort(key=lambda x: _parse_dt(x.get("start_time"), user_tz) or now)

    # Pending tasks: tasks not yet assigned a specific time slot
    pending = [
        it for it in all_items
        if it.get("status") == "pending" and not it.get("start_time")
    ]
    pending.sort(key=lambda x: int(x.get("priority") or 3))

    # Overdue tasks: past uncompleted items
    overdue = []
    for it in all_items:
        e = _parse_dt(it.get("end_time"), user_tz)
        if e and e < now and it.get("status") in ["scheduled", "pending", "rescheduled"]:
            overdue.append(it)
    overdue.sort(key=lambda x: _parse_dt(x.get("end_time"), user_tz) or now)

    # Upcoming deadlines: tasks that have an explicit deadline in the future
    upcoming_deadlines = []
    for it in all_items:
        d_str = it.get("deadline")
        if d_str and it.get("status") != "completed":
            d_dt = _parse_dt(d_str, user_tz)
            if d_dt and now <= d_dt <= week_end:
                upcoming_deadlines.append(it)
    upcoming_deadlines.sort(key=lambda x: _parse_dt(x.get("deadline"), user_tz) or now)

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

# ---- AI-ASSISTED TASK EXECUTION (SECTION 4H) ----

@app.post("/api/agents/execute")
async def execute_agent(request: AgentExecuteRequest, user_id: str = Depends(get_user_id)):
    """
    Delegate a task to a specialized AI Execution Agent (Section 4H).
    Generates study guides, research briefings, executive summaries, or checklists.
    """
    if request.user_id != user_id:
        raise HTTPException(403, "User mismatch")

    # Fetch task
    resp = sb_get("schedule_items", {"id": f"eq.{request.task_id}", "user_id": f"eq.{user_id}"})
    if resp.status_code != 200 or not resp.json():
        raise HTTPException(404, "Task not found")
    task = resp.json()[0]

    title = task.get("title", "Untitled")
    desc = task.get("description", "")
    action = request.action.lower()

    system_prompt = """You are Chrona's specialized AI Execution Agent.
You execute delegated work for the user's personal time management system.
Produce high-quality, practical, well-formatted markdown output."""

    if action == "research":
        prompt = f"""Conduct a thorough, structured research briefing on: "{title}".
Additional context: {desc}
User instructions: {request.instructions or "Cover core concepts, current best practices, key challenges, and actionable steps."}

Provide a well-structured markdown briefing with:
1. Executive Summary
2. Key Concepts & Architecture
3. Important Resources / Tools
4. Recommended Next Steps"""
    elif action == "study_guide":
        prompt = f"""Create a comprehensive, exam-ready study guide for: "{title}".
Context: {desc}
Instructions: {request.instructions or "Focus on high-yield concepts, core principles, likely exam questions, and memory aids."}

Provide markdown with:
1. Core Concepts & Definitions
2. Key Principles / Formulas
3. High-Yield Practice Questions & Answers
4. Summary Flashcard Review"""
    elif action == "summary":
        prompt = f"""Generate an executive summary and high-impact key points for: "{title}".
Context: {desc}
Instructions: {request.instructions or "Make it concise, actionable, and structured."}"""
    else:  # checklist
        prompt = f"""Create an actionable step-by-step checklist to complete: "{title}".
Context: {desc}
Instructions: {request.instructions or "Break into 5-8 concrete, achievable steps with time estimates."}"""

    result_text = _ai_complete(prompt, json_mode=False, system=system_prompt)

    execution_data = {
        "action": action,
        "content": result_text,
        "completed_at": _now_utc().isoformat(),
        "task_id": request.task_id,
        "title": title,
    }

    # Store execution result on task
    sb_patch("schedule_items", {
        "execution_status": "completed",
        "execution_result": execution_data,
        "completion_note": f"Executed by AI agent ({action})",
    }, params={"id": f"eq.{request.task_id}", "user_id": f"eq.{user_id}"})

    return {
        "success": True,
        "task_id": request.task_id,
        "action": action,
        "result": execution_data,
    }

# ---- SCHEDULE DECISION EXPLANATIONS (SECTION 4E) ----

@app.get("/api/schedule/explain/{task_id}")
async def explain_schedule_decision(task_id: str, user_id: str = Depends(get_user_id)):
    """Explain why Chrona scheduled this task at its assigned time (Section 4E)."""
    resp = sb_get("schedule_items", {"id": f"eq.{task_id}", "user_id": f"eq.{user_id}"})
    if resp.status_code != 200 or not resp.json():
        raise HTTPException(404, "Task not found")
    task = resp.json()[0]

    profile = await get_behavior_profile(user_id)
    start_time = task.get("start_time")
    if not start_time:
        return {
            "task_id": task_id,
            "title": task.get("title"),
            "explanation": "This task is flexible/pending and will be placed in your next optimal focus window during schedule generation.",
        }

    st = _parse_dt(start_time)
    slot_name = _slot_for_hour(st.hour) if st else "afternoon"
    archetype = profile.get("archetype", "student_balanced")

    explanation = (
        f"Chrona scheduled '{task.get('title')}' for {st.strftime('%A at %I:%M %p')} because "
        f"your productivity archetype ({archetype}) has high energy during the {slot_name}. "
        f"It avoids conflicts with your fixed commitments and preserves buffer time before your deadline."
    )

    return {
        "task_id": task_id,
        "title": task.get("title"),
        "scheduled_time": start_time,
        "slot": slot_name,
        "archetype": archetype,
        "explanation": explanation,
    }

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

    # Purge any dummy phantom tasks from memory
    if "schedule_items" in _in_memory_db:
        _in_memory_db["schedule_items"] = [
            it for it in _in_memory_db["schedule_items"]
            if (it.get("title") or "").strip().lower() not in ["pending task", "untitled", "task", "undefined", "null"]
        ]

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000, reload=True)
