import type { OrchestratorPlan, OrchestratorTask, Difficulty } from "./shared-types.js";

export const PLAN_JSON_SCHEMA = {
  $schema: "https://json-schema.org/draft/2020-12/schema",
  type: "object",
  additionalProperties: false,
  required: ["goal", "tasks", "globalCompletionCriteria", "risks"],
  properties: {
    goal: { type: "string", minLength: 1 },
    globalCompletionCriteria: { type: "array", items: { type: "string" } },
    risks: { type: "array", items: { type: "string" } },
    tasks: {
      type: "array",
      minItems: 1,
      items: {
        type: "object",
        additionalProperties: false,
        required: ["id", "goal", "dependencies", "readFiles", "writableFiles", "completionCriteria", "difficulty"],
        properties: {
          id: { type: "string", pattern: "^[A-Za-z0-9][A-Za-z0-9._-]*$" },
          goal: { type: "string", minLength: 1 },
          dependencies: { type: "array", items: { type: "string" } },
          readFiles: { type: "array", items: { type: "string" } },
          writableFiles: { type: "array", items: { type: "string" } },
          completionCriteria: { type: "array", minItems: 1, items: { type: "string" } },
          difficulty: { enum: ["easy", "medium", "hard"] },
        },
      },
    },
  },
} as const;

export class PlanValidationError extends Error {
  constructor(public readonly issues: string[]) {
    super(`Sol plan failed JSON Schema validation: ${issues.join("; ")}`);
    this.name = "PlanValidationError";
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function stringArray(value: unknown, path: string, issues: string[], minItems = 0): value is string[] {
  if (!Array.isArray(value) || value.some((item) => typeof item !== "string")) {
    issues.push(`${path} must be an array of strings`);
    return false;
  }
  if (value.length < minItems) issues.push(`${path} must contain at least ${minItems} item(s)`);
  return true;
}

function exactKeys(value: Record<string, unknown>, keys: readonly string[], path: string, issues: string[]) {
  for (const key of Object.keys(value)) {
    if (!keys.includes(key)) issues.push(`${path}.${key} is not allowed by the plan schema`);
  }
}

function validateRelativeFiles(files: string[], field: string, issues: string[]): void {
  files.forEach((file, index) => {
    const normalized = file.replace(/\\/g, "/");
    if (normalized.trim() === "" || normalized.startsWith("/") || /^[A-Za-z]:\//.test(normalized) || normalized === ".." || normalized.startsWith("../") || normalized.includes("/../")) {
      issues.push(`${field}[${index}] must be repository-relative and cannot escape the workspace`);
    }
  });
}

export function validatePlan(value: unknown): OrchestratorPlan {
  const issues: string[] = [];
  if (!isRecord(value)) throw new PlanValidationError(["root must be an object"]);
  exactKeys(value, ["goal", "tasks", "globalCompletionCriteria", "risks"], "root", issues);
  if (typeof value.goal !== "string" || value.goal.trim() === "") issues.push("goal must be a non-empty string");
  stringArray(value.globalCompletionCriteria, "globalCompletionCriteria", issues);
  stringArray(value.risks, "risks", issues);
  if (!Array.isArray(value.tasks) || value.tasks.length < 1) {
    issues.push("tasks must contain at least one task");
  }
  const tasks: OrchestratorTask[] = [];
  const ids = new Set<string>();
  if (Array.isArray(value.tasks)) {
    value.tasks.forEach((raw, index) => {
      const p = `tasks[${index}]`;
      if (!isRecord(raw)) {
        issues.push(`${p} must be an object`);
        return;
      }
      exactKeys(raw, ["id", "goal", "dependencies", "readFiles", "writableFiles", "completionCriteria", "difficulty"], p, issues);
      const id = raw.id;
      if (typeof id !== "string" || !/^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(id)) issues.push(`${p}.id is invalid`);
      else if (ids.has(id)) issues.push(`duplicate task id: ${id}`);
      else ids.add(id);
      if (typeof raw.goal !== "string" || raw.goal.trim() === "") issues.push(`${p}.goal must be a non-empty string`);
      const dependencies = stringArray(raw.dependencies, `${p}.dependencies`, issues) ? raw.dependencies : [];
      const readFiles = stringArray(raw.readFiles, `${p}.readFiles`, issues) ? raw.readFiles : [];
      const writableFiles = stringArray(raw.writableFiles, `${p}.writableFiles`, issues) ? raw.writableFiles : [];
      const completionCriteria = stringArray(raw.completionCriteria, `${p}.completionCriteria`, issues, 1) ? raw.completionCriteria : [];
      validateRelativeFiles(readFiles, `${p}.readFiles`, issues);
      validateRelativeFiles(writableFiles, `${p}.writableFiles`, issues);
      if (raw.difficulty !== "easy" && raw.difficulty !== "medium" && raw.difficulty !== "hard") issues.push(`${p}.difficulty must be easy, medium, or hard`);
      if (typeof id === "string" && typeof raw.goal === "string") {
        tasks.push({
          id,
          goal: raw.goal,
          dependencies,
          readFiles,
          writableFiles,
          completionCriteria,
          difficulty: (raw.difficulty as Difficulty) ?? "medium",
        });
      }
    });
  }
  for (const task of tasks) {
    for (const dependency of task.dependencies) {
      if (!ids.has(dependency)) issues.push(`task ${task.id} depends on unknown task ${dependency}`);
      if (dependency === task.id) issues.push(`task ${task.id} cannot depend on itself`);
    }
  }
  if (issues.length > 0) throw new PlanValidationError(issues);
  return {
    goal: value.goal as string,
    tasks,
    globalCompletionCriteria: value.globalCompletionCriteria as string[],
    risks: value.risks as string[],
  };
}

export function extractJsonObject(text: string): unknown {
  const fenced = text.match(/```(?:json)?\s*([\s\S]*?)\s*```/i);
  const candidate = fenced?.[1] ?? text;
  try {
    return JSON.parse(candidate);
  } catch {
    const start = candidate.indexOf("{");
    const end = candidate.lastIndexOf("}");
    if (start >= 0 && end > start) return JSON.parse(candidate.slice(start, end + 1));
    throw new Error("Sol response did not contain a JSON object");
  }
}

export function parsePlan(text: string): OrchestratorPlan {
  return validatePlan(extractJsonObject(text));
}

export function planPrompt(goal: string, cwd: string): string {
  return [
    "You are the Sol planning lead in a Codex App Server multi-agent run.",
    "Analyze the goal, inspect the repository as needed, and return ONLY one JSON object matching the supplied schema.",
    "Do not make edits during planning. Split independent work into separate tasks.",
    "Every task must include id, goal, dependencies, readFiles, writableFiles, completionCriteria, and difficulty.",
    "Never grant a task write access outside its writableFiles. Use repository-relative paths.",
    `Repository: ${cwd}`,
    `Goal: ${goal}`,
    "Schema:",
    JSON.stringify(PLAN_JSON_SCHEMA),
  ].join("\n");
}

export const REVIEW_JSON_SCHEMA = {
  $schema: "https://json-schema.org/draft/2020-12/schema",
  type: "object",
  additionalProperties: false,
  required: ["approved", "summary", "taskFindings", "risks", "retryTaskIds"],
  properties: {
    approved: { type: "boolean" },
    summary: { type: "string" },
    risks: { type: "array", items: { type: "string" } },
    retryTaskIds: { type: "array", items: { type: "string" } },
    taskFindings: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        required: ["taskId", "approved", "requiredChanges", "reason"],
        properties: {
          taskId: { type: "string" },
          approved: { type: "boolean" },
          requiredChanges: { type: "array", items: { type: "string" } },
          reason: { type: "string" },
        },
      },
    },
  },
} as const;

