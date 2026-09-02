import path from "node:path";

export type LogLevel = "debug" | "info" | "warn" | "error";

export interface OrchestratorConfig {
  cwd: string;
  workers: number;
  maxRetries: number;
  timeoutMs: number;
  apply: boolean;
  mock: boolean;
  codexBin: string;
  logLevel: LogLevel;
}

function positiveInt(value: string | undefined, fallback: number, name: string): number {
  if (value === undefined || value.trim() === "") return fallback;
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 1) {
    throw new Error(`${name} must be a positive integer`);
  }
  return parsed;
}

function nonNegativeInt(value: string | undefined, fallback: number, name: string): number {
  if (value === undefined || value.trim() === "") return fallback;
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 0) {
    throw new Error(`${name} must be a non-negative integer`);
  }
  return parsed;
}

export function loadConfig(options: {
  cwd?: string;
  workers?: string;
  maxRetries?: string;
  timeoutMs?: string;
  apply?: boolean;
  mock?: boolean;
  codexBin?: string;
  logLevel?: string;
} = {}): OrchestratorConfig {
  const env = process.env;
  const cwd = path.resolve(options.cwd ?? process.cwd());
  const workers = Math.min(
    positiveInt(options.workers ?? env.CODEX_ORCHESTRATOR_WORKERS, 4, "--workers"),
    4,
  );
  const maxRetries = Math.min(
    nonNegativeInt(options.maxRetries ?? env.CODEX_ORCHESTRATOR_MAX_RETRIES, 2, "--max-retries"),
    2,
  );
  const timeoutMs = positiveInt(options.timeoutMs ?? env.CODEX_ORCHESTRATOR_TIMEOUT_MS, 120_000, "--timeout-ms");
  const logLevel = (options.logLevel ?? env.CODEX_ORCHESTRATOR_LOG_LEVEL ?? "info") as LogLevel;
  if (!("debug info warn error".split(" ").includes(logLevel))) {
    throw new Error(`--log-level must be one of debug, info, warn, error`);
  }
  return {
    cwd,
    workers,
    maxRetries,
    timeoutMs,
    apply: options.apply ?? false,
    mock: options.mock ?? false,
    codexBin: options.codexBin ?? env.CODEX_BIN ?? "codex",
    logLevel,
  };
}

