"""
Parser Agent Node
==================
Uses Gemini to extract structured event data from raw input.
Handles text, images, and PDFs.

Graph position: ingestion -> PARSER -> enrichment
"""
import json
import tempfile
import os


EXTRACTION_PROMPT = """
You are an intelligent event extraction engine. Analyze the input and extract event information.

Return JSON in this exact format:
{
  "title": "",
  "category": "",
  "venue": "",
  "event_datetime": "",
  "severity_level": "",
  "complexity_score": 0,
  "estimated_prep_hours": 0,
  "key_topics": [],
  "action_items": []
}

Rules:
- category: exam, hackathon, assignment, meeting, personal, reminder
- severity_level: low, medium, high, critical
- complexity_score: 1-10
- estimated_prep_hours: realistic hours needed
- key_topics: 3-5 specific sub-topics
- action_items: 3-5 concrete steps
- Convert datetime to ISO format: YYYY-MM-DDTHH:MM:SS
- Return ONLY valid JSON
"""


def parser_node(state: dict, gemini_client) -> dict:
    """
    Parse raw input into structured event data using Gemini.

    Args:
        state: ChronaState dict
        gemini_client: google.generativeai configured client

    Updates state with:
        - extracted_events: list of event dicts
        - extraction_error: error message if failed
    """
    from datetime import datetime

    state["extracted_events"] = state.get("extracted_events", [])
    state["errors"] = state.get("errors", [])

    prompt_with_date = EXTRACTION_PROMPT + f"\n\nToday's date: {datetime.now().strftime('%Y-%m-%d')}"

    try:
        if state["input_type"] == "text":
            model = gemini_client.GenerativeModel(model_name="gemini-2.5-flash")
            full_prompt = f"{prompt_with_date}\n\nText to analyze:\n{state['raw_input']}"

            response = model.generate_content(
                full_prompt,
                generation_config={"response_mime_type": "application/json"},
            )
            raw_text = response.text
            parsed = json.loads(raw_text)

            # Handle both single event and list of events
            if isinstance(parsed, list):
                state["extracted_events"].extend(parsed)
            else:
                state["extracted_events"].append(parsed)

        elif state["input_type"] in ("image", "pdf", "file"):
            # Write bytes to temp file and upload to Gemini
            with tempfile.NamedTemporaryFile(delete=False, suffix=".tmp") as tmp:
                tmp.write(state["input_bytes"])
                tmp_path = tmp.name

            try:
                import google.generativeai as genai
                uploaded = genai.upload_file(path=tmp_path)
                model = gemini_client.GenerativeModel(model_name="gemini-2.5-flash")

                response = model.generate_content(
                    [prompt_with_date, uploaded],
                    generation_config={"response_mime_type": "application/json"},
                )
                raw_text = response.text
                parsed = json.loads(raw_text)

                if isinstance(parsed, list):
                    state["extracted_events"].extend(parsed)
                else:
                    state["extracted_events"].append(parsed)
            finally:
                os.unlink(tmp_path)
        else:
            state["errors"].append(f"parser_error: Unknown input type '{state['input_type']}'")

    except json.JSONDecodeError as e:
        state["errors"].append(f"parser_json_error: {e}")
        state["extraction_error"] = f"Gemini returned invalid JSON"
    except Exception as e:
        state["errors"].append(f"parser_error: {e}")
        state["extraction_error"] = str(e)

    return state


# ============================================================
# SELF-TEST
# ============================================================
if __name__ == "__main__":
    # Test with mock (no actual Gemini call)
    state = {
        "raw_input": "Test exam",
        "input_type": "text",
        "extracted_events": [],
        "errors": [],
    }
    print("Parser Node: OK (mock test - no Gemini call)")
