"""
Test fixtures for scheduling engine validation.
These JSON fixtures store scenario inputs and expected outputs
so the same fixtures can later validate a Kotlin implementation.
"""

# ============================================================
# P1: Task due in 3h, only slot is 10-12h out → scheduled past deadline
# ============================================================
P1_TASK_DUE_IN_3H = {
    "tasks": [
        {
            "id": "task-1",
            "title": "Urgent Task",
            "duration_minutes": 60,
            "deadline": "2026-10-28T13:00:00",  # 3 hours from now (10:00)
            "priority": 1,
            "flexibility": True,
            "task_type": "task"
        }
    ],
    "free_slots": [
        {
            "start": "2026-10-28T20:00:00",  # 10 hours out
            "end": "2026-10-28T22:00:00"
        }
    ],
    "behavior_weights": {
        "morning": 0.5, "afternoon": 0.5, "evening": 0.5, "night": 0.5
    },
    "expected": {
        "scheduled_count": 0,  # Should NOT schedule past deadline
        "impossible_count": 1,  # Should be marked impossible
        "deferred_count": 0
    }
}

# ============================================================
# P2: Behavior weights should not choose INVALID (past deadline) slots
# The fix for P1 ensures slots past deadline are filtered out.
# This test verifies: a high-weight slot that's PAST deadline is never chosen,
# while a lower-weight slot that's valid IS chosen.
# Uses relative times from "now" to avoid flakiness.
# ============================================================
def _make_p2_fixture():
    from datetime import datetime, timedelta, timezone
    now = datetime.now(timezone.utc)
    # Deadline 4 hours from now
    deadline_dt = now + timedelta(hours=4)
    # Morning slot: 1-3 hours from now (valid, ends before deadline)
    morning_start = now + timedelta(hours=1)
    morning_end = now + timedelta(hours=3)
    # Night slot: 5-7 hours from now (INVALID, ends after deadline)
    night_start = now + timedelta(hours=5)
    night_end = now + timedelta(hours=7)
    
    return {
        "tasks": [{
            "id": "task-1",
            "title": "Study Task",
            "duration_minutes": 60,
            "deadline": deadline_dt.isoformat(),
            "priority": 3,
            "flexibility": True,
            "task_type": "task"
        }],
        "free_slots": [
            {"start": morning_start.isoformat(), "end": morning_end.isoformat()},
            {"start": night_start.isoformat(), "end": night_end.isoformat()},
        ],
        "behavior_weights": {
            "morning": 0.2, "afternoon": 0.5, "evening": 0.5, "night": 1.0
        },
        "expected": {
            "scheduled_count": 1,
            "assigned_start": morning_start.isoformat(),  # Should pick morning (only VALID slot)
            "impossible_count": 0,
            "deferred_count": 0
        }
    }

P2_NIGHT_PREFERENCE = _make_p2_fixture()

# ============================================================
# P3: Fixed commitment / explicit requested time not representable
# ============================================================
P3_FIXED_COMMITMENT = {
    "tasks": [
        {
            "id": "fixed-gym",
            "title": "Gym (FIXED 18:00-19:00)",
            "duration_minutes": 60,
            "deadline": "2026-10-28T19:00:00",
            "priority": 1,
            "flexibility": False,  # Fixed!
            "task_type": "fixed",
            "fixed_start": "2026-10-28T18:00:00",
            "fixed_end": "2026-10-28T19:00:00"
        },
        {
            "id": "flexible-study",
            "title": "Study",
            "duration_minutes": 60,
            "deadline": "2026-10-28T22:00:00",
            "priority": 2,
            "flexibility": True,
            "task_type": "task"
        }
    ],
    "free_slots": [
        {
            "start": "2026-10-28T16:00:00",
            "end": "2026-10-28T18:00:00"
        },
        {
            "start": "2026-10-28T19:00:00",
            "end": "2026-10-28T21:00:00"
        }
    ],
    "behavior_weights": {
        "morning": 0.5, "afternoon": 0.5, "evening": 0.5, "night": 0.5
    },
    "expected": {
        "fixed_items_respected": True,
        "fixed_not_moved": True,
        "free_time_excludes_fixed": True
    }
}

# ============================================================
# P5: Timezone-aware deadline crash
# Uses a deadline 3 hours in the future from "now" (test runtime)
# Slot ends 2 hours from now, deadline 3 hours from now -> slot.end <= deadline
# The key test is NO CRASH with tz-aware datetimes.
# ============================================================
def _make_p5_fixture():
    from datetime import datetime, timedelta, timezone
    now = datetime.now(timezone.utc)
    # Create timezone-aware deadline 3 hours from now in +02:00 tz
    deadline_dt = (now + timedelta(hours=3)).astimezone(timezone(timedelta(hours=2)))
    slot_start = (now + timedelta(hours=1)).astimezone(timezone(timedelta(hours=2)))
    slot_end = (now + timedelta(hours=2)).astimezone(timezone(timedelta(hours=2)))  # Ends 2h from now
    
    return {
        "tasks": [{
            "id": "task-1",
            "title": "Task with TZ",
            "duration_minutes": 60,
            "deadline": deadline_dt.isoformat(),
            "priority": 2,
            "flexibility": True,
            "task_type": "task"
        }],
        "free_slots": [{
            "start": slot_start.isoformat(),
            "end": slot_end.isoformat()
        }],
        "behavior_weights": {
            "morning": 0.5, "afternoon": 0.5, "evening": 0.5, "night": 0.5
        },
        "expected": {
            "no_crash": True,
            "scheduled_count": 1
        }
    }

