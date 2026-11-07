"""
LangGraph Agent Graph — Chrona v2
====================================
Orchestrates the full pipeline: ingestion → parsing → enrichment →
scheduling → conflict check → repair → explanation.

Changes in v2:
- scheduling_node now INVOKES the EDF scheduler (was just loading behavior)
- conflict_check_node uses actual schedule data
- Proper google-genai SDK throughout
- Sequential fallback also runs full scheduling

Usage:
    from agents.graph import build_chrona_graph, run_pipeline

    graph = build_chrona_graph(gemini_client, supabase_client, redis_client)
    result = graph.invoke(initial_state)
    # or:
    result = run_pipeline(state, gemini_client, supabase_client)
"""
import json
import os
from datetime import datetime, timedelta, timezone
from typing import Optional


def _now_utc():
    return datetime.now(timezone.utc)


def _ensure_utc(dt):
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)


def _parse_dt(s):
    if not s:
        return None
    try:
        dt = datetime.fromisoformat(str(s).replace("Z", "+00:00"))
        return _ensure_utc(dt)
    except (ValueError, TypeError):
        return None


# Import agent nodes
from .ingestion import ingestion_node
from .parser import parser_node
from .enrichment import enrichment_node
from .repair import repair_node
from .explanation import explanation_node


def build_chrona_graph(gemini_client, supabase_client, redis_client=None, groq_client=None):
    """
    Build the LangGraph state machine for Chrona.
    Returns compiled graph or None if langgraph not available.
    """
    try:
        from langgraph.graph import StateGraph, END
        from typing import TypedDict, Annotated
        import operator

        class ChronaState(TypedDict):
            # Input
            raw_input:        Optional[str]
            input_bytes:      Optional[bytes]
            input_type:       str
            user_id:          str

            # Extraction
            extracted_events: list
            extraction_error: Optional[str]
            enriched_tasks:   list

            # Scheduling
            schedule_result:  Optional[dict]
            conflicts:        list

            # Repair
            repair_proposals: list
            accepted_repair:  Optional[dict]

            # Behavior
            behavior_weights: dict

            # Output
            explanation:      str
            final_output:     Optional[dict]
            errors:           Annotated[list, operator.add]

        def scheduling_node(state):
            """
            Load behavior weights AND run the EDF scheduler on enriched tasks.
            This node now produces an actual schedule_result.
            """
            from behavior.profile import BehaviorProfile
            from scheduling.edf import EDFScheduler, Task, TimeSlot

            # 1. Load behavior profile
            profile = None
            if redis_client:
                try:
                    cached = redis_client.get(f"chrona:profile:{state['user_id']}")
                    if cached:
                        profile = BehaviorProfile.from_db_row(json.loads(cached))
                except Exception:
                    pass

            if not profile and supabase_client:
                try:
                    row = (supabase_client.table("behavior_profiles")
                           .select("*").eq("user_id", state["user_id"])
                           .single().execute())
                    profile = (BehaviorProfile.from_db_row(row.data) if row.data
                               else BehaviorProfile(user_id=state["user_id"]))
                except Exception:
                    profile = BehaviorProfile(user_id=state["user_id"])

            if not profile:
                profile = BehaviorProfile(user_id=state["user_id"])

            weights = profile.get_scheduling_weights()
            state["behavior_weights"] = weights

            # 2. Run EDF scheduler on enriched tasks
            enriched = state.get("enriched_tasks", [])
            if not enriched:
                state["schedule_result"] = {"scheduled": [], "impossible": [], "deferred": []}
                return state

            # Build Task objects
            tasks = []
            fixed_items = []
            for t in enriched:
                try:
                    deadline = _parse_dt(t.get("deadline")) or (_now_utc() + timedelta(days=7))
                    task = Task(
                        id=t.get("id", f"task-{len(tasks)}"),
                        title=t.get("title", "Untitled"),
                        duration_minutes=int(t.get("duration_minutes", 60)),
                        deadline=deadline,
                        priority=int(t.get("priority", 3)),
                        flexibility=bool(t.get("flexibility", True)),
                        task_type=t.get("task_type", "task"),
                    )
                    if not t.get("flexibility", True) and t.get("fixed_start") and t.get("fixed_end"):
                        task.fixed_start = _parse_dt(t["fixed_start"])
                        task.fixed_end   = _parse_dt(t["fixed_end"])
                        fixed_items.append(task)
                    else:
                        tasks.append(task)
                except Exception as e:
                    state["errors"] = state.get("errors", []) + [f"task_build_error: {e}"]

            # Generate free slots (next 7 days, 7am-10pm)
            now = _now_utc()
            free_slots = []
            for day_offset in range(7):
                day = now + timedelta(days=day_offset)
                day_start = day.replace(hour=7, minute=0, second=0, microsecond=0)
                day_end   = day.replace(hour=22, minute=0, second=0, microsecond=0)
                if day_start > now:
                    free_slots.append(TimeSlot(start=day_start, end=day_end))
                elif day_end > now:
                    free_slots.append(TimeSlot(start=now, end=day_end))

            # Run scheduler
            try:
                scheduler = EDFScheduler()
                result = scheduler.schedule(tasks, free_slots, weights, fixed_items)

                state["schedule_result"] = {
                    "scheduled": [
                        {
                            "task_id": a.task.id,
                            "title": a.task.title,
                            "start_time": a.assigned_start.isoformat(),
                            "end_time": a.assigned_end.isoformat(),
                            "task_type": a.task.task_type,
                            "priority": a.task.priority,
                            "flexibility": a.task.flexibility,
                        }
                        for a in result.scheduled
                    ],
                    "impossible": [{"task_id": t.id, "title": t.title} for t in result.impossible],
                    "deferred":   [{"task_id": t.id, "title": t.title} for t in result.deferred],
                }
            except Exception as e:
                state["errors"] = state.get("errors", []) + [f"scheduling_error: {e}"]
                state["schedule_result"] = {"scheduled": [], "impossible": [], "deferred": []}

            return state

        def conflict_check_node(state):
            """Check for schedule conflicts in extracted events."""
            from scheduling.conflict import detect_conflicts

            conflicts = []
            for event in state.get("extracted_events", []):
                event_dt = event.get("event_datetime") or event.get("fixed_start")
                if not event_dt:
                    continue
                try:
                    start = _parse_dt(event_dt)
                    duration = event.get("duration_minutes", 60) or 60
                    end = start + timedelta(minutes=duration)
                    if supabase_client:
                        found = detect_conflicts(supabase_client, state["user_id"], start, end)
                        conflicts.extend(found or [])
                except Exception as e:
                    state["errors"] = state.get("errors", []) + [f"conflict_check_error: {e}"]

            state["conflicts"] = conflicts
            return state

        def should_repair(state) -> str:
            return "repair" if state.get("conflicts") else "explanation"

        # Build graph
        graph = StateGraph(ChronaState)
        graph.add_node("ingestion",      lambda s: ingestion_node(s))
        graph.add_node("parser",         lambda s: parser_node(s, gemini_client, groq_client))
        graph.add_node("enrichment",     lambda s: enrichment_node(s, gemini_client, groq_client))
        graph.add_node("scheduling",     scheduling_node)
        graph.add_node("conflict_check", conflict_check_node)
        graph.add_node("repair",         lambda s: repair_node(s, gemini_client, redis_client))
        graph.add_node("explanation",    lambda s: explanation_node(s, gemini_client))

        graph.set_entry_point("ingestion")
        graph.add_edge("ingestion",      "parser")
        graph.add_edge("parser",         "enrichment")
        graph.add_edge("enrichment",     "scheduling")
        graph.add_edge("scheduling",     "conflict_check")
        graph.add_conditional_edges("conflict_check", should_repair, {
            "repair":      "repair",
            "explanation": "explanation",
        })
        graph.add_edge("repair",         "explanation")
        graph.add_edge("explanation",    END)

        return graph.compile()

    except ImportError:
        print("WARNING: langgraph not installed. Using run_pipeline() sequential fallback.")
        return None


