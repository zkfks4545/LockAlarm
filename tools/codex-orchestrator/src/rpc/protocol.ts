/**
 * Wire types for the Codex App Server JSON-RPC protocol.
 *
 * The App Server intentionally does not use the OpenAI HTTP/Responses API.  The
 * types in this file are permissive around method payloads because App Server
 * versions add fields over time, while keeping the JSON-RPC envelope strict.
 */

export const JSON_RPC_VERSION = "2.0" as const;

export type JsonPrimitive = string | number | boolean | null;
export type JsonValue = JsonPrimitive | JsonObject | JsonValue[];
export interface JsonObject {
  [key: string]: JsonValue | undefined;
}

export type JsonRpcId = string | number;

export interface JsonRpcRequest<P extends JsonValue | undefined = JsonValue | undefined> {
  jsonrpc: typeof JSON_RPC_VERSION;
  id: JsonRpcId;
  method: string;
  params?: P;
}

export interface JsonRpcNotification<P extends JsonValue | undefined = JsonValue | undefined> {
  jsonrpc: typeof JSON_RPC_VERSION;
  method: string;
  params?: P;
}

export interface JsonRpcSuccess<R extends JsonValue | undefined = JsonValue | undefined> {
  jsonrpc: typeof JSON_RPC_VERSION;
  id: JsonRpcId | null;
  result: R;
}

export interface JsonRpcErrorObject<D extends JsonValue | undefined = JsonValue | undefined> {
  code: number;
  message: string;
  data?: D;
}

export interface JsonRpcErrorResponse<D extends JsonValue | undefined = JsonValue | undefined> {
  jsonrpc: typeof JSON_RPC_VERSION;
  id: JsonRpcId | null;
  error: JsonRpcErrorObject<D>;
}

export type JsonRpcResponse<R extends JsonValue | undefined = JsonValue | undefined> =
  | JsonRpcSuccess<R>
  | JsonRpcErrorResponse;

export type JsonRpcMessage =
  | JsonRpcRequest
  | JsonRpcNotification
  | JsonRpcSuccess
  | JsonRpcErrorResponse;

export function isJsonRpcResponse(value: unknown): value is JsonRpcResponse {
  if (!isObject(value) || value.jsonrpc !== JSON_RPC_VERSION) return false;
  return "result" in value || ("error" in value && isObject(value.error));
}

export function isJsonRpcRequest(value: unknown): value is JsonRpcRequest {
  return (
    isObject(value) &&
    value.jsonrpc === JSON_RPC_VERSION &&
    typeof value.method === "string" &&
    "id" in value
  );
}

export function isJsonRpcNotification(value: unknown): value is JsonRpcNotification {
  return (
    isObject(value) &&
    value.jsonrpc === JSON_RPC_VERSION &&
    typeof value.method === "string" &&
    !("id" in value)
  );
}

export function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

export interface InitializeClientInfo extends JsonObject {
  name: string;
  version: string;
}

export interface InitializeParams extends JsonObject {
  clientInfo?: InitializeClientInfo;
  capabilities?: JsonObject;
  protocolVersion?: number | string;
}

export interface InitializeResult extends JsonObject {
  serverInfo?: InitializeClientInfo;
  capabilities?: JsonObject;
  protocolVersion?: number | string;
}

export interface AccountReadResult extends JsonObject {
  /** Current App Server authentication mode, when exposed by this version. */
  authMode?: string | null;
  auth_mode?: string | null;
  authType?: string;
  auth_type?: string;
  type?: string;
  planType?: string;
  plan_type?: string;
  plan?: string | JsonObject;
  email?: string;
  isLoggedIn?: boolean;
  loggedIn?: boolean;
  authenticated?: boolean;
}

export interface ModelInfo extends JsonObject {
  id?: string;
  model?: string;
  name?: string;
  slug?: string;
  displayName?: string;
  display_name?: string;
  supportedReasoningEfforts?: string[];
  supported_reasoning_efforts?: string[];
  supportedReasoningLevels?: string[];
  supported_reasoning_levels?: string[];
  reasoningEfforts?: string[];
  reasoning_efforts?: string[];
  defaultReasoningEffort?: string;
  default_reasoning_effort?: string;
  [key: string]: JsonValue | undefined;
}

export interface ModelListResult extends JsonObject {
  models?: ModelInfo[];
  data?: ModelInfo[];
  items?: ModelInfo[];
  [key: string]: JsonValue | undefined;
}

export interface RateLimitWindow extends JsonObject {
  /** Number of remaining requests/tokens, if supplied. */
  remaining?: number;
  limit?: number;
  used?: number;
  resetAt?: string | number;
  reset_at?: string | number;
  windowSeconds?: number;
  window_seconds?: number;
  usedPercent?: number;
  windowDurationMins?: number;
  resetsAt?: number | string;
  rateLimitReachedType?: string | null;
  windowName?: string;
  [key: string]: JsonValue | undefined;
}

