// Provider-neutral task-understanding gateway.
//
// This module is deliberately runtime-agnostic: it uses only standard web APIs (fetch,
// AbortController, setTimeout) and never touches Deno or Node globals. The Deno entry
// (index.ts) builds providers from the environment and calls `understandTask`; the tests
// (gateway.test.ts) exercise this file directly. Keeping the policy here means the provider
// order, retry rules and output validation are all testable without a network or a runtime.

export type Priority = "CRITICAL" | "IMPORTANT" | "LOW";
export type Flexibility = "FLEXIBLE" | "FIXED";

/** The draft shape the Android app consumes. Mirrors `TaskIntentDraft` (a subset). */
export interface TaskDraft {
  title: string;
  detail: string | null;
  requestedDate: string | null; // YYYY-MM-DD
  deadline: string | null; // ISO-8601 with offset
  estimatedDurationMinutes: number | null;
  priority: Priority | null;
  flexibility: Flexibility | null;
  splittable: boolean | null;
  minimumSessionMinutes: number | null;
  fixedStart: string | null;
  fixedEnd: string | null;
  assumptions: { field: string; text: string }[];
}

export interface UnderstandRequest {
  text: string;
  referenceTime: string; // ISO-8601 with offset
  zoneId: string;
}

export interface ProviderResult {
  provider: string;
  draft: TaskDraft;
}

/** One upstream model. `complete` returns the raw model text (expected to be JSON). */
export interface Provider {
  name: string;
  complete(request: UnderstandRequest, ctx: GatewayContext): Promise<string>;
}

export interface GatewayContext {
  fetchFn: typeof fetch;
  timeoutMillis: number;
  /** Attempts per provider, including the first. 1 means "no retry". */
  maxAttempts: number;
  sleep: (ms: number) => Promise<void>;
  log?: (message: string) => void;
}

/** A provider call failed. `transient` decides retry-then-failover vs immediate failover. */
export class ProviderError extends Error {
  provider: string;
  transient: boolean;
  retryAfterMillis: number | undefined;

  constructor(provider: string, transient: boolean, message: string, retryAfterMillis?: number) {
    super(message);
    this.name = "ProviderError";
    this.provider = provider;
    this.transient = transient;
    this.retryAfterMillis = retryAfterMillis;
  }
}

/** The model answered, but not with a usable draft. Never retried; fails over immediately. */
export class SchemaError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "SchemaError";
  }
}

export class NoProviderConfiguredError extends Error {
  constructor() {
    super("no provider is configured");
    this.name = "NoProviderConfiguredError";
  }
}

export class AllProvidersFailedError extends Error {
  attempts: string[];

  constructor(attempts: string[]) {
    super(`all providers failed: ${attempts.join("; ")}`);
    this.name = "AllProvidersFailedError";
    this.attempts = attempts;
  }
}

/**
 * Try each provider in order (Groq → Gemini → OpenRouter as configured) until one returns a
 * schema-valid draft. A transient failure is retried within the provider, then fails over; a
 * schema failure or a non-transient HTTP error fails over immediately. If every provider
 * fails, throw [AllProvidersFailedError] — the caller never sees a partial draft.
 */
export async function understandTask(
  request: UnderstandRequest,
  providers: Provider[],
  ctx: GatewayContext,
): Promise<ProviderResult> {
  if (providers.length === 0) throw new NoProviderConfiguredError();
  const attempts: string[] = [];
  for (const provider of providers) {
    try {
      const raw = await callWithRetry(provider, request, ctx);
      const draft = decodeDraft(JSON.parse(stripFences(raw)));
      return { provider: provider.name, draft };
    } catch (error) {
      attempts.push(`${provider.name}: ${describe(error)}`);
      ctx.log?.(`provider ${provider.name} failed: ${describe(error)}`);
    }
  }
  throw new AllProvidersFailedError(attempts);
}

async function callWithRetry(
  provider: Provider,
  request: UnderstandRequest,
  ctx: GatewayContext,
): Promise<string> {
  let lastError: unknown;
  for (let attempt = 1; attempt <= ctx.maxAttempts; attempt++) {
    try {
      return await provider.complete(request, ctx);
    } catch (error) {
      lastError = error;
      const transient = error instanceof ProviderError ? error.transient : true;
      if (!transient || attempt >= ctx.maxAttempts) break;
      const wait = error instanceof ProviderError && error.retryAfterMillis !== undefined
        ? error.retryAfterMillis
        : backoffMillis(attempt);
      await ctx.sleep(wait);
    }
  }
  throw lastError;
}

function backoffMillis(attempt: number): number {
  return Math.min(250 * 2 ** (attempt - 1), 2000);
}

