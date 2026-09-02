import type { Writable } from "node:stream";

export type LogLevel = "debug" | "info" | "warn" | "error";

export interface LoggerLike {
  child(fields: Record<string, unknown>): LoggerLike;
  log(level: LogLevel, event: string, fields?: Record<string, unknown>): void;
  debug(event: string, fields?: Record<string, unknown>): void;
  info(event: string, fields?: Record<string, unknown>): void;
  warn(event: string, fields?: Record<string, unknown>): void;
  error(event: string, fields?: Record<string, unknown>): void;
}

export interface JsonLoggerOptions {
  stream?: Writable;
  context?: Record<string, unknown>;
  minLevel?: LogLevel;
  clock?: () => Date;
}

const LEVEL_WEIGHT: Record<LogLevel, number> = {
  debug: 10,
  info: 20,
  warn: 30,
  error: 40,
};

const SENSITIVE_KEY = /(token|secret|password|api[-_]?key|authorization|cookie|credential)/i;

function redact(value: unknown, ancestors = new WeakSet<object>()): unknown {
  if (value === null || typeof value !== "object") return value;
  if (ancestors.has(value as object)) return "[Circular]";
  ancestors.add(value as object);
  if (Array.isArray(value)) {
    const result = value.map((entry) => redact(entry, ancestors));
    ancestors.delete(value as object);
    return result;
  }
  const result: Record<string, unknown> = {};
  Object.entries(value as Record<string, unknown>).forEach(([key, entry]) => {
    result[key] = SENSITIVE_KEY.test(key) ? "[REDACTED]" : redact(entry, ancestors);
  });
  ancestors.delete(value as object);
  return result;
}

/** Newline-delimited JSON logger suitable for both humans and log shippers. */
export class JsonLogger implements LoggerLike {
  private readonly stream: Writable;
  private readonly context: Record<string, unknown>;
  private readonly minimum: number;
  private readonly clock: () => Date;

  constructor(options: JsonLoggerOptions = {}) {
    this.stream = options.stream ?? process.stderr;
    this.context = { ...(options.context ?? {}) };
    this.minimum = LEVEL_WEIGHT[options.minLevel ?? "info"];
    this.clock = options.clock ?? (() => new Date());
  }

  child(fields: Record<string, unknown>): JsonLogger {
    return new JsonLogger({
      stream: this.stream,
      minLevel: this.levelForWeight(this.minimum),
      clock: this.clock,
      context: { ...this.context, ...redact(fields) as Record<string, unknown> },
    });
  }

  log(level: LogLevel, event: string, fields: Record<string, unknown> = {}): void {
    if (LEVEL_WEIGHT[level] < this.minimum) return;
    const safeFields = redact(fields) as Record<string, unknown>;
    const record = {
      ...safeFields,
      ...this.context,
      timestamp: this.clock().toISOString(),
      level,
      event,
    };
    try {
      this.stream.write(`${JSON.stringify(record)}\n`);
    } catch {
      // Logging must not crash a worker if its output stream is closed.
    }
  }

  debug(event: string, fields?: Record<string, unknown>): void { this.log("debug", event, fields); }
  info(event: string, fields?: Record<string, unknown>): void { this.log("info", event, fields); }
  warn(event: string, fields?: Record<string, unknown>): void { this.log("warn", event, fields); }
  error(event: string, fields?: Record<string, unknown>): void { this.log("error", event, fields); }

  private levelForWeight(weight: number): LogLevel {
    if (weight <= LEVEL_WEIGHT.debug) return "debug";
    if (weight <= LEVEL_WEIGHT.info) return "info";
    if (weight <= LEVEL_WEIGHT.warn) return "warn";
    return "error";
  }
}

export function createJsonLogger(options: JsonLoggerOptions = {}): JsonLogger {
  return new JsonLogger(options);
}

