#!/usr/bin/env node
import { stat } from "node:fs/promises";
import { pathToFileURL } from "node:url";
import { loadConfig } from "./config.js";
import { collectDoctor, runOrchestration } from "./orchestrator.js";

const HELP = `codex-orchestrate — ChatGPT-authenticated Codex App Server multi-agent orchestration

Usage:
  codex-orchestrate run --goal "..." [--cwd .] [--workers 4] [--apply] [--mock]
  codex-orchestrate doctor [--cwd .] [--mock]
  codex-orchestrate models [--cwd .] [--mock]

Options:
  --goal <text>       Goal sent to the Sol planning lead (required for run)
  --cwd <path>        Project root (default: current directory)
  --workers <1-4>     Maximum independent Luna workers (default: 4)
  --apply             Apply only Sol-approved changes, with conflict checks
  --mock              Use the in-memory Mock App Server; consumes no model usage
  --codex <command>   Codex executable (default: codex)
  --max-retries <0-2> Sol-directed retries after the first review
  --timeout-ms <n>    App Server request timeout (default: 120000)
  --log-level <level> debug|info|warn|error
  --help              Show this help

Authentication:
  This CLI never uses the OpenAI Responses API or OPENAI_API_KEY. Live runs
  require ChatGPT authentication in the local Codex App Server.
`;

interface Parsed {
  command: "run" | "doctor" | "models" | "help";
  goal?: string;
  cwd?: string;
  workers?: string;
  apply?: boolean;
  mock?: boolean;
  codexBin?: string;
  maxRetries?: string;
  timeoutMs?: string;
  logLevel?: string;
}

function parse(argv: string[]): Parsed {
  if (argv.length === 0 || argv[0] === "--help" || argv[0] === "-h") return { command: "help" };
  const command = (argv[0] ?? "help") as Parsed["command"];
  if (command !== "run" && command !== "doctor" && command !== "models" && command !== "help") throw new Error(`Unknown command: ${command}`);
  const result: Parsed = { command };
  for (let index = 1; index < argv.length; index += 1) {
    const flag = argv[index];
    if (flag === "--help" || flag === "-h") return { command: "help" };
    if (flag === "--apply") { result.apply = true; continue; }
    if (flag === "--mock") { result.mock = true; continue; }
    const value = argv[index + 1];
    if (!value || value.startsWith("--")) throw new Error(`${flag} requires a value`);
    index += 1;
    switch (flag) {
      case "--goal": result.goal = value; break;
      case "--cwd": result.cwd = value; break;
      case "--workers": result.workers = value; break;
      case "--codex": result.codexBin = value; break;
      case "--max-retries": result.maxRetries = value; break;
      case "--timeout-ms": result.timeoutMs = value; break;
      case "--log-level": result.logLevel = value; break;
      default: throw new Error(`Unknown option: ${flag}`);
    }
  }
  return result;
}

async function ensureDirectory(cwd: string): Promise<void> {
  const info = await stat(cwd).catch(() => undefined);
  if (!info?.isDirectory()) throw new Error(`--cwd is not a directory: ${cwd}`);
}

export async function main(argv = process.argv.slice(2)): Promise<number> {
  try {
    const parsed = parse(argv);
    if (parsed.command === "help") {
      process.stdout.write(HELP);
      return 0;
    }
    const config = loadConfig(parsed);
    await ensureDirectory(config.cwd);
    if (parsed.command === "run") {
      if (!parsed.goal?.trim()) throw new Error("run requires --goal <text>");
      const report = await runOrchestration({ goal: parsed.goal, config });
      process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
      return report.status === "completed" ? 0 : 1;
    }
    const diagnostic = await collectDoctor(config);
    process.stdout.write(`${JSON.stringify(diagnostic, null, 2)}\n`);
    return diagnostic.ok === true ? 0 : 1;
  } catch (error) {
    process.stderr.write(`${JSON.stringify({ ok: false, error: error instanceof Error ? error.message : String(error) })}\n`);
    process.stderr.write("Run `codex-orchestrate --help` for usage.\n");
    return 1;
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().then((code) => { process.exitCode = code; }).catch((error) => {
    process.stderr.write(`${JSON.stringify({ ok: false, error: String(error) })}\n`);
    process.exitCode = 1;
  });
}
