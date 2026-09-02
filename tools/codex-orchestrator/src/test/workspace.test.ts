import test from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { WorkspaceManager } from "../core/workspace.js";

test("non-git workspace apply preserves a user's concurrent edit", async () => {
  const root = await mkdtemp(path.join(tmpdir(), "codex-orchestrator-root-"));
  try {
    const file = path.join(root, "note.txt");
    await writeFile(file, "original\n", "utf8");
    const manager = new WorkspaceManager({ rootDir: root, baseDir: path.join(tmpdir(), "codex-orchestrator-workers") });
    const handle = await manager.prepare("change-note");
    await writeFile(path.join(handle.path, "note.txt"), "worker\n", "utf8");
    await writeFile(file, "user-edit\n", "utf8");
    const applied = await manager.apply(handle);
    assert.equal(applied.applied, false);
    assert.equal(applied.conflict, true);
    assert.equal(await readFile(file, "utf8"), "user-edit\n");
    await manager.dispose();
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

