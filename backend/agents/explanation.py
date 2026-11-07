"""
Explanation Agent Node — v2
============================
Generates human-readable explanations for scheduling decisions.
Uses Gemini (google-genai SDK) with Groq fallback.

Graph position: repair/conflict_check -> EXPLANATION -> END
"""
import json
import os
from typing import Any


EXPLANATION_PROMPT = """You are Chrona, a friendly AI scheduling assistant explaining scheduling decisions.

Given the scheduling context below, write a brief, helpful explanation (2-4 sentences) of what was scheduled and why.

Context:
{context}

Rules:
- Be friendly and concise
- Mention specific tasks if there are few (< 5)
- If there were conflicts, explain how they were resolved
- Mention behavioral preferences if used (e.g., "Since you prefer mornings...")
- If tasks couldn't be scheduled, explain why briefly
- Do NOT return JSON — return only natural language text
"""


def explanation_node(state: dict, gemini_client: Any = None) -> dict:
    """
    Generate a natural language explanation of scheduling decisions.
    Falls back to template-based explanation if AI unavailable.
    """
    state.setdefault("errors", [])

    context = {
        "events_extracted": len(state.get("extracted_events", [])),
        "tasks_enriched": len(state.get("enriched_tasks", [])),
        "conflicts_found": len(state.get("conflicts", [])),
        "repairs_proposed": len(state.get("repair_proposals", [])),
        "event_titles": [e.get("title", "?") for e in state.get("extracted_events", [])[:5]],
        "behavior_weights": state.get("behavior_weights", {}),
        "schedule_result": {
            "scheduled": len(state.get("schedule_result", {}).get("scheduled", [])) if state.get("schedule_result") else 0,
            "impossible": len(state.get("schedule_result", {}).get("impossible", [])) if state.get("schedule_result") else 0,
        }
    }

    explanation = None

    if gemini_client:
        try:
            from google import genai
            api_key = os.getenv("GEMINI_API_KEY")
            client = gemini_client if hasattr(gemini_client, 'models') else genai.Client(api_key=api_key)

            prompt = EXPLANATION_PROMPT.format(context=json.dumps(context, indent=2))
            resp = client.models.generate_content(
                model="gemini-3.8-flash",
                contents=prompt,
                config=genai.types.GenerateContentConfig(
                    temperature=0.7,
                    max_output_tokens=512,
                )
            )
            explanation = resp.text.strip()
        except Exception as e:
            state["errors"].append(f"explanation_error: {e}")

    if not explanation:
        explanation = _fallback_explanation(context)

    state["explanation"] = explanation

    state["final_output"] = {
        "events": state.get("extracted_events", []),
        "enriched_tasks": state.get("enriched_tasks", []),
        "schedule": state.get("schedule_result"),
        "conflicts": state.get("conflicts", []),
        "repairs": state.get("repair_proposals", []),
        "explanation": explanation,
        "errors": state.get("errors", []),
    }

    return state


def _fallback_explanation(context: dict) -> str:
    parts = []

    n_events = context.get("events_extracted", 0)
    if n_events > 0:
        titles = context.get("event_titles", [])
        if titles:
            parts.append(f"Extracted {n_events} task(s): {', '.join(titles[:3])}{'...' if len(titles) > 3 else ''}.")
        else:
            parts.append(f"Extracted {n_events} task(s).")

    n_tasks = context.get("tasks_enriched", 0)
    if n_tasks > n_events:
        parts.append(f"Generated {n_tasks - n_events} additional prep/study block(s).")

    n_conflicts = context.get("conflicts_found", 0)
    if n_conflicts > 0:
        n_repairs = context.get("repairs_proposed", 0)
        parts.append(f"Found {n_conflicts} scheduling conflict(s) and proposed {n_repairs} repair option(s).")

    weights = context.get("behavior_weights", {})
    if weights:
        best = max(weights, key=weights.get) if weights else "afternoon"
        parts.append(f"Based on your productivity patterns, {best} sessions are preferred.")

    return " ".join(parts) if parts else "Tasks processed successfully. Ready to schedule."


# ============================================================
# SELF-TEST
# ============================================================
if __name__ == "__main__":
    state = {
        "extracted_events": [{"title": "Math Exam"}, {"title": "Physics Lab"}],
        "enriched_tasks": [{"title": "Study: Calculus"}, {"title": "Study: Mechanics"}],
        "conflicts": [],
        "repair_proposals": [],
        "behavior_weights": {"morning": 0.8, "afternoon": 0.5, "evening": 0.3, "night": 0.1},
        "errors": [],
    }
    result = explanation_node(state, None)
    print(f"Explanation: {result['explanation']}")
    assert result["final_output"] is not None
    print("Explanation Node: OK")
