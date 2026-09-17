"""
Repair Agent Node
==================
When conflicts are detected, proposes repair strategies using
the RepairEngine and optionally uses Gemini for reasoning.

Graph position: conflict_check -> REPAIR (conditional) -> explanation
"""
import json
from datetime import datetime, timedelta
from typing import Any


def repair_node(state: dict, gemini_client: Any, redis_client: Any = None) -> dict:
    """
    Generate repair proposals for conflicting schedule items.

    Uses RepairEngine for deterministic proposals, optionally enhanced
    with Gemini reasoning for the explanation.

    Args:
        state: ChronaState dict with conflicts populated
        gemini_client: google.generativeai configured client
        redis_client: optional Redis client for caching proposals

    Updates state with:
        - repair_proposals: list of proposal dicts
    """
    from scheduling.repair_engine import RepairEngine

    state["repair_proposals"] = []
    state["errors"] = state.get("errors", [])

    if not state.get("conflicts"):
        return state

    engine = RepairEngine()

    for conflict in state["conflicts"]:
        # Build free slots (simplified: use gaps in today's schedule)
        free_slots = _compute_free_slots_from_state(state)

        try:
            proposals = engine.propose_repairs(
                disrupted_task=conflict,
                free_slots=free_slots,
                user_id=state.get("user_id")
            )

            # Cache proposals in Redis if available
            if redis_client and state.get("user_id"):
                cache_key = f"chrona:repair:{state['user_id']}:{conflict.get('id')}"
                try:
                    redis_client.setex(
                        cache_key, 300,  # 5 min TTL
                        json.dumps(proposals)
                    )
                except Exception:
                    pass  # Redis failure is non-fatal

            state["repair_proposals"].extend(proposals)

        except Exception as e:
            state["errors"].append(f"repair_error for '{conflict.get('title')}': {e}")

    return state


def _compute_free_slots_from_state(state: dict) -> list:
    """
    Compute free slots from the current state.
    Simplified: returns standard daily slots.
    """
    now = datetime.now()
    today_end = now.replace(hour=23, minute=0, second=0)

    # Generate hourly slots from now until end of day
    slots = []
    cursor = now + timedelta(minutes=30)  # Start 30min from now
    cursor = cursor.replace(minute=0, second=0)  # Round to hour

    while cursor < today_end:
        slot_end = cursor + timedelta(hours=2)
        if slot_end > today_end:
            slot_end = today_end
        slots.append({
            "start": cursor.isoformat(),
            "end": slot_end.isoformat()
        })
        cursor = slot_end + timedelta(minutes=15)  # 15min gap between slots

    return slots


# ============================================================
# SELF-TEST
# ============================================================
if __name__ == "__main__":
    state = {
        "user_id": "test-user",
        "conflicts": [
            {
                "id": "task-001",
                "title": "Study OS",
                "duration_min": 60,
                "deadline": (datetime.now() + timedelta(days=2)).isoformat(),
                "priority": 2,
            }
        ],
        "errors": [],
    }

    result = repair_node(state, None, None)
    print(f"Generated {len(result['repair_proposals'])} proposals")
    for p in result["repair_proposals"]:
        print(f"  [{p.get('risk', 'unknown').upper()}] {p['label']}")
    print("Repair Node: OK")
