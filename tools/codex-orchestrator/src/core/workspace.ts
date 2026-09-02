import { createHash, randomUUID } from "node:crypto";
import { execFile } from "node:child_process";
import { cp, mkdir, readFile, readlink, readdir, rm, stat } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import type { CommitInfo, TaskSpec } from "./schema.js";

export type WorkspaceKind = "git-worktree" | "isolated-copy";

export interface WorkspaceManagerOptions {
  rootDir: string;
  /** Defaults to a temporary directory outside the repository. */
  baseDir?: string;
  runId?: string;
  branchPrefix?: string;
}

interface WorkspaceHandleBase {
  id: string;
  taskId: string;
  path: string;
  sourcePath: string;
  createdAt: string;
}

export interface GitWorkspaceHandle extends WorkspaceHandleBase {
  kind: "git-worktree";
  branch: string;
  baseRevision: string;
}

export interface IsolatedWorkspaceHandle extends WorkspaceHandleBase {
  kind: "isolated-copy";
}

/** Discriminated handle lets callers safely access `branch` for git workers. */
export type WorkspaceHandle = GitWorkspaceHandle | IsolatedWorkspaceHandle;

export interface WorkspaceDiff {
  kind: WorkspaceKind;
  changedFiles: string[];
  patch?: string;
  status: string;
}

export interface ApplyResult {
  applied: boolean;
  conflict: boolean;
  changedFiles: string[];
  message: string;
}

export interface WorkspaceCommitResult extends CommitInfo {
  kind: WorkspaceKind;
}

type InternalHandle = WorkspaceHandle & {
  baseline?: Record<string, string>;
};

interface CommandResult {
  code: number;
  stdout: string;
  stderr: string;
}

function runCommand(command: string, args: string[], cwd: string, input?: string): Promise<CommandResult> {
  return new Promise((resolve) => {
    const child = execFile(command, args, { cwd, windowsHide: true, encoding: "utf8" }, (error, stdout, stderr) => {
      const code = typeof (error as NodeJS.ErrnoException | null)?.code === "number"
        ? Number((error as NodeJS.ErrnoException).code)
        : error
          ? 1
          : 0;
      resolve({ code, stdout: String(stdout ?? ""), stderr: String(stderr ?? "") });
    });
    if (input !== undefined && child.stdin) child.stdin.end(input);
  });
}

function normalizeSlashes(value: string): string {
  return value.replace(/\\/g, "/");
}

function slug(value: string): string {
  const result = value
    .normalize("NFKD")
    .replace(/[^a-zA-Z0-9._-]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 48);
  return result || "task";
}

function inside(parent: string, child: string): boolean {
  const relative = path.relative(path.resolve(parent), path.resolve(child));
  return relative === "" || (relative !== ".." && !relative.startsWith(`..${path.sep}`) && !path.isAbsolute(relative));
}

async function hashFile(filePath: string): Promise<string> {
  const info = await stat(filePath);
  if (!info.isFile()) return `special:${info.mode}`;
  const data = await readFile(filePath);
  return createHash("sha256").update(data).digest("hex");
}

async function listFiles(root: string, current = root, excludedPath?: string): Promise<string[]> {
  const entries = await readdir(current, { withFileTypes: true });
  const result: string[] = [];
  for (const entry of entries) {
    const full = path.join(current, entry.name);
    if (excludedPath && (full === excludedPath || full.startsWith(`${excludedPath}${path.sep}`))) continue;
    if (entry.name === ".git" || entry.name === "node_modules") continue;
    if (entry.isDirectory()) {
      result.push(...await listFiles(root, full, excludedPath));
    } else {
      result.push(normalizeSlashes(path.relative(root, full)));
    }
  }
  return result;
}

async function snapshot(root: string, excludedPath?: string): Promise<Record<string, string>> {
  const files = await listFiles(root, root, excludedPath);
  const result: Record<string, string> = {};
  for (const relative of files) {
    const full = path.join(root, relative);
    try {
      const info = await stat(full);
      if (info.isSymbolicLink()) {
        result[relative] = `symlink:${await readlink(full)}`;
      } else {
        result[relative] = await hashFile(full);
      }
    } catch {
      // A file disappearing while taking a snapshot is simply absent.
    }
  }
  return result;
}

/**
 * Creates one isolated workspace per worker. Git projects use real worktrees
 * and uniquely named branches; non-git projects receive a private copy with a
 * baseline snapshot so applying changes can detect user conflicts.
 */
