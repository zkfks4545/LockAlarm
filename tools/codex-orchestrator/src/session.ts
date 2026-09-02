import { spawnSync } from "node:child_process";
import type { OrchestratorConfig } from "./config.js";
import {
  extractModels,
  modelIdentifier,
  modelReasoningEfforts,
  type AuthenticationKind,
} from "./rpc/protocol.js";
import { normalizeAccount } from "./rpc/helpers.js";

export interface ThreadStartOptions {
  model: string;
  reasoningEffort: string;
  cwd: string;
  approvalPolicy?: string;
  sandbox?: string;
}

export interface AppServerFacade {
  initialize(): Promise<unknown>;
  accountRead(): Promise<unknown>;
  modelList(): Promise<unknown>;
  rateLimitsRead(): Promise<unknown>;
  startThread(options: ThreadStartOptions): Promise<string>;
  turn(threadId: string, prompt: string): Promise<unknown>;
  close(): Promise<void>;
}

export interface ModelCapability {
  id: string;
  reasoningEfforts: string[];
  raw: unknown;
}

export interface PreflightResult {
  codex: { command: string; version?: string; available: boolean; error?: string };
  account: unknown;
  authentication: AuthenticationKind;
  plan?: string;
  models: ModelCapability[];
  usageBefore: unknown;
  sol: ModelCapability;
  luna: ModelCapability;
  solReasoning: string;
  lunaReasoning: string;
}

export class PreflightError extends Error {
  constructor(public readonly code: string, message: string, public readonly details: unknown = undefined) {
    super(message);
    this.name = "PreflightError";
  }
}

function safeAccountSummary(value: unknown): Record<string, unknown> {
  if (typeof value !== "object" || value === null || Array.isArray(value)) return { valueType: typeof value };
  const record = value as Record<string, unknown>;
  const summary: Record<string, unknown> = {};
  for (const key of ["authMode", "auth_mode", "authType", "auth_type", "isLoggedIn", "loggedIn", "authenticated", "planType", "plan_type", "plan", "email"]) {
    if (key in record) summary[key] = record[key];
  }
  return summary;
}

export function inspectCodexBinary(command: string): PreflightResult["codex"] {
  const result = spawnSync(command, ["--version"], {
    encoding: "utf8",
    windowsHide: true,
    timeout: 10_000,
  });
  const stdout = typeof result.stdout === "string" ? result.stdout.trim() : "";
  const stderr = typeof result.stderr === "string" ? result.stderr.trim() : "";
  if (result.error) {
    return { command, available: false, error: result.error.message };
  }
  if (result.status !== 0) {
    return { command, available: false, error: stderr || stdout || `process exited with status ${result.status}` };
  }
  return { command, available: true, version: stdout || "unknown" };
}

export async function runPreflight(
  facade: AppServerFacade,
  config: OrchestratorConfig,
): Promise<PreflightResult> {
  const codex = config.mock ? { command: "mock", available: true, version: "mock-app-server" } : inspectCodexBinary(config.codexBin);
  if (!config.mock && !codex.available) {
    throw new PreflightError(
      "CODEX_UNAVAILABLE",
      `The codex executable could not be started: ${codex.error ?? "unknown error"}`,
      codex,
    );
  }
  await facade.initialize();
  const account = await facade.accountRead();
  const accountState = normalizeAccount(account);
  const authentication = accountState.authKind;
  if (authentication === "logged_out") {
    throw new PreflightError(
      "CHATGPT_LOGIN_REQUIRED",
      "Codex is logged out. Log in with ChatGPT device-code authentication, then retry: codex login --device-auth (chatgptDeviceCode).",
      safeAccountSummary(account),
    );
  }
  if (authentication === "api_key") {
    throw new PreflightError(
      "API_KEY_AUTH_NOT_ALLOWED",
      "The App Server is using API-key authentication. This orchestrator only accepts ChatGPT Pro authentication; switch Codex to ChatGPT device-code login (chatgptDeviceCode), then retry.",
      safeAccountSummary(account),
    );
  }
  if (authentication !== "chatgpt") {
    throw new PreflightError(
      "AUTH_MODE_UNKNOWN",
      "Could not prove that Codex App Server is authenticated with ChatGPT. Log in with ChatGPT device-code authentication (chatgptDeviceCode); no API-key fallback or model substitution was selected.",
      safeAccountSummary(account),
    );
  }
  if (!accountState.plan) {
    throw new PreflightError(
      "CHATGPT_PLAN_UNKNOWN",
      "Codex reported ChatGPT authentication but did not expose a subscription plan. The orchestrator will not assume Pro access or switch credentials silently.",
      safeAccountSummary(account),
    );
  }
  if (!accountState.plan.toLowerCase().includes("pro")) {
    throw new PreflightError(
      "CHATGPT_PRO_REQUIRED",
      `The active ChatGPT plan is ${accountState.plan}; this orchestrator requires ChatGPT Pro Codex usage and will not continue.`,
      safeAccountSummary(account),
    );
  }
  const rawModels = await facade.modelList();
  const models = extractModels(rawModels).flatMap((raw) => {
    const id = modelIdentifier(raw);
    return id ? [{ id, reasoningEfforts: modelReasoningEfforts(raw), raw }] : [];
  });
  const sol = models.find((model) => model.id === "gpt-5.6-sol");
  const luna = models.find((model) => model.id === "gpt-5.6-luna");
  if (!sol || !luna) {
    throw new PreflightError(
      "REQUIRED_MODELS_UNAVAILABLE",
      `Required models gpt-5.6-sol and gpt-5.6-luna were not both advertised by App Server. Available models: ${models.map((model) => model.id).join(", ") || "none"}`,
      models,
    );
  }
  if (sol.reasoningEfforts.length === 0 || luna.reasoningEfforts.length === 0) {
    throw new PreflightError(
      "REASONING_LEVELS_UNKNOWN",
      "App Server listed the required models but did not advertise their supported reasoning levels. The orchestrator will not assume max or silently choose a fallback.",
      { sol: sol.raw, luna: luna.raw },
    );
  }
  const solReasoning = chooseSolReasoning(sol.reasoningEfforts);
  const lunaReasoning = chooseFastestReasoning(luna.reasoningEfforts);
  const usageBefore = await facade.rateLimitsRead();
  return { codex, account, authentication, ...(accountState.plan ? { plan: accountState.plan } : {}), models, usageBefore, sol, luna, solReasoning, lunaReasoning };
}

