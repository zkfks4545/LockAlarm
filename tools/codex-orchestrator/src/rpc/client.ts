import {
  type AccountReadResult,
  type InitializeParams,
  type InitializeResult,
  type JsonObject,
  type JsonValue,
  type JsonRpcErrorObject,
  type JsonRpcId,
  type JsonRpcMessage,
  type ModelInfo,
  type ModelListResult,
  type RateLimitWindow,
  type RateLimitsReadResult,
  extractModels,
  extractRateLimitWindows,
  isJsonRpcRequest,
  isJsonRpcResponse,
  isObject,
} from "./protocol.js";
import type { RpcTransport } from "./transport.js";
import { ChildProcessTransport } from "./transport.js";

export interface JsonRpcRequestOptions {
  timeoutMs?: number;
}

export class JsonRpcRemoteError extends Error {
  readonly code: number;
  readonly data: unknown;

  constructor(error: JsonRpcErrorObject | Error | unknown) {
    if (error instanceof Error) {
      super(error.message);
      this.code = -32000;
      this.data = undefined;
    } else if (isObject(error)) {
      super(typeof error.message === "string" ? error.message : "JSON-RPC server error");
      this.code = typeof error.code === "number" ? error.code : -32000;
      this.data = error.data;
    } else {
      super(String(error));
      this.code = -32000;
      this.data = undefined;
    }
    this.name = "JsonRpcRemoteError";
  }
}

export interface AppServerThreadStartParams extends JsonObject {
  model?: string;
  reasoningEffort?: string;
  cwd?: string;
  approvalPolicy?: string;
  sandbox?: string;
  sandboxPolicy?: JsonObject;
  permissions?: JsonObject;
  profile?: string;
}

export interface AppServerThreadResult extends JsonObject {
  id?: string;
  threadId?: string;
  thread_id?: string;
  thread?: JsonObject;
}

export interface AppServerTurnInput extends JsonObject {
  type: string;
  text?: string;
}

export interface AppServerTurnStartParams extends JsonObject {
  threadId: string;
  input: AppServerTurnInput[];
  model?: string;
  effort?: string;
  reasoningEffort?: string;
  cwd?: string;
  approvalPolicy?: string;
  sandbox?: string;
  sandboxPolicy?: JsonObject;
  permissions?: JsonObject;
}

export interface AppServerTurnResult extends JsonObject {
  id?: string;
  turnId?: string;
  turn_id?: string;
  turn?: JsonObject;
}

export interface AppServerTurnCompleted extends JsonObject {
  threadId?: string;
  turnId?: string;
  turn?: JsonObject;
  [key: string]: JsonValue | undefined;
}

export interface AppServerClientLike {
  start(): void;
  initialize(params?: InitializeParams): Promise<InitializeResult>;
  request<T = unknown>(method: string, params?: unknown, options?: JsonRpcRequestOptions): Promise<T>;
  notify(method: string, params?: unknown): void;
  respond(id: JsonRpcId, result?: unknown, error?: JsonRpcErrorObject): void;
  startThread(params: AppServerThreadStartParams): Promise<AppServerThreadResult>;
  startTurn(
    threadId: string,
    input: string | AppServerTurnInput[],
    options?: Omit<AppServerTurnStartParams, "threadId" | "input">,
  ): Promise<AppServerTurnResult>;
  startTurn(params: AppServerTurnStartParams): Promise<AppServerTurnResult>;
  waitForTurn(threadId: string, turnId?: string, options?: JsonRpcRequestOptions): Promise<AppServerTurnCompleted>;
  startTurnAndWait(
    threadId: string,
    input: string | AppServerTurnInput[],
    options?: Omit<AppServerTurnStartParams, "threadId" | "input">,
  ): Promise<AppServerTurnCompleted | AppServerTurnResult>;
  accountRead(params?: unknown): Promise<AccountReadResult>;
  modelList(params?: unknown): Promise<ModelListResult>;
  rateLimitsRead(params?: unknown): Promise<RateLimitsReadResult>;
  listModels(params?: unknown): Promise<ModelInfo[]>;
  rateLimitWindows(params?: unknown): Promise<RateLimitWindow[]>;
  close(): Promise<void>;
}

type PendingRequest = {
  resolve: (value: unknown) => void;
  reject: (error: Error) => void;
  timer?: ReturnType<typeof setTimeout>;
};

export interface CodexAppServerClientOptions {
  transport?: RpcTransport;
  requestTimeoutMs?: number;
  initialize?: InitializeParams;
}

export const DEFAULT_INITIALIZE_PARAMS: InitializeParams = {
  protocolVersion: 1,
  clientInfo: { name: "codex-orchestrator", version: "0.1.0" },
  capabilities: {},
};

