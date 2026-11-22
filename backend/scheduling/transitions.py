"""
100% Dynamic Common-Sense Transition & Stakes Engine
=====================================================
Uses AI reasoning (Groq transitions key -> Gemini fallback) to evaluate
real-world human physics, spatial travel, preparation time, and mental fatigue
for ANY arbitrary pair of tasks—with zero hardcoded assumptions.
"""
from typing import List, Dict, Any, Optional
from ai_router import AIRouter


TRANSITION_SYSTEM_PROMPT = """You are Chrona's common-sense human schedule reasoner.
Given a user's context and an ordered sequence of tasks/events, think like a thoughtful, practical human assistant about what happens BETWEEN consecutive items (Task A -> Task B).

Consider:
1. Location & Commute: Where does Task A happen vs Task B? (e.g., an external event/college/venue vs studying at home). Estimate realistic travel time for the time of day, plus settling-in / freshening-up time.
2. Preparation & Readiness: Does Task B require getting ready beforehand (showering, dressing up for a function/date, packing)? Include that BEFORE Task B starts.
3. Cognitive & Physical Fatigue vs Natural Breaks:
   - If Task A is mentally intense (e.g., 2h exam/study/coding) and Task B is relaxing or social (e.g., going out with friends, casual walk, dinner), Task B itself acts as a mental break—only a brief wrap-up (5-15 min) is needed unless travel is required.
   - If Task A is intense and Task B is ALSO intense, allow realistic mental decompression (15-30 min).
   - If Task A is physical (gym/sports), include cooldown/shower time.
4. User Autonomy & Explicit Overrides: If the user specified "no break", "strictly at X", or an exact travel duration, strictly honor their instruction.

Return ONLY valid JSON:
{
  "transitions": [
    {
      "from_index": 0,
      "to_index": 1,
      "buffer_minutes": 45,
      "travel_minutes": 30,
      "prep_minutes": 15,
      "recovery_minutes": 0,
      "reason": "Clear, natural 1-sentence explanation for the user"
    }
  ]
}"""


def compute_dynamic_transitions(
    ordered_tasks: List[dict],
    user_profile: Optional[dict] = None,
    router: Optional[AIRouter] = None,
) -> List[Dict[str, Any]]:
    """Dynamically compute human-sense transitions between every consecutive task pair."""
    if len(ordered_tasks) < 2:
        return []

    router = router or AIRouter()
    city = (user_profile or {}).get("city", "Bengaluru")
    wake = (user_profile or {}).get("wake_time", "08:00")
    sleep = (user_profile or {}).get("sleep_time", "22:30")

    compact_tasks = [
        {
            "index": idx,
            "title": t.get("title"),
            "task_type": t.get("task_type"),
            "location": t.get("location") or t.get("venue") or "unspecified",
            "start_time": t.get("start_time") or t.get("fixed_start"),
            "end_time": t.get("end_time") or t.get("fixed_end"),
            "duration_minutes": t.get("duration_minutes", 60),
            "notes": t.get("description") or t.get("completion_note") or "",
            "strict_time": bool(t.get("strict_time")),
        }
        for idx, t in enumerate(ordered_tasks)
    ]

    prompt = (
        f"User location context: {city} | Waking hours: {wake} to {sleep}\n"
        f"Ordered tasks for evaluation:\n{compact_tasks}"
    )

    try:
        res = router.complete(
            prompt=prompt,
            system=TRANSITION_SYSTEM_PROMPT,
            role="transitions",
            prefer_gemini=False,
            json_mode=True,
            temperature=0.15,
        )
        return res.get("transitions", [])
    except Exception:
        return [
            {
                "from_index": i,
                "to_index": i + 1,
                "buffer_minutes": 0 if (ordered_tasks[i].get("strict_time") or ordered_tasks[i + 1].get("strict_time")) else 15,
                "travel_minutes": 0,
                "prep_minutes": 0,
                "recovery_minutes": 15,
                "reason": "Standard transition window (offline fallback).",
            }
            for i in range(len(ordered_tasks) - 1)
        ]


def compute_pair_transition(prev_task: dict, next_task: dict, user_profile: Optional[dict] = None, router: Optional[AIRouter] = None) -> Dict[str, Any]:
    """Convenience wrapper for evaluating a single consecutive pair (Task A -> Task B)."""
    transitions = compute_dynamic_transitions([prev_task, next_task], user_profile=user_profile, router=router)
    if transitions:
        return transitions[0]
    return {
        "from_index": 0,
        "to_index": 1,
        "buffer_minutes": 15,
        "travel_minutes": 0,
        "prep_minutes": 0,
        "recovery_minutes": 15,
        "reason": "Standard transition buffer.",
    }