function describe(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

/** Models sometimes wrap JSON in a Markdown fence despite instructions. */
export function stripFences(raw: string): string {
  const trimmed = raw.trim();
  const match = /^```(?:json)?\s*([\s\S]*?)\s*```$/i.exec(trimmed);
  return match ? match[1] : trimmed;
}

const DATE_ONLY = /^\d{4}-\d{2}-\d{2}$/;
const ISO_DATE_TIME = /^\d{4}-\d{2}-\d{2}[Tt ]\d{2}:\d{2}(:\d{2})?([.,]\d+)?([Zz]|[+-]\d{2}:?\d{2})$/;
const PRIORITIES: Priority[] = ["CRITICAL", "IMPORTANT", "LOW"];
const FLEXIBILITIES: Flexibility[] = ["FLEXIBLE", "FIXED"];
const MAX_DURATION_MINUTES = 24 * 60;

/**
 * Strictly validate a decoded object into a [TaskDraft]. Anything the app could choke on
 * (bad date, end not after start, unusable duration) is rejected here so the gateway fails
 * over instead of forwarding a draft that would break the client.
 */
export function decodeDraft(value: unknown): TaskDraft {
  if (typeof value !== "object" || value === null) throw new SchemaError("output is not an object");
  const o = value as Record<string, unknown>;

  const title = o.title;
  if (typeof title !== "string" || title.trim().length === 0) {
    throw new SchemaError("title is missing or empty");
  }

  const requestedDate = optionalDateOnly(o.requestedDate);
  const deadline = optionalDateTime(o.deadline);
  const fixedStart = optionalDateTime(o.fixedStart);
  const fixedEnd = optionalDateTime(o.fixedEnd);
  if ((fixedStart === null) !== (fixedEnd === null)) {
    throw new SchemaError("fixedStart and fixedEnd must be provided together");
  }
  if (fixedStart !== null && fixedEnd !== null && !(Date.parse(fixedEnd) > Date.parse(fixedStart))) {
    throw new SchemaError("fixedEnd must be after fixedStart");
  }

  return {
    title: title.trim(),
    detail: optionalString(o.detail),
    requestedDate,
    deadline,
    estimatedDurationMinutes: optionalDuration(o.estimatedDurationMinutes),
    priority: optionalEnum(o.priority, PRIORITIES, "priority"),
    flexibility: optionalEnum(o.flexibility, FLEXIBILITIES, "flexibility"),
    splittable: optionalBoolean(o.splittable),
    minimumSessionMinutes: optionalDuration(o.minimumSessionMinutes),
    fixedStart,
    fixedEnd,
    assumptions: decodeAssumptions(o.assumptions),
  };
}

function optionalString(value: unknown): string | null {
  if (value === null || value === undefined || value === "") return null;
  if (typeof value !== "string") throw new SchemaError("expected a string");
  return value;
}

function optionalBoolean(value: unknown): boolean | null {
  if (value === null || value === undefined) return null;
  if (typeof value !== "boolean") throw new SchemaError("expected a boolean");
  return value;
}

function optionalEnum<T extends string>(value: unknown, allowed: T[], field: string): T | null {
  if (value === null || value === undefined || value === "") return null;
  if (typeof value !== "string" || !allowed.includes(value as T)) {
    throw new SchemaError(`${field} is not one of ${allowed.join(", ")}`);
  }
  return value as T;
}

function optionalDateOnly(value: unknown): string | null {
  if (value === null || value === undefined || value === "") return null;
  if (typeof value !== "string" || !DATE_ONLY.test(value)) throw new SchemaError("requestedDate is not YYYY-MM-DD");
  const [y, m, d] = value.split("-").map(Number);
  const probe = new Date(Date.UTC(y, m - 1, d));
  if (probe.getUTCFullYear() !== y || probe.getUTCMonth() !== m - 1 || probe.getUTCDate() !== d) {
    throw new SchemaError("requestedDate is not a real calendar date");
  }
  return value;
}

function optionalDateTime(value: unknown): string | null {
  if (value === null || value === undefined || value === "") return null;
  if (typeof value !== "string" || !ISO_DATE_TIME.test(value)) {
    throw new SchemaError("expected an ISO-8601 date-time with an offset");
  }
  if (Number.isNaN(Date.parse(value.replace(" ", "T")))) throw new SchemaError("unparseable date-time");
  return value;
}

function optionalDuration(value: unknown): number | null {
  if (value === null || value === undefined) return null;
  if (typeof value !== "number" || !Number.isInteger(value) || value <= 0 || value > MAX_DURATION_MINUTES) {
    throw new SchemaError(`duration must be a whole number of minutes in 1..${MAX_DURATION_MINUTES}`);
  }
  return value;
}

function decodeAssumptions(value: unknown): { field: string; text: string }[] {
  if (value === null || value === undefined) return [];
  if (!Array.isArray(value)) throw new SchemaError("assumptions must be an array");
  const out: { field: string; text: string }[] = [];
  for (const item of value) {
    if (typeof item === "string") {
      if (item.trim().length > 0) out.push({ field: "TITLE", text: item.trim() });
      continue;
    }
    if (typeof item === "object" && item !== null) {
      const o = item as Record<string, unknown>;
      const text = typeof o.text === "string" ? o.text.trim() : "";
      if (text.length === 0) continue;
      const field = typeof o.field === "string" && o.field.length > 0 ? o.field : "TITLE";
      out.push({ field, text });
      continue;
    }
    throw new SchemaError("each assumption must be a string or {field,text}");
  }
  return out;
}

const SYSTEM_PROMPT_BASE =
  "You convert one short spoken task request into strict JSON for a personal task app. " +
  "Interpret only; never place the task on a calendar. Output a single JSON object and nothing else. " +
  "Fields: title (non-empty string), detail (string|null), requestedDate (YYYY-MM-DD|null), " +
  "deadline (ISO-8601 with offset|null), estimatedDurationMinutes (integer 1..1440|null), " +
  'priority ("CRITICAL"|"IMPORTANT"|"LOW"|null), flexibility ("FLEXIBLE"|"FIXED"|null), ' +
  "splittable (boolean|null), minimumSessionMinutes (integer|null), fixedStart/fixedEnd " +
  "(ISO-8601 with offset|null, both or neither), assumptions (array of {field,text}). " +
  "Use null for anything not stated. Do not invent a date when none is said.";

export function buildPrompt(request: UnderstandRequest): { system: string; user: string } {
  return {
    system: SYSTEM_PROMPT_BASE,
    user: `Reference time: ${request.referenceTime}\nTime zone: ${request.zoneId}\nTask request: ${request.text}`,
  };
}
