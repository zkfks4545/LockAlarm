export {
  PLAN_JSON_SCHEMA,
  TASK_JSON_SCHEMA,
  SchemaValidationError,
  parsePlan,
  parseTask,
  stableStringify,
  taskFingerprint,
} from "./schema.js";
export type {
  CommitInfo,
  OrchestrationPlan,
  TaskDifficulty,
  TaskExecutionContext,
  TaskExecutor,
  TaskSpec,
  TaskStatus,
  TestResult,
  WorkerResult,
} from "./schema.js";

/** Short names kept stable for planner/worker integrations. */
export type { TaskSpec as Task, OrchestrationPlan as Plan, WorkerResult as TaskResult } from "./schema.js";

export {
  DagScheduler,
} from "./scheduler.js";
export type {
  RetryPolicy,
  SchedulerOptions,
  SchedulerRunResult,
  SchedulerTaskState,
} from "./scheduler.js";

export {
  WorkspaceManager,
} from "./workspace.js";
export type {
  ApplyResult,
  WorkspaceCommitResult,
  WorkspaceDiff,
  GitWorkspaceHandle,
  WorkspaceHandle,
  IsolatedWorkspaceHandle,
  WorkspaceKind,
  WorkspaceManagerOptions,
} from "./workspace.js";

export {
  JsonLogger,
  createJsonLogger,
} from "./logger.js";
export type {
  JsonLoggerOptions,
  LogLevel,
  LoggerLike,
} from "./logger.js";