export interface RateLimitsReadResult extends JsonObject {
  limits?: RateLimitWindow[] | JsonObject;
  rateLimits?: RateLimitWindow[] | JsonObject;
  rate_limits?: RateLimitWindow[] | JsonObject;
  [key: string]: JsonValue | undefined;
}

export type AuthenticationKind = "chatgpt" | "api_key" | "logged_out" | "unknown";

function stringField(value: unknown, keys: string[]): string | undefined {
  if (!isObject(value)) return undefined;
  for (const key of keys) {
    const candidate = value[key];
    if (typeof candidate === "string" && candidate.trim()) return candidate;
  }
  return undefined;
}

/**
 * Best-effort normalization of account/read variants used by App Server builds.
 * Unknown values remain unknown; callers must not silently fall back to API-key
 * authentication when this function cannot identify the mode.
 */
export function detectAuthenticationKind(value: unknown): AuthenticationKind {
  if (!isObject(value)) return "unknown";
  if (isObject(value.account)) return detectAuthenticationKind(value.account);
  if (Object.prototype.hasOwnProperty.call(value, "account") && value.account === null) return "logged_out";

  const loggedIn = [value.isLoggedIn, value.loggedIn, value.authenticated].find(
    (candidate) => typeof candidate === "boolean",
  );
  if (loggedIn === false) return "logged_out";

  const authMode = stringField(value, [
    "authMode",
    "auth_mode",
    "authType",
    "auth_type",
    "authMethod",
    "auth_method",
    "authentication",
    "authenticationType",
  ]);
  if (Object.prototype.hasOwnProperty.call(value, "authMode") && value.authMode === null) return "logged_out";
  if (Object.prototype.hasOwnProperty.call(value, "auth_mode") && value.auth_mode === null) return "logged_out";
  const text = authMode?.toLowerCase() ?? "";
  if (text.includes("api") || text.includes("key") || text.includes("token")) {
    return "api_key";
  }
  if (text.includes("chatgpt") || text.includes("oauth") || text.includes("subscription")) {
    return "chatgpt";
  }

  const type = stringField(value, ["type", "provider", "source"]);
  const typeText = type?.toLowerCase() ?? "";
  if (typeText.includes("api") || typeText.includes("key")) return "api_key";
  if (typeText.includes("chatgpt") || typeText.includes("oauth")) return "chatgpt";

  if (loggedIn === true) {
    // A logged-in account without an explicit mode is intentionally unknown.
    // The caller should display the account payload and ask for clarification.
    return "unknown";
  }

  return "unknown";
}

export function isAuthenticated(value: unknown): boolean {
  if (isObject(value) && isObject(value.account)) return isAuthenticated(value.account);
  const kind = detectAuthenticationKind(value);
  if (kind === "chatgpt" || kind === "api_key") return true;
  if (!isObject(value)) return false;
  return [value.isLoggedIn, value.loggedIn, value.authenticated].some((candidate) => candidate === true);
}

export function modelIdentifier(value: unknown): string | undefined {
  if (typeof value === "string") return value;
  return stringField(value, ["id", "model", "slug", "name"]);
}

export function modelReasoningEfforts(value: unknown): string[] {
  if (!isObject(value)) return [];
  const fields = [
    value.supportedReasoningEfforts,
    value.supported_reasoning_efforts,
    value.supportedReasoningLevels,
    value.supported_reasoning_levels,
    value.reasoningEfforts,
    value.reasoning_efforts,
  ];
  for (const field of fields) {
    if (Array.isArray(field)) {
      return field.filter((entry): entry is string => typeof entry === "string");
    }
  }
  return [];
}

export function extractModels(value: unknown): ModelInfo[] {
  if (Array.isArray(value)) {
    return value.filter((entry): entry is ModelInfo => isObject(entry));
  }
  if (!isObject(value)) return [];
  for (const key of ["models", "data", "items"]) {
    const candidate = value[key];
    if (Array.isArray(candidate)) {
      return candidate.filter((entry): entry is ModelInfo => isObject(entry));
    }
  }
  return [];
}

export function extractRateLimitWindows(value: unknown): RateLimitWindow[] {
  if (Array.isArray(value)) return value.filter((entry): entry is RateLimitWindow => isObject(entry));
  if (!isObject(value)) return [];
  for (const key of ["limits", "rateLimits", "rate_limits", "windows", "data"]) {
    const candidate = value[key];
    if (Array.isArray(candidate)) {
      return candidate.filter((entry): entry is RateLimitWindow => isObject(entry));
    }
    if (isObject(candidate)) {
      const nested = Object.entries(candidate)
        .filter(([, entry]) => isObject(entry))
        .map(([name, entry]) => ({ ...(entry as JsonObject), windowName: name } as RateLimitWindow));
      return nested.length ? nested : [candidate as RateLimitWindow];
    }
  }
  return [];
}
