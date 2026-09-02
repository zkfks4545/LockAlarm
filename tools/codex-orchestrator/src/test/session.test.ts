import test from "node:test";
import assert from "node:assert/strict";
import { loadConfig } from "../config.js";
import { PreflightError, runPreflight, type AppServerFacade } from "../session.js";

function facade(overrides: Partial<Record<"account" | "models", unknown>>): AppServerFacade {
  return {
    initialize: async () => ({}),
    accountRead: async () => overrides.account ?? { authMode: "chatgpt", isLoggedIn: true, plan: "pro" },
    modelList: async () => overrides.models ?? { models: [{ id: "gpt-5.6-sol", supportedReasoningEfforts: ["max"] }] },
    rateLimitsRead: async () => ({ limits: [] }),
    startThread: async () => "thread",
    turn: async () => ({ text: "" }),
    close: async () => undefined,
  };
}

test("preflight stops on API-key authentication", async () => {
  await assert.rejects(
    runPreflight(facade({ account: { authMode: "apiKey", isLoggedIn: true, plan: "pro" } }), loadConfig({ cwd: ".", mock: true })),
    (error: unknown) => error instanceof PreflightError && error.code === "API_KEY_AUTH_NOT_ALLOWED",
  );
});

test("preflight stops when Luna is missing instead of substituting a model", async () => {
  await assert.rejects(
    runPreflight(facade({}), loadConfig({ cwd: ".", mock: true })),
    (error: unknown) => error instanceof PreflightError && error.code === "REQUIRED_MODELS_UNAVAILABLE",
  );
});