export function validateReview(value: unknown): import("./shared-types.js").ReviewResult {
  if (!isRecord(value)) throw new Error("Sol review must be an object");
  const issues: string[] = [];
  exactKeys(value, ["approved", "summary", "taskFindings", "risks", "retryTaskIds"], "review", issues);
  if (typeof value.approved !== "boolean") issues.push("review.approved must be boolean");
  if (typeof value.summary !== "string") issues.push("review.summary must be string");
  const risks = stringArray(value.risks, "review.risks", issues) ? value.risks : [];
  const retryTaskIds = stringArray(value.retryTaskIds, "review.retryTaskIds", issues) ? value.retryTaskIds : [];
  const taskFindings: import("./shared-types.js").ReviewResult["taskFindings"] = [];
  if (!Array.isArray(value.taskFindings)) issues.push("review.taskFindings must be an array");
  else {
    value.taskFindings.forEach((raw, index) => {
      if (!isRecord(raw)) {
        issues.push(`review.taskFindings[${index}] must be an object`);
        return;
      }
      exactKeys(raw, ["taskId", "approved", "requiredChanges", "reason"], `review.taskFindings[${index}]`, issues);
      if (typeof raw.taskId !== "string") issues.push(`review.taskFindings[${index}].taskId must be string`);
      if (typeof raw.approved !== "boolean") issues.push(`review.taskFindings[${index}].approved must be boolean`);
      const requiredChanges = stringArray(raw.requiredChanges, `review.taskFindings[${index}].requiredChanges`, issues) ? raw.requiredChanges : [];
      if (typeof raw.reason !== "string") issues.push(`review.taskFindings[${index}].reason must be string`);
      if (typeof raw.taskId === "string" && typeof raw.approved === "boolean" && typeof raw.reason === "string") {
        taskFindings.push({ taskId: raw.taskId, approved: raw.approved, requiredChanges, reason: raw.reason });
      }
    });
  }
  if (issues.length) throw new PlanValidationError(issues);
  return { approved: value.approved as boolean, summary: value.summary as string, taskFindings, risks, retryTaskIds };
}

export function parseReview(text: string): import("./shared-types.js").ReviewResult {
  return validateReview(extractJsonObject(text));
}

export function reviewPrompt(goal: string, plan: OrchestratorPlan, results: unknown[]): string {
  return [
    "You are the Sol review lead. Review every Luna worker result against the goal and completion criteria.",
    "Return ONLY one JSON object matching the review schema. Approve only evidence-backed completed work.",
    "Set retryTaskIds only for failed or incomplete tasks. Give concrete requiredChanges for every retry.",
    `Goal: ${goal}`,
    `Plan: ${JSON.stringify(plan)}`,
    `Worker results: ${JSON.stringify(results)}`,
    "Schema:",
    JSON.stringify(REVIEW_JSON_SCHEMA),
  ].join("\n");
}