/**
 * JSON-RPC client for the Codex App Server. It is intentionally transport
 * agnostic, allowing the same planner/worker code to run against an in-memory
 * mock without consuming Codex usage.
 */
export class CodexAppServerClient implements AppServerClientLike {
  private readonly transport: RpcTransport;
  private readonly pending = new Map<JsonRpcId, PendingRequest>();
  private readonly notificationListeners = new Set<(message: JsonRpcMessage) => void>();
  private readonly notificationHistory: JsonRpcMessage[] = [];
  private readonly serverRequestListeners = new Set<(message: JsonRpcMessage) => void>();
  private readonly errorListeners = new Set<(error: Error) => void>();
  private requestId = 0;
  private initialized = false;
  private initializePromise: Promise<InitializeResult> | undefined;
  private closed = false;
  private readonly defaultTimeoutMs: number;
  private removeMessageListener: (() => void) | undefined;
  private removeErrorListener: (() => void) | undefined;
  private removeCloseListener: (() => void) | undefined;

  constructor(options: CodexAppServerClientOptions = {}) {
    this.transport = options.transport ?? new ChildProcessTransport();
    this.defaultTimeoutMs = options.requestTimeoutMs ?? 30_000;
    this.removeMessageListener = this.transport.onMessage((message) => this.handleMessage(message));
    this.removeErrorListener = this.transport.onError((error) => this.handleTransportError(error));
    this.removeCloseListener = this.transport.onClose(() => this.handleTransportClose());
    this.initializationParams = options.initialize;
  }

  private readonly initializationParams: InitializeParams | undefined;

  start(): void {
    if (this.closed) throw new Error("Cannot start a closed Codex App Server client");
    this.transport.start();
  }

  async initialize(params: InitializeParams = this.initializationParams ?? DEFAULT_INITIALIZE_PARAMS): Promise<InitializeResult> {
    if (this.initialized) return this.initializationResult ?? {};
    if (this.initializePromise) return this.initializePromise;
    if (!this.transport.isOpen()) this.start();
    this.initializePromise = this.request<InitializeResult>("initialize", params).then((result) => {
      this.initialized = true;
      this.initializationResult = result ?? {};
      // The App Server expects `initialized` as a notification after the
      // initialize response; no API key or HTTP fallback is involved.
      this.notify("initialized");
      return this.initializationResult;
    }).catch((error) => {
      this.initializePromise = undefined;
      throw error;
    });
    return this.initializePromise;
  }

  private initializationResult: InitializeResult | undefined;

  request<T = unknown>(method: string, params?: unknown, options: JsonRpcRequestOptions = {}): Promise<T> {
    if (this.closed) return Promise.reject(new Error("Codex App Server client is closed"));
    if (!this.transport.isOpen()) this.start();
    const id: JsonRpcId = ++this.requestId;
    const message: JsonRpcMessage = {
      jsonrpc: "2.0",
      id,
      method,
      ...(params === undefined ? {} : { params: params as never }),
    } as JsonRpcMessage;
    const timeoutMs = options.timeoutMs ?? this.defaultTimeoutMs;
    return new Promise<T>((resolve, reject) => {
      const pending: PendingRequest = {
        resolve: (value) => resolve(value as T),
        reject,
      };
      if (timeoutMs > 0 && Number.isFinite(timeoutMs)) {
        pending.timer = setTimeout(() => {
          this.pending.delete(id);
          reject(new Error(`JSON-RPC request timed out after ${timeoutMs} ms: ${method}`));
        }, timeoutMs);
      }
      this.pending.set(id, pending);
      try {
        this.transport.send(message);
      } catch (error) {
        this.pending.delete(id);
        if (pending.timer) clearTimeout(pending.timer);
        reject(error instanceof Error ? error : new Error(String(error)));
      }
    });
  }

  notify(method: string, params?: unknown): void {
    if (this.closed) throw new Error("Codex App Server client is closed");
    if (!this.transport.isOpen()) this.start();
    const message = {
      jsonrpc: "2.0" as const,
      method,
      ...(params === undefined ? {} : { params }),
    } as JsonRpcMessage;
    this.transport.send(message);
  }

  /** Respond to a server-initiated JSON-RPC request (approval/input/etc.). */
  respond(id: JsonRpcId, result?: unknown, error?: JsonRpcErrorObject): void {
    if (this.closed) throw new Error("Codex App Server client is closed");
    if (!this.transport.isOpen()) this.start();
    this.transport.send({
      jsonrpc: "2.0",
      id,
      ...(error ? { error } : { result: result === undefined ? {} : result }),
    } as JsonRpcMessage);
  }

