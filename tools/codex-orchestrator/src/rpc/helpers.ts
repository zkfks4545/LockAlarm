import {
  type AccountReadResult,
  type AuthenticationKind,
  type ModelInfo,
  type RateLimitWindow,
  detectAuthenticationKind,
  extractModels,
  extractRateLimitWindows,
  isAuthenticated,
  isObject,
  modelIdentifier,
  modelReasoningEfforts,
} from "./protocol.js";
import type { AppServerClientLike } from "./client.js";

export interface AccountState {
  raw: AccountReadResult;
  authKind: AuthenticationKind;
  authenticated: boolean;
  plan?: string | undefined;
  email?: string | undefined;
}

export class AccountAuthenticationError extends Error {
  constructor(
    message: string,
    public readonly authKind: AuthenticationKind,
    public readonly loginHint = "codex login --device-auth (chatgptDeviceCode)",
  ) {
    super(message);
    this.name = "AccountAuthenticationError";
  }
}

function unwrapAccount(value: unknown): AccountReadResult {
  if (isObject(value) && isObject(value.account)) return value.account as AccountReadResult;
  return (isObject(value) ? value : {}) as AccountReadResult;
}

function textFrom(value: unknown, keys: string[]): string | undefined {
  if (!isObject(value)) return undefined;
  for (const key of keys) {
    const candidate = value[key];
    if (typeof candidate === "string" && candidate.trim()) return candidate;
  }
  return undefined;
}

export function normalizeAccount(value: unknown): AccountState {
  const raw = unwrapAccount(value);
  const planValue = raw.plan;
  const plan = typeof planValue === "string"
    ? planValue
    : textFrom(planValue, ["type", "name", "id", "slug"])
      ?? textFrom(raw, ["planType", "plan_type", "subscription", "subscriptionType", "subscription_type", "tier"]);
  return {
    raw,
    authKind: detectAuthenticationKind(raw),
    authenticated: isAuthenticated(raw),
    plan,
    email: textFrom(raw, ["email", "userEmail", "user_email"]),
  };
}

/**
 * Verify the account is explicitly ChatGPT-authenticated. API-key mode and
 * unknown/logged-out modes are rejected so callers cannot incur API billing by
 * quietly switching credentials.
 */
export function requireChatGptAuthentication(value: unknown): AccountState {
  const state = normalizeAccount(value);
  if (state.authKind === "chatgpt") return state;
  if (state.authKind === "api_key") {
    throw new AccountAuthenticationError(
      "Codex App Server is authenticated with an API key. This orchestrator requires ChatGPT Pro authentication; sign out of API-key mode and log in with chatgptDeviceCode.",
      state.authKind,
    );
  }
  throw new AccountAuthenticationError(
    "Codex App Server is not authenticated with ChatGPT. Log in with chatgptDeviceCode before running the orchestrator.",
    state.authKind,
  );
}

export interface ModelRequirement {
  id: string;
  reasoningEfforts?: string[];
}

export interface ModelInventory {
  raw: unknown;
  models: ModelInfo[];
  identifiers: string[];
  missing: ModelRequirement[];
}

export class RequiredModelError extends Error {
  constructor(public readonly missing: ModelRequirement[], public readonly available: string[]) {
    super(
      `Required App Server model(s) are unavailable: ${missing.map((model) => model.id).join(", ") || "unknown"}. ` +
      `Available models: ${available.join(", ") || "none"}. No fallback model was selected.`,
    );
    this.name = "RequiredModelError";
  }
}

export function normalizeModelInventory(value: unknown, required: ModelRequirement[] = []): ModelInventory {
  const models = extractModels(value);
  const identifiers = models.map(modelIdentifier).filter((id): id is string => Boolean(id));
  const missing = required.filter((requirement) => {
    const model = models.find((candidate) => modelIdentifier(candidate)?.toLowerCase() === requirement.id.toLowerCase());
    if (!model) return true;
    if (!requirement.reasoningEfforts?.length) return false;
    const supported = modelReasoningEfforts(model).map((effort) => effort.toLowerCase());
    return requirement.reasoningEfforts.some((effort) => !supported.includes(effort.toLowerCase()));
  });
  return { raw: value, models, identifiers, missing };
}

export function requireModels(value: unknown, required: ModelRequirement[]): ModelInventory {
  const inventory = normalizeModelInventory(value, required);
  if (inventory.missing.length) throw new RequiredModelError(inventory.missing, inventory.identifiers);
  return inventory;
}

export function findModel(value: unknown, id: string): ModelInfo | undefined {
  return extractModels(value).find((model) => modelIdentifier(model)?.toLowerCase() === id.toLowerCase());
}

export function supportsReasoningEffort(model: unknown, effort: string): boolean {
  const supported = modelReasoningEfforts(model);
  return supported.length === 0 || supported.some((candidate) => candidate.toLowerCase() === effort.toLowerCase());
}

const FASTEST_FIRST = ["minimal", "none", "fast", "low", "standard", "medium", "high", "max"];

export function fastestReasoningEffort(model: unknown): string | undefined {
  const supported = modelReasoningEfforts(model);
  if (!supported.length) return undefined;
  return [...supported].sort((left, right) => {
    const a = FASTEST_FIRST.indexOf(left.toLowerCase());
    const b = FASTEST_FIRST.indexOf(right.toLowerCase());
    return (a < 0 ? FASTEST_FIRST.length : a) - (b < 0 ? FASTEST_FIRST.length : b);
  })[0];
}

export function chooseReasoningEffort(model: unknown, preferred: string, fallbackToFastest = true): string | undefined {
  if (supportsReasoningEffort(model, preferred)) return preferred;
  return fallbackToFastest ? fastestReasoningEffort(model) : undefined;
}

export async function readAccountState(client: AppServerClientLike): Promise<AccountState> {
  return normalizeAccount(await client.accountRead());
}

export async function readModelInventory(
  client: AppServerClientLike,
  required: ModelRequirement[] = [],
): Promise<ModelInventory> {
  return normalizeModelInventory(await client.modelList(), required);
}

export interface RateLimitSnapshot {
  raw: unknown;
  windows: RateLimitWindow[];
  capturedAt: string;
}

export function normalizeRateLimits(value: unknown): RateLimitSnapshot {
  return {
    raw: value,
    windows: extractRateLimitWindows(value),
    capturedAt: new Date().toISOString(),
  };
}

export async function readRateLimitSnapshot(client: AppServerClientLike): Promise<RateLimitSnapshot> {
  return normalizeRateLimits(await client.rateLimitsRead());
}