export function chooseSolReasoning(efforts: string[]): string {
  if (efforts.includes("max")) return "max";
  const preferred = ["xhigh", "high", "medium", "low", "minimal", "none"];
  return preferred.find((effort) => efforts.includes(effort)) ?? efforts[0] ?? "max";
}

export function chooseFastestReasoning(efforts: string[]): string {
  const preferred = ["none", "minimal", "low", "medium", "high", "xhigh", "max"];
  return preferred.find((effort) => efforts.includes(effort)) ?? efforts[0] ?? "low";
}

export function extractText(value: unknown): string {
  if (typeof value === "string") return value;
  if (Array.isArray(value)) return value.map(extractText).filter(Boolean).join("\n");
  if (typeof value !== "object" || value === null) return "";
  const object = value as Record<string, unknown>;
  for (const key of ["text", "output_text", "message", "content", "result", "response", "output", "items", "parts", "turn", "final", "delta"]) {
    if (key in object) {
      const text = extractText(object[key]);
      if (text) return text;
    }
  }
  return "";
}

export function extractThreadId(value: unknown): string {
  if (typeof value === "string" && value.trim()) return value;
  if (typeof value !== "object" || value === null) throw new Error("thread/start returned no thread id");
  const object = value as Record<string, unknown>;
  for (const key of ["threadId", "thread_id", "id"]) {
    const candidate = object[key];
    if (typeof candidate === "string" && candidate.trim()) return candidate;
  }
  if (object.thread && typeof object.thread === "object" && object.thread !== null) return extractThreadId(object.thread);
  throw new Error("thread/start returned no thread id");
}

/**
 * Adapter for the versioned RPC client. Keeping the orchestration layer on
 * this small interface makes the mock deterministic and avoids importing any
 * Responses API or API-key SDK.
 */
export class RpcAppServerFacade implements AppServerFacade {
  constructor(private readonly client: {
    initialize?: () => Promise<unknown>;
    request?: (method: string, params?: unknown) => Promise<unknown>;
    startThread?: (params: unknown) => Promise<unknown>;
    startTurn?: (params: unknown) => Promise<unknown>;
    startTurnAndWait?: (threadId: string, input: unknown, options?: unknown) => Promise<unknown>;
    close?: () => Promise<void>;
  }) {}

  async initialize(): Promise<unknown> {
    if (this.client.initialize) return this.client.initialize();
    return this.request("initialize", {
      protocolVersion: 1,
      clientInfo: { name: "codex-orchestrator", version: "0.1.0" },
      capabilities: {},
    });
  }

  accountRead(): Promise<unknown> { return this.request("account/read", {}); }
  modelList(): Promise<unknown> { return this.request("model/list", {}); }
  rateLimitsRead(): Promise<unknown> { return this.request("account/rateLimits/read", {}); }

  async startThread(options: ThreadStartOptions): Promise<string> {
    const value = this.client.startThread
      ? await this.client.startThread({ model: options.model, reasoningEffort: options.reasoningEffort, cwd: options.cwd, ...(options.approvalPolicy ? { approvalPolicy: options.approvalPolicy } : {}), ...(options.sandbox ? { sandbox: options.sandbox } : {}) })
      : await this.request("thread/start", { model: options.model, reasoningEffort: options.reasoningEffort, cwd: options.cwd, ...(options.approvalPolicy ? { approvalPolicy: options.approvalPolicy } : {}), ...(options.sandbox ? { sandbox: options.sandbox } : {}) });
    return extractThreadId(value);
  }