export class WorkspaceManager {
  readonly rootPath: string;
  readonly runId: string;
  readonly baseDir: string;

  private readonly branchPrefix: string;
  private readonly handles = new Map<string, InternalHandle>();
  private gitRoot?: string;
  private gitChecked = false;

  constructor(options: WorkspaceManagerOptions) {
    this.rootPath = path.resolve(options.rootDir);
    this.runId = options.runId ?? randomUUID();
    this.baseDir = path.resolve(options.baseDir ?? path.join(requireTempDir(), "codex-orchestrator", this.runId));
    if (this.baseDir === this.rootPath) {
      throw new Error("Workspace baseDir must be distinct from rootDir");
    }
    this.branchPrefix = slug(options.branchPrefix ?? "codex-orchestrator");
  }

  async prepare(task: Pick<TaskSpec, "id"> | string): Promise<WorkspaceHandle> {
    const taskId = typeof task === "string" ? task : task.id;
    if (!taskId || taskId.trim().length === 0) throw new Error("A task ID is required to create a workspace");
    await mkdir(this.baseDir, { recursive: true });
    const gitRoot = await this.findGitRoot();
    const workspaceId = `${slug(taskId)}-${randomUUID().slice(0, 8)}`;
    const workspacePath = path.join(this.baseDir, workspaceId);
    if (gitRoot) {
      const revisionResult = await runCommand("git", ["rev-parse", "HEAD"], gitRoot);
      if (revisionResult.code !== 0 || revisionResult.stdout.trim().length === 0) {
        throw new Error(`Git repository has no usable HEAD: ${revisionResult.stderr.trim()}`);
      }
      const baseRevision = revisionResult.stdout.trim();
      const branch = `${this.branchPrefix}/${slug(this.runId)}/${slug(taskId)}-${randomUUID().slice(0, 8)}`;
      const created = await runCommand("git", ["worktree", "add", "-b", branch, workspacePath, baseRevision], gitRoot);
      if (created.code !== 0) {
        throw new Error(`Unable to create git worktree: ${created.stderr.trim() || created.stdout.trim()}`);
      }
      const handle: InternalHandle = {
        id: workspaceId,
        taskId,
        path: workspacePath,
        sourcePath: gitRoot,
        kind: "git-worktree",
        branch,
        baseRevision,
        createdAt: new Date().toISOString(),
      };
      this.handles.set(handle.id, handle);
      return handle;
    }

    const baseInsideRoot = inside(this.rootPath, this.baseDir) && path.resolve(this.baseDir) !== this.rootPath;
    const copyFilter = (source: string): boolean => {
      if (source === this.rootPath) return true;
      const relative = path.relative(this.rootPath, source);
      if (relative === ".git" || relative.startsWith(`.git${path.sep}`)) return false;
      if (relative === "node_modules" || relative.startsWith(`node_modules${path.sep}`)) return false;
      if (baseInsideRoot && (source === this.baseDir || source.startsWith(`${this.baseDir}${path.sep}`))) return false;
      return true;
    };
    // fs.cp rejects a destination nested below its source before invoking its
    // filter. Stage through the system temp directory when callers place the
    // requested baseDir inside a non-git project.
    let stagingPath: string | undefined;
    try {
      if (baseInsideRoot) {
        stagingPath = path.join(requireTempDir(), "codex-orchestrator-staging", `${this.runId}-${workspaceId}`);
        await mkdir(stagingPath, { recursive: true });
        await cp(this.rootPath, stagingPath, { recursive: true, dereference: false, filter: copyFilter });
        await mkdir(workspacePath, { recursive: true });
        await cp(stagingPath, workspacePath, { recursive: true, dereference: false });
      } else {
        await mkdir(workspacePath, { recursive: true });
        await cp(this.rootPath, workspacePath, { recursive: true, dereference: false, filter: copyFilter });
      }
    } finally {
      if (stagingPath) await rm(stagingPath, { recursive: true, force: true });
    }
    const handle: InternalHandle = {
      id: workspaceId,
      taskId,
      path: workspacePath,
      sourcePath: this.rootPath,
      kind: "isolated-copy",
      createdAt: new Date().toISOString(),
      baseline: await snapshot(this.rootPath, baseInsideRoot ? this.baseDir : undefined),
    };
    this.handles.set(handle.id, handle);
    return handle;
  }

  /** Alias useful to callers that prefer create terminology. */
  create(task: Pick<TaskSpec, "id"> | string): Promise<WorkspaceHandle> {
    return this.prepare(task);
  }

