export type Difficulty = "easy" | "medium" | "hard";

export interface OrchestratorTask {
  id: string;
  goal: string;
  dependencies: string[];
  readFiles: string[];
  writableFiles: string[];
  completionCriteria: string[];
  difficulty: Difficulty;
}

export interface OrchestratorPlan {
  goal: string;
  tasks: OrchestratorTask[];
  globalCompletionCriteria: string[];
  risks: string[];
}

export type WorkerStatus = "completed" | "failed" | "skipped";

export interface WorkerResult {
  taskId: string;
  status: WorkerStatus;
  summary: string;
  changedFiles: string[];
  tests: Array<{ command: string; status: "passed" | "failed" | "not-run"; output?: string }>;
  completionCriteriaMet: boolean;
  risks: string[];
  commitOrPatch?: string;
  failureReason?: string;
  workspacePath?: string;
  attempt: number;
}

export interface ReviewResult {
  approved: boolean;
  summary: string;
  taskFindings: Array<{
    taskId: string;
    approved: boolean;
    requiredChanges: string[];
    reason: string;
  }>;
  risks: string[];
  retryTaskIds: string[];
}

export interface RunReport {
  runId: string;
  status: "completed" | "failed" | "blocked";
  goal: string;
  plan: OrchestratorPlan;
  results: WorkerResult[];
  review: ReviewResult;
  applied: boolean;
  usageBefore?: unknown;
  usageAfter?: unknown;
  authentication?: {
    mode: "chatgpt" | "apiKey" | "loggedOut" | "unknown";
    plan?: string;
  };
  models?: unknown;
  limitations: string[];
}