  async startThread(params: AppServerThreadStartParams): Promise<AppServerThreadResult> {
    return this.request<AppServerThreadResult>("thread/start", params);
  }

  async startTurn(params: AppServerTurnStartParams): Promise<AppServerTurnResult>;
  async startTurn(
    threadId: string,
    input: string | AppServerTurnInput[],
    options?: Omit<AppServerTurnStartParams, "threadId" | "input">,
  ): Promise<AppServerTurnResult>;
  async startTurn(
    threadIdOrParams: string | AppServerTurnStartParams,
    input?: string | AppServerTurnInput[],
    options: Omit<AppServerTurnStartParams, "threadId" | "input"> = {},
  ): Promise<AppServerTurnResult> {
    if (typeof threadIdOrParams !== "string") {
      return this.request<AppServerTurnResult>("turn/start", threadIdOrParams);
    }
    const threadId = threadIdOrParams;
    if (input === undefined) throw new Error("turn/start requires input");
    const normalizedInput: AppServerTurnInput[] = typeof input === "string"
      ? [{ type: "text", text: input }]
      : input;
    return this.request<AppServerTurnResult>("turn/start", {
      threadId,
      input: normalizedInput,
      ...options,
    });
  }

  async waitForTurn(
    threadId: string,
    turnId?: string,
    options: JsonRpcRequestOptions = {},
  ): Promise<AppServerTurnCompleted> {
    const historyMatch = this.notificationHistory.find((message) => this.isMatchingCompletedTurn(message, threadId, turnId));
    if (historyMatch && "params" in historyMatch && isObject(historyMatch.params)) {
      return historyMatch.params as AppServerTurnCompleted;
    }
    const timeoutMs = options.timeoutMs ?? this.defaultTimeoutMs;
    return new Promise<AppServerTurnCompleted>((resolve, reject) => {
      let timer: ReturnType<typeof setTimeout> | undefined;
      const remove = this.onNotification((message) => {
        if (!this.isMatchingCompletedTurn(message, threadId, turnId)) return;
        remove();
        if (timer) clearTimeout(timer);
        if ("params" in message && isObject(message.params)) resolve(message.params as AppServerTurnCompleted);
        else resolve({});
      });
      if (timeoutMs > 0 && Number.isFinite(timeoutMs)) {
        timer = setTimeout(() => {
          remove();
          reject(new Error(`Timed out waiting for turn/completed (${threadId}${turnId ? `/${turnId}` : ""})`));
        }, timeoutMs);
      }
    });
  }

  async startTurnAndWait(
    threadId: string,
    input: string | AppServerTurnInput[],
    options: Omit<AppServerTurnStartParams, "threadId" | "input"> = {},
  ): Promise<AppServerTurnCompleted | AppServerTurnResult> {
    const initial = await this.startTurn(threadId, input, options);
    const turnId = extractTurnId(initial);
    if (!turnId) return initial;
    const completed = await this.waitForTurn(threadId, turnId);
    const text = extractTurnText(completed);
    return text ? { ...completed, text } : completed;
  }

  accountRead(params?: unknown): Promise<AccountReadResult> {
    return this.request<AccountReadResult>("account/read", params);
  }

  modelList(params?: unknown): Promise<ModelListResult> {
    return this.request<ModelListResult>("model/list", params);
  }

  rateLimitsRead(params?: unknown): Promise<RateLimitsReadResult> {
    return this.request<RateLimitsReadResult>("account/rateLimits/read", params);
  }

  async listModels(params?: unknown): Promise<ModelInfo[]> {
    return extractModels(await this.modelList(params));
  }

  async rateLimitWindows(params?: unknown): Promise<RateLimitWindow[]> {
    return extractRateLimitWindows(await this.rateLimitsRead(params));
  }

  onNotification(listener: (message: JsonRpcMessage) => void): () => void {
    this.notificationListeners.add(listener);
    return () => this.notificationListeners.delete(listener);
  }

  onServerRequest(listener: (message: JsonRpcMessage) => void): () => void {
    this.serverRequestListeners.add(listener);
    return () => this.serverRequestListeners.delete(listener);
  }

  onError(listener: (error: Error) => void): () => void {
    this.errorListeners.add(listener);
    return () => this.errorListeners.delete(listener);
  }

  async close(): Promise<void> {
    if (this.closed) return;
    this.closed = true;
    const error = new Error("Codex App Server client closed");
    for (const [id, pending] of this.pending) {
      if (pending.timer) clearTimeout(pending.timer);
      pending.reject(error);
      this.pending.delete(id);
    }
    this.removeMessageListener?.();
    this.removeErrorListener?.();
    this.removeCloseListener?.();
    await this.transport.close();
  }

