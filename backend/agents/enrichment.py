"""
Enrichment Agent Node - Chrona's Killer Feature
=================================================
Converts key_topics + estimated_prep_hours into individual study blocks.
This is what no other scheduler does.

Example:
  Input:  "OS Final Exam" with key_topics=["scheduling", "memory", "deadlocks"]
          and estimated_prep_hours=12
  Output: 5 study blocks of 45-90 min each, with progressive deadlines

Graph position: parser -> ENRICHMENT -> scheduling
"""
import json
from datetime import datetime, timedelta
from typing import Any


ENRICHMENT_PROMPT = """
You are a study planning expert. Given an extracted event with key_topics and estimated_prep_hours,
generate a list of individual study block tasks.

Rules:
- Split the total prep hours across all topics proportionally by complexity
- Each block should be 45-90 minutes maximum (cognitive science optimal range)
- If a topic has sub-topics, split further
- Set deadlines progressively: hardest topics first, review session last (1 day before event)
- Return ONLY valid JSON, no markdown, no explanation

Input event:
{event_json}

Return format (JSON array only):
[
  {{
    "title": "Study: <topic>",
    "task_type": "study_block",
    "duration_minutes": <int>,
    "deadline": "<ISO date string>",
    "priority": <1-4>,
    "topic": "<topic name>"
  }}
]
"""


def enrichment_node(state: dict, gemini_client: Any) -> dict:
    """
    Enrich extracted events by generating study blocks from key_topics.

    Only enriches events that have:
    - key_topics (non-empty list)
    - estimated_prep_hours >= 0.5

    Args:
        state: ChronaState dict
        gemini_client: google.generativeai configured client

    Updates state with:
        - enriched_tasks: list of study block dicts
    """
    enriched_tasks = []
    state["errors"] = state.get("errors", [])

    for event in state.get("extracted_events", []):
        # Only enrich events with prep work needed
        if not event.get("key_topics") or not event.get("estimated_prep_hours"):
            continue
        if event.get("estimated_prep_hours", 0) < 0.5:
            continue

        prompt = ENRICHMENT_PROMPT.format(
            event_json=json.dumps(event, indent=2)
        )

        try:
            model = gemini_client.GenerativeModel(model_name="gemini-2.5-flash")
            response = model.generate_content(prompt)
            raw = response.text.strip()

            # Clean JSON (strip markdown fences if present)
            if raw.startswith("```"):
                lines = raw.split("\n")
                # Remove first and last lines (``` markers)
                raw = "\n".join(lines[1:-1] if lines[-1].strip() == "```" else lines[1:])
                if raw.startswith("json"):
                    raw = raw[4:]
            raw = raw.strip()

            tasks = json.loads(raw)
            for task in tasks:
                task["event_id"] = event.get("id")
                task["user_id"]  = state.get("user_id")
                enriched_tasks.append(task)

        except Exception as e:
            state["errors"].append(f"enrichment_error for '{event.get('title')}': {e}")
            # Fallback: create one generic prep block
            if event.get("event_datetime"):
                try:
                    event_dt = datetime.fromisoformat(
                        str(event["event_datetime"]).replace("Z", "+00:00").replace("+00:00", "")
                    )
                except (ValueError, TypeError):
                    event_dt = datetime.now() + timedelta(days=3)

                enriched_tasks.append({
                    "title":            f"Prepare: {event['title']}",
                    "task_type":        "study_block",
                    "duration_minutes": min(int(event["estimated_prep_hours"] * 60), 90),
                    "deadline":         (event_dt - timedelta(days=1)).isoformat(),
                    "priority":         2,
                    "event_id":         event.get("id"),
                    "user_id":          state.get("user_id")
                })

    state["enriched_tasks"] = enriched_tasks
    return state


# ============================================================
# SELF-TEST
# ============================================================
if __name__ == "__main__":
    # Test fallback behavior (no Gemini)
    state = {
        "user_id": "test-user",
        "extracted_events": [
            {
                "id": "evt-001",
                "title": "OS Final Exam",
                "key_topics": ["scheduling", "memory", "deadlocks"],
                "estimated_prep_hours": 12,
                "event_datetime": (datetime.now() + timedelta(days=5)).isoformat(),
            }
        ],
        "errors": [],
    }

    # Without Gemini, the fallback should create 1 generic block
    class MockClient:
        class GenerativeModel:
            def __init__(self, **kw): pass
            def generate_content(self, prompt):
                raise Exception("Mock - no API key")

        def GenerativeModel(self, **kw):
            return self.GenerativeModel(**kw)

    result = enrichment_node(state, MockClient())
    assert len(result.get("enriched_tasks", [])) == 1
    assert result["enriched_tasks"][0]["task_type"] == "study_block"
    print(f"Fallback task: {result['enriched_tasks'][0]['title']}")
    print("Enrichment Node: OK (fallback test)")
