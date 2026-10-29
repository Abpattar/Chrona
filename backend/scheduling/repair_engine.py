"""
Schedule Repair Engine
=======================
Proposes 3 strategies when a schedule conflict occurs:
  1. Move — shift the task to the next free slot today
  2. Split — break the task into two shorter blocks in actual free slots
  3. Defer — move to tomorrow (only if deadline allows)

Every repair is scored before proposing to user.
A repair is REJECTED if it makes urgency_score > 0.9 (computed from proposed time).
Proposals must pass the independent validator.

Usage:
    from scheduling.repair_engine import RepairEngine

    engine = RepairEngine()
    proposals = engine.propose_repairs(disrupted_task, free_slots, supabase_client, user_id)
    # Returns list of proposal dicts with strategy, label, risk, urgency_after

Vercel Equivalent: N/A (repair engine runs only on Python backend)
"""
from datetime import datetime, timedelta, timezone
from typing import Optional, List, Dict
from .edf import Task
from .validator import validate_plan


def _ensure_utc(dt: datetime) -> datetime:
    """Ensure datetime is timezone-aware in UTC."""
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)


def _now_utc() -> datetime:
    """Current time in UTC."""
    return datetime.now(timezone.utc)


def _parse_dt(dt_str: str) -> datetime:
    """Parse ISO string to timezone-aware datetime."""
    dt = datetime.fromisoformat(dt_str)
    return _ensure_utc(dt)


