"""
Enrichment Agent Node — v2
===========================
Enriches extracted events with scheduling metadata.
Estimates durations, priorities, and prep blocks.
Optionally decomposes projects into subtasks.

Graph position: parser -> ENRICHMENT -> scheduling
"""
import json
import os
from datetime import datetime, timedelta, timezone


ENRICHMENT_PROMPT = """You are a scheduling enrichment engine for Chrona.

Given extracted events/tasks, enrich each with realistic scheduling metadata and generate study/prep blocks if needed.

For each input event, return an enriched version. For events needing preparation (exams, presentations, big assignments), also generate prep_blocks.

Return JSON in this format:
{
  "enriched": [
    {
      "original_id": "index",
      "title": "string",
      "task_type": "string",
      "duration_minutes": number,
      "priority": 1-4,
      "flexibility": boolean,
      "deadline": "ISO string or null",
      "estimated_sessions": 1,
      "session_duration_minutes": 60,
      "needs_prep": false,
      "completion_criteria": "string"
    }
  ],
  "prep_blocks": [
    {
      "for_event": "event title",
      "title": "string",
      "duration_minutes": number,
      "priority": 2,
      "task_type": "study_block",
      "days_before_deadline": number
    }
  ]
}

Rules:
- For exams: generate 1 prep block per subject/topic (60-120 min each)
- For assignments: estimate realistic completion time
- For recurring tasks (class, workout): set estimated_sessions=1 (each occurrence is 1 session)
- Completion criteria should be specific and measurable
- If prep_hours > 2, split into multiple sessions
- Today's date: {today}
"""


def enrichment_node(state: dict, gemini_client=None, groq_client=None) -> dict:
    """
    Enrich extracted events with scheduling metadata.
    Generates prep/study blocks for events that need preparation.
    """
    state.setdefault("enriched_tasks", [])
    state.setdefault("errors", [])

    events = state.get("extracted_events", [])
    if not events:
        return state

    today = datetime.now().strftime("%Y-%m-%d")
    prompt = ENRICHMENT_PROMPT.format(today=today)

    def _enrich_with_gemini(events_data):
        from google import genai
        api_key = os.getenv("GEMINI_API_KEY")
        client = gemini_client if (gemini_client and hasattr(gemini_client, 'models')) else genai.Client(api_key=api_key)

        full_prompt = f"{prompt}\n\nEvents to enrich:\n{json.dumps(events_data, indent=2)}"
        resp = client.models.generate_content(
            model="gemini-3.8-flash",
            contents=full_prompt,
            config=genai.types.GenerateContentConfig(
                response_mime_type="application/json",
                temperature=0.1,
                max_output_tokens=4096,
            )
        )
        text = resp.text.strip()
        if text.startswith("```"):
            text = "\n".join(text.split("\n")[1:-1])
        return json.loads(text)

    def _enrich_with_groq(events_data):
        full_prompt = f"{prompt}\n\nEvents to enrich:\n{json.dumps(events_data, indent=2)}"
        resp = groq_client.chat.completions.create(
            model="openai/gpt-oss-120b",
            messages=[{"role": "user", "content": full_prompt}],
            response_format={"type": "json_object"},
            temperature=0.1,
            max_tokens=4096,
        )
        return json.loads(resp.choices[0].message.content)

    # Try enrichment
    result = None
    if gemini_client:
        try:
            result = _enrich_with_gemini(events)
        except Exception as e:
            state["errors"].append(f"enrichment_gemini_error: {e}")

    if not result and groq_client:
        try:
            result = _enrich_with_groq(events)
        except Exception as e:
            state["errors"].append(f"enrichment_groq_error: {e}")

    if result:
        # Merge enriched data back with original events
        enriched_map = {str(e.get("original_id", i)): e for i, e in enumerate(result.get("enriched", []))}
        all_tasks = []

        for i, event in enumerate(events):
            enriched = enriched_map.get(str(i), {})
            merged = {**event, **enriched}
            all_tasks.append(merged)

        # Add prep blocks as separate tasks
        for block in result.get("prep_blocks", []):
            all_tasks.append({
                **block,
                "task_type": block.get("task_type", "study_block"),
                "flexibility": True,
                "is_prep_block": True,
            })

        state["enriched_tasks"] = all_tasks
    else:
        # Fallback: use events as-is with defaults
        state["enriched_tasks"] = _default_enrichment(events)

    return state


def _default_enrichment(events: list) -> list:
    """Simple rule-based enrichment when AI is unavailable."""
    enriched = []
    for event in events:
        task = dict(event)
        category = event.get("category", "").lower()
        prep = event.get("prep_hours", 0) or 0

        # Set defaults based on category
        if category == "exam":
            task.setdefault("priority", 1)
            task.setdefault("duration_minutes", 180)
            task["flexibility"] = False
        elif category in ("class", "meeting"):
            task.setdefault("priority", 2)
            task.setdefault("duration_minutes", 90)
            task["flexibility"] = False
        elif category == "assignment":
            task.setdefault("priority", 2)
            task.setdefault("duration_minutes", 120)
        elif category == "workout":
            task.setdefault("priority", 3)
            task.setdefault("duration_minutes", 60)
        else:
            task.setdefault("priority", 3)
            task.setdefault("duration_minutes", 60)

        enriched.append(task)

        # Generate prep blocks for exams
        if category == "exam" and prep > 0 and event.get("event_datetime"):
            topics = event.get("key_topics", []) or ["Review"]
            for topic in topics[:5]:
                enriched.append({
                    "title": f"Study: {topic}",
                    "task_type": "study_block",
                    "duration_minutes": min(90, int(prep * 60 / max(len(topics), 1))),
                    "priority": 2,
                    "flexibility": True,
                    "deadline": event.get("event_datetime"),
                    "is_prep_block": True,
                    "for_event": event.get("title"),
                })

    return enriched


# ============================================================
# SELF-TEST
# ============================================================
if __name__ == "__main__":
    state = {
        "extracted_events": [
            {"title": "OS Final Exam", "category": "exam", "event_datetime": "2026-11-15T14:00:00",
             "prep_hours": 6, "key_topics": ["Processes", "Memory", "File Systems"]},
        ],
        "errors": [],
    }
    result = enrichment_node(state, None, None)
    print(f"Enriched tasks: {len(result.get('enriched_tasks', []))}")
    for t in result.get("enriched_tasks", []):
        print(f"  - {t.get('title')} ({t.get('task_type')})")
    print("Enrichment Node: OK (fallback used)")
