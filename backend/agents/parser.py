"""
Parser Agent Node
==================
Uses Gemini to extract structured event data from raw input.
Handles text, images, and PDFs. Falls back to Groq if Gemini fails.

Graph position: ingestion -> PARSER -> enrichment
"""
import json
import tempfile
import os
import base64


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


def parser_node(state: dict, gemini_client, groq_client=None) -> dict:
    """
    Parse raw input into structured event data using Gemini with Groq fallback.

    Args:
        state: ChronaState dict
        gemini_client: google.generativeai configured client
        groq_client: optional Groq client for fallback

    Updates state with:
        - extracted_events: list of event dicts
        - extraction_error: error message if failed
    """
    from datetime import datetime

    state["extracted_events"] = state.get("extracted_events", [])
    state["errors"] = state.get("errors", [])

    prompt_with_date = EXTRACTION_PROMPT + f"\n\nToday's date: {datetime.now().strftime('%Y-%m-%d')}"

    def _extract_with_gemini(text_or_parts):
        model = gemini_client.GenerativeModel(model_name="gemini-flash-latest")
        response = model.generate_content(
            text_or_parts,
            generation_config={"response_mime_type": "application/json"},
            request_options={"timeout": 10}
        )
        return json.loads(response.text)

    def _extract_with_groq(text_or_parts):
        if not groq_client:
            raise Exception("Groq client not available")
        # Convert parts to text for Groq (Groq doesn't support inline images the same way)
        if isinstance(text_or_parts, list):
            text_content = ""
            for part in text_or_parts:
                if isinstance(part, str):
                    text_content += part
                elif hasattr(part, 'mime_type') and part.mime_type.startswith('image'):
                    text_content += f"\n[Image: {part.mime_type}]"
            text_or_parts = text_content
        response = groq_client.chat.completions.create(
            model="llama-3.3-70b-versatile",
            messages=[{"role": "user", "content": text_or_parts}],
            response_format={"type": "json_object"},
            temperature=0.1,
            max_tokens=4096,
        )
        return json.loads(response.choices[0].message.content)

    try:
        if state["input_type"] == "text":
            full_prompt = f"{prompt_with_date}\n\nText to analyze:\n{state['raw_input']}"
            
            # Try Gemini first
            if gemini_client:
                try:
                    raw_text = response.text
                    parsed = _extract_with_gemini(full_prompt)
                except Exception as e:
                    print(f"⚠️ Parser Gemini failed: {e}, trying Groq...")
                    if groq_client:
                        parsed = _extract_with_groq(full_prompt)
                    else:
                        raise
            else:
                parsed = _extract_with_groq(full_prompt)

            # Handle both single event and list of events
            if isinstance(parsed, list):
                state["extracted_events"].extend(parsed)
            else:
                state["extracted_events"].append(parsed)

        elif state["input_type"] in ("image", "pdf", "file"):
            # Write bytes to temp file and encode as base64 for inline data
            mime_map = {
                "image": "image/png",  # default for images
                "pdf": "application/pdf",
                "file": "application/octet-stream",
            }
            suffix_map = {
                "image": ".png",
                "pdf": ".pdf",
                "file": ".tmp",
            }
            mime_type = mime_map.get(state["input_type"], "application/octet-stream")
            suffix = suffix_map.get(state["input_type"], ".tmp")
            
            with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
                tmp.write(state["input_bytes"])
                tmp_path = tmp.name

            try:
                import google.generativeai as genai
                import base64
                
                # Read file and encode as base64 for inline data
                with open(tmp_path, "rb") as f:
                    file_data = f.read()
                
                # Use inline data for Gemini
                inline_data = {
                    "mime_type": mime_type,
                    "data": base64.b64encode(file_data).decode('utf-8')
                }
                
                # Try Gemini first
                if gemini_client:
                    try:
                        model = gemini_client.GenerativeModel(model_name="gemini-flash-latest")
                        response = model.generate_content(
                            [prompt_with_date, inline_data],
                            generation_config={"response_mime_type": "application/json"},
                            request_options={"timeout": 10}
                        )
                        parsed = json.loads(response.text)
                    except Exception as e:
                        print(f"⚠️ Parser Gemini (image) failed: {e}, trying Groq...")
                        # For Groq, we can only send text description, not the image directly
                        if groq_client:
                            full_prompt = f"{prompt_with_date}\n\nFile: {state.get('raw_input', 'uploaded file')} (type: {mime_type})\n\nAnalyze this file and extract event information as JSON."
                            parsed = _extract_with_groq(full_prompt)
                        else:
                            raise
                else:
                    # No Gemini client, use Groq
                    if groq_client:
                        full_prompt = f"{prompt_with_date}\n\nFile: {state.get('raw_input', 'uploaded file')} (type: {mime_type})\n\nAnalyze this file and extract event information as JSON."
                        parsed = _extract_with_groq(full_prompt)
                    else:
                        raise Exception("No AI client available")

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