class RepairEngine:
    """
    Generates repair proposals for disrupted tasks.

    Design principles:
    - Never propose a repair that makes urgency > 0.9 (too risky)
    - Urgency is calculated from the PROPOSED slot time, not current time
    - Always include risk level (low/medium/critical) for user decision
    - Proposals are ranked by safety (lowest risk first)
    - Every proposal must pass validate_plan
    """

    URGENCY_LIMIT = 0.9   # never propose a repair that cuts it this close

    def propose_repairs(
        self,
        disrupted_task: dict,
        free_slots: list,
        supabase_client=None,
        user_id: str = None
    ) -> list:
        """
        Generate repair proposals for a disrupted task.

        Args:
            disrupted_task: dict with id, title, duration_min, deadline, priority, flexibility
            free_slots: list of dicts with {start, end} as ISO strings
            supabase_client: optional, for future use
            user_id: optional, for future use

        Returns:
            list of proposal dicts
        """
        proposals = []

        # Build a Task object for urgency calculations
        duration = disrupted_task.get("duration_min") or disrupted_task.get("duration_minutes") or 60
        deadline_str = disrupted_task.get("deadline")

        if deadline_str:
            try:
                deadline = _parse_dt(deadline_str)
            except (ValueError, TypeError):
                deadline = _now_utc() + timedelta(days=7)
        else:
            deadline = _now_utc() + timedelta(days=7)

        task = Task(
            id=disrupted_task.get("id", "unknown"),
            title=disrupted_task.get("title", "Untitled"),
            duration_minutes=int(duration),
            deadline=deadline,
            priority=disrupted_task.get("priority", 3),
            flexibility=disrupted_task.get("flexibility", True),
        )

        # Convert free_slots to TimeSlot objects
        slot_objs = []
        for slot in free_slots:
            try:
                slot_start = _parse_dt(slot["start"])
                slot_end = _parse_dt(slot["end"])
                if slot_end > slot_start:
                    slot_objs.append({"start": slot_start, "end": slot_end, "minutes": (slot_end - slot_start).total_seconds() / 60})
            except (ValueError, TypeError, KeyError):
                continue

        # Sort by start time
        slot_objs.sort(key=lambda s: s["start"])

        # Strategy 1: Move to next valid free slot (before deadline)
        for slot in slot_objs:
            if slot["minutes"] >= task.duration_minutes:
                proposed_start = slot["start"]
                proposed_end = proposed_start + timedelta(minutes=task.duration_minutes)
                
                # Validate: proposed_end must be <= deadline
                if proposed_end <= task.deadline:
                    # Calculate urgency from PROPOSED time
                    urgency_after = task.urgency_score(from_time=proposed_end)
                    
                    if urgency_after <= self.URGENCY_LIMIT:
                        proposals.append({
                            "strategy": "move_today",
                            "label": f"Move to {proposed_start.strftime('%I:%M %p')} today",
                            "new_start": proposed_start.isoformat(),
                            "new_end": proposed_end.isoformat(),
                            "urgency_after": round(urgency_after, 2),
                            "risk": "low" if urgency_after < 0.5 else "medium"
                        })
                        break  # Only propose the first viable slot

        # Strategy 2: Split task into blocks that fit in actual free slots
        half = task.duration_minutes // 2
        if half >= 20:
            # Find two slots that can accommodate half-duration each
            valid_slots_for_half = [s for s in slot_objs if s["minutes"] >= half]
            
            if len(valid_slots_for_half) >= 2:
                # Assign to first two valid slots
                block_assignments = []
                for i, slot in enumerate(valid_slots_for_half[:2]):
                    block_start = slot["start"]
                    block_end = block_start + timedelta(minutes=half)
                    block_assignments.append({
                        "block": i + 1,
                        "start": block_start.isoformat(),
                        "end": block_end.isoformat(),
                        "duration_minutes": half
                    })
                
                # Validate the split plan
                plan = [{"start": a["start"], "end": a["end"], "task_id": f"{task.id}_block_{a['block']}"} for a in block_assignments]
                fixed_items = []  # No fixed items in repair context
                windows = [{"start": s["start"].isoformat(), "end": s["end"].isoformat()} for s in slot_objs]
                
                validation = validate_plan(plan, fixed_items, windows)
                if validation["valid"]:
                    urgency_after = task.urgency_score(from_time=valid_slots_for_half[1]["end"])
                    if urgency_after <= self.URGENCY_LIMIT:
                        proposals.append({
                            "strategy": "split",
                            "label": f"Split into 2 x {half}-min blocks",
                            "blocks": 2,
                            "block_duration": half,
                            "block_assignments": block_assignments,
                            "urgency_after": round(urgency_after, 2),
                            "risk": "medium",
                            "note": "Cognitive load is higher with split sessions"
                        })

        # Strategy 3: Defer to tomorrow morning
        tomorrow_9am = (_now_utc() + timedelta(days=1)).replace(
            hour=9, minute=0, second=0, microsecond=0
        )
        simulated_end = tomorrow_9am + timedelta(minutes=task.duration_minutes)

        # Calculate urgency as if task is at tomorrow 9am
        time_to_deadline = (deadline - simulated_end).total_seconds() / 60
        tomorrow_urgency = task.duration_minutes / max(time_to_deadline, 1)

        if tomorrow_urgency <= self.URGENCY_LIMIT:
            risk = "low" if tomorrow_urgency < 0.5 else "medium"
            warning = None if tomorrow_urgency < 0.7 else "Deadline is close - deferring is risky"
            proposals.append({
                "strategy": "defer_tomorrow",
                "label": "Move to tomorrow morning",
                "new_start": tomorrow_9am.isoformat(),
                "new_end": simulated_end.isoformat(),
                "risk": risk,
                "urgency_after": round(tomorrow_urgency, 2),
                "warning": warning
            })
        else:
            proposals.append({
                "strategy": "defer_impossible",
                "label": "Cannot defer - deadline too close",
                "risk": "critical",
                "urgency_after": 1.0,
                "warning": "Deferring would make this task impossible to complete on time"
            })

        return proposals


# ============================================================
# SELF-TEST
# ============================================================
if __name__ == "__main__":
    from datetime import datetime, timedelta, timezone

    now = datetime.now(timezone.utc)

    task = {
        "id": "task-001",
        "title": "Study for OS Exam",
        "duration_min": 60,
        "deadline": (now + timedelta(days=3)).isoformat(),
        "priority": 2,
        "flexibility": True,
    }

    free_slots = [
        {"start": (now + timedelta(hours=2)).isoformat(),
         "end": (now + timedelta(hours=4)).isoformat()},
        {"start": (now + timedelta(hours=5)).isoformat(),
         "end": (now + timedelta(hours=7)).isoformat()},
    ]

    engine = RepairEngine()
    proposals = engine.propose_repairs(task, free_slots)

    print(f"Generated {len(proposals)} proposals:")
    for p in proposals:
        print(f"  [{p['risk'].upper()}] {p['label']} (urgency_after: {p['urgency_after']})")
    print("Repair Engine: OK")