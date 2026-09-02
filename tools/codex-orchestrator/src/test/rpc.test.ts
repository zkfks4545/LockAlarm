import test from "node:test";
import assert from "node:assert/strict";
import { CodexAppServerClient } from "../rpc/client.js";
import { MockAppServerTransport } from "../rpc/mock.js";
import { detectAuthenticationKind, extractModels } from "../rpc/protocol.js";

test("mock App Server performs initialize/initialized and account/model/rate-limit calls", async () => {
  const server = new MockAppServerTransport({ rateLimits: [{ remaining: 7, limit: 10 }] });
  const client = new CodexAppServerClient({ transport: server });
  await client.initialize();
  const account = await client.accountRead();
  const models = await client.modelList();
  const limits = await client.rateLimitsRead();
  assert.equal(detectAuthenticationKind(account), "chatgpt");
  assert.ok(extractModels(models).some((model) => model.id === "gpt-5.6-sol"));
  assert.deepEqual(limits.limits, [{ remaining: 7, limit: 10 }]);
  assert.ok(server.notifications.some((message) => message.method === "initialized"));
  await client.close();
});

test("API-key account is observable and not silently converted", async () => {
  const server = new MockAppServerTransport({ account: { authMode: "apiKey", isLoggedIn: true } });
  const client = new CodexAppServerClient({ transport: server });
  await client.initialize();
  assert.equal(detectAuthenticationKind(await client.accountRead()), "api_key");
  await client.close();
});

test("turn completion notifications are available after the initial turn/start response", async () => {
  const server = new MockAppServerTransport();
  const client = new CodexAppServerClient({ transport: server });
  await client.initialize();
  const started = await client.startThread({ model: "gpt-5.6-sol", cwd: "." });
  const completed = await client.startTurnAndWait(String(started.id), "hello");
  assert.equal((completed as { text?: string }).text, "mock turn completed");
  await client.close();
});
