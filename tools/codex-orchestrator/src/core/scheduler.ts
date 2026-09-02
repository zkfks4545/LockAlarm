import path from "node:path";

import {
  parseTask,
  stableStringify,
  taskFingerprint,
  type TaskExecutionContext,
  type TaskExecutor,
  type TaskSpec,
  type TaskStatus,
  type WorkerResult,
} from "./schema.js";
import type { LoggerLike } from "./logger.js";

export interface RetryPolicy {
  /** Total attempts, including the first call. Always capped at three. */
  maxAttempts?: number;
  /** Optional delay between attempts. */
  backoffMs?: number | ((attempt: number, error?: unknown) => number);
  /** Return false to stop retrying a failed result. */
  shouldRetry?: (result: WorkerResult, attempt: number) => boolean;
}

export interface SchedulerOptions {
  /** Maximum number of independent workers. Defaults to four. */
  workers?: number;
  retry?: RetryPolicy;
  signal?: AbortSignal;
  logger?: LoggerLike;
  /** Additional context (for example a prepared workspace) for each attempt. */
  contextFactory?: (
    task: TaskSpec,
    attempt: number,
  ) => Promise<Partial<TaskExecutionContext>> | Partial<TaskExecutionContext>;
  /** Disable task fingerprint de-duplication only when explicitly needed. */
  deduplicate?: boolean;
}

export interface SchedulerTaskState {
  taskId: string;
  status: TaskStatus;
  attempts: number;
  result?: WorkerResult;
}

export interface SchedulerRunResult {
  results: Record<string, WorkerResult>;
  states: Record<string, SchedulerTaskState>;
  orderedResults: WorkerResult[];
  deduplicated: Record<string, string>;
}

interface RunningTask {
  task: TaskSpec;
  busyFiles: string[];
  promise: Promise<WorkerResult>;
}

const TERMINAL_SUCCESS: ReadonlySet<TaskStatus> = new Set(["succeeded"]);
const TERMINAL_FAILURE: ReadonlySet<TaskStatus> = new Set(["failed", "skipped"]);

function clampAttempts(value: number | undefined, fallback: number): number {
  const candidate = Number.isFinite(value) ? Math.trunc(value as number) : fallback;
  return Math.max(1, Math.min(3, candidate));
}

function errorMessage(error: unknown): string {
  if (error instanceof Error) return error.message;
  if (typeof error === "string") return error;
  try {
    return JSON.stringify(error);
  } catch {
    return String(error);
  }
}

function normalizeFailure(task: TaskSpec, reason: string, attempts = 1): WorkerResult {
  return {
    taskId: task.id,
    status: "failed",
    summary: "Worker failed before producing a result",
    changedFiles: [],
    tests: [],
    completionCriteriaMet: false,
    risks: [],
    failureReason: reason,
    attempts,
  };
}

function normalizeResult(task: TaskSpec, value: WorkerResult, attempts: number): WorkerResult {
  const terminal: TaskStatus[] = ["succeeded", "failed", "skipped", "deduplicated"];
  // Public integrations sometimes call the success state "completed";
  // normalize that spelling at the boundary while keeping the core contract
  // intentionally small.
  const incomingStatus = value.status as string;
  const status = incomingStatus === "completed"
    ? "succeeded"
    : terminal.includes(value.status) ? value.status : "failed";
  const invalidStatus = status === "failed" && !terminal.includes(value.status) && incomingStatus !== "completed";
  return {
    taskId: task.id,
    status,
    summary: typeof value.summary === "string" ? value.summary : "",
    changedFiles: Array.isArray(value.changedFiles) ? value.changedFiles.map(String) : [],
    tests: Array.isArray(value.tests) ? value.tests : [],
    completionCriteriaMet: value.completionCriteriaMet === true,
    risks: Array.isArray(value.risks) ? value.risks.map(String) : [],
    ...(value.commit ? { commit: value.commit } : {}),
    ...(typeof value.patch === "string" ? { patch: value.patch } : {}),
    ...(value.failureReason ? { failureReason: value.failureReason } : {}),
    ...(value.workspaceId ? { workspaceId: value.workspaceId } : {}),
    ...(value.workspacePath ? { workspacePath: value.workspacePath } : {}),
    ...(invalidStatus ? { failureReason: `Worker returned unsupported status: ${String(value.status)}` } : {}),
    ...(value.retryable !== undefined ? { retryable: value.retryable } : {}),
    attempts,
  };
}

