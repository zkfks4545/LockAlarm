import type {
  AccountReadResult,
  JsonRpcErrorResponse,
  JsonRpcMessage,
  JsonRpcNotification,
  JsonRpcRequest,
  JsonRpcResponse,
  JsonRpcSuccess,
  ModelInfo,
  RateLimitWindow,
} from "./protocol.js";
import { CodexAppServerClient, type CodexAppServerClientOptions } from "./client.js";
import type { RpcTransport } from "./transport.js";

export type MockRpcHandler = (
  request: JsonRpcRequest,
  transport: MockAppServerTransport,
) => JsonRpcResponse | JsonRpcNotification | void | Promise<JsonRpcResponse | JsonRpcNotification | void>;

export interface MockAppServerOptions {
  account?: AccountReadResult;
  models?: ModelInfo[];
  rateLimits?: RateLimitWindow[];
  handlers?: Record<string, MockRpcHandler>;
  responseDelayMs?: number;
  emitTurnCompleted?: boolean;
  serverInfo?: { name: string; version: string };
}

/**
 * In-memory App Server substitute for unit and mock end-to-end tests. It
 * implements the same transport contract as ChildProcessTransport, so no
 * model usage is consumed while testing orchestration and retry behavior.
 */
export class MockAppServerTransport implements RpcTransport {
  private open = false;
  private readonly messageListeners = new Set<(message: JsonRpcMessage) => void>();
  private readonly errorListeners = new Set<(error: Error) => void>();
  private readonly closeListeners = new Set<(code: number | null, signal: NodeJS.Signals | null) => void>();
  private readonly handlers: Record<string, MockRpcHandler>;
  private readonly responseDelayMs: number;
  private readonly serverInfo: { name: string; version: string };
  private readonly emitTurnCompleted: boolean;
  private threadNumber = 0;
  private turnNumber = 0;
  readonly requests: JsonRpcRequest[] = [];
  readonly notifications: JsonRpcNotification[] = [];

  constructor(options: MockAppServerOptions = {}) {
    this.responseDelayMs = Math.max(0, options.responseDelayMs ?? 0);
    this.emitTurnCompleted = options.emitTurnCompleted !== false;
    this.serverInfo = options.serverInfo ?? { name: "mock-codex-app-server", version: "0.0.0-test" };
    this.handlers = {
      initialize: () => ({
        jsonrpc: "2.0",
        id: 0,
        result: { serverInfo: this.serverInfo, capabilities: {} },
      }),
      "account/read": () => ({
        jsonrpc: "2.0",
        id: 0,
        result: options.account ?? { authMode: "chatgpt", isLoggedIn: true, planType: "pro" },
      }),
      "model/list": () => ({
        jsonrpc: "2.0",
        id: 0,
        result: {
          models: options.models ?? [
            { id: "gpt-5.6-sol", name: "GPT-5.6 Sol", supportedReasoningEfforts: ["fast", "max"] },
            { id: "gpt-5.6-luna", name: "GPT-5.6 Luna", supportedReasoningEfforts: ["fast", "high"] },
          ],
        },
      }),
      "account/rateLimits/read": () => ({
        jsonrpc: "2.0",
        id: 0,
        result: { limits: options.rateLimits ?? [] },
      }),
      "thread/start": () => ({
        jsonrpc: "2.0",
        id: 0,
        result: { id: `mock-thread-${++this.threadNumber}` },
      }),
      "turn/start": () => ({
        jsonrpc: "2.0",
        id: 0,
        result: { id: `mock-turn-${++this.turnNumber}`, status: "completed" },
      }),
      ...(options.handlers ?? {}),
    };
  }

  start(): void {
    this.open = true;
  }

