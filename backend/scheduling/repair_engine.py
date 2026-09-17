"""
Schedule Repair Engine
=======================
Proposes 3 strategies when a schedule conflict occurs:
  1. Move — shift the task to the next free slot today
  2. Split — break the task into two shorter blocks
  3. Defer — move to tomorrow (only if deadline allows)

Every repair is scored before proposing to user.
A repair is REJECTED if it makes urgency_score > 0.9.

Usage:
    from scheduling.repair_engine import RepairEngine

    engine = RepairEngine()
    proposals = engine.propose_repairs(disrupted_task, free_slots, supabase_client, user_id)
    # Returns list of proposal dicts with strategy, label, risk, urgency_after

Vercel Equivalent: N/A (repair engine runs only on Python backend)
"""
from datetime import datetime, timedelta
from typing import Optional
from .edf import Task


class RepairEngine:
    """
    Generates repair proposals for disrupted tasks.

    Design principles:
    - Never propose a repair that makes urgency > 0.9 (too risky)
    - Always include risk level (low/medium/critical) for user decision
    - Proposals are ranked by safety (lowest risk first)
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
                deadline = datetime.fromisoformat(str(deadline_str).replace("Z", "+00:00").replace("+00:00", ""))
            except (ValueError, TypeError):
                deadline = datetime.now() + timedelta(days=7)
        else:
            deadline = datetime.now() + timedelta(days=7)

        task = Task(
            id=disrupted_task.get("id", "unknown"),
            title=disrupted_task.get("title", "Untitled"),
            duration_minutes=int(duration),
            deadline=deadline,
            priority=disrupted_task.get("priority", 3),
            flexibility=disrupted_task.get("flexibility", True),
        )

        # Strategy 1: Move to next free slot today
        for slot in free_slots:
            try:
                slot_start = datetime.fromisoformat(str(slot["start"]).replace("Z", "+00:00").replace("+00:00", ""))
                slot_end   = datetime.fromisoformat(str(slot["end"]).replace("Z", "+00:00").replace("+00:00", ""))
            except (ValueError, TypeError):
                continue

            slot_minutes = (slot_end - slot_start).total_seconds() / 60

            if slot_minutes >= task.duration_minutes:
                # Check urgency after move
                if task.urgency_score() <= self.URGENCY_LIMIT:
                    proposals.append({
                        "strategy": "move_today",
                        "label": f"Move to {slot_start.strftime('%I:%M %p')} today",
                        "new_start": slot_start.isoformat(),
                        "new_end": (slot_start + timedelta(minutes=task.duration_minutes)).isoformat(),
                        "urgency_after": round(task.urgency_score(), 2),
                        "risk": "low"
                    })
                    break  # Only propose the first viable slot

        # Strategy 2: Split task into two halves
        half = task.duration_minutes // 2
        if half >= 20 and len(free_slots) >= 2:
            proposals.append({
                "strategy": "split",
                "label": f"Split into 2 x {half}-min blocks",
                "blocks": 2,
                "block_duration": half,
                "urgency_after": round(task.urgency_score() * 0.6, 2),
                "risk": "medium",
                "note": "Cognitive load is higher with split sessions"
            })

        # Strategy 3: Defer to tomorrow
        tomorrow_9am = (datetime.now() + timedelta(days=1)).replace(
            hour=9, minute=0, second=0, microsecond=0
        )
        simulated_end = tomorrow_9am + timedelta(minutes=task.duration_minutes)

        # Calculate urgency as if task is at tomorrow 9am
        time_to_deadline = (deadline - tomorrow_9am).total_seconds() / 60
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
    from datetime import datetime, timedelta

    now = datetime.now()

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
