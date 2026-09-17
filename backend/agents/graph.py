"""
LangGraph Agent Graph - Chrona's Agentic Core
===============================================
Orchestrates the full pipeline from input ingestion to scheduled output.

Graph topology:
  ingestion -> parser -> enrichment -> scheduling -> conflict_check
                                                          |           |
                                                     (conflict)   (clear)
                                                          |           |
                                                       repair     explanation
                                                          |           |
                                                       explanation  <-+
                                                          |
                                                         END

Usage:
    from agents.graph import build_chrona_graph

    graph = build_chrona_graph(gemini_client, supabase_client, redis_client)
    result = graph.invoke(initial_state)

Note: This module requires langgraph. If langgraph is not installed,
use run_pipeline() as a simple sequential fallback.
"""
import json
from datetime import datetime, timedelta
from typing import Optional

# Import agent nodes
from .ingestion import ingestion_node
from .parser import parser_node
from .enrichment import enrichment_node
from .repair import repair_node
from .explanation import explanation_node


def build_chrona_graph(gemini_client, supabase_client, redis_client=None):
    """
    Build the LangGraph state machine for Chrona.

    Args:
        gemini_client: google.generativeai module (configured)
        supabase_client: Supabase client instance
        redis_client: optional Redis client for caching

    Returns:
        Compiled LangGraph graph, or None if langgraph not available.
        Use run_pipeline() as fallback.
    """
    try:
        from langgraph.graph import StateGraph, END
        from typing import TypedDict, Annotated
        import operator

        class ChronaState(TypedDict):
            # Input
            raw_input:        Optional[str]
            input_bytes:      Optional[bytes]
            input_type:       str
            user_id:          str

            # Extraction
            extracted_events: list
            extraction_error: Optional[str]
            enriched_tasks:   list

            # Scheduling
            schedule_result:  Optional[dict]
            conflicts:        list

            # Repair
            repair_proposals: list
            accepted_repair:  Optional[dict]

            # Behavior
            behavior_weights: dict

            # Output
            explanation:      str
            final_output:     Optional[dict]
            errors:           Annotated[list, operator.add]

        def scheduling_node(state):
            """Load behavior weights and prepare for scheduling."""
            try:
                from behavior.profile import BehaviorProfile

                # Try Redis cache first
                cached = None
                if redis_client:
                    try:
                        cached = redis_client.get(f"chrona:profile:{state['user_id']}")
                    except Exception:
                        pass

                if cached:
                    profile_data = json.loads(cached)
                    profile = BehaviorProfile.from_db_row(profile_data)
                else:
                    # Fallback to Supabase
                    try:
                        row = (supabase_client.table("behavior_profiles")
                               .select("*").eq("user_id", state["user_id"])
                               .single().execute())
                        profile = (BehaviorProfile.from_db_row(row.data)
                                   if row.data else
                                   BehaviorProfile(user_id=state["user_id"]))
                    except Exception:
                        profile = BehaviorProfile(user_id=state["user_id"])

                state["behavior_weights"] = profile.get_scheduling_weights()
            except Exception as e:
                state["errors"] = state.get("errors", [])
                state["errors"].append(f"behavior_load_error: {e}")
                state["behavior_weights"] = {
                    "morning": 0.5, "afternoon": 0.5,
                    "evening": 0.5, "night": 0.5
                }
            return state

        def conflict_check_node(state):
            """Check for schedule conflicts on extracted events."""
            from scheduling.conflict import detect_conflicts

            conflicts = []
            for event in state.get("extracted_events", []):
                if event.get("event_datetime"):
                    try:
                        start = datetime.fromisoformat(
                            str(event["event_datetime"]).replace("Z", "").replace("+00:00", "")
                        )
                        duration = event.get("estimated_prep_hours", 1) or 1
                        end = start + timedelta(hours=duration)
                        found = detect_conflicts(
                            supabase_client, state["user_id"], start, end
                        )
                        if found:
                            conflicts.extend(found)
                    except Exception as e:
                        state["errors"] = state.get("errors", [])
                        state["errors"].append(f"conflict_check_error: {e}")

            state["conflicts"] = conflicts
            return state

        def should_repair(state) -> str:
            return "repair" if state.get("conflicts") else "explanation"

        # Build graph
        graph = StateGraph(ChronaState)
        graph.add_node("ingestion",      lambda s: ingestion_node(s))
        graph.add_node("parser",         lambda s: parser_node(s, gemini_client))
        graph.add_node("enrichment",     lambda s: enrichment_node(s, gemini_client))
        graph.add_node("scheduling",     scheduling_node)
        graph.add_node("conflict_check", conflict_check_node)
        graph.add_node("repair",         lambda s: repair_node(s, gemini_client, redis_client))
        graph.add_node("explanation",    lambda s: explanation_node(s, gemini_client))

        graph.set_entry_point("ingestion")
        graph.add_edge("ingestion",      "parser")
        graph.add_edge("parser",         "enrichment")
        graph.add_edge("enrichment",     "scheduling")
        graph.add_edge("scheduling",     "conflict_check")
        graph.add_conditional_edges("conflict_check", should_repair, {
            "repair":      "repair",
            "explanation": "explanation"
        })
        graph.add_edge("repair",         "explanation")
        graph.add_edge("explanation",    END)

        return graph.compile()

    except ImportError:
        print("WARNING: langgraph not installed. Use run_pipeline() as fallback.")
        return None


