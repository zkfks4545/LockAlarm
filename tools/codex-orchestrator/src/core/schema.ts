/**
 * Shared data contracts for the Codex orchestrator.
 *
 * These types intentionally do not depend on a runtime schema package.  The
 * orchestrator is normally launched in a clean checkout and should be able to
 * validate Sol's plan before any worker is started, even when optional npm
 * dependencies are not installed.
 */

export type TaskDifficulty = "easy" | "medium" | "hard";

export type TaskStatus =
  | "pending"
  | "running"
  | "succeeded"
  | "failed"
  | "skipped"
  | "deduplicated";

export interface TaskSpec {
  /** Stable identifier within a plan. */
  id: string;
  /** The one-sentence objective sent to a worker. */
  goal: string;
  /** IDs that must complete successfully before this task can run. */
  prerequisites: string[];
  /** Files a worker may inspect. Empty means the caller did not restrict reads. */
  readFiles: string[];
  /** Files a worker may modify. An empty list means the task is read-only. */
  writeFiles: string[];
  /** Human-readable, checkable completion conditions. */
  completionCriteria: string[];
  /** Sol's estimate used to choose an inference level. */
  difficulty: TaskDifficulty;
  /** Optional structured input used by the worker and for de-duplication. */
  input?: unknown;
  /** Non-sensitive extension data from the planner. */
  metadata?: Record<string, unknown>;
  /** Maximum total attempts. Values are capped at three (two retries). */
  maxAttempts?: number;
}

export interface OrchestrationPlan {
  goal: string;
  tasks: TaskSpec[];
  rationale?: string;
}

export interface TestResult {
  command: string;
  passed: boolean;
  output?: string;
  durationMs?: number;
}

export interface CommitInfo {
  hash?: string;
  branch?: string;
  patchPath?: string;
}

/** Canonical result shape returned by every Luna worker attempt. */
export interface WorkerResult {
  taskId: string;
  status: TaskStatus;
  summary: string;
  changedFiles: string[];
  tests: TestResult[];
  completionCriteriaMet: boolean;
  risks: string[];
  commit?: CommitInfo;
  patch?: string;
  failureReason?: string;
  /** Workers may mark a failure non-retryable (for example invalid input). */
  retryable?: boolean;
  attempts?: number;
  deduplicatedFrom?: string;
  /** Isolated workspace identity and path for inspection/apply reporting. */
  workspaceId?: string;
  workspacePath?: string;
}

export interface TaskExecutionContext {
  task: TaskSpec;
  attempt: number;
  signal: AbortSignal;
  /** Optional child logger or workspace handle supplied by the integrator. */
  logger?: unknown;
  workspace?: unknown;
}

export type TaskExecutor = (
  task: TaskSpec,
  context: TaskExecutionContext,
) => Promise<WorkerResult>;

/** A JSON-Schema-compatible description for Sol's structured plan response. */
export const TASK_JSON_SCHEMA = {
  type: "object",
  additionalProperties: false,
  required: [
    "id",
    "goal",
    "prerequisites",
    "readFiles",
    "writeFiles",
    "completionCriteria",
    "difficulty",
  ],
  properties: {
    id: { type: "string", minLength: 1 },
    goal: { type: "string", minLength: 1 },
    prerequisites: { type: "array", items: { type: "string" } },
    readFiles: { type: "array", items: { type: "string" } },
    writeFiles: { type: "array", items: { type: "string" } },
    completionCriteria: {
      type: "array",
      minItems: 1,
      items: { type: "string", minLength: 1 },
    },
    difficulty: { type: "string", enum: ["easy", "medium", "hard"] },
    input: {},
    metadata: { type: "object" },
    maxAttempts: { type: "integer", minimum: 1, maximum: 3 },
  },
} as const;

export const PLAN_JSON_SCHEMA = {
  type: "object",
  additionalProperties: false,
  required: ["goal", "tasks"],
  properties: {
    goal: { type: "string", minLength: 1 },
    rationale: { type: "string" },
    tasks: {
      type: "array",
      minItems: 1,
      items: TASK_JSON_SCHEMA,
    },
  },
} as const;

export class SchemaValidationError extends Error {
  readonly issues: string[];