  send(message: JsonRpcMessage): void {
    if (!this.open) throw new Error("Mock App Server transport is not open");
    if (!("method" in message)) return;
    if ("id" in message) {
      const request = message as JsonRpcRequest;
      this.requests.push(request);
      const handler = this.handlers[request.method];
      if (!handler) {
        this.schedule({
          jsonrpc: "2.0",
          id: request.id,
          error: { code: -32601, message: `Method not found: ${request.method}` },
        });
        return;
      }
      Promise.resolve(handler(request, this)).then((response) => {
        if (!response) return;
        if ("method" in response) {
          this.schedule(response);
          return;
        }
        const normalizedResponse = { ...response, id: request.id } as JsonRpcResponse;
        this.schedule(normalizedResponse);
        if (this.emitTurnCompleted && request.method === "turn/start" && "result" in normalizedResponse && normalizedResponse.result && typeof normalizedResponse.result === "object") {
          const result = normalizedResponse.result as Record<string, unknown>;
          const threadId = request.params && typeof request.params === "object" && !Array.isArray(request.params)
            ? (request.params as Record<string, unknown>).threadId
            : undefined;
          const turnId = typeof result.id === "string" ? result.id : undefined;
          if (typeof threadId === "string" && turnId) {
            this.schedule({ jsonrpc: "2.0", method: "turn/started", params: { threadId, turn: { id: turnId, status: "inProgress" } } });
            this.schedule({ jsonrpc: "2.0", method: "turn/completed", params: { threadId, turnId, turn: { id: turnId, status: "completed", items: [{ type: "agentMessage", text: "mock turn completed" }] } } });
          }
        }
      }).catch((error) => {
        this.emitError(error instanceof Error ? error : new Error(String(error)));
        this.schedule({
          jsonrpc: "2.0",
          id: request.id,
          error: { code: -32000, message: error instanceof Error ? error.message : String(error) },
        });
      });
      return;
    }
    this.notifications.push(message as JsonRpcNotification);
    const handler = this.handlers[message.method];
    if (handler) {
      Promise.resolve(handler(message as JsonRpcRequest, this)).then((response) => {
        if (response) this.schedule(response);
      }).catch((error) => this.emitError(error instanceof Error ? error : new Error(String(error))));
    }
  }

  private schedule(message: JsonRpcMessage): void {
    const emit = () => this.messageListeners.forEach((listener) => listener(message));
    if (this.responseDelayMs > 0) setTimeout(emit, this.responseDelayMs);
    else queueMicrotask(emit);
  }

  isOpen(): boolean {
    return this.open;
  }

  async close(): Promise<void> {
    if (!this.open) return;
    this.open = false;
    this.closeListeners.forEach((listener) => listener(0, null));
  }

  onMessage(listener: (message: JsonRpcMessage) => void): () => void {
    this.messageListeners.add(listener);
    return () => this.messageListeners.delete(listener);
  }

  onError(listener: (error: Error) => void): () => void {
    this.errorListeners.add(listener);
    return () => this.errorListeners.delete(listener);
  }

  onClose(listener: (code: number | null, signal: NodeJS.Signals | null) => void): () => void {
    this.closeListeners.add(listener);
    return () => this.closeListeners.delete(listener);
  }

  private emitError(error: Error): void {
    this.errorListeners.forEach((listener) => listener(error));
  }

  /** Send a server notification to the client under test. */
  emitNotification(method: string, params?: unknown): void {
    if (!this.open) throw new Error("Mock App Server transport is not open");
    this.schedule({
      jsonrpc: "2.0",
      method,
      ...(params === undefined ? {} : { params }),
    } as JsonRpcNotification);
  }
}

export interface MockAppServerClientOptions extends Omit<CodexAppServerClientOptions, "transport"> {
  server?: MockAppServerTransport;
  serverOptions?: MockAppServerOptions;
}

export class MockAppServerClient extends CodexAppServerClient {
  readonly mockServer: MockAppServerTransport;

  constructor(options: MockAppServerClientOptions = {}) {
    const server = options.server ?? new MockAppServerTransport(options.serverOptions);
    super({ ...options, transport: server });
    this.mockServer = server;
  }
}

export function createMockAppServerClient(options: MockAppServerClientOptions = {}): MockAppServerClient {
  return new MockAppServerClient(options);
}