P5_TIMEZONE_AWARE = _make_p5_fixture()

# ============================================================
# P6: Repair for task due in 2h with slot 5h out
# ============================================================
P6_REPAIR_PAST_DEADLINE = {
    "disrupted_task": {
        "id": "task-1",
        "title": "Urgent Task",
        "duration_min": 60,
        "deadline": "2026-10-28T12:00:00",  # 2 hours from 10:00
        "priority": 2,
        "flexibility": True
    },
    "free_slots": [
        {
            "start": "2026-10-28T15:00:00",  # 5 hours out - PAST deadline
            "end": "2026-10-28T17:00:00"
        }
    ],
    "expected": {
        "move_proposal_valid": False,  # Should NOT propose slot past deadline
        "defer_impossible_present": True  # Should have defer_impossible
    }
}

# ============================================================
# P7: Repair "split" returns block sizes only, no start times
# ============================================================
P7_REPAIR_SPLIT_NO_TIMES = {
    "disrupted_task": {
        "id": "task-1",
        "title": "Long Task",
        "duration_min": 120,
        "deadline": "2026-10-29T10:00:00",
        "priority": 2,
        "flexibility": True
    },
    "free_slots": [
        {"start": "2026-10-28T09:00:00", "end": "2026-10-28T11:00:00"},
        {"start": "2026-10-28T14:00:00", "end": "2026-10-28T16:00:00"}
    ],
    "expected": {
        "split_proposal_has_times": True,  # Should have actual start/end times
        "split_blocks_are_valid_slots": True  # Blocks must fit in free slots
    }
}

# ============================================================
# P8: urgency_after computed from now, not proposed slot
# ============================================================
P8_URGENCY_FROM_SLOT = {
    "disrupted_task": {
        "id": "task-1",
        "title": "Task",
        "duration_min": 60,
        "deadline": "2026-10-29T10:00:00",
        "priority": 2,
        "flexibility": True
    },
    "free_slots": [
        {"start": "2026-10-28T21:00:00", "end": "2026-10-28T23:00:00"}  # Night slot
    ],
    "expected": {
        "urgency_after_reflects_proposed_time": True  # Not current time
    }
}

# ============================================================
# Additional: Validator tests
# ============================================================
VALIDATOR_TESTS = [
    {
        "name": "valid_plan_no_overlap",
        "plan": [
            {"start": "2026-10-28T09:00:00", "end": "2026-10-28T10:00:00", "task_id": "1"},
            {"start": "2026-10-28T10:00:00", "end": "2026-10-28T11:00:00", "task_id": "2"}
        ],
        "fixed_items": [],
        "windows": [{"start": "2026-10-28T08:00:00", "end": "2026-10-28T12:00:00"}],
        "expected_valid": True
    },
    {
        "name": "plan_with_overlap",
        "plan": [
            {"start": "2026-10-28T09:00:00", "end": "2026-10-28T11:00:00", "task_id": "1"},
            {"start": "2026-10-28T10:00:00", "end": "2026-10-28T12:00:00", "task_id": "2"}
        ],
        "fixed_items": [],
        "windows": [{"start": "2026-10-28T08:00:00", "end": "2026-10-28T12:00:00"}],
        "expected_valid": False  # Overlap at 10:00-11:00
    },
    {
        "name": "plan_outside_window",
        "plan": [
            {"start": "2026-10-28T07:00:00", "end": "2026-10-28T08:00:00", "task_id": "1"}
        ],
        "fixed_items": [],
        "windows": [{"start": "2026-10-28T08:00:00", "end": "2026-10-28T12:00:00"}],
        "expected_valid": False  # Before window
    },
    {
        "name": "plan_past_deadline",
        "plan": [
            {"start": "2026-10-28T15:00:00", "end": "2026-10-28T16:00:00", "task_id": "1", "deadline": "2026-10-28T14:00:00"}
        ],
        "fixed_items": [],
        "windows": [{"start": "2026-10-28T08:00:00", "end": "2026-10-28T18:00:00"}],
        "expected_valid": False  # End > deadline
    },
    {
        "name": "plan_conflicts_fixed",
        "plan": [
            {"start": "2026-10-28T10:00:00", "end": "2026-10-28T11:00:00", "task_id": "1"}
        ],
        "fixed_items": [
            {"start": "2026-10-28T10:30:00", "end": "2026-10-28T11:30:00"}  # Fixed gym
        ],
        "windows": [{"start": "2026-10-28T08:00:00", "end": "2026-10-28T12:00:00"}],
        "expected_valid": False  # Overlaps fixed item
    }
]