"""
Conflict Detection Module
==========================
Uses Supabase SQL to detect overlapping schedule items.
The overlap condition: start_time < new_end AND end_time > new_start

Relies on the index: idx_schedule_overlap ON schedule_items (user_id, start_time, end_time)

Usage:
    from scheduling.conflict import detect_conflicts, compute_conflict_hash

    conflicts = detect_conflicts(supabase_client, user_id, new_start, new_end)
    if conflicts:
        hash = compute_conflict_hash(user_id, conflicts)

Vercel Equivalent: N/A (conflict detection runs only on Python backend)
"""
from datetime import datetime
from typing import Optional
import hashlib
import json


def detect_conflicts(supabase_client, user_id: str,
                     new_start: datetime, new_end: datetime,
                     exclude_id: Optional[str] = None) -> list:
    """
    Find all schedule_items that overlap with the proposed [new_start, new_end] range.

    Uses the standard overlap condition:
        existing.start_time < new_end AND existing.end_time > new_start

    Args:
        supabase_client: Supabase client instance
        user_id: The user's UUID
        new_start: Proposed start time
        new_end: Proposed end time
        exclude_id: Optional schedule_item ID to exclude (for self-overlap on edits)

    Returns:
        List of conflicting schedule_item dicts
    """
    query = (
        supabase_client
        .table("schedule_items")
        .select("*")
        .eq("user_id", user_id)
        .eq("status", "scheduled")
        .lt("start_time", new_end.isoformat())
        .gt("end_time",   new_start.isoformat())
    )
    if exclude_id:
        query = query.neq("id", exclude_id)

    result = query.execute()
    return result.data or []


def compute_conflict_hash(user_id: str, conflicts: list) -> str:
    """
    Compute a stable hash for a set of conflicts.
    Used as a cache key in Redis for repair proposals.
    """
    key = user_id + json.dumps(
        sorted([c["id"] for c in conflicts])
    )
    return hashlib.md5(key.encode()).hexdigest()


# ============================================================
# SELF-TEST
# ============================================================
if __name__ == "__main__":
    # Test conflict hash computation
    test_conflicts = [
        {"id": "abc-123", "title": "Task A"},
        {"id": "def-456", "title": "Task B"},
    ]
    h = compute_conflict_hash("user-001", test_conflicts)
    print(f"Conflict hash: {h}")
    assert len(h) == 32  # MD5 hex digest length
    print("Conflict module: OK")
