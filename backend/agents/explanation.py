"""
Explanation Agent Node
=======================
Generates human-readable explanations for scheduling decisions.
Uses Gemini to produce natural language reasoning.

Graph position: repair/behavior_filter -> EXPLANATION -> END
"""
import json
from typing import Any


EXPLANATION_PROMPT = """
You are a scheduling assistant explaining schedule changes to a student.

Given the following scheduling context, write a brief, friendly explanation
(2-3 sentences) explaining what was scheduled and why.

Context:
{context_json}

Rules:
- Be concise and friendly
- Mention specific times if available
- If there were conflicts, explain how they were resolved
- If behavior data was used, mention it casually ("Based on your patterns...")
- Return ONLY the explanation text, no JSON
"""


def explanation_node(state: dict, gemini_client: Any = None) -> dict:
    """
    Generate a natural language explanation of the scheduling decisions.

    If Gemini is unavailable, falls back to a template-based explanation.

    Args:
        state: ChronaState dict with scheduling results
        gemini_client: optional google.generativeai configured client

    Updates state with:
        - explanation: human-readable string
        - final_output: complete result dict
    """
    state["errors"] = state.get("errors", [])

    # Build context for explanation
    context = {
        "events_extracted": len(state.get("extracted_events", [])),
        "tasks_enriched": len(state.get("enriched_tasks", [])),
        "conflicts_found": len(state.get("conflicts", [])),
        "repairs_proposed": len(state.get("repair_proposals", [])),
        "behavior_weights": state.get("behavior_weights", {}),
        "event_titles": [e.get("title", "Untitled") for e in state.get("extracted_events", [])[:5]],
    }

    # Try Gemini explanation
    if gemini_client:
        try:
            prompt = EXPLANATION_PROMPT.format(
                context_json=json.dumps(context, indent=2)
            )
            model = gemini_client.GenerativeModel(model_name="gemini-2.5-flash")
            response = model.generate_content(prompt)
            state["explanation"] = response.text.strip()
        except Exception as e:
            state["errors"].append(f"explanation_error: {e}")
            state["explanation"] = _fallback_explanation(context)
    else:
        state["explanation"] = _fallback_explanation(context)

    # Build final output
    state["final_output"] = {
        "events": state.get("extracted_events", []),
        "enriched_tasks": state.get("enriched_tasks", []),
        "conflicts": state.get("conflicts", []),
        "repairs": state.get("repair_proposals", []),
        "explanation": state["explanation"],
        "errors": state.get("errors", []),
    }

    return state


def _fallback_explanation(context: dict) -> str:
    """Template-based explanation when Gemini is unavailable."""
    parts = []

    n_events = context.get("events_extracted", 0)
    if n_events > 0:
        titles = context.get("event_titles", [])
        title_str = ", ".join(titles[:3])
        parts.append(f"Extracted {n_events} event(s): {title_str}.")

    n_tasks = context.get("tasks_enriched", 0)
    if n_tasks > 0:
        parts.append(f"Generated {n_tasks} study block(s) for preparation.")

    n_conflicts = context.get("conflicts_found", 0)
    if n_conflicts > 0:
        n_repairs = context.get("repairs_proposed", 0)
        parts.append(f"Found {n_conflicts} conflict(s) and proposed {n_repairs} repair(s).")

    weights = context.get("behavior_weights", {})
    if weights:
        best_slot = max(weights, key=weights.get) if weights else "afternoon"
        parts.append(f"Based on your patterns, {best_slot} sessions are preferred.")

    return " ".join(parts) if parts else "Schedule processed successfully."


# ============================================================
# SELF-TEST
# ============================================================
if __name__ == "__main__":
    state = {
        "extracted_events": [
            {"title": "OS Final Exam"},
            {"title": "Database Quiz"},
        ],
        "enriched_tasks": [
            {"title": "Study: Scheduling"},
            {"title": "Study: Indexing"},
        ],
        "conflicts": [],
        "repair_proposals": [],
        "behavior_weights": {"morning": 0.3, "afternoon": 0.7, "evening": 0.5, "night": 0.2},
        "errors": [],
    }

    result = explanation_node(state, None)
    print(f"Explanation: {result['explanation']}")
    assert "OS Final Exam" in result["explanation"]
    assert result["final_output"] is not None
    print("Explanation Node: OK")
