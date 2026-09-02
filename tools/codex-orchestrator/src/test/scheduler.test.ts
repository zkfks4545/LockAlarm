import test from "node:test";
import assert from "node:assert/strict";
import { DagScheduler } from "../core/scheduler.js";
import type { TaskSpec, WorkerResult } from "../core/schema.js";

function success(taskId: string, attempt: number): WorkerResult {
  return { taskId, status: "succeeded", summary: "ok", changedFiles: [], tests: [], completionCriteriaMet: true, risks: [], attempts: attempt };
}

test("scheduler honors dependencies, overlapping writes, and worker limit", async () => {
  const tasks: TaskSpec[] = [
    { id: "a", goal: "a", prerequisites: [], readFiles: [], writeFiles: ["same.txt"], completionCriteria: ["ok"], difficulty: "easy" },
    { id: "b", goal: "b", prerequisites: [], readFiles: [], writeFiles: ["other.txt"], completionCriteria: ["ok"], difficulty: "easy" },
    { id: "c", goal: "c", prerequisites: ["a"], readFiles: [], writeFiles: ["same.txt"], completionCriteria: ["ok"], difficulty: "medium" },
  ];
  let active = 0;
  let maxActive = 0;
  const starts: string[] = [];
  const result = await new DagScheduler({ workers: 2 }).run(tasks, async (task, context) => {
    starts.push(task.id);
    active += 1;
    maxActive = Math.max(maxActive, active);
    await new Promise((resolve) => setTimeout(resolve, 5));
    active -= 1;
    return success(task.id, context.attempt);
  });
  assert.equal(maxActive, 2);
  assert.ok(starts.indexOf("a") < starts.indexOf("c"));
  assert.equal(result.results.c?.status, "succeeded");
});

test("scheduler retries at most the configured number and deduplicates identical tasks", async () => {
  const tasks: TaskSpec[] = [
    { id: "retry", goal: "retry", prerequisites: [], readFiles: [], writeFiles: [], completionCriteria: ["ok"], difficulty: "easy" },
    { id: "same-1", goal: "same", prerequisites: [], readFiles: [], writeFiles: [], completionCriteria: ["ok"], difficulty: "easy" },
    { id: "same-2", goal: "same", prerequisites: [], readFiles: [], writeFiles: [], completionCriteria: ["ok"], difficulty: "easy" },
  ];
  let retryCalls = 0;
  const result = await new DagScheduler({ workers: 3, retry: { maxAttempts: 2 } }).run(tasks, async (task, context) => {
    if (task.id === "retry" && retryCalls++ === 0) return { ...success(task.id, context.attempt), status: "failed", completionCriteriaMet: false, failureReason: "transient" };
    return success(task.id, context.attempt);
  });
  assert.equal(result.results.retry?.status, "succeeded");
  assert.equal(result.states.retry?.attempts, 2);
  assert.equal(result.results["same-2"]?.status, "deduplicated");
  assert.equal(retryCalls, 2);
});
