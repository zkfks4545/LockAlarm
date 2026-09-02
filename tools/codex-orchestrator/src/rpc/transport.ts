import { spawn, type ChildProcessWithoutNullStreams } from "node:child_process";
import { Buffer } from "node:buffer";

import type { JsonRpcMessage } from "./protocol.js";

export type RpcFraming = "line" | "content-length";

export interface RpcTransport {
  start(): void;
  send(message: JsonRpcMessage): void;
  close(): Promise<void>;
  isOpen(): boolean;
  onMessage(listener: (message: JsonRpcMessage) => void): () => void;
  onError(listener: (error: Error) => void): () => void;
  onClose(listener: (code: number | null, signal: NodeJS.Signals | null) => void): () => void;
}

export class RpcProtocolError extends Error {
  constructor(message: string, public readonly raw?: string) {
    super(message);
    this.name = "RpcProtocolError";
  }
}

export type JsonRpcParserOptions = {
  framing?: RpcFraming;
  onMessage: (message: JsonRpcMessage) => void;
  onError?: (error: Error) => void;
};

/**
 * Parses either JSONL (the current App Server stdio format) or the
 * Content-Length framing used by language-server-style JSON-RPC transports.
 * Detection is per message, which makes the parser useful with older App
 * Server builds and simple mock servers too.
 */
export class JsonRpcStreamParser {
  private buffer = Buffer.alloc(0);
  private readonly framing: RpcFraming | undefined;

  constructor(private readonly options: JsonRpcParserOptions) {
    this.framing = options.framing;
  }

  feed(chunk: Buffer | string): void {
    const bytes = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk, "utf8");
    this.buffer = Buffer.concat([this.buffer, bytes]);
    this.parseAvailable();
  }

  end(): void {
    if (this.buffer.length === 0) return;
    const text = this.buffer.toString("utf8").trim();
    this.buffer = Buffer.alloc(0);
    if (!text) return;
    try {
      this.emitParsed(text);
    } catch (error) {
      this.emitError(error);
    }
  }

  private parseAvailable(): void {
    while (this.buffer.length > 0) {
      // Ignore blank lines. JSONL servers occasionally leave a trailing CRLF.
      while (this.buffer.length > 0 && (this.buffer[0] === 10 || this.buffer[0] === 13 || this.buffer[0] === 32 || this.buffer[0] === 9)) {
        this.buffer = this.buffer.subarray(1);
      }
      if (this.buffer.length === 0) return;

      const header = this.readContentLengthHeader();
      if (header === "incomplete") return;
      if (header) {
        const { bodyOffset, bodyLength } = header;
        if (this.buffer.length < bodyOffset + bodyLength) return;
        const body = this.buffer.subarray(bodyOffset, bodyOffset + bodyLength).toString("utf8");
        this.buffer = this.buffer.subarray(bodyOffset + bodyLength);
        try {
          this.emitParsed(body);
        } catch (error) {
          this.emitError(error);
        }
        continue;
      }

      // A configured Content-Length parser should not silently interpret a
      // partial header as JSON. Wait for its separator before attempting line
      // framing. The auto mode still accepts JSONL immediately.
      if (this.framing === "content-length") {
        const prefix = this.buffer.toString("ascii").toLowerCase();
        if ("content-length".startsWith(prefix) || prefix.startsWith("content-length:")) return;
      }

      const newline = this.buffer.indexOf(10);
      if (newline < 0) return;
      const line = this.buffer.subarray(0, newline).toString("utf8").replace(/\r$/, "").trim();
      this.buffer = this.buffer.subarray(newline + 1);
      if (!line) continue;
      try {
        this.emitParsed(line);
      } catch (error) {
        this.emitError(error);
      }
    }
  }

  private readContentLengthHeader(): { bodyOffset: number; bodyLength: number } | "incomplete" | undefined {
    // Find the first line without decoding the body. Header fields are ASCII.
    const firstLineEnd = this.buffer.indexOf(10);
    if (firstLineEnd < 0) {
      const prefix = this.buffer.toString("ascii").toLowerCase();
      if (prefix.startsWith("content-length") || this.framing === "content-length") return "incomplete";
      return undefined;
    }
    const firstLine = this.buffer.subarray(0, firstLineEnd).toString("ascii").replace(/\r$/, "");
    const match = /^content-length\s*:\s*(\d+)$/i.exec(firstLine.trim());
    if (!match) return undefined;

    // Protocol headers end with an empty line. Permit LF and CRLF.
    let separatorLength = 1;
    let bodyOffset = firstLineEnd + 1;
    if (this.buffer.length > bodyOffset && this.buffer[bodyOffset] === 13) {
      if (this.buffer.length <= bodyOffset + 1) return "incomplete";
      if (this.buffer[bodyOffset + 1] === 10) {
        bodyOffset += 1;
        separatorLength = 2;
      }
    }
    if (this.buffer.length < bodyOffset + 1) return "incomplete";
    if (this.buffer[bodyOffset] !== 10) return undefined;
    bodyOffset += separatorLength;
    return { bodyOffset, bodyLength: Number(match[1]) };
  }

  private emitParsed(text: string): void {
    let value: unknown;
    try {
      value = JSON.parse(text);
    } catch {
      throw new RpcProtocolError(`Invalid JSON-RPC payload: ${text.slice(0, 200)}`, text);
    }
    if (!isJsonRpcEnvelope(value)) {
      throw new RpcProtocolError("JSON-RPC payload is missing a valid jsonrpc/method/result/error envelope", text);
    }
    // Codex App Server's stdio protocol deliberately omits the JSON-RPC
    // version member on the wire. Normalize it for the rest of the client.
    if (isObjectRecord(value) && value.jsonrpc === undefined) {
      this.options.onMessage({ ...(value as Record<string, unknown>), jsonrpc: "2.0" } as JsonRpcMessage);
    } else {
      this.options.onMessage(value);
    }
  }

  private emitError(error: unknown): void {
    const normalized = error instanceof Error ? error : new Error(String(error));
    this.options.onError?.(normalized);
  }
}

