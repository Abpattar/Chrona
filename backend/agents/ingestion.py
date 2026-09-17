"""
Ingestion Agent Node
=====================
Classifies input type and prepares it for the parser node.
This is a routing node — no AI calls, just classification.

Graph position: ENTRY POINT -> parser
"""


def ingestion_node(state: dict) -> dict:
    """
    Classify the input and prepare for parsing.

    Determines input_type from the state:
    - If input_bytes is present -> check MIME type for image/pdf
    - If raw_input is text -> set type to "text"
    """
    if state.get("input_bytes"):
        # Binary input — classify by content
        content = state["input_bytes"]
        if content[:4] == b'%PDF':
            state["input_type"] = "pdf"
        elif content[:3] == b'\xff\xd8\xff':  # JPEG
            state["input_type"] = "image"
        elif content[:8] == b'\x89PNG\r\n\x1a\n':  # PNG
            state["input_type"] = "image"
        else:
            state["input_type"] = "file"  # generic file
    elif state.get("raw_input"):
        state["input_type"] = "text"
    else:
        state["errors"] = state.get("errors", [])
        state["errors"].append("ingestion_error: No input provided")
        state["input_type"] = "unknown"

    return state


# ============================================================
# SELF-TEST
# ============================================================
if __name__ == "__main__":
    # Test text input
    state = {"raw_input": "Exam on Friday at 2pm", "errors": []}
    result = ingestion_node(state)
    assert result["input_type"] == "text"

    # Test no input
    state = {"errors": []}
    result = ingestion_node(state)
    assert result["input_type"] == "unknown"
    assert len(result["errors"]) == 1

    print("Ingestion Node: OK")
