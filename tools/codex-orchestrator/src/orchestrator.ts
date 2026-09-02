import { randomUUID } from "node:crypto";
import type { OrchestratorConfig } from "./config.js";
import type { OrchestratorPlan, ReviewResult, RunReport, WorkerResult as PublicWorkerResult } from "./shared-types.js";
import { JsonLogger } from "./core/logger.js";
import { DagScheduler } from "./core/scheduler.js";
import type { TaskSpec, WorkerResult as CoreWorkerResult } from "./core/schema.js";
import { WorkspaceManager } from "./core/workspace.js";
import { LunaWorker } from "./worker.js";
import { SolSession } from "./sol.js";
import { MockAppServerFacade, createLiveFacade, runPreflight, type AppServerFacade, type PreflightResult } from "./session.js";

export class OrchestrationError extends Error {
  constructor(message: string, public readonly report?: Partial<RunReport>) {
    super(message);
    this.name = "OrchestrationError";
  }
}

function publicResult(result: CoreWorkerResult): PublicWorkerResult {
  const status = result.status === "succeeded" || result.status === "deduplicated" ? "completed" : result.status === "skipped" ? "skipped" : "failed";
  return {
    taskId: result.taskId,
    status,
    summary: result.summary,
    changedFiles: result.changedFiles,
    tests: result.tests.map((test) => ({ command: test.command, status: test.passed ? "passed" : "failed", ...(test.output ? { output: test.output } : {}) })),
    completionCriteriaMet: result.completionCriteriaMet,
    risks: result.risks,
    ...(result.commit?.patchPath || result.workspacePath ? { commitOrPatch: result.commit?.patchPath ?? result.workspacePath } : {}),
    ...(result.failureReason ? { failureReason: result.failureReason } : {}),
    attempt: result.attempts ?? 0,
    ...(result.workspacePath ? { workspacePath: result.workspacePath } : {}),
  };
}

function isSuccessful(result: CoreWorkerResult | undefined): boolean {
  return result?.status === "succeeded" || result?.status === "deduplicated";
}

export interface OrchestratorRunOptions {
  goal: string;
  config: OrchestratorConfig;
  logger?: JsonLogger;
  facade?: AppServerFacade;
}