  turn(threadId: string, prompt: string): Promise<unknown> {
    const params = { threadId, input: [{ type: "text", text: prompt }] };
    if (this.client.startTurnAndWait) return this.client.startTurnAndWait(threadId, prompt);
    if (this.client.startTurn) {
      const startTurn = this.client.startTurn as unknown as (threadId: string, input: unknown) => Promise<unknown>;
      return startTurn(threadId, params.input);
    }
    return this.request("turn/start", params);
  }

  async close(): Promise<void> {
    await this.client.close?.();
  }

  private request(method: string, params: unknown): Promise<unknown> {
    if (!this.client.request) throw new Error(`RPC client does not expose request() for ${method}`);
    return this.client.request(method, params);
  }
}

export class MockAppServerFacade implements AppServerFacade {
  private nextThread = 1;
  private readonly threads = new Map<string, ThreadStartOptions>();

  async initialize(): Promise<unknown> { return { protocolVersion: 1, serverInfo: { name: "mock-codex", version: "0.1.0" } }; }
  async accountRead(): Promise<unknown> { return { authMode: "chatgpt", isLoggedIn: true, plan: "pro" }; }
  async modelList(): Promise<unknown> {
    return {
      models: [
        { id: "gpt-5.6-sol", supportedReasoningEfforts: ["low", "medium", "high", "xhigh", "max"] },
        { id: "gpt-5.6-luna", supportedReasoningEfforts: ["none", "minimal", "low", "medium"] },
      ],
    };
  }
  async rateLimitsRead(): Promise<unknown> { return { limits: [{ remaining: 999, limit: 1000, used: 1, windowSeconds: 3600 }] }; }
  async startThread(options: ThreadStartOptions): Promise<string> {
    const id = `mock-thread-${this.nextThread++}`;
    this.threads.set(id, options);
    return id;
  }
  async turn(threadId: string, prompt: string): Promise<unknown> {
    if (!this.threads.has(threadId)) throw new Error(`unknown mock thread ${threadId}`);
    if (prompt.toLowerCase().includes("review schema") || prompt.toLowerCase().includes("review every luna") || prompt.toLowerCase().includes("review lead")) {
      return { text: JSON.stringify({ approved: true, summary: "Mock review approved all worker evidence.", taskFindings: [], risks: [], retryTaskIds: [] }) };
    }
    if (prompt.includes("Luna worker")) {
      return { text: JSON.stringify({ taskId: "unknown", status: "completed", summary: "Mock Luna completed the task.", changedFiles: [], tests: [{ command: "mock", status: "passed" }], completionCriteriaMet: true, risks: [], attempt: 1 }) };
    }
    return { text: JSON.stringify({
      goal: "mock verification",
      tasks: [
        {
          id: "mock-inspect",
          goal: "Inspect the repository and report its shape",
          dependencies: [],
          readFiles: ["README.md"],
          writableFiles: [],
          completionCriteria: ["Return a concise repository inspection"],
          difficulty: "easy",
        },
        {
          id: "mock-write",
          goal: "Create a harmless mock artifact in the isolated worker workspace",
          dependencies: ["mock-inspect"],
          readFiles: [],
          writableFiles: [".codex-orchestrator/mock-worker.txt"],
          completionCriteria: ["Artifact exists in the worker workspace"],
          difficulty: "medium",
        },
      ],
      globalCompletionCriteria: ["Both mock tasks complete"],
      risks: [],
    }) };
  }
  async close(): Promise<void> { this.threads.clear(); }
}

export async function createLiveFacade(config: OrchestratorConfig): Promise<AppServerFacade> {
  const module = await import("./rpc/client.js") as Record<string, unknown>;
  const transportModule = await import("./rpc/transport.js") as Record<string, unknown>;
  const transportFactory = transportModule.createCodexAppServerTransport as ((options: unknown) => unknown) | undefined;
  const transport = transportFactory?.({ command: config.codexBin, args: ["app-server", "--listen", "stdio://"], cwd: config.cwd });
  const factory = module.createCodexAppServerClient as ((options: unknown) => unknown) | undefined;
  const Constructor = module.CodexAppServerClient ?? module.AppServerClient ?? module.JsonRpcClient;
  let client: unknown;
  if (factory) client = factory({ transport, requestTimeoutMs: config.timeoutMs });
  else if (typeof Constructor === "function") {
    try { client = new (Constructor as new (options: unknown) => unknown)({ transport, requestTimeoutMs: config.timeoutMs }); }
    catch { client = new (Constructor as new (options: unknown) => unknown)(transport); }
  }
  if (!client || typeof client !== "object") throw new Error("RPC client module did not expose a supported client factory");
  const record = client as Record<string, unknown>;
  if (typeof record.start === "function") await (record.start as () => Promise<void>)();
  return new RpcAppServerFacade(client as ConstructorParameters<typeof RpcAppServerFacade>[0]);
}
