// Upstream provider adapters for the task-understanding gateway.
//
// Runtime-agnostic like gateway.ts: adapters receive `fetchFn` through the GatewayContext, so
// they can be unit-tested with a fake fetch and never read Deno/Node globals themselves.

import {
  buildPrompt,
  ProviderError,
  type GatewayContext,
  type Provider,
  type UnderstandRequest,
} from "./gateway.ts";

export type ProviderKind = "openai" | "gemini";

export interface ProviderConfig {
  name: string;
  kind: ProviderKind;
  url: string;
  apiKey: string;
  model: string;
}

export function makeProvider(config: ProviderConfig): Provider {
  return {
    name: config.name,
    complete(request: UnderstandRequest, ctx: GatewayContext): Promise<string> {
      const { system, user } = buildPrompt(request);
      return config.kind === "gemini"
        ? completeGemini(config, system, user, ctx)
        : completeOpenAi(config, system, user, ctx);
    },
  };
}

async function completeOpenAi(
  config: ProviderConfig,
  system: string,
  user: string,
  ctx: GatewayContext,
): Promise<string> {
  const body = {
    model: config.model,
    temperature: 0,
    messages: [
      { role: "system", content: system },
      { role: "user", content: user },
    ],
  };
  const text = await postJson(
    config,
    ctx,
    config.url,
    { "content-type": "application/json", authorization: `Bearer ${config.apiKey}` },
    body,
  );
  const parsed = parseBody(config, text);
  const content = (parsed as { choices?: { message?: { content?: unknown } }[] })?.choices?.[0]?.message?.content;
  if (typeof content !== "string" || content.trim().length === 0) {
    throw new ProviderError(config.name, true, "empty_response");
  }
  return content;
}

async function completeGemini(
  config: ProviderConfig,
  system: string,
  user: string,
  ctx: GatewayContext,
): Promise<string> {
  const url = `${config.url}?key=${encodeURIComponent(config.apiKey)}`;
  const body = {
    systemInstruction: { parts: [{ text: system }] },
    contents: [{ role: "user", parts: [{ text: user }] }],
    generationConfig: { temperature: 0, responseMimeType: "application/json" },
  };
  const text = await postJson(config, ctx, url, { "content-type": "application/json" }, body);
  const parsed = parseBody(config, text);
  const parts = (parsed as { candidates?: { content?: { parts?: { text?: unknown }[] } }[] })
    ?.candidates?.[0]?.content?.parts;
  const content = Array.isArray(parts)
    ? parts.map((part) => (part && typeof part.text === "string" ? part.text : "")).join("")
    : "";
  if (content.trim().length === 0) throw new ProviderError(config.name, true, "empty_response");
  return content;
}

async function postJson(
  config: ProviderConfig,
  ctx: GatewayContext,
  url: string,
  headers: Record<string, string>,
  body: unknown,
): Promise<string> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), ctx.timeoutMillis);
  let response: Response;
  try {
    response = await ctx.fetchFn(url, {
      method: "POST",
      headers,
      body: JSON.stringify(body),
      signal: controller.signal,
    });
  } catch (error) {
    const name = (error as { name?: string } | undefined)?.name;
    throw new ProviderError(config.name, true, name === "AbortError" ? "timeout" : "network_error");
  } finally {
    clearTimeout(timer);
  }

  const text = await response.text();
  if (response.status < 200 || response.status >= 300) {
    const transient = response.status === 429 || response.status >= 500;
    throw new ProviderError(
      config.name,
      transient,
      `http_${response.status}`,
      retryAfterMillis(response.headers.get("retry-after")),
    );
  }
  return text;
}

/** Honour `Retry-After` (seconds or HTTP-date) within a sane cap, so a retry never stalls a request. */
function retryAfterMillis(value: string | null): number | undefined {
  if (!value) return undefined;
  const seconds = Number(value);
  if (Number.isFinite(seconds) && seconds >= 0) return Math.min(seconds * 1000, 5000);
  const date = Date.parse(value);
  if (!Number.isNaN(date)) return Math.min(Math.max(date - Date.now(), 0), 5000);
  return undefined;
}

function parseBody(config: ProviderConfig, text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    throw new ProviderError(config.name, true, "malformed_json");
  }
}
