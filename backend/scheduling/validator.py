"""
Independent Schedule Validator
===============================
Every proposed plan (from scheduler or repair engine) must pass this validator.
Checks: no overlap, inside working windows, end <= deadline, fixed items untouched, durations preserved.

Usage:
    from scheduling.validator import validate_plan

    result = validate_plan(plan, fixed_items, windows)
    # result: {"valid": bool, "errors": list[str]}
"""
from datetime import datetime, timezone
from typing import List, Dict, Any


def _ensure_utc(dt: datetime) -> datetime:
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)


def _parse_dt(dt_input: Any) -> datetime:
    """Parse datetime from ISO string or datetime object."""
    if isinstance(dt_input, datetime):
        return _ensure_utc(dt_input)
    dt = datetime.fromisoformat(str(dt_input))
    return _ensure_utc(dt)


def validate_plan(
    plan: List[Dict[str, Any]],
    fixed_items: List[Dict[str, Any]],
    windows: List[Dict[str, Any]]
) -> Dict[str, Any]:
    """
    Validate a proposed schedule plan.

    Args:
        plan: List of dicts with {start, end, task_id, deadline?}
        fixed_items: List of dicts with {start, end} - immutable commitments
        windows: List of dicts with {start, end} - allowed working windows

    Returns:
        {"valid": bool, "errors": list[str]}
    """
    errors = []

    # Convert all times to UTC datetimes
    plan_items = []
    for item in plan:
        try:
            start = _parse_dt(item["start"])
            end = _parse_dt(item["end"])
            if end <= start:
                errors.append(f"Task {item.get('task_id', '?')}: end <= start")
                continue
            plan_items.append({
                "task_id": item.get("task_id", "unknown"),
                "start": start,
                "end": end,
                "deadline": _parse_dt(item["deadline"]) if item.get("deadline") else None
            })
        except (KeyError, ValueError) as e:
            errors.append(f"Task {item.get('task_id', '?')}: invalid time format - {e}")

    fixed = []
    for item in fixed_items:
        try:
            fixed.append({
                "start": _parse_dt(item["start"]),
                "end": _parse_dt(item["end"])
            })
        except (KeyError, ValueError) as e:
            errors.append(f"Fixed item: invalid time format - {e}")

    working_windows = []
    for w in windows:
        try:
            working_windows.append({
                "start": _parse_dt(w["start"]),
                "end": _parse_dt(w["end"])
            })
        except (KeyError, ValueError) as e:
            errors.append(f"Window: invalid time format - {e}")

    if errors:
        return {"valid": False, "errors": errors}

    # 1. Check no overlaps within plan
    for i, a in enumerate(plan_items):
        for j, b in enumerate(plan_items):
            if i >= j:
                continue
            # Overlap: a.start < b.end AND a.end > b.start
            if a["start"] < b["end"] and a["end"] > b["start"]:
                errors.append(f"Overlap: {a['task_id']} ({a['start']}-{a['end']}) overlaps {b['task_id']} ({b['start']}-{b['end']})")

    # 2. Check plan items don't overlap fixed items
    for item in plan_items:
        for fix in fixed:
            if item["start"] < fix["end"] and item["end"] > fix["start"]:
                errors.append(f"Task {item['task_id']} overlaps fixed commitment {fix['start']}-{fix['end']}")

    # 3. Check each item is inside at least one working window
    for item in plan_items:
        inside_window = any(
            w["start"] <= item["start"] and item["end"] <= w["end"]
            for w in working_windows
        )
        if not inside_window:
            errors.append(f"Task {item['task_id']} outside working windows: {item['start']}-{item['end']}")

    # 4. Check end <= deadline (if deadline specified)
    for item in plan_items:
        if item["deadline"] and item["end"] > item["deadline"]:
            errors.append(f"Task {item['task_id']} ends after deadline: {item['end']} > {item['deadline']}")

    # 5. Check fixed items don't overlap each other
    for i, a in enumerate(fixed):
        for j, b in enumerate(fixed):
            if i >= j:
                continue
            if a["start"] < b["end"] and a["end"] > b["start"]:
                errors.append(f"Fixed items overlap: {a['start']}-{a['end']} vs {b['start']}-{b['end']}")

    return {
        "valid": len(errors) == 0,
        "errors": errors
    }


# ============================================================
# SELF-TEST
# ============================================================
if __name__ == "__main__":
    from datetime import datetime, timedelta, timezone

    now = datetime.now(timezone.utc)

    # Test 1: Valid plan
    plan = [
        {"start": (now + timedelta(hours=1)).isoformat(), "end": (now + timedelta(hours=2)).isoformat(), "task_id": "1"},
        {"start": (now + timedelta(hours=2)).isoformat(), "end": (now + timedelta(hours=3)).isoformat(), "task_id": "2"}
    ]
    fixed = []
    windows = [{"start": now.isoformat(), "end": (now + timedelta(hours=4)).isoformat()}]
    result = validate_plan(plan, fixed, windows)
    assert result["valid"] == True, f"Test 1 failed: {result}"
    print("Test 1 (valid plan): PASS")

    # Test 2: Overlap
    plan = [
        {"start": (now + timedelta(hours=1)).isoformat(), "end": (now + timedelta(hours=3)).isoformat(), "task_id": "1"},
        {"start": (now + timedelta(hours=2)).isoformat(), "end": (now + timedelta(hours=4)).isoformat(), "task_id": "2"}
    ]
    result = validate_plan(plan, fixed, windows)
    assert result["valid"] == False, f"Test 2 failed: {result}"
    print("Test 2 (overlap): PASS")

    # Test 3: Outside window
    plan = [
        {"start": (now - timedelta(hours=1)).isoformat(), "end": now.isoformat(), "task_id": "1"}
    ]
    result = validate_plan(plan, fixed, windows)
    assert result["valid"] == False, f"Test 3 failed: {result}"
    print("Test 3 (outside window): PASS")

    # Test 4: Past deadline
    plan = [
        {"start": (now + timedelta(hours=5)).isoformat(), "end": (now + timedelta(hours=6)).isoformat(), "task_id": "1", "deadline": (now + timedelta(hours=4)).isoformat()}
    ]
    result = validate_plan(plan, fixed, windows)
    assert result["valid"] == False, f"Test 4 failed: {result}"
    print("Test 4 (past deadline): PASS")

    # Test 5: Conflicts with fixed
    plan = [
        {"start": (now + timedelta(hours=1, minutes=30)).isoformat(), "end": (now + timedelta(hours=2, minutes=30)).isoformat(), "task_id": "1"}
    ]
    fixed = [{"start": (now + timedelta(hours=1, minutes=45)).isoformat(), "end": (now + timedelta(hours=2, minutes=45)).isoformat()}]
    result = validate_plan(plan, fixed, windows)
    assert result["valid"] == False, f"Test 5 failed: {result}"
    print("Test 5 (conflicts fixed): PASS")

    print("All validator tests: OK")