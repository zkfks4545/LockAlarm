import type { AppServerFacade, PreflightResult } from "./session.js";
import { extractText } from "./session.js";
import { parsePlan, parseReview, planPrompt, reviewPrompt } from "./plan.js";
import type { OrchestratorPlan, ReviewResult } from "./shared-types.js";
import { mapTask } from "./worker.js";
import type { OrchestrationPlan, TaskSpec } from "./core/schema.js";

export class SolSession {
  private threadId: string | undefined;

  constructor(
    private readonly facade: AppServerFacade,
    private readonly preflight: PreflightResult,
    private readonly cwd: string,
  ) {}

  async start(): Promise<void> {
    this.threadId = await this.facade.startThread({
      model: this.preflight.sol.id,
      reasoningEffort: this.preflight.solReasoning,
      cwd: this.cwd,
      approvalPolicy: "never",
      sandbox: "read-only",
    });
  }

  async plan(goal: string): Promise<{ publicPlan: OrchestratorPlan; corePlan: OrchestrationPlan }> {
    if (!this.threadId) throw new Error("Sol session is not started");
    const response = await this.facade.turn(this.threadId, planPrompt(goal, this.cwd));
    const publicPlan = parsePlan(extractText(response));
    const corePlan: OrchestrationPlan = {
      goal: publicPlan.goal,
      tasks: publicPlan.tasks.map(mapTask),
      ...(publicPlan.risks.length ? { rationale: `Risks: ${publicPlan.risks.join("; ")}` } : {}),
    };
    return { publicPlan, corePlan };
  }

  async review(goal: string, plan: OrchestratorPlan, results: unknown[]): Promise<ReviewResult> {
    if (!this.threadId) throw new Error("Sol session is not started");
    const response = await this.facade.turn(this.threadId, reviewPrompt(goal, plan, results));
    return parseReview(extractText(response));
  }
}
