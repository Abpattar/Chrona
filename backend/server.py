"""
Neuravex Backend API Server
Handles event extraction via Gemini and insertion into Supabase.
Run with: uvicorn server:app --port 8000 --reload
"""

import os
import json
import tempfile
import requests
import google.generativeai as genai
from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from datetime import datetime

# ==============================
# CONFIGURATION
# ==============================

from dotenv import load_dotenv
load_dotenv()  # Load variables from .env file

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
SUPABASE_URL = os.getenv("SUPABASE_URL")
SUPABASE_ANON_KEY = os.getenv("SUPABASE_ANON_KEY")

if not GEMINI_API_KEY or not SUPABASE_URL or not SUPABASE_ANON_KEY:
    print("⚠️ WARNING: Missing necessary environment variables (.env)")

# Configure Gemini
genai.configure(api_key=GEMINI_API_KEY)

# ==============================
# FAST API APP
# ==============================

app = FastAPI(title="Neuravex Backend", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ==============================
# GEMINI PROMPTS
# ==============================

EXTRACTION_PROMPT = """
You are an event extraction engine.

Analyze the input and extract event information.

Return JSON in this exact format:

{
  "title": "",
  "category": "",
  "venue": "",
  "event_datetime": ""
}

Rules:

category must be one of:
  exam
  hackathon
  assignment
  meeting
  personal
  reminder

Convert relative times using today's date (%s):
  "today" → today's date
  "tomorrow" → tomorrow's date

Convert datetime to ISO format: YYYY-MM-DDTHH:MM:SS

Return ONLY valid JSON.
If a field is missing, return null for that field.
""" % datetime.now().strftime("%Y-%m-%d")


# ==============================
# DATABASE INSERT FUNCTION
# ==============================

def insert_into_supabase(event_data: dict) -> bool:
    """Insert an event into the Supabase events table."""
    url = f"{SUPABASE_URL}/rest/v1/events"

    headers = {
        "apikey": SUPABASE_ANON_KEY,
        "Authorization": f"Bearer {SUPABASE_ANON_KEY}",
        "Content-Type": "application/json",
        "Prefer": "return=minimal",
    }

    # Clean up: remove None values and ensure required fields exist
    clean_data = {}
    for key in ["title", "category", "venue", "event_datetime"]:
        if key in event_data and event_data[key] is not None:
            clean_data[key] = event_data[key]

    if not clean_data.get("title"):
        raise ValueError("Extracted event has no title")

    response = requests.post(url, headers=headers, json=clean_data)

    if response.status_code in [200, 201, 204]:
        print(f"✅ Event inserted: {clean_data.get('title')}")
        return True
    else:
        print(f"❌ Failed to insert: {response.text}")
        raise Exception(f"Supabase error: {response.text}")


# ==============================
# GEMINI EXTRACTION FUNCTIONS
# ==============================

def extract_data_from_file(file_path: str) -> dict:
    """Extract event data from a file (PDF/image) using Gemini."""
    print(f"\n📄 Processing file: {file_path}")

    uploaded_file = genai.upload_file(path=file_path)
    model = genai.GenerativeModel(model_name="gemini-2.5-flash")

    response = model.generate_content(
        [EXTRACTION_PROMPT, uploaded_file],
        generation_config={"response_mime_type": "application/json"},
    )

    extracted = json.loads(response.text)
    print(f"📦 Extracted: {json.dumps(extracted, indent=2)}")
    return extracted


def extract_data_from_text(text: str) -> dict:
    """Extract event data from plain text using Gemini."""
    print(f"\n📝 Processing text: {text}")

    model = genai.GenerativeModel(model_name="gemini-2.5-flash")

    prompt = f"{EXTRACTION_PROMPT}\n\nText to analyze:\n{text}"

    response = model.generate_content(
        prompt,
        generation_config={"response_mime_type": "application/json"},
    )

    extracted = json.loads(response.text)
    print(f"📦 Extracted: {json.dumps(extracted, indent=2)}")
    return extracted


# ==============================
# API ENDPOINTS
# ==============================

@app.get("/api/health")
async def health_check():
    return {"status": "ok", "service": "neuravex-backend"}


@app.post("/api/process-event")
async def process_event(
    file: UploadFile | None = File(None),
    text: str | None = Form(None),
):
    """
    Process an event from text or file upload.
    Accepts either:
      - JSON body with { "text": "..." }
      - FormData with file and optional text
    """
    try:
        extracted_data = None

        if file and file.filename:
            # Save uploaded file to a temp location
            suffix = os.path.splitext(file.filename)[1]
            with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
                content = await file.read()
                tmp.write(content)
                tmp_path = tmp.name

            try:
                extracted_data = extract_data_from_file(tmp_path)
            finally:
                os.unlink(tmp_path)  # Clean up temp file

        elif text and text.strip():
            extracted_data = extract_data_from_text(text.strip())
        else:
            raise HTTPException(status_code=400, detail="No text or file provided")

        # Insert into Supabase
        insert_into_supabase(extracted_data)

        return {
            "success": True,
            "message": "Event processed successfully",
            "event": extracted_data,
        }

    except HTTPException:
        raise
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e))
    except Exception as e:
        print(f"❌ Error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# Handle JSON body as well (text-only submissions)
from fastapi import Request

@app.post("/api/process-event-json")
async def process_event_json(request: Request):
    """Alternative endpoint for JSON body submissions."""
    body = await request.json()
    text = body.get("text", "")
    if not text.strip():
        raise HTTPException(status_code=400, detail="No text provided")

    try:
        extracted_data = extract_data_from_text(text.strip())
        insert_into_supabase(extracted_data)
        return {
            "success": True,
            "message": "Event processed successfully",
            "event": extracted_data,
        }
    except Exception as e:
        print(f"❌ Error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
