// Supabase Edge Function entry point (Deno runtime).
//
// The app never talks to Groq/Gemini/OpenRouter directly: it POSTs a transcript here, this
// function runs the provider chain (Groq → Gemini → OpenRouter, whichever keys are set) and
// returns one validated draft. Provider selection, retries and output validation live in
// gateway.ts; this file only does env wiring and HTTP.
//
// Secrets live in the function environment (Supabase secrets / supabase/.env.local). They are
// never logged and never returned to the client.

import { type GatewayContext, type UnderstandRequest } from "./gateway.ts";
import { makeProvider, type ProviderConfig } from "./providers.ts";
import { understandTask } from "./gateway.ts";

const MAX_TEXT_LENGTH = 500;
const TIMEOUT_MILLIS = 8000;
const MAX_ATTEMPTS = 2;

function json(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

function providerConfigs(env: (key: string) => string | undefined): ProviderConfig[] {
  const configs: ProviderConfig[] = [];
  const groqKey = env("GROQ_API_KEY");
  if (groqKey) {
    configs.push({
      name: "groq",
      kind: "openai",
      url: "https://api.groq.com/openai/v1/chat/completions",
      apiKey: groqKey,
      model: env("GROQ_MODEL") ?? "llama-3.3-70b-versatile",
    });
  }
  const geminiKey = env("GEMINI_API_KEY");
  if (geminiKey) {
    const model = env("GEMINI_MODEL") ?? "gemini-3.8-flash";
    configs.push({
      name: "gemini",
      kind: "gemini",
      url: `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`,
      apiKey: geminiKey,
      model,
    });
  }
  const openRouterKey = env("OPENROUTER_API_KEY");
  if (openRouterKey) {
    configs.push({
      name: "openrouter",
      kind: "openai",
      url: "https://openrouter.ai/api/v1/chat/completions",
      apiKey: openRouterKey,
      model: env("OPENROUTER_MODEL") ?? "meta-llama/llama-3.3-70b-instruct",
    });
  }
  return configs;
}

function parseRequest(value: unknown): UnderstandRequest | null {
  if (typeof value !== "object" || value === null) return null;
  const body = value as Record<string, unknown>;
  const text = typeof body.text === "string" ? body.text.trim() : "";
  const referenceTime = typeof body.referenceTime === "string" ? body.referenceTime : "";
  const zoneId = typeof body.zoneId === "string" ? body.zoneId : "";
  if (text.length === 0 || text.length > MAX_TEXT_LENGTH) return null;
  if (referenceTime.length === 0 || Number.isNaN(Date.parse(referenceTime))) return null;
  if (zoneId.length === 0) return null;
  return { text, referenceTime, zoneId };
}

const context: GatewayContext = {
  fetchFn: fetch,
  timeoutMillis: TIMEOUT_MILLIS,
  maxAttempts: MAX_ATTEMPTS,
  sleep: (ms: number) => new Promise((resolve) => setTimeout(resolve, ms)),
  // Provider name + failure class only; never the key, the transcript or the raw model output.
  log: (message: string) => console.log(message),
};

Deno.serve(async (request: Request): Promise<Response> => {
  if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  let parsed: unknown;
  try {
    parsed = await request.json();
  } catch {
    return json({ error: "invalid_json" }, 400);
  }

  const payload = parseRequest(parsed);
  if (payload === null) return json({ error: "invalid_request" }, 400);

  const providers = providerConfigs((key) => Deno.env.get(key));
  if (providers.length === 0) return json({ error: "no_provider_configured" }, 503);

  try {
    const result = await understandTask(payload, providers.map(makeProvider), context);
    return json({ provider: result.provider, draft: result.draft }, 200);
  } catch (error) {
    console.error("task understanding failed for all providers");
    return json({ error: "understanding_unavailable" }, 502);
  }
});