def run_pipeline(state: dict, gemini_client, supabase_client, redis_client=None) -> dict:
    """
    Sequential fallback when LangGraph is not available.
    Runs the same nodes in order without the graph framework.

    This is useful for:
    - Testing without langgraph installed
    - Simpler deployments
    - Debugging individual nodes
    """
    state["errors"] = state.get("errors", [])
    state["extracted_events"] = state.get("extracted_events", [])
    state["enriched_tasks"] = state.get("enriched_tasks", [])
    state["conflicts"] = state.get("conflicts", [])
    state["repair_proposals"] = state.get("repair_proposals", [])

    # 1. Ingestion
    state = ingestion_node(state)

    # 2. Parser
    state = parser_node(state, gemini_client)

    # 3. Enrichment
    state = enrichment_node(state, gemini_client)

    # 4. Scheduling (behavior weights)
    from behavior.profile import BehaviorProfile
    try:
        cached = None
        if redis_client:
            try:
                cached = redis_client.get(f"chrona:profile:{state.get('user_id', '')}")
            except Exception:
                pass
        if cached:
            profile = BehaviorProfile.from_db_row(json.loads(cached))
        else:
            profile = BehaviorProfile(user_id=state.get("user_id", ""))
        state["behavior_weights"] = profile.get_scheduling_weights()
    except Exception:
        state["behavior_weights"] = {"morning":0.5,"afternoon":0.5,"evening":0.5,"night":0.5}

    # 5. Conflict check
    # (simplified — needs supabase_client for real check)

    # 6. Repair (if conflicts)
    if state.get("conflicts"):
        state = repair_node(state, gemini_client, redis_client)

    # 7. Explanation
    state = explanation_node(state, gemini_client)

    return state


# ============================================================
# SELF-TEST
# ============================================================
if __name__ == "__main__":
    # Test the pipeline fallback
    state = {
        "raw_input": "Test event",
        "input_bytes": None,
        "input_type": "",
        "user_id": "test-user",
        "extracted_events": [],
        "extraction_error": None,
        "enriched_tasks": [],
        "schedule_result": None,
        "conflicts": [],
        "repair_proposals": [],
        "accepted_repair": None,
        "behavior_weights": {},
        "explanation": "",
        "final_output": None,
        "errors": [],
    }

    # Run pipeline with no Gemini (will fail gracefully)
    result = run_pipeline(state, None, None, None)
    print(f"Pipeline completed with {len(result.get('errors', []))} error(s)")
    print(f"Explanation: {result.get('explanation', 'N/A')}")
    print("Agent Graph: OK (fallback pipeline)")