  /** More explicit alias for integrations that call this a workspace factory. */
  createWorkspace(task: Pick<TaskSpec, "id"> | string): Promise<WorkspaceHandle> {
    return this.prepare(task);
  }

  async diff(handleOrId: WorkspaceHandle | string): Promise<WorkspaceDiff> {
    const handle = this.resolve(handleOrId);
    if (handle.kind === "git-worktree") {
      let status = await runCommand("git", ["status", "--porcelain=v1", "--untracked-files=all"], handle.path);
      const untrackedFiles = status.stdout
        .split(/\r?\n/)
        .filter((line) => line.startsWith("?? "))
        .map((line) => line.slice(3).trim())
        .filter(Boolean);
      // `git diff` normally omits untracked files. Intent-to-add entries make
      // the regular binary diff include their contents without staging them,
      // which keeps --apply able to carry newly created worker files safely.
      for (const file of untrackedFiles) {
        await runCommand("git", ["add", "--intent-to-add", "--", file], handle.path);
      }
      if (untrackedFiles.length > 0) {
        status = await runCommand("git", ["status", "--porcelain=v1", "--untracked-files=all"], handle.path);
      }
      const statusFiles = status.stdout
        .split(/\r?\n/)
        .filter(Boolean)
        .map((line) => line.slice(3).trim())
        .filter(Boolean);
      // Compare against the original revision as well as the worktree index so
      // a worker that committed its branch still produces an applicable diff.
      const names = await runCommand("git", ["diff", "--name-only", "--no-ext-diff", handle.baseRevision ?? "HEAD"], handle.path);
      const revisionFiles = names.stdout.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
      const changedFiles = [...new Set([...statusFiles, ...revisionFiles])].sort();
      const patchResult = await runCommand("git", ["diff", "--binary", "--no-ext-diff", handle.baseRevision ?? "HEAD"], handle.path);
      return {
        kind: handle.kind,
        changedFiles,
        ...(patchResult.code === 0 ? { patch: patchResult.stdout } : {}),
        status: status.stdout,
      };
    }
    const before = handle.baseline ?? {};
    const after = await snapshot(handle.path);
    const files = new Set([...Object.keys(before), ...Object.keys(after)]);
    const changedFiles = [...files].filter((file) => before[file] !== after[file]).sort();
    return { kind: handle.kind, changedFiles, status: changedFiles.join("\n") };
  }

  async commit(handleOrId: WorkspaceHandle | string, message: string): Promise<WorkspaceCommitResult> {
    const handle = this.resolve(handleOrId);
    if (handle.kind !== "git-worktree") {
      throw new Error("Commits are only available for git worktree workspaces");
    }
    const add = await runCommand("git", ["add", "--all"], handle.path);
    if (add.code !== 0) throw new Error(`Unable to stage workspace changes: ${add.stderr.trim()}`);
    const commit = await runCommand("git", ["commit", "--no-verify", "-m", message], handle.path);
    if (commit.code !== 0) {
      if (/nothing to commit/i.test(`${commit.stdout}\n${commit.stderr}`)) {
        const revision = await runCommand("git", ["rev-parse", "HEAD"], handle.path);
        return { kind: handle.kind, hash: revision.stdout.trim(), ...(handle.branch ? { branch: handle.branch } : {}) };
      }
      throw new Error(`Unable to commit workspace changes: ${commit.stderr.trim() || commit.stdout.trim()}`);
    }
    const revision = await runCommand("git", ["rev-parse", "HEAD"], handle.path);
    return { kind: handle.kind, hash: revision.stdout.trim(), ...(handle.branch ? { branch: handle.branch } : {}) };
  }

