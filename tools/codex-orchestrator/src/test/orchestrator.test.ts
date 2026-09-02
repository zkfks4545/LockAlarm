import test from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { loadConfig } from "../config.js";
import { runOrchestration } from "../orchestrator.js";

test("mock full run plans, schedules, reviews, and preserves the source project without --apply", async () => {
  const root = await mkdtemp(path.join(tmpdir(), "codex-orchestrator-e2e-"));
  try {
    const readme = path.join(root, "README.md");
    await writeFile(readme, "fixture\n", "utf8");
    const report = await runOrchestration({
      goal: "mock verification",
      config: loadConfig({ cwd: root, mock: true, workers: "2", apply: false }),
    });
    assert.equal(report.status, "completed");
    assert.equal(report.review.approved, true);
    assert.equal(report.results.length, 2);
    assert.equal(await readFile(readme, "utf8"), "fixture\n");
    assert.equal(report.applied, false);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test("mock --apply copies only Sol-approved isolated changes", async () => {
  const root = await mkdtemp(path.join(tmpdir(), "codex-orchestrator-apply-"));
  try {
    await writeFile(path.join(root, "README.md"), "fixture\n", "utf8");
    const report = await runOrchestration({
      goal: "mock apply",
      config: loadConfig({ cwd: root, mock: true, workers: "2", apply: true }),
    });
    assert.equal(report.status, "completed");
    assert.equal(report.applied, true);
    assert.match(await readFile(path.join(root, ".codex-orchestrator", "mock-worker.txt"), "utf8"), /mock worker/);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});
