"""
Chrona Multi-Model AI Router
=============================
- Gemini: Multimodal vision/PDF extraction + complex schedule reasoning with multi-model fallback pool
- Groq Key Pool: Task-routed keys with automatic rotation on 429 rate limits
"""
import os
import sys
import socket
import json
import base64
import requests
import itertools
from typing import Optional, Any, List
from dotenv import load_dotenv

# Force IPv4 on external domains to prevent 30-60s timeouts on unroutable IPv6
_orig_getaddrinfo = socket.getaddrinfo
def _smart_getaddrinfo(host, port, family=0, type=0, proto=0, flags=0):
    if family == socket.AF_UNSPEC and host and str(host) not in ('localhost', '127.0.0.1', '0.0.0.0', '::1', None):
        if not str(host).startswith(('127.', '192.168.', '10.', '172.')):
            family = socket.AF_INET
    return _orig_getaddrinfo(host, port, family, type, proto, flags)
socket.getaddrinfo = _smart_getaddrinfo

import urllib3.util.connection as urllib3_cn
urllib3_cn.allowed_gai_family = lambda: socket.AF_INET

# Ensure .env is loaded with high priority
load_dotenv(os.path.join(os.path.dirname(__file__), "..", ".env"), override=True)

from google import genai
from google.genai import types
from groq import Groq

# Candidate list of fast, modern Gemini models supporting multimodal & vision
GEMINI_MODELS = [
    "gemini-flash-lite-latest",
    "gemini-3-flash-preview",
    "gemini-3.1-flash-lite",
    "gemini-3.5-flash-lite",
    "gemini-3.5-flash",
]
GROQ_FAST_MODEL = os.getenv("GROQ_MODEL", "openai/gpt-oss-120b")


