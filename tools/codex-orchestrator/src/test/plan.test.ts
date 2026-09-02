import test from "node:test";
import assert from "node:assert/strict";
import { parsePlan, PlanValidationError, validatePlan } from "../plan.js";

const valid = {
  goal: "test",
  tasks: [{ id: "a", goal: "inspect", dependencies: [], readFiles: ["README.md"], writableFiles: [], completionCriteria: ["report"], difficulty: "easy" }],
  globalCompletionCriteria: ["done"],
  risks: [],
};

test("Sol plan schema accepts required fields and rejects unknown keys", () => {
  const plan = validatePlan(valid);
  assert.equal(plan.tasks[0]?.id, "a");
  assert.throws(() => validatePlan({ ...valid, extra: true }), PlanValidationError);
  assert.throws(() => validatePlan({ ...valid, tasks: [{ ...valid.tasks[0], dependencies: ["missing"] }] }), /unknown task/);
  assert.throws(() => validatePlan({ ...valid, tasks: [{ ...valid.tasks[0], writableFiles: ["../outside.txt"] }] }), /escape the workspace/);
});

test("plan parser handles fenced JSON", () => {
  const plan = parsePlan(`\n\`\`\`json\n${JSON.stringify(valid)}\n\`\`\``);
  assert.equal(plan.goal, "test");
});
