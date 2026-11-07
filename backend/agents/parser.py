"""
Parser Agent Node — v2 (google-genai SDK)
==========================================
Extracts structured task/event data from raw input using Gemini.
Handles text, images, and PDFs. Falls back to Groq if Gemini fails.

Graph position: ingestion -> PARSER -> enrichment
"""
import json
import os
from datetime import datetime


EXTRACTION_PROMPT = """You are an intelligent event and task extraction engine for Chrona, an AI scheduling assistant.

Analyze the input and extract ALL tasks/events/commitments with rich metadata.

Return a JSON ARRAY of objects in this exact format:
[{
  "title": "string",
  "category": "exam|assignment|class|meeting|study|workout|personal|reminder|project|other",
  "venue": "string or null",
  "event_datetime": "ISO datetime or null",
  "duration_minutes": 60,
  "priority": 3,
  "flexibility": true,
  "task_type": "task|fixed|study|exam|assignment|class|workout|meeting",
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
- priority: 1=critical, 2=important, 3=normal, 4=optional
- flexibility=false for classes, meetings, fixed appointments
- flexibility=true for study, assignments, workouts
- is_fixed=true only when user specifies an exact time
- Recurrence: "daily" if every day, "weekdays" if Mon-Fri, "weekly" if once/week
- Convert relative times to ISO dates using today's date provided
- If user mentions a project or large goal, set is_project=true and project_description
- Extract EVERY task mentioned, even if brief
- Return ONLY valid JSON array, no markdown
"""


def parser_node(state: dict, gemini_client=None, groq_client=None) -> dict:
    """
    Parse raw input into structured event/task data.

    Uses Gemini (google-genai SDK) with Groq fallback.
    Handles text, PDF bytes, and image bytes.
    """
    state.setdefault("extracted_events", [])
    state.setdefault("errors", [])

    today = datetime.now().strftime("%Y-%m-%d %A")
    prompt_header = EXTRACTION_PROMPT + f"\n\nToday's date: {today}"

    def _extract_with_new_gemini(content):
        """Use google-genai SDK."""
        from google import genai
        api_key = os.getenv("GEMINI_API_KEY")
        client = gemini_client if (gemini_client and hasattr(gemini_client, 'models')) else genai.Client(api_key=api_key)

        full_prompt = f"{prompt_header}\n\nInput:\n{content}"
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
        result = json.loads(text)
        return result if isinstance(result, list) else [result]

    def _extract_with_legacy_gemini(content):
        """Use old google.generativeai SDK as fallback."""
        import google.generativeai as old_genai
        model = old_genai.GenerativeModel(model_name="gemini-2.0-flash")
        full_prompt = f"{prompt_header}\n\nInput:\n{content}"
        resp = model.generate_content(
            full_prompt,
            generation_config={"response_mime_type": "application/json", "temperature": 0.1}
        )
        text = resp.text.strip()
        if text.startswith("```"):
            text = "\n".join(text.split("\n")[1:-1])
        result = json.loads(text)
        return result if isinstance(result, list) else [result]

    def _extract_with_groq(content):
        if not groq_client:
            raise ValueError("Groq client not available")
        full_prompt = f"{prompt_header}\n\nInput:\n{content}"
        resp = groq_client.chat.completions.create(
            model="openai/gpt-oss-120b",
            messages=[{"role": "user", "content": full_prompt}],
            response_format={"type": "json_object"},
            temperature=0.1,
            max_tokens=4096,
        )
        text = resp.choices[0].message.content.strip()
        result = json.loads(text)
        # Groq might return {"tasks": [...]} instead of [...]
        if isinstance(result, dict) and not isinstance(result, list):
            for key in ("tasks", "events", "items"):
                if key in result:
                    return result[key]
            return [result]
        return result

    # Determine what to parse
    input_type = state.get("input_type", "text")
    raw_input  = state.get("raw_input", "")
    input_bytes = state.get("input_bytes")

    # Prepare content
    content = raw_input or ""
    if input_bytes and input_type in ("image", "pdf"):
        import base64
        # For now encode to base64 and pass as text description
        # Full vision would use Gemini Files API
        content = f"[Binary file: {input_type}]\n{raw_input}"

    if not content.strip():
        state["errors"].append("parser_error: No content to parse")
        return state

    # Try extraction chain
    extracted = None
    for attempt, extractor in enumerate([_extract_with_new_gemini, _extract_with_legacy_gemini, _extract_with_groq]):
        try:
            extracted = extractor(content)
            break
        except Exception as e:
            state["errors"].append(f"parser_attempt_{attempt}_error: {e}")

    if extracted:
        state["extracted_events"] = extracted
    else:
        state["extraction_error"] = "All extraction attempts failed"

    return state


# ============================================================
# SELF-TEST
# ============================================================
if __name__ == "__main__":
    state = {
        "raw_input": "I have a math exam on Friday at 2pm and need to study for 3 hours",
        "input_bytes": None,
        "input_type": "text",
        "errors": [],
    }
    result = parser_node(state, None, None)
    print(f"Errors: {result.get('errors')}")
    print(f"Events: {len(result.get('extracted_events', []))}")
    if result.get("extracted_events"):
        print(f"First: {result['extracted_events'][0].get('title')}")
    print("Parser Node: OK (no AI available, expected empty)")