function normalizeConflictPath(value: string): string {
  // Worktree paths can be reported with either separator on Windows. Treat
  // relative and absolute spellings of the same lexical path consistently.
  const replaced = value.replace(/\\/g, "/");
  const normalized = path.posix.normalize(replaced).replace(/^\.\//, "");
  return process.platform === "win32" ? normalized.toLowerCase() : normalized;
}

function filesConflict(taskFiles: string[], busyFiles: Set<string>): boolean {
  return taskFiles.some((file) => busyFiles.has(file));
}

function sleep(ms: number, signal: AbortSignal): Promise<void> {
  if (ms <= 0) return Promise.resolve();
  return new Promise((resolve, reject) => {
    if (signal.aborted) {
      reject(new Error("Scheduler aborted"));
      return;
    }
    const timer = setTimeout(resolve, ms);
    const onAbort = () => {
      clearTimeout(timer);
      reject(new Error("Scheduler aborted"));
    };
    signal.addEventListener("abort", onAbort, { once: true });
    // Avoid retaining the listener after a normal timeout.
    setTimeout(() => signal.removeEventListener("abort", onAbort), ms);
  });
}

/**
 * Dependency-aware scheduler for Sol's plan and Luna workers.
 *
 * A task only becomes runnable after all prerequisites succeeded. Tasks with
 * overlapping write files are serialized even when workers are otherwise
 * available. Retries are bounded to two retries, and identical task inputs are
 * executed once with alias results for duplicate IDs.
 */
export class DagScheduler {
  private readonly defaultOptions: SchedulerOptions;

  constructor(options: SchedulerOptions = {}) {
    this.defaultOptions = options;
  }

  /** Alias for callers that describe a run as scheduling a plan. */
  schedule(tasks: readonly TaskSpec[], executor: TaskExecutor, options: SchedulerOptions = {}): Promise<SchedulerRunResult> {
    return this.run(tasks, executor, options);
  }

  async run(tasks: readonly TaskSpec[], executor: TaskExecutor, options: SchedulerOptions = {}): Promise<SchedulerRunResult> {
    const config: SchedulerOptions = {
      ...this.defaultOptions,
      ...options,
      retry: { ...(this.defaultOptions.retry ?? {}), ...(options.retry ?? {}) },
    };
    const normalized = tasks.map((task, index) => parseTask(task, `tasks[${index}]`));
    const duplicateMap = new Map<string, string>();
    const canonicalByFingerprint = new Map<string, TaskSpec>();
    const canonicalTasks: TaskSpec[] = [];
    const byId = new Map<string, TaskSpec>();

    normalized.forEach((task) => {
      if (byId.has(task.id)) throw new Error(`Duplicate task id: ${task.id}`);
      byId.set(task.id, task);
      if (config.deduplicate === false) {
        canonicalTasks.push(task);
        return;
      }
      let fingerprint: string;
      try {
        // Include retry configuration in the fingerprint so two otherwise
        // equal tasks with intentionally different budgets are not collapsed.
        fingerprint = stableStringify({ task: taskFingerprint(task), maxAttempts: task.maxAttempts });
      } catch (error) {
        throw new Error(`Cannot fingerprint task ${task.id}: ${errorMessage(error)}`);
      }
      const canonical = canonicalByFingerprint.get(fingerprint);
      if (canonical) {
        duplicateMap.set(task.id, canonical.id);
      } else {
        canonicalByFingerprint.set(fingerprint, task);
        canonicalTasks.push(task);
      }
    });

    const canonicalIds = new Set(canonicalTasks.map((task) => task.id));
    const canonicalDependencies = new Map<string, string[]>();
    canonicalTasks.forEach((task) => {
      const dependencies = task.prerequisites.map((dependency) => duplicateMap.get(dependency) ?? dependency);
      const unique = [...new Set(dependencies)];
      unique.forEach((dependency) => {
        if (!canonicalIds.has(dependency)) {
          throw new Error(`Task ${task.id} references unknown prerequisite ${dependency}`);
        }
      });
      canonicalDependencies.set(task.id, unique);
    });
    this.assertAcyclic(canonicalTasks, canonicalDependencies);

    const signal = config.signal ?? new AbortController().signal;
    const workers = Math.max(1, Math.min(64, Math.trunc(config.workers ?? 4)));
    const pending = new Set(canonicalTasks.map((task) => task.id));
    const states = new Map<string, SchedulerTaskState>();
    const results = new Map<string, WorkerResult>();
    const running = new Map<string, RunningTask>();
    const busyFiles = new Set<string>();
    canonicalTasks.forEach((task) => states.set(task.id, { taskId: task.id, status: "pending", attempts: 0 }));

    const markSkippedDependencies = (): void => {
      for (const task of canonicalTasks) {
        if (!pending.has(task.id)) continue;
        const dependencies = canonicalDependencies.get(task.id) ?? [];
        if (dependencies.some((dependency) => TERMINAL_FAILURE.has(states.get(dependency)?.status as TaskStatus))) {
          const failed = dependencies.find((dependency) => TERMINAL_FAILURE.has(states.get(dependency)?.status as TaskStatus));
          const result: WorkerResult = {
            taskId: task.id,
            status: "skipped",
            summary: "Skipped because a prerequisite failed",
            changedFiles: [],
            tests: [],
            completionCriteriaMet: false,
            risks: [],
            failureReason: failed ? `Prerequisite ${failed} did not succeed` : "Prerequisite did not succeed",
            attempts: 0,
          };
          pending.delete(task.id);
          results.set(task.id, result);
          states.set(task.id, { taskId: task.id, status: "skipped", attempts: 0, result });
          config.logger?.warn("task_skipped", { taskId: task.id, failureReason: result.failureReason });
        }
      }
    };

    const launch = (task: TaskSpec): void => {
      const files = [...new Set(task.writeFiles.map(normalizeConflictPath))];
      files.forEach((file) => busyFiles.add(file));
      pending.delete(task.id);
      states.set(task.id, { taskId: task.id, status: "running", attempts: 0 });
      const promise = this.executeWithRetry(task, executor, config, signal)
        .catch((error) => normalizeFailure(task, errorMessage(error)))
        .finally(() => files.forEach((file) => busyFiles.delete(file)));
      running.set(task.id, { task, busyFiles: files, promise });
      config.logger?.info("task_started", { taskId: task.id, writeFiles: task.writeFiles });
    };

    while (pending.size > 0 || running.size > 0) {
      if (signal.aborted) {
        for (const task of canonicalTasks) {
          if (!pending.has(task.id)) continue;
          const result = normalizeFailure(task, "Scheduler aborted", 0);
          result.status = "skipped";
          pending.delete(task.id);
          results.set(task.id, result);
          states.set(task.id, { taskId: task.id, status: "skipped", attempts: 0, result });
        }
      }
      markSkippedDependencies();

      const ready = canonicalTasks.filter((task) => {
        if (!pending.has(task.id)) return false;
        const dependencies = canonicalDependencies.get(task.id) ?? [];
        return dependencies.every((dependency) => TERMINAL_SUCCESS.has(states.get(dependency)?.status as TaskStatus));
      });

      for (const task of ready) {
        if (running.size >= workers) break;
        const files = [...new Set(task.writeFiles.map(normalizeConflictPath))];
        if (filesConflict(files, busyFiles)) continue;
        launch(task);
      }

      if (running.size === 0) {
        if (pending.size === 0) break;
        const blocked = [...pending].join(", ");
        throw new Error(`Scheduler made no progress; unresolved dependencies for: ${blocked}`);
      }

      const settled = await Promise.race(
        [...running.entries()].map(async ([taskId, entry]) => ({ taskId, result: await entry.promise })),
      );
      running.delete(settled.taskId);
      const result = settled.result;
      results.set(settled.taskId, result);
      states.set(settled.taskId, {
        taskId: settled.taskId,
        status: result.status,
        attempts: result.attempts ?? 0,
        result,
      });
      config.logger?.info("task_finished", {
        taskId: settled.taskId,
        status: result.status,
        attempts: result.attempts ?? 0,
      });
    }

    // Add deterministic alias results after canonical execution has settled.
    for (const [aliasId, canonicalId] of duplicateMap) {
      const canonical = results.get(canonicalId);
      if (!canonical) continue;
      const alias: WorkerResult = {
        ...canonical,
        taskId: aliasId,
        status: "deduplicated",
        summary: `Deduplicated; reused result from ${canonicalId}`,
        deduplicatedFrom: canonicalId,
        attempts: 0,
      };
      results.set(aliasId, alias);
      states.set(aliasId, { taskId: aliasId, status: "deduplicated", attempts: 0, result: alias });
    }

    const orderedResults = normalized.map((task) => results.get(task.id) as WorkerResult);
    const stateRecord: Record<string, SchedulerTaskState> = {};
    const resultRecord: Record<string, WorkerResult> = {};
    states.forEach((state, id) => { stateRecord[id] = state; });
    results.forEach((result, id) => { resultRecord[id] = result; });
    const deduplicated: Record<string, string> = {};
    duplicateMap.forEach((canonicalId, aliasId) => { deduplicated[aliasId] = canonicalId; });
    return { results: resultRecord, states: stateRecord, orderedResults, deduplicated };
  }

  private assertAcyclic(tasks: readonly TaskSpec[], dependencies: Map<string, string[]>): void {
    const visiting = new Set<string>();
    const visited = new Set<string>();
    const visit = (id: string): void => {
      if (visiting.has(id)) throw new Error(`Task dependency cycle detected at ${id}`);
      if (visited.has(id)) return;
      visiting.add(id);
      (dependencies.get(id) ?? []).forEach(visit);
      visiting.delete(id);
      visited.add(id);
    };
    tasks.forEach((task) => visit(task.id));
  }

  private async executeWithRetry(
    task: TaskSpec,
    executor: TaskExecutor,
    config: SchedulerOptions,
    signal: AbortSignal,
  ): Promise<WorkerResult> {
    // A per-run retry budget (when provided) is authoritative; otherwise use
    // the task's budget and finally the two-retry default. This lets a review
    // round intentionally run a task once while retaining task metadata for
    // normal runs.
    const maxAttempts = clampAttempts(config.retry?.maxAttempts ?? task.maxAttempts, 3);
    let lastResult: WorkerResult | undefined;
    for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
      if (signal.aborted) return normalizeFailure(task, "Scheduler aborted", attempt - 1);
      const childLogger = config.logger?.child({ taskId: task.id, attempt });
      childLogger?.debug("task_attempt", { taskId: task.id, attempt, maxAttempts });
      try {
        const extra = config.contextFactory ? await config.contextFactory(task, attempt) : {};
        const context: TaskExecutionContext = {
          ...extra,
          // Scheduler-owned identity cannot be overridden by an integrator's
          // context factory; this keeps retry accounting and dependency logs
          // truthful even when custom context includes an `attempt` key.
          task,
          attempt,
          signal,
          logger: extra.logger ?? childLogger,
        };
        const value = await executor(task, context);
        lastResult = normalizeResult(task, value, attempt);
      } catch (error) {
        lastResult = normalizeFailure(task, errorMessage(error), attempt);
      }

      if (lastResult.status !== "failed") return lastResult;
      const shouldRetry = config.retry?.shouldRetry
        ? config.retry.shouldRetry(lastResult, attempt)
        : lastResult.retryable !== false;
      if (!shouldRetry || attempt >= maxAttempts) return lastResult;
      const configuredDelay = config.retry?.backoffMs;
      const delay = typeof configuredDelay === "function"
        ? configuredDelay(attempt, lastResult.failureReason)
        : (configuredDelay ?? 0);
      if (delay > 0) await sleep(Math.min(delay, 60_000), signal);
      childLogger?.warn("task_retry", { taskId: task.id, attempt, nextAttempt: attempt + 1 });
    }
    return lastResult ?? normalizeFailure(task, "Worker did not produce a result");
  }
}
