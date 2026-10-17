// Tests for the runtime-agnostic gateway core.
//
// Run with Node's built-in test runner (no Deno required):
//   node --test supabase/functions/understand-task/gateway.test.ts
// Node strips the TypeScript types, so these exercise exactly the code the Edge Function uses.

import { test } from "node:test";
import assert from "node:assert/strict";

import {
  AllProvidersFailedError,
  decodeDraft,
  NoProviderConfiguredError,
  ProviderError,
  stripFences,
  understandTask,
  type GatewayContext,
  type Provider,
  type UnderstandRequest,
} from "./gateway.ts";
import { makeProvider } from "./providers.ts";

const request: UnderstandRequest = {
  text: "submit the report tomorrow at 5pm for two hours",
  referenceTime: "2026-10-15T08:00:00+00:00",
  zoneId: "UTC",
};

function context(fetchFn: typeof fetch, overrides: Partial<GatewayContext> = {}): GatewayContext {
  return {
    fetchFn,
    timeoutMillis: 1000,
    maxAttempts: 2,
    sleep: async () => {},
    ...overrides,
  };
}

function providerReturning(name: string, raw: string): Provider {
  return { name, complete: async () => raw };
}

function providerThrowing(name: string, error: unknown): Provider {
  return {
    name,
    complete: async () => {
      throw error;
    },
  };
}

const VALID_DRAFT_JSON = JSON.stringify({
  title: "submit the report",
  detail: null,
  requestedDate: "2026-10-16",
  deadline: "2026-10-16T17:00:00+00:00",
  estimatedDurationMinutes: 120,
  priority: "IMPORTANT",
  flexibility: null,
  splittable: null,
  minimumSessionMinutes: null,
  fixedStart: null,
  fixedEnd: null,
  assumptions: [],
});

test("decodeDraft accepts a full valid object", () => {
  const draft = decodeDraft(JSON.parse(VALID_DRAFT_JSON));
  assert.equal(draft.title, "submit the report");
  assert.equal(draft.requestedDate, "2026-10-16");
  assert.equal(draft.estimatedDurationMinutes, 120);
  assert.equal(draft.priority, "IMPORTANT");
});

test("decodeDraft accepts an object with every optional field null", () => {
  const draft = decodeDraft({ title: "buy milk" });
  assert.equal(draft.title, "buy milk");
  assert.equal(draft.requestedDate, null);
  assert.equal(draft.priority, null);
  assert.deepEqual(draft.assumptions, []);
});

test("decodeDraft rejects an empty title", () => {
  assert.throws(() => decodeDraft({ title: "   " }));
});

test("decodeDraft rejects a non-calendar requested date", () => {
  assert.throws(() => decodeDraft({ title: "x", requestedDate: "2026-13-45" }));
  assert.throws(() => decodeDraft({ title: "x", requestedDate: "16/10/2026" }));
});

test("decodeDraft rejects a fixed range that ends before it starts", () => {
  assert.throws(() =>
    decodeDraft({
      title: "x",
      fixedStart: "2026-10-16T10:00:00+00:00",
      fixedEnd: "2026-10-16T09:00:00+00:00",
    }),
  );
});

test("decodeDraft rejects a fixed range missing one end", () => {
  assert.throws(() => decodeDraft({ title: "x", fixedStart: "2026-10-16T10:00:00+00:00" }));
});

test("decodeDraft rejects unusable durations", () => {
  assert.throws(() => decodeDraft({ title: "x", estimatedDurationMinutes: 0 }));
  assert.throws(() => decodeDraft({ title: "x", estimatedDurationMinutes: 1441 }));
  assert.throws(() => decodeDraft({ title: "x", estimatedDurationMinutes: 12.5 }));
});

test("decodeDraft rejects an unknown priority", () => {
  assert.throws(() => decodeDraft({ title: "x", priority: "URGENT" }));
});

test("decodeDraft normalises assumptions from strings and objects", () => {
  const draft = decodeDraft({
    title: "x",
    assumptions: ["assumed 30 minutes", { field: "DURATION", text: "You said two hours." }, ""],
  });
  assert.deepEqual(draft.assumptions, [
    { field: "TITLE", text: "assumed 30 minutes" },
    { field: "DURATION", text: "You said two hours." },
  ]);
});

test("stripFences removes a Markdown code fence", () => {
  assert.equal(stripFences("```json\n{\"a\":1}\n```"), "{\"a\":1}");
  assert.equal(stripFences("{\"a\":1}"), "{\"a\":1}");
});

test("understandTask returns the first provider's valid draft", async () => {
  const result = await understandTask(request, [providerReturning("groq", VALID_DRAFT_JSON)], context(fetch));
  assert.equal(result.provider, "groq");
  assert.equal(result.draft.title, "submit the report");
});

test("understandTask fails over to the next provider when the first is transient", async () => {
  const first = providerThrowing("groq", new ProviderError("groq", true, "http_503"));
  const second = providerReturning("gemini", VALID_DRAFT_JSON);
  const result = await understandTask(request, [first, second], context(fetch));
  assert.equal(result.provider, "gemini");
});

test("understandTask retries a transient failure within the provider before failing over", async () => {
  let calls = 0;
  const flaky: Provider = {
    name: "groq",
    complete: async () => {
      calls += 1;
      if (calls === 1) throw new ProviderError("groq", true, "http_503");
      return VALID_DRAFT_JSON;
    },
  };
  const result = await understandTask(request, [flaky], context(fetch));
  assert.equal(result.provider, "groq");
  assert.equal(calls, 2);
});