  private handleMessage(message: JsonRpcMessage): void {
    if (isJsonRpcResponse(message)) {
      if (message.id === null) return;
      const pending = this.pending.get(message.id);
      if (!pending) {
        this.emitError(new Error(`Received response for unknown request id ${String(message.id)}`));
        return;
      }
      this.pending.delete(message.id);
      if (pending.timer) clearTimeout(pending.timer);
      if ("error" in message) pending.reject(new JsonRpcRemoteError(message.error));
      else pending.resolve(message.result);
      return;
    }
    if (isJsonRpcRequest(message)) {
      this.serverRequestListeners.forEach((listener) => listener(message));
      // App Server normally only sends notifications, but acknowledge unknown
      // server requests to avoid leaving the peer blocked forever.
      if (this.serverRequestListeners.size === 0) {
        try {
          this.respond(message.id, undefined, { code: -32601, message: `Unsupported server request: ${message.method}` });
        } catch (error) {
          this.emitError(error instanceof Error ? error : new Error(String(error)));
        }
      }
      return;
    }
    this.notificationHistory.push(message);
    if (this.notificationHistory.length > 100) this.notificationHistory.shift();
    this.notificationListeners.forEach((listener) => listener(message));
  }

  private isMatchingCompletedTurn(message: JsonRpcMessage, threadId: string, turnId?: string): boolean {
    if (!("method" in message) || message.method !== "turn/completed") return false;
    if (!("params" in message) || !isObject(message.params)) return false;
    const params = message.params;
    const candidateThreadId = typeof params.threadId === "string"
      ? params.threadId
      : isObject(params.turn) && typeof params.turn.threadId === "string"
        ? params.turn.threadId
        : undefined;
    const candidateTurnId = typeof params.turnId === "string"
      ? params.turnId
      : isObject(params.turn) && typeof params.turn.id === "string"
        ? params.turn.id
        : undefined;
    return candidateThreadId === threadId && (turnId === undefined || candidateTurnId === turnId);
  }

  private handleTransportError(error: Error): void {
    this.errorListeners.forEach((listener) => listener(error));
    // A transport error does not immediately reject pending requests: some
    // child-process transports emit transient stream errors before close.
  }

  private handleTransportClose(): void {
    if (this.closed) return;
    const error = new Error("Codex App Server transport closed unexpectedly");
    this.pending.forEach((pending) => {
      if (pending.timer) clearTimeout(pending.timer);
      pending.reject(error);
    });
    this.pending.clear();
    this.closed = true;
  }

  private emitError(error: Error): void {
    this.errorListeners.forEach((listener) => listener(error));
  }
}

export function createCodexAppServerClient(options: CodexAppServerClientOptions = {}): CodexAppServerClient {
  return new CodexAppServerClient(options);
}

// Compatibility aliases for callers that refer to the transport-facing class
// by its shorter historical names.
export const AppServerClient = CodexAppServerClient;
export const JsonRpcClient = CodexAppServerClient;

export function createJsonRpcClient(options: CodexAppServerClientOptions = {}): CodexAppServerClient {
  return new CodexAppServerClient(options);
}

export function extractThreadId(value: unknown): string | undefined {
  if (!isObject(value)) return undefined;
  for (const candidate of [value.threadId, value.thread_id, value.id]) {
    if (typeof candidate === "string" && candidate) return candidate;
  }
  const thread = value.thread;
  if (isObject(thread) && typeof thread.id === "string") return thread.id;
  return undefined;
}

export function extractTurnId(value: unknown): string | undefined {
  if (!isObject(value)) return undefined;
  for (const candidate of [value.turnId, value.turn_id, value.id]) {
    if (typeof candidate === "string" && candidate) return candidate;
  }
  const turn = value.turn;
  if (isObject(turn) && typeof turn.id === "string") return turn.id;
  return undefined;
}

/** Extract accumulated agent-message text from a `turn/completed` payload. */
export function extractTurnText(value: unknown): string {
  if (typeof value === "string") return value;
  if (Array.isArray(value)) return value.map(extractTurnText).filter(Boolean).join("\n");
  if (!isObject(value)) return "";
  if (typeof value.text === "string" && (value.type === undefined || value.type === "agentMessage" || value.type === "message")) {
    return value.text;
  }
  for (const key of ["turn", "items", "item", "content", "parts", "message"]) {
    const text = extractTurnText(value[key]);
    if (text) return text;
  }
  return "";
}