  constructor(message: string, issues: string[] = []) {
    super(message);
    this.name = "SchemaValidationError";
    this.issues = issues;
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function requireString(value: unknown, path: string, issues: string[], nonEmpty = true): value is string {
  if (typeof value !== "string" || (nonEmpty && value.trim().length === 0)) {
    issues.push(`${path} must be a non-empty string`);
    return false;
  }
  return true;
}

function requireStringArray(value: unknown, path: string, issues: string[], minItems = 0): value is string[] {
  if (!Array.isArray(value)) {
    issues.push(`${path} must be an array`);
    return false;
  }
  if (value.length < minItems) {
    issues.push(`${path} must contain at least ${minItems} item(s)`);
  }
  value.forEach((entry, index) => requireString(entry, `${path}[${index}]`, issues));
  return value.every((entry) => typeof entry === "string");
}

/** Validate and return a normalized task. Throws on malformed planner output. */
export function parseTask(value: unknown, path = "task"): TaskSpec {
  const issues: string[] = [];
  if (!isRecord(value)) {
    throw new SchemaValidationError(`${path} must be an object`, [`${path} must be an object`]);
  }

  const allowedKeys = new Set([
    "id",
    "goal",
    "prerequisites",
    "readFiles",
    "writeFiles",
    "completionCriteria",
    "difficulty",
    "input",
    "metadata",
    "maxAttempts",
  ]);
  Object.keys(value).forEach((key) => {
    if (!allowedKeys.has(key)) issues.push(`${path}.${key} is not allowed by the task schema`);
  });

  const idOk = requireString(value.id, `${path}.id`, issues);
  const goalOk = requireString(value.goal, `${path}.goal`, issues);
  const prerequisitesOk = requireStringArray(value.prerequisites, `${path}.prerequisites`, issues);
  const readFilesOk = requireStringArray(value.readFiles, `${path}.readFiles`, issues);
  const writeFilesOk = requireStringArray(value.writeFiles, `${path}.writeFiles`, issues);
  const criteriaOk = requireStringArray(value.completionCriteria, `${path}.completionCriteria`, issues, 1);

  if (value.difficulty !== "easy" && value.difficulty !== "medium" && value.difficulty !== "hard") {
    issues.push(`${path}.difficulty must be one of easy, medium, hard`);
  }
  if (value.metadata !== undefined && !isRecord(value.metadata)) {
    issues.push(`${path}.metadata must be an object when provided`);
  }
  if (value.maxAttempts !== undefined &&
      (typeof value.maxAttempts !== "number" ||
       !Number.isInteger(value.maxAttempts) ||
       value.maxAttempts < 1 ||
       value.maxAttempts > 3)) {
    issues.push(`${path}.maxAttempts must be an integer from 1 to 3`);
  }

  if (issues.length > 0 || !idOk || !goalOk || !prerequisitesOk || !readFilesOk || !writeFilesOk || !criteriaOk) {
    throw new SchemaValidationError(`Invalid ${path}`, issues);
  }

  const task: TaskSpec = {
    id: value.id as string,
    goal: value.goal as string,
    prerequisites: [...(value.prerequisites as string[])],
    readFiles: [...(value.readFiles as string[])],
    writeFiles: [...(value.writeFiles as string[])],
    completionCriteria: [...(value.completionCriteria as string[])],
    difficulty: value.difficulty as TaskDifficulty,
  };
  if (value.input !== undefined) task.input = value.input;
  if (value.metadata !== undefined) task.metadata = { ...(value.metadata as Record<string, unknown>) };
  if (value.maxAttempts !== undefined) task.maxAttempts = value.maxAttempts as number;
  return task;
}

/** Validate and normalize a complete planner response. */
export function parsePlan(value: unknown): OrchestrationPlan {
  const issues: string[] = [];
  if (!isRecord(value)) {
    throw new SchemaValidationError("Plan must be an object", ["plan must be an object"]);
  }
  const allowedPlanKeys = new Set(["goal", "tasks", "rationale"]);
  Object.keys(value).forEach((key) => {
    if (!allowedPlanKeys.has(key)) issues.push(`plan.${key} is not allowed by the plan schema`);
  });
  const goalOk = requireString(value.goal, "plan.goal", issues);
  if (!Array.isArray(value.tasks)) {
    issues.push("plan.tasks must be an array");
  } else if (value.tasks.length < 1) {
    issues.push("plan.tasks must contain at least 1 item");
  }
  if (value.rationale !== undefined && typeof value.rationale !== "string") {
    issues.push("plan.rationale must be a string when provided");
  }

  const tasks: TaskSpec[] = [];
  if (Array.isArray(value.tasks)) {
    value.tasks.forEach((entry, index) => {
      try {
        tasks.push(parseTask(entry, `plan.tasks[${index}]`));
      } catch (error) {
        if (error instanceof SchemaValidationError) issues.push(...error.issues);
        else issues.push(`plan.tasks[${index}] is invalid`);
      }
    });
  }

  const seen = new Set<string>();
  tasks.forEach((task) => {
    if (seen.has(task.id)) issues.push(`duplicate task id: ${task.id}`);
    seen.add(task.id);
  });
  const ids = new Set(tasks.map((task) => task.id));
  tasks.forEach((task) => {
    task.prerequisites.forEach((dependency) => {
      if (!ids.has(dependency)) issues.push(`task ${task.id} references unknown prerequisite ${dependency}`);
    });
  });
  if (issues.length > 0 || !goalOk) throw new SchemaValidationError("Invalid plan", issues);

  return {
    goal: value.goal as string,
    tasks,
    ...(typeof value.rationale === "string" ? { rationale: value.rationale } : {}),
  };
}

/** Stable, JSON-compatible serialization used to detect duplicate task input. */
export function stableStringify(value: unknown): string {
  const seen = new WeakSet<object>();
  const canonicalize = (entry: unknown): unknown => {
    if (entry === null || typeof entry !== "object") {
      if (typeof entry === "number" && !Number.isFinite(entry)) return String(entry);
      if (typeof entry === "bigint") return `${entry.toString()}n`;
      return entry;
    }
    if (seen.has(entry as object)) throw new TypeError("Cannot serialize a cyclic task input");
    seen.add(entry as object);
    if (Array.isArray(entry)) {
      const result = entry.map(canonicalize);
      seen.delete(entry as object);
      return result;
    }
    if (entry instanceof Date) {
      seen.delete(entry as object);
      return entry.toISOString();
    }
    const record = entry as Record<string, unknown>;
    const result: Record<string, unknown> = {};
    Object.keys(record).sort().forEach((key) => {
      result[key] = canonicalize(record[key]);
    });
    seen.delete(entry as object);
    return result;
  };
  return JSON.stringify(canonicalize(value));
}

/** Hash-independent fingerprint; stable across processes and platforms. */
export function taskFingerprint(task: TaskSpec): string {
  const input = {
    goal: task.goal,
    prerequisites: [...task.prerequisites].sort(),
    readFiles: [...task.readFiles].sort(),
    writeFiles: [...task.writeFiles].sort(),
    completionCriteria: [...task.completionCriteria],
    difficulty: task.difficulty,
    input: task.input,
    metadata: task.metadata,
  };
  return stableStringify(input);
}