class AIRouter:
    def __init__(self):
        self.gemini_key = (os.getenv("GEMINI_API_KEY") or "").strip()
        if self.gemini_key:
            try:
                self.gemini = genai.Client(
                    api_key=self.gemini_key,
                    http_options={"api_version": "v1beta"}
                )
            except Exception:
                self.gemini = None
        else:
            self.gemini = None

        primary_groq = os.getenv("GROQ_API_KEY") or ""
        raw_keys = {
            "chat": os.getenv("GROQ_KEY_CHAT") or primary_groq,
            "transitions": os.getenv("GROQ_KEY_TRANSITIONS") or primary_groq,
            "agents": os.getenv("GROQ_KEY_AGENTS") or primary_groq,
        }
        self.groq_clients = {
            role: Groq(api_key=key) for role, key in raw_keys.items() if key
        }
        unique_clients = list({id(c): c for c in self.groq_clients.values()}.values())
        self._pool_cycle = itertools.cycle(unique_clients) if unique_clients else None

    def _get_groq_candidates(self, role: str) -> List[Groq]:
        candidates = []
        if role in self.groq_clients:
            candidates.append(self.groq_clients[role])
        for client in self.groq_clients.values():
            if client not in candidates:
                candidates.append(client)
        return candidates

    def parse_multimodal_with_gemini(
        self,
        file_bytes: bytes,
        mime_type: str,
        prompt: str,
        json_mode: bool = True,
    ) -> Any:
        """
        Robust Gemini vision/PDF extraction for posters, timetables, invitation cards, and syllabi.
        Tries modern flash models in order with automatic SDK + REST failover.
        """
        if not self.gemini_key:
            raise RuntimeError("GEMINI_API_KEY is required for image/PDF parsing.")

        errors = []
        b64_data = base64.b64encode(file_bytes).decode("utf-8")

        for model_name in GEMINI_MODELS:
            # 1. Direct REST request (fastest, guaranteed IPv4, sub-second latency)
            try:
                url = f"https://generativelanguage.googleapis.com/v1beta/models/{model_name}:generateContent?key={self.gemini_key}"
                payload = {
                    "contents": [{
                        "parts": [
                            {"inline_data": {"mime_type": mime_type, "data": b64_data}},
                            {"text": prompt}
                        ]
                    }],
                    "generationConfig": {
                        "response_mime_type": "application/json" if json_mode else "text/plain",
                        "temperature": 0.1,
                        "maxOutputTokens": 8192,
                    }
                }
                r = requests.post(url, json=payload, timeout=18)
                if r.status_code == 200:
                    data = r.json()
                    candidates = data.get("candidates", [])
                    if candidates and "content" in candidates[0]:
                        parts = candidates[0]["content"].get("parts", [])
                        if parts and "text" in parts[0]:
                            text = parts[0]["text"].strip()
                            return self._clean_json(text) if json_mode else text
                else:
                    errors.append(f"REST {model_name} {r.status_code}: {r.text[:120]}")
            except Exception as e:
                errors.append(f"REST {model_name}: {e}")

            # 2. SDK client fallback if REST had transient issues
            if self.gemini:
                try:
                    resp = self.gemini.models.generate_content(
                        model=model_name,
                        contents=[
                            types.Part.from_bytes(data=file_bytes, mime_type=mime_type),
                            prompt,
                        ],
                        config=types.GenerateContentConfig(
                            response_mime_type="application/json" if json_mode else "text/plain",
                            temperature=0.1,
                            max_output_tokens=8192,
                        ),
                    )
                    text = (resp.text or "").strip()
                    if text:
                        return self._clean_json(text) if json_mode else text
                except Exception as e:
                    errors.append(f"SDK {model_name}: {e}")

        raise RuntimeError(f"All Gemini vision models failed: {' | '.join(errors[-3:])}")

    def complete(
        self,
        prompt: str,
        system: Optional[str] = None,
        role: str = "chat",
        prefer_gemini: bool = False,
        json_mode: bool = False,
        temperature: float = 0.2,
    ) -> Any:
        """
        Route task to Gemini (for important/complex reasoning) or Groq key pool (for fast tasks),
        with automatic cross-provider failover.
        """
        full_prompt = f"{system}\n\n{prompt}" if system else prompt

        def _run_gemini():
            if not self.gemini_key:
                raise RuntimeError("GEMINI_API_KEY not configured")
            errors = []
            for model_name in GEMINI_MODELS:
                if self.gemini:
                    try:
                        resp = self.gemini.models.generate_content(
                            model=model_name,
                            contents=full_prompt,
                            config=types.GenerateContentConfig(
                                response_mime_type="application/json" if json_mode else "text/plain",
                                temperature=temperature,
                                max_output_tokens=8192,
                            ),
                        )
                        text = (resp.text or "").strip()
                        if text:
                            return self._clean_json(text) if json_mode else text
                    except Exception as e:
                        errors.append(f"SDK {model_name}: {e}")
                try:
                    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model_name}:generateContent?key={self.gemini_key}"
                    payload = {
                        "contents": [{"parts": [{"text": full_prompt}]}],
                        "generationConfig": {
                            "response_mime_type": "application/json" if json_mode else "text/plain",
                            "temperature": temperature,
                            "maxOutputTokens": 8192,
                        }
                    }
                    r = requests.post(url, json=payload, timeout=20)
                    if r.status_code == 200:
                        data = r.json()
                        candidates = data.get("candidates", [])
                        if candidates and "content" in candidates[0]:
                            parts = candidates[0]["content"].get("parts", [])
                            if parts and "text" in parts[0]:
                                text = parts[0]["text"].strip()
                                return self._clean_json(text) if json_mode else text
                except Exception as e:
                    errors.append(f"REST {model_name}: {e}")
            raise RuntimeError(f"Gemini failed: {' | '.join(errors[-2:])}")

        def _run_groq():
            candidates = self._get_groq_candidates(role)
            if not candidates:
                raise RuntimeError("No Groq keys configured")
            last_err = None
            for client in candidates:
                try:
                    kwargs = {
                        "model": GROQ_FAST_MODEL,
                        "messages": [
                            *([{"role": "system", "content": system}] if system else []),
                            {"role": "user", "content": prompt},
                        ],
                        "temperature": temperature,
                        "max_tokens": 4096,
                    }
                    if json_mode:
                        kwargs["response_format"] = {"type": "json_object"}
                    resp = client.chat.completions.create(**kwargs)
                    content = resp.choices[0].message.content.strip()
                    return self._clean_json(content) if json_mode else content
                except Exception as e:
                    last_err = e
                    continue
            raise last_err

        order = (_run_gemini, _run_groq) if prefer_gemini else (_run_groq, _run_gemini)
        errors = []
        for fn in order:
            try:
                return fn()
            except Exception as e:
                errors.append(str(e))
        raise RuntimeError(f"All AI providers failed: {' | '.join(errors)}")

    @staticmethod
    def _clean_json(text: str) -> Any:
        text = text.strip()
        if text.startswith("```"):
            lines = text.split("\n")
            if lines[0].startswith("```"):
                lines = lines[1:]
            if lines and lines[-1].strip() == "```":
                lines = lines[:-1]
            text = "\n".join(lines).strip()
        # Find json array or object boundaries
        try:
            return json.loads(text)
        except Exception:
            start_arr = text.find("[")
            end_arr = text.rfind("]")
            if start_arr != -1 and end_arr != -1 and end_arr > start_arr:
                return json.loads(text[start_arr:end_arr + 1])
            start_obj = text.find("{")
            end_obj = text.rfind("}")
            if start_obj != -1 and end_obj != -1 and end_obj > start_obj:
                return json.loads(text[start_obj:end_obj + 1])
            raise