test("understandTask fails over immediately on a non-transient error", async () => {
  let calls = 0;
  const bad: Provider = {
    name: "groq",
    complete: async () => {
      calls += 1;
      throw new ProviderError("groq", false, "http_400");
    },
  };
  const result = await understandTask(request, [bad, providerReturning("gemini", VALID_DRAFT_JSON)], context(fetch));
  assert.equal(result.provider, "gemini");
  assert.equal(calls, 1);
});

test("understandTask fails over on malformed JSON from a model", async () => {
  const result = await understandTask(
    request,
    [providerReturning("groq", "not json at all"), providerReturning("gemini", VALID_DRAFT_JSON)],
    context(fetch),
  );
  assert.equal(result.provider, "gemini");
});

test("understandTask fails over when the model output violates the schema", async () => {
  const bad = JSON.stringify({ title: "", priority: "URGENT" });
  const result = await understandTask(
    request,
    [providerReturning("groq", bad), providerReturning("gemini", VALID_DRAFT_JSON)],
    context(fetch),
  );
  assert.equal(result.provider, "gemini");
});

test("understandTask reports every failure when all providers fail", async () => {
  const providers = [
    providerThrowing("groq", new ProviderError("groq", false, "http_401")),
    providerThrowing("gemini", new ProviderError("gemini", false, "http_403")),
  ];
  await assert.rejects(
    () => understandTask(request, providers, context(fetch)),
    (error: unknown) => {
      assert.ok(error instanceof AllProvidersFailedError);
      assert.equal(error.attempts.length, 2);
      return true;
    },
  );
});

test("understandTask refuses to run with no provider configured", async () => {
  await assert.rejects(() => understandTask(request, [], context(fetch)), NoProviderConfiguredError);
});

test("understandTask honours Retry-After before retrying", async () => {
  const sleeps: number[] = [];
  let calls = 0;
  const provider: Provider = {
    name: "groq",
    complete: async () => {
      calls += 1;
      if (calls === 1) throw new ProviderError("groq", true, "http_429", 1234);
      return VALID_DRAFT_JSON;
    },
  };
  await understandTask(request, [provider], context(fetch, { sleep: async (ms) => { sleeps.push(ms); } }));
  assert.deepEqual(sleeps, [1234]);
});

test("openai provider extracts the message content", async () => {
  const seen: string[] = [];
  const fetchFn: typeof fetch = async (url, init) => {
    seen.push(String(url));
    const body = JSON.parse(String(init?.body));
    assert.equal(body.messages[0].role, "system");
    return new Response(JSON.stringify({ choices: [{ message: { content: VALID_DRAFT_JSON } }] }), { status: 200 });
  };
  const provider = makeProvider({
    name: "groq",
    kind: "openai",
    url: "https://api.groq.com/openai/v1/chat/completions",
    apiKey: "secret",
    model: "llama-3.3-70b-versatile",
  });
  const raw = await provider.complete(request, context(fetchFn));
  assert.equal(raw, VALID_DRAFT_JSON);
  assert.equal(seen[0], "https://api.groq.com/openai/v1/chat/completions");
});

test("openai provider maps a 429 to a transient error with Retry-After", async () => {
  const fetchFn: typeof fetch = async () =>
    new Response("rate limited", { status: 429, headers: { "retry-after": "2" } });
  const provider = makeProvider({ name: "groq", kind: "openai", url: "https://x", apiKey: "k", model: "m" });
  await assert.rejects(
    () => provider.complete(request, context(fetchFn)),
    (error: unknown) => {
      assert.ok(error instanceof ProviderError);
      assert.equal(error.transient, true);
      assert.equal(error.retryAfterMillis, 2000);
      return true;
    },
  );
});

test("openai provider maps a 401 to a non-transient error", async () => {
  const fetchFn: typeof fetch = async () => new Response("nope", { status: 401 });
  const provider = makeProvider({ name: "groq", kind: "openai", url: "https://x", apiKey: "k", model: "m" });
  await assert.rejects(
    () => provider.complete(request, context(fetchFn)),
    (error: unknown) => {
      assert.ok(error instanceof ProviderError && error.transient === false);
      return true;
    },
  );
});

test("gemini provider joins content parts and sends the key as a query parameter", async () => {
  let seen = "";
  const fetchFn: typeof fetch = async (url) => {
    seen = String(url);
    return new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: "{\"a\":" }, { text: "1}" }] } }] }), { status: 200 });
  };
  const provider = makeProvider({
    name: "gemini",
    kind: "gemini",
    url: "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent",
    apiKey: "abc",
    model: "gemini-3.8-flash",
  });
  const raw = await provider.complete(request, context(fetchFn));
  assert.equal(raw, "{\"a\":1}");
  assert.ok(seen.includes("key=abc"));
});

test("provider surfaces an aborted fetch as a transient timeout", async () => {
  const fetchFn: typeof fetch = async () => {
    const error = new Error("aborted");
    error.name = "AbortError";
    throw error;
  };
  const provider = makeProvider({ name: "groq", kind: "openai", url: "https://x", apiKey: "k", model: "m" });
  await assert.rejects(
    () => provider.complete(request, context(fetchFn)),
    (error: unknown) => {
      assert.ok(error instanceof ProviderError && error.transient && error.message === "timeout");
      return true;
    },
  );
});