function isJsonRpcEnvelope(value: unknown): value is JsonRpcMessage {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const object = value as Record<string, unknown>;
  if (object.jsonrpc !== undefined && object.jsonrpc !== "2.0") return false;
  if (typeof object.method === "string") {
    return !Object.prototype.hasOwnProperty.call(object, "id") ||
      (typeof object.id === "string" || typeof object.id === "number");
  }
  if ("result" in object) return Object.prototype.hasOwnProperty.call(object, "id");
  if ("error" in object) return Object.prototype.hasOwnProperty.call(object, "id");
  return false;
}

function isObjectRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

export interface ChildProcessTransportOptions {
  command?: string;
  args?: string[];
  cwd?: string;
  env?: NodeJS.ProcessEnv;
  framing?: RpcFraming;
  /**
   * App Server's stdio protocol omits the JSON-RPC version field on the wire.
   * Set this for a conventional JSON-RPC peer (or Content-Length transport).
   */
  includeJsonRpcVersion?: boolean;
  /** If true, kill the child when close() is called instead of waiting for exit. */
  forceKillOnClose?: boolean;
}

/** A JSON-RPC transport backed by `codex app-server --listen stdio://`. */
export class ChildProcessTransport implements RpcTransport {
  private child: ChildProcessWithoutNullStreams | undefined;
  private parser: JsonRpcStreamParser | undefined;
  private readonly messageListeners = new Set<(message: JsonRpcMessage) => void>();
  private readonly errorListeners = new Set<(error: Error) => void>();
  private readonly closeListeners = new Set<(code: number | null, signal: NodeJS.Signals | null) => void>();
  private closePromise: Promise<void> | undefined;
  private closeResolve: (() => void) | undefined;
  private closed = false;

  constructor(private readonly options: ChildProcessTransportOptions = {}) {}

  start(): void {
    if (this.child && !this.closed) return;
    this.closed = false;
    const command = this.options.command ?? "codex";
    const args = this.options.args ?? ["app-server", "--listen", "stdio://"];
    const environment = this.options.env ? { ...process.env, ...this.options.env } : process.env;
    const child = spawn(command, args, {
      cwd: this.options.cwd,
      env: environment,
      stdio: ["pipe", "pipe", "pipe"],
      windowsHide: true,
    });
    this.child = child;
    const parserOptions: JsonRpcParserOptions = {
      onMessage: (message) => this.messageListeners.forEach((listener) => listener(message)),
      onError: (error) => this.emitError(error),
    };
    if (this.options.framing !== undefined) parserOptions.framing = this.options.framing;
    this.parser = new JsonRpcStreamParser(parserOptions);
    child.stdout.on("data", (chunk: Buffer) => this.parser?.feed(chunk));
    child.stdout.on("error", (error) => this.emitError(error));
    child.stderr.on("data", (chunk: Buffer) => {
      // stderr is deliberately surfaced as an Error event only for consumers
      // that opt in; normal diagnostics should not corrupt the JSON-RPC stream.
      const text = chunk.toString("utf8").trim();
      if (text) this.emitError(new Error(`codex app-server: ${text}`));
    });
    child.stderr.on("error", (error) => this.emitError(error));
    child.on("error", (error) => this.emitError(error));
    child.on("close", (code, signal) => {
      this.closed = true;
      this.parser?.end();
      this.closeListeners.forEach((listener) => listener(code, signal));
      this.closeResolve?.();
      this.closeResolve = undefined;
      this.closePromise = undefined;
    });
  }

  send(message: JsonRpcMessage): void {
    if (!this.child || this.closed || !this.child.stdin.writable) {
      throw new Error("Cannot send JSON-RPC message: App Server transport is not open");
    }
    const framing = this.options.framing ?? "line";
    const wireMessage = framing === "line" && this.options.includeJsonRpcVersion !== true
      ? omitJsonRpcVersion(message)
      : message;
    const json = JSON.stringify(wireMessage);
    if (framing === "content-length") {
      const body = Buffer.from(json, "utf8");
      this.child.stdin.write(Buffer.concat([Buffer.from(`Content-Length: ${body.byteLength}\r\n\r\n`), body]));
    } else {
      this.child.stdin.write(`${json}\n`);
    }
  }

  isOpen(): boolean {
    return Boolean(this.child && !this.closed && this.child.stdin.writable);
  }

  close(): Promise<void> {
    const child = this.child;
    if (!child || this.closed) return Promise.resolve();
    if (this.closePromise) return this.closePromise;
    this.closePromise = new Promise<void>((resolve) => {
      this.closeResolve = resolve;
      child.stdin.end();
      if (this.options.forceKillOnClose !== false) {
        // Give a well-behaved server a short opportunity to exit before kill.
        const timer = setTimeout(() => {
          if (!this.closed) child.kill();
        }, 250);
        child.once("close", () => clearTimeout(timer));
      }
    });
    return this.closePromise;
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
}

function omitJsonRpcVersion(message: JsonRpcMessage): Omit<JsonRpcMessage, "jsonrpc"> {
  const { jsonrpc: _jsonrpc, ...withoutVersion } = message;
  return withoutVersion;
}

export function createCodexAppServerTransport(options: ChildProcessTransportOptions = {}): ChildProcessTransport {
  return new ChildProcessTransport(options);
}
