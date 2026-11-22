"""
Parser Agent Node — v3 (AIRouter: Gemini Vision/PDF + Groq Fast Pool)
====================================================================
Extracts structured task/event data with 3-axis stakes, location, and effort.
"""
import json
from datetime import datetime
from ai_router import AIRouter


EXTRACTION_PROMPT = """You are an intelligent event and task extraction engine for Chrona, an AI scheduling assistant.

Analyze the input and extract ALL tasks/events/commitments with rich real-world metadata.

Return a JSON ARRAY of objects in this exact format:
[{
  "title": "string",
  "category": "exam|quiz|assignment|class|meeting|study|workout|family|social|appointment|personal|reminder|project|other",
  "venue": "string or null",
  "location": "home|campus|gym|external_venue|string",
  "event_datetime": "ISO datetime or null",
  "start_time": "ISO datetime or time string",
  "end_time": "ISO datetime or time string",
  "date": "YYYY-MM-DD or DD-MM-YYYY string",
  "duration_minutes": 60,
  "priority": 3,
  "flexibility": true,
  "movability": "immovable|negotiable|fluid",
  "effort_level": "low|medium|high",
  "life_significance": "critical|high|normal|low",
  "task_type": "task|fixed|study|exam|quiz|assignment|class|workout|family|social|appointment",
  "is_fixed": false,
  "fixed_start": "ISO datetime or null",
  "fixed_end": "ISO datetime or null",
  "deadline": "ISO datetime or null",
  "recurrence": "daily|weekdays|weekly|null",
  "recurrence_days": null,
  "key_topics": [],
  "prep_hours": 0,
  "is_project": false,
  "project_description": null
}]

Rules:
- movability: "immovable" for final exams, internals, live classes, weddings/family functions, medical appointments; "negotiable" for personal dates/milestones; "fluid" for self-study, assignments, gym, chores.
- effort_level: "low" for small internal tests/quizzes or casual hangouts; "high" for final exams, major projects, deep study.
- location: infer "home" for self-study/coding/chores, "campus" for classes/exams, "gym" for workouts, or exact external venue if stated.
- Convert relative times to ISO dates using today's date provided.
- Return ONLY a valid JSON array, no markdown.
"""


def parser_node(state: dict, gemini_client=None, groq_client=None) -> dict:
    state.setdefault("extracted_events", [])
    state.setdefault("errors", [])

    router = AIRouter()
    today = datetime.now().strftime("%Y-%m-%d %A")
    prompt_header = f"{EXTRACTION_PROMPT}\n\nToday's date: {today}"

    input_type = state.get("input_type", "text")
    raw_input = state.get("raw_input", "")
    input_bytes = state.get("input_bytes")

    try:
        if input_bytes and input_type in ("image", "pdf"):
            mime = "application/pdf" if input_type == "pdf" else "image/jpeg"
            extracted = router.parse_multimodal_with_gemini(
                file_bytes=input_bytes,
                mime_type=mime,
                prompt=f"{prompt_header}\n\nAdditional user note: {raw_input}",
                json_mode=True,
            )
        else:
            if not raw_input.strip():
                state["errors"].append("parser_error: No content to parse")
                return state
            extracted = router.complete(
                prompt=f"{prompt_header}\n\nInput:\n{raw_input}",
                role="chat",
                prefer_gemini=False,
                json_mode=True,
                temperature=0.1,
            )

        if isinstance(extracted, dict):
            for key in ("tasks", "events", "items"):
                if key in extracted:
                    extracted = extracted[key]
                    break
            else:
                extracted = [extracted]

        state["extracted_events"] = extracted if isinstance(extracted, list) else []
    except Exception as e:
        state["errors"].append(f"parser_error: {e}")
        state["extraction_error"] = str(e)

    return state