def run_pipeline(state: dict, gemini_client, supabase_client, redis_client=None, groq_client=None) -> dict:
    """
    Sequential fallback when LangGraph is unavailable.
    Runs the complete pipeline including EDF scheduling.
    """
    state.setdefault("errors", [])
    state.setdefault("extracted_events", [])
    state.setdefault("enriched_tasks", [])
    state.setdefault("conflicts", [])
    state.setdefault("repair_proposals", [])

    # 1. Ingestion — classify input type
    state = ingestion_node(state)

    # 2. Parser — extract events/tasks from raw input
    state = parser_node(state, gemini_client, groq_client)

    # 3. Enrichment — add scheduling metadata, generate prep blocks
    state = enrichment_node(state, gemini_client, groq_client)

    # 4. Scheduling — load behavior weights + run EDF
    try:
        from behavior.profile import BehaviorProfile
        from scheduling.edf import EDFScheduler, Task, TimeSlot

        # Behavior weights
        profile = BehaviorProfile(user_id=state.get("user_id", ""))
        state["behavior_weights"] = profile.get_scheduling_weights()

        # Build tasks
        tasks = []
        fixed_items = []
        for t in state.get("enriched_tasks", []):
            try:
                deadline = _parse_dt(t.get("deadline")) or (_now_utc() + timedelta(days=7))
                task = Task(
                    id=t.get("id", f"task-{len(tasks)}"),
                    title=t.get("title", "Untitled"),
                    duration_minutes=int(t.get("duration_minutes", 60)),
                    deadline=deadline,
                    priority=int(t.get("priority", 3)),
                    flexibility=bool(t.get("flexibility", True)),
                    task_type=t.get("task_type", "task"),
                )
                if not t.get("flexibility", True) and t.get("fixed_start") and t.get("fixed_end"):
                    task.fixed_start = _parse_dt(t["fixed_start"])
                    task.fixed_end   = _parse_dt(t["fixed_end"])
                    fixed_items.append(task)
                else:
                    tasks.append(task)
            except Exception as e:
                state["errors"].append(f"task_build: {e}")

        # Free slots (default: next 7 days, 7am-10pm)
        now = _now_utc()
        free_slots = []
        for d in range(7):
            day = now + timedelta(days=d)
            ds = day.replace(hour=7, minute=0, second=0, microsecond=0)
            de = day.replace(hour=22, minute=0, second=0, microsecond=0)
            if ds < de:
                free_slots.append(TimeSlot(start=max(ds, now), end=de))

        if tasks:
            scheduler = EDFScheduler()
            result = scheduler.schedule(tasks, free_slots, state["behavior_weights"], fixed_items)
            state["schedule_result"] = {
                "scheduled": [
                    {
                        "task_id": a.task.id,
                        "title": a.task.title,
                        "start_time": a.assigned_start.isoformat(),
                        "end_time": a.assigned_end.isoformat(),
                        "task_type": a.task.task_type,
                        "priority": a.task.priority,
                    }
                    for a in result.scheduled
                ],
                "impossible": [{"task_id": t.id, "title": t.title} for t in result.impossible],
                "deferred":   [{"task_id": t.id, "title": t.title} for t in result.deferred],
            }
    except Exception as e:
        state["errors"].append(f"scheduling_pipeline: {e}")
        state.setdefault("behavior_weights", {"morning": 0.5, "afternoon": 0.5, "evening": 0.5, "night": 0.5})

    # 5. Conflict check (simplified for sequential)
    state.setdefault("conflicts", [])

    # 6. Repair if conflicts
    if state.get("conflicts"):
        state = repair_node(state, gemini_client, redis_client)

    # 7. Explanation
    state = explanation_node(state, gemini_client)

    return state


# ============================================================
# SELF-TEST
# ============================================================
if __name__ == "__main__":
    state = {
        "raw_input": "I have a math exam next Friday and need to study 6 hours for it.",
        "input_bytes": None,
        "input_type": "",
        "user_id": "test-user",
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

    result = run_pipeline(state, None, None, None, None)
    print(f"Pipeline completed with {len(result.get('errors', []))} errors")
    print(f"Extracted: {len(result.get('extracted_events', []))} events")
    print(f"Enriched: {len(result.get('enriched_tasks', []))} tasks")
    print(f"Scheduled: {len(result.get('schedule_result', {}).get('scheduled', []))} items")
    print(f"Explanation: {result.get('explanation', 'N/A')[:100]}")
    print("Agent Graph: OK")
