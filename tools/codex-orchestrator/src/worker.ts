import { writeFile, mkdir } from "node:fs/promises";
import path from "node:path";
import type { AppServerFacade, PreflightResult } from "./session.js";
import { extractJsonObject } from "./plan.js";
import type { OrchestratorTask } from "./shared-types.js";
import { extractText } from "./session.js";
import type { TaskExecutionContext, TaskSpec, WorkerResult } from "./core/schema.js";
import { WorkspaceManager, type WorkspaceHandle } from "./core/workspace.js";

function mapTask(task: OrchestratorTask): TaskSpec {
  return {
    id: task.id,
    goal: task.goal,
    prerequisites: task.dependencies,
    readFiles: task.readFiles,
    writeFiles: task.writableFiles,
    completionCriteria: task.completionCriteria,
    difficulty: task.difficulty,
    maxAttempts: 3,
  };
}

function normalizeStatus(value: unknown): WorkerResult["status"] {
  if (value === "succeeded" || value === "completed") return "succeeded";
  if (value === "skipped") return "skipped";
  if (value === "deduplicated") return "deduplicated";
  return "failed";
}

function normalizeRelative(value: string): string {
  return value.replace(/\\/g, "/").replace(/^\.\//, "");
}

function isAllowed(file: string, allowed: string[]): boolean {
  if (allowed.length === 0) return false;
  const normalized = normalizeRelative(file);
  return allowed.some((candidate) => {
    const expected = normalizeRelative(candidate);
    return normalized === expected || normalized.startsWith(`${expected.replace(/\/$/, "")}/`);
  });
}

function chooseHardReasoning(efforts: string[], fallback: string): string {
  return ["xhigh", "high", "medium", "low", "minimal", "none"].find((effort) => efforts.includes(effort)) ?? fallback;
}

function commitInfo(workspace: WorkspaceHandle): { branch?: string; patchPath: string } {
  return workspace.kind === "git-worktree" && workspace.branch
    ? { branch: workspace.branch, patchPath: workspace.id }
    : { patchPath: workspace.id };
}

export interface LunaWorkerOptions {
  facade: AppServerFacade;
  preflight: PreflightResult;
  workspaceManager: WorkspaceManager;
  mock?: boolean;
  keepWorkspaces?: boolean;
}

export class LunaWorker {
  constructor(private readonly options: LunaWorkerOptions) {}

  async execute(task: TaskSpec, context: TaskExecutionContext): Promise<WorkerResult> {
    const workspace = await this.options.workspaceManager.prepare(task);
    try {
      if (this.options.mock) return this.mockExecute(task, context, workspace);
      const reasoningEffort = task.difficulty === "hard"
        ? chooseHardReasoning(this.options.preflight.luna.reasoningEfforts, this.options.preflight.lunaReasoning)
        : this.options.preflight.lunaReasoning;
      const threadId = await this.options.facade.startThread({
        model: this.options.preflight.luna.id,
        reasoningEffort,
        cwd: workspace.path,
        approvalPolicy: "never",
        sandbox: "workspace-write",
      });
      const response = await this.options.facade.turn(threadId, workerPrompt(task, workspace.path, context.attempt));
      const parsed = parseWorkerResponse(extractText(response), task, context.attempt);
      const diff = await this.options.workspaceManager.diff(workspace);
      const changes = diff.changedFiles;
      const unexpected = changes.filter((change) => !isAllowed(change, task.writeFiles));
      if (unexpected.length) {
        return failedResult(task, context.attempt, `Worker changed files outside its allowlist: ${unexpected.join(", ")}`, workspace);
      }
      parsed.changedFiles = changes;
      parsed.commit = commitInfo(workspace);
      parsed.workspaceId = workspace.id;
      parsed.workspacePath = workspace.path;
      return parsed;
    } catch (error) {
      return failedResult(task, context.attempt, error instanceof Error ? error.message : String(error), workspace);
    } finally {
      if (!this.options.keepWorkspaces) await this.options.workspaceManager.release(workspace);
    }
  }

  private async mockExecute(task: TaskSpec, context: TaskExecutionContext, workspace: WorkspaceHandle): Promise<WorkerResult> {
    if (task.writeFiles.length > 0) {
      const target = task.writeFiles[0];
      if (target) {
        const targetPath = path.join(workspace.path, target);
        await mkdir(path.dirname(targetPath), { recursive: true });
        await writeFile(targetPath, `mock worker ${task.id} attempt ${context.attempt}\n`, "utf8");
      }
    }
    const changes = (await this.options.workspaceManager.diff(workspace)).changedFiles;
    return {
      taskId: task.id,
      status: "succeeded",
      summary: "Mock Luna completed the isolated task.",
      changedFiles: changes,
      tests: [{ command: "mock-worker", passed: true, output: "mock pass" }],
      completionCriteriaMet: true,
      risks: [],
      attempts: context.attempt,
      commit: commitInfo(workspace),
      workspaceId: workspace.id,
      workspacePath: workspace.path,
    };
  }
}

function workerPrompt(task: TaskSpec, workspacePath: string, attempt: number): string {
  return [
    "You are a Luna implementation worker in a Codex App Server orchestrator.",
    "Work only inside the supplied workspace and only modify the allowed write files.",
    "Run relevant tests and return ONLY one JSON object with taskId, status, summary, changedFiles, tests, completionCriteriaMet, risks, and failureReason when needed.",
    `Attempt: ${attempt}`,
    `Workspace: ${workspacePath}`,
    `Task: ${JSON.stringify(task)}`,
    `Read allowlist: ${JSON.stringify(task.readFiles)}`,
    `Write allowlist: ${JSON.stringify(task.writeFiles)}`,
    `Completion criteria: ${JSON.stringify(task.completionCriteria)}`,
  ].join("\n");
}

function parseWorkerResponse(text: string, task: TaskSpec, attempt: number): WorkerResult {
  let value: Record<string, unknown> = {};
  try {
    const parsed = extractJsonObject(text);
    if (typeof parsed === "object" && parsed !== null && !Array.isArray(parsed)) value = parsed as Record<string, unknown>;
  } catch {
    return failedResult(task, attempt, "Luna did not return the required JSON worker result");
  }
  const tests: WorkerResult["tests"] = Array.isArray(value.tests)
    ? value.tests.map((test) => {
      if (typeof test !== "object" || test === null) return { command: "unknown", passed: false };
      const record = test as Record<string, unknown>;
      return {
        command: typeof record.command === "string" ? record.command : "unknown",
        passed: record.passed === true || record.status === "passed",
        ...(typeof record.output === "string" ? { output: record.output } : {}),
      };
    })
    : [];
  return {
    taskId: task.id,
    status: normalizeStatus(value.status),
    summary: typeof value.summary === "string" ? value.summary : "",
    changedFiles: Array.isArray(value.changedFiles) ? value.changedFiles.map(String) : [],
    tests,
    completionCriteriaMet: value.completionCriteriaMet === true,
    risks: Array.isArray(value.risks) ? value.risks.map(String) : [],
    ...(typeof value.failureReason === "string" ? { failureReason: value.failureReason } : {}),
    attempts: attempt,
  };
}

function failedResult(task: TaskSpec, attempt: number, reason: string, workspace?: WorkspaceHandle): WorkerResult {
  return {
    taskId: task.id,
    status: "failed",
    summary: "Luna worker failed",
    changedFiles: [],
    tests: [],
    completionCriteriaMet: false,
    risks: [],
    failureReason: reason,
    attempts: attempt,
    ...(workspace ? { commit: commitInfo(workspace) } : {}),
    ...(workspace ? { workspaceId: workspace.id, workspacePath: workspace.path } : {}),
  };
}

export { mapTask };