  /**
   * Explicitly apply a worker's changes to the source checkout. This is never
   * called by prepare/release, allowing the CLI to make --apply opt-in.
   */
  async apply(handleOrId: WorkspaceHandle | string): Promise<ApplyResult> {
    const handle = this.resolve(handleOrId);
    const workspaceDiff = await this.diff(handle);
    if (workspaceDiff.changedFiles.length === 0) {
      return { applied: true, conflict: false, changedFiles: [], message: "No changes to apply" };
    }
    if (handle.kind === "git-worktree") {
      if (!workspaceDiff.patch?.trim()) {
        return {
          applied: false,
          conflict: true,
          changedFiles: workspaceDiff.changedFiles,
          message: "Changes include untracked files; commit the worktree or provide a patch before applying",
        };
      }
      const check = await runCommand("git", ["apply", "--check", "--whitespace=nowarn"], handle.sourcePath, workspaceDiff.patch);
      if (check.code !== 0) {
        return {
          applied: false,
          conflict: true,
          changedFiles: workspaceDiff.changedFiles,
          message: check.stderr.trim() || check.stdout.trim() || "Git apply check failed; source changes were preserved",
        };
      }
      const applied = await runCommand("git", ["apply", "--whitespace=nowarn"], handle.sourcePath, workspaceDiff.patch);
      return {
        applied: applied.code === 0,
        conflict: applied.code !== 0,
        changedFiles: workspaceDiff.changedFiles,
        message: applied.code === 0 ? "Patch applied" : (applied.stderr.trim() || "Patch application failed; source changes were preserved"),
      };
    }

    // For non-git projects compare source hashes with the baseline before each
    // copy. A user edit made after prepare is reported as a conflict and is
    // never overwritten.
    const baseline = handle.baseline ?? {};
    const conflicts: string[] = [];
    const appliedFiles: string[] = [];
    for (const relative of workspaceDiff.changedFiles) {
      const sourceFile = path.resolve(handle.sourcePath, relative);
      const workspaceFile = path.resolve(handle.path, relative);
      if (!inside(handle.sourcePath, sourceFile) || !inside(handle.path, workspaceFile)) {
        conflicts.push(relative);
        continue;
      }
      let currentSource: string | undefined;
      try { currentSource = await hashFile(sourceFile); } catch { currentSource = undefined; }
      if (currentSource !== baseline[relative]) {
        conflicts.push(relative);
        continue;
      }
      const workspaceExists = await stat(workspaceFile).then(() => true).catch(() => false);
      if (workspaceExists) {
        await mkdir(path.dirname(sourceFile), { recursive: true });
        await cp(workspaceFile, sourceFile, { recursive: true, dereference: false });
      } else {
        await rm(sourceFile, { recursive: true, force: true });
      }
      appliedFiles.push(relative);
    }
    return {
      applied: conflicts.length === 0,
      conflict: conflicts.length > 0,
      changedFiles: workspaceDiff.changedFiles,
      message: conflicts.length === 0
        ? `Applied ${appliedFiles.length} file(s)`
        : `Conflict(s) preserved user changes: ${conflicts.join(", ")}`,
    };
  }

  async release(handleOrId: WorkspaceHandle | string, options: { deleteBranch?: boolean } = {}): Promise<void> {
    const handle = this.resolve(handleOrId);
    this.handles.delete(handle.id);
    if (handle.kind === "git-worktree") {
      const removed = await runCommand("git", ["worktree", "remove", "--force", handle.path], handle.sourcePath);
      if (removed.code !== 0 && await stat(handle.path).then(() => true).catch(() => false)) {
        throw new Error(`Unable to remove git worktree: ${removed.stderr.trim()}`);
      }
      if (options.deleteBranch && handle.branch) {
        await runCommand("git", ["branch", "-D", handle.branch], handle.sourcePath);
      }
      return;
    }
    await rm(handle.path, { recursive: true, force: true });
  }

  async releaseAll(options: { deleteBranch?: boolean } = {}): Promise<void> {
    const handles = [...this.handles.values()];
    for (const handle of handles) await this.release(handle, options);
  }

  async dispose(options: { deleteBranch?: boolean } = {}): Promise<void> {
    await this.releaseAll(options);
  }

  private resolve(handleOrId: WorkspaceHandle | string): InternalHandle {
    const id = typeof handleOrId === "string" ? handleOrId : handleOrId.id;
    const handle = this.handles.get(id);
    if (!handle) throw new Error(`Unknown or released workspace: ${id}`);
    return handle;
  }

  private async findGitRoot(): Promise<string | undefined> {
    if (this.gitChecked) return this.gitRoot;
    this.gitChecked = true;
    const result = await runCommand("git", ["rev-parse", "--show-toplevel"], this.rootPath);
    if (result.code !== 0) return undefined;
    const candidate = result.stdout.trim();
    if (!candidate) return undefined;
    this.gitRoot = path.resolve(candidate);
    return this.gitRoot;
  }
}

function requireTempDir(): string {
  // Kept in a function to make tests able to monkey-patch the environment
  // without changing the manager's public constructor shape.
  return process.env.TMPDIR || process.env.TEMP || process.env.TMP || tmpdir();
}