export async function runOrchestration(options: OrchestratorRunOptions): Promise<RunReport> {
  const runId = randomUUID();
  const logger = options.logger ?? new JsonLogger({ minLevel: options.config.logLevel, context: { runId } });
  const facade = options.facade ?? (options.config.mock ? new MockAppServerFacade() : await createLiveFacade(options.config));
  let preflight: PreflightResult | undefined;
  let workspaceManager: WorkspaceManager | undefined;
  try {
    logger.info("run_started", { runId, goal: options.goal, cwd: options.config.cwd, workers: options.config.workers, apply: options.config.apply, mock: options.config.mock });
    preflight = await runPreflight(facade, options.config);
    logger.info("preflight_passed", {
      codex: preflight.codex,
      authentication: preflight.authentication,
      models: preflight.models.map((model) => ({ id: model.id, reasoningEfforts: model.reasoningEfforts })),
      solReasoning: preflight.solReasoning,
      lunaReasoning: preflight.lunaReasoning,
    });
    if (preflight.solReasoning !== "max") {
      logger.warn("sol_max_unavailable", {
        requested: "max",
        selected: preflight.solReasoning,
        supported: preflight.sol.reasoningEfforts,
      });
    }
    const workspaceRoot = options.config.cwd;
    workspaceManager = new WorkspaceManager({ rootDir: workspaceRoot, runId });
    const sol = new SolSession(facade, preflight, options.config.cwd);
    await sol.start();
    const planned = await sol.plan(options.goal);
    logger.info("plan_created", { taskIds: planned.corePlan.tasks.map((task) => task.id), taskCount: planned.corePlan.tasks.length });
    const worker = new LunaWorker({ facade, preflight, workspaceManager, mock: options.config.mock, keepWorkspaces: true });
    const scheduler = new DagScheduler({ workers: options.config.workers, logger });
    let coreResults: Record<string, CoreWorkerResult> = {};
    let publicResults: PublicWorkerResult[] = [];
    let review: ReviewResult = { approved: false, summary: "Review has not run", taskFindings: [], risks: [], retryTaskIds: [] };
    let retryRound = 0;
    let pendingTasks: TaskSpec[] = planned.corePlan.tasks;
    while (true) {
      const scheduled = await scheduler.run(pendingTasks, (task, context) => worker.execute(task, context), {
        workers: options.config.workers,
        retry: { maxAttempts: 1 },
        contextFactory: (_task, _attempt) => ({ attempt: retryRound + 1 }),
      });
      coreResults = { ...coreResults, ...scheduled.results };
      publicResults = planned.corePlan.tasks.map((task) => publicResult(coreResults[task.id] ?? {
        taskId: task.id,
        status: "skipped",
        summary: "No result",
        changedFiles: [],
        tests: [],
        completionCriteriaMet: false,
        risks: [],
        attempts: 0,
      }));
      review = await sol.review(options.goal, planned.publicPlan, publicResults);
      logger.info("sol_reviewed", { approved: review.approved, retryTaskIds: review.retryTaskIds, retryRound });
      if (review.approved || review.retryTaskIds.length === 0 || retryRound >= options.config.maxRetries) break;
      retryRound += 1;
      const retrySet = new Set(review.retryTaskIds);
      pendingTasks = planned.corePlan.tasks
        .filter((task) => retrySet.has(task.id))
        .map((task) => ({ ...task, prerequisites: [], maxAttempts: 1 }));
      if (pendingTasks.length === 0) break;
      logger.warn("sol_retry_requested", { retryRound, taskIds: pendingTasks.map((task) => task.id) });
    }

    const limitations: string[] = [
      "Live verification was not performed by this run unless the user explicitly omitted --mock and supplied a working ChatGPT-authenticated Codex App Server.",
      "Worker changes are isolated by default; automatic merging is disabled.",
    ];
    if (preflight.solReasoning !== "max") {
      limitations.push(`Sol does not advertise max reasoning; selected ${preflight.solReasoning} from the advertised levels.`);
    }
    let status: RunReport["status"] = review.approved ? "completed" : "failed";
    if (review.approved && options.config.apply) {
      const appliedHandles = new Set<string>();
      for (const result of Object.values(coreResults)) {
        if (!isSuccessful(result) || result.status === "deduplicated" || !result.commit?.patchPath) continue;
        if (appliedHandles.has(result.commit.patchPath)) continue;
        appliedHandles.add(result.commit.patchPath);
        const applied = await workspaceManager.apply(result.commit.patchPath);
        logger.info("changes_applied", { taskId: result.taskId, ...applied });
        if (!applied.applied) {
          status = "blocked";
          limitations.push(`Changes for ${result.taskId} were not applied: ${applied.message}`);
        }
      }
    } else if (!options.config.apply) {
      limitations.push("Use --apply only after reviewing the Sol-approved isolated changes; worker workspaces remain available for inspection in the system temporary directory.");
    }
    const usageAfter = await facade.rateLimitsRead();
    const report: RunReport = {
      runId,
      status,
      goal: options.goal,
      plan: planned.publicPlan,
      results: publicResults,
      review,
      applied: options.config.apply && status !== "blocked",
      usageBefore: preflight.usageBefore,
      usageAfter,
      authentication: { mode: "chatgpt", ...(preflight.plan ? { plan: preflight.plan } : {}) },
      models: preflight.models.map((model) => ({ id: model.id, reasoningEfforts: model.reasoningEfforts })),
      limitations,
    };
    logger.info("run_finished", { status, applied: report.applied, usageBefore: preflight.usageBefore, usageAfter });
    return report;
  } catch (error) {
    logger.error("run_failed", { error: error instanceof Error ? error.message : String(error) });
    throw error;
  } finally {
    await facade.close().catch((error) => logger.warn("app_server_close_failed", { error: String(error) }));
    if (workspaceManager && options.config.apply) await workspaceManager.dispose().catch((error) => logger.warn("workspace_cleanup_failed", { error: String(error) }));
  }
}

export async function collectDoctor(config: OrchestratorConfig, facade?: AppServerFacade): Promise<Record<string, unknown>> {
  const appServer = facade ?? (config.mock ? new MockAppServerFacade() : await createLiveFacade(config));
  try {
    const preflight = await runPreflight(appServer, config);
    return {
      ok: true,
      codex: preflight.codex,
      authentication: preflight.authentication,
      ...(preflight.plan ? { plan: preflight.plan } : {}),
      account: { authentication: preflight.authentication, ...(preflight.plan ? { plan: preflight.plan } : {}) },
      models: preflight.models.map((model) => ({ id: model.id, reasoningEfforts: model.reasoningEfforts })),
      usageBefore: preflight.usageBefore,
      solReasoning: preflight.solReasoning,
      lunaReasoning: preflight.lunaReasoning,
    };
  } catch (error) {
    return {
      ok: false,
      error: error instanceof Error ? error.message : String(error),
      ...(error && typeof error === "object" && "code" in error ? { code: (error as { code?: unknown }).code } : {}),
      details: error && typeof error === "object" && "details" in error ? (error as { details?: unknown }).details : undefined,
    };
  } finally {
    await appServer.close().catch(() => undefined);
  }
}
