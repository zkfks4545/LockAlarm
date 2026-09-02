# codex-orchestrate

`codex-orchestrate` is a local, ChatGPT-authenticated multi-agent runner for Codex App Server. It uses one Sol lead to plan and review work, then runs independent Luna workers in isolated workspaces.

It deliberately does **not** use the OpenAI Responses API, an OpenAI API key, or a hidden paid fallback. Live execution is allowed only when the local App Server proves ChatGPT authentication and a Pro plan. The required model IDs are exact: `gpt-5.6-sol` and `gpt-5.6-luna`. If either is missing, the run stops and prints the advertised model list.

## Install

From this directory:

```text
npm install
npm run build
npm link
```

The package requires Node.js 20 or newer and exposes the `codex-orchestrate` command. On Windows PowerShell execution-policy setups, use `npm.cmd` or invoke `node dist/index.js` directly.

## Commands

```text
codex-orchestrate doctor
codex-orchestrate models
codex-orchestrate run --goal "로그인 기능을 분석하고 테스트까지 추가해줘" --cwd . --workers 4
codex-orchestrate run --goal "로그인 기능을 수정하고 검증해줘" --cwd . --workers 4 --apply
```

`--workers` is capped at four. `--apply` is opt-in: without it, workers only modify their private workspaces and the source project is untouched. With it, Sol-approved changes are applied one workspace at a time and user edits made after the worker baseline are reported as conflicts instead of being overwritten.

For a no-usage local run:

```text
codex-orchestrate run --goal "mock verification" --cwd . --workers 2 --mock
```

`--mock` uses the in-memory Mock App Server and exercises planning, scheduling, review, retries, JSON logs, and workspace application without consuming Codex usage.

## Live startup checks

Every live run performs these checks in order:

1. `codex --version` is executable.
2. App Server stdio JSON-RPC starts and completes `initialize` plus `initialized`.
3. `account/read` proves ChatGPT authentication and reports the plan.
4. API-key, logged-out, unknown-auth, unknown-plan, and non-Pro states stop with a login/diagnostic message. The intended recovery is ChatGPT device-code login (`chatgptDeviceCode`), not an API key.
5. `model/list` must advertise both required models and their reasoning levels. Sol uses `max` when available; otherwise the strongest advertised level is reported and used. Luna defaults to the fastest advertised level; hard tasks may use a higher level.
6. `account/rateLimits/read` is captured before and after the run.

The child process is exactly the App Server stdio transport (`codex app-server --listen stdio://`). JSONL and Content-Length framing are accepted for compatibility. App Server turn completion notifications are collected so Sol/Luna output is not mistaken for the initial `turn/start` acknowledgement.

## Safety and isolation

- Plans are validated against a strict JSON Schema before workers start.
- Each task receives explicit read and write file lists.
- Dependencies form a DAG; cycles and unknown prerequisites stop the run.
- Overlapping write files are serialized, while independent tasks run in parallel.
- Task fingerprints prevent duplicate execution; retries are bounded to two retries.
- Git projects use a fresh worktree and branch per task. Non-Git projects use a private copy under the system temporary directory.
- Automatic merging is disabled. Applying changes requires `--apply` and is conflict-checked.
- Structured newline-delimited JSON logs are written to stderr; the final run report is JSON on stdout.

## Tests

```text
npm run typecheck
npm test
npm run test:mock
```

The test suite covers JSON-RPC handshake and Mock App Server behavior, plan validation, DAG dependencies and overlapping-file serialization, bounded retries and de-duplication, non-Git user-change preservation, and an end-to-end Mock run. It never calls a real model. A real account check is opt-in by running `doctor`, `models`, or `run` without `--mock`.

`npm run test:integration` is intentionally live and runs the read-only `doctor` preflight against the local App Server. Run it only when you explicitly want to verify the current ChatGPT account; it will stop before planning if the executable, authentication, Pro plan, or required models are unavailable.

## Limitations

- The exact model IDs and reasoning levels are entitlement-dependent. The CLI does not silently substitute another model.
- App Server approval requests are surfaced by the RPC client; this MVP does not grant approvals outside the worker workspace policy.
- A worker's isolated patch remains available for inspection when automatic application is disabled. Cleanup is safe and owned by the run manager.
- A live run cannot be claimed from Mock results alone. The final report keeps the authentication, model inventory, rate-limit snapshots, and limitations explicit.
