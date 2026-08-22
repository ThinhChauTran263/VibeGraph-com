import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdir, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

async function importCliWithConfig(config) {
  const configDir = path.join(tmpdir(), `vg-api-config-${process.pid}-${Date.now()}`);
  await mkdir(configDir, { recursive: true });
  await writeFile(path.join(configDir, "config.json"), `${JSON.stringify(config)}\n`, "utf8");
  process.env.VIBEGRAPH_CONFIG_DIR = configDir;
  const module = await import(`../bin/vibegraph.js?api-test=${Date.now()}-${Math.random()}`);
  return { configDir, module };
}

test("apiRequest uses X-API-Key and omits Bearer for API-key-first requests", async () => {
  const rawKey = "vbg_abcd1234secretwxyz";
  const previousFetch = globalThis.fetch;
  const previousConfigDir = process.env.VIBEGRAPH_CONFIG_DIR;
  const { configDir, module } = await importCliWithConfig({
    apiUrl: "http://api.example.test",
    apiKey: rawKey,
    token: "legacy-jwt",
  });
  let captured;
  try {
    globalThis.fetch = async (url, options) => {
      captured = { url, options };
      return new Response(JSON.stringify({ success: true, data: { ok: true } }), {
        status: 200,
        headers: { "content-type": "application/json" },
      });
    };

    for (const endpoint of ["/api/projects/current/patch", "/api/projects/project-1/patch"]) {
      await module.apiRequest(endpoint, {
        method: "POST",
        auth: "api-key-first",
        body: { files: [], deletions: [], dryRun: true },
      });

      assert.equal(captured.url, `http://api.example.test${endpoint}`);
      assert.equal(captured.options.headers["X-API-Key"], rawKey);
      assert.equal("Authorization" in captured.options.headers, false);
    }
  } finally {
    globalThis.fetch = previousFetch;
    if (previousConfigDir === undefined) delete process.env.VIBEGRAPH_CONFIG_DIR;
    else process.env.VIBEGRAPH_CONFIG_DIR = previousConfigDir;
    await rm(configDir, { recursive: true, force: true });
  }
});

test("apiRequest falls back to the legacy Bearer token when no API key is configured", async () => {
  const previousFetch = globalThis.fetch;
  const previousConfigDir = process.env.VIBEGRAPH_CONFIG_DIR;
  const { configDir, module } = await importCliWithConfig({
    apiUrl: "http://api.example.test",
    token: "legacy-jwt",
  });
  let captured;
  try {
    globalThis.fetch = async (url, options) => {
      captured = { url, options };
      return new Response(JSON.stringify({ success: true, data: [] }), {
        status: 200,
        headers: { "content-type": "application/json" },
      });
    };

    await module.apiRequest("/api/projects/project-1/patch", {
      auth: "api-key-first",
    });

    assert.equal(captured.options.headers.Authorization, "Bearer legacy-jwt");
    assert.equal("X-API-Key" in captured.options.headers, false);
  } finally {
    globalThis.fetch = previousFetch;
    if (previousConfigDir === undefined) delete process.env.VIBEGRAPH_CONFIG_DIR;
    else process.env.VIBEGRAPH_CONFIG_DIR = previousConfigDir;
    await rm(configDir, { recursive: true, force: true });
  }
});

test("doctor checks health and validates a configured API key without printing it", async () => {
  const rawKey = "vbg_doctor12345678wxyz";
  const previousFetch = globalThis.fetch;
  const previousLog = console.log;
  const previousConfigDir = process.env.VIBEGRAPH_CONFIG_DIR;
  const { configDir, module } = await importCliWithConfig({
    apiUrl: "http://api.example.test",
    apiKey: rawKey,
  });
  const calls = [];
  let output = "";
  try {
    globalThis.fetch = async (url, options = {}) => {
      calls.push({ url: String(url), options });
      const body = String(url).endsWith("/actuator/health")
        ? { status: "UP" }
        : { success: true, data: [] };
      return new Response(JSON.stringify(body), {
        status: 200,
        headers: { "content-type": "application/json" },
      });
    };
    console.log = (value) => {
      output += String(value);
    };

    await module.handleDoctor();

    assert.equal(calls.length, 2);
    assert.equal(calls[1].url, "http://api.example.test/api/projects/current/patch");
    assert.equal(calls[1].options.headers["X-API-Key"], rawKey);
    assert.equal("Authorization" in calls[1].options.headers, false);
    assert.deepEqual(JSON.parse(calls[1].options.body), {
      files: [],
      deletions: [],
      dryRun: true,
    });
    assert.match(output, /"apiKeyStatus": "active"/);
    assert.match(output, /vbg_doct\.\.\.wxyz/);
    assert.doesNotMatch(output, new RegExp(rawKey));
  } finally {
    globalThis.fetch = previousFetch;
    console.log = previousLog;
    if (previousConfigDir === undefined) delete process.env.VIBEGRAPH_CONFIG_DIR;
    else process.env.VIBEGRAPH_CONFIG_DIR = previousConfigDir;
    await rm(configDir, { recursive: true, force: true });
  }
});

test("apiRequest formats backend error envelopes without [object Object]", async () => {
  const previousFetch = globalThis.fetch;
  const previousConfigDir = process.env.VIBEGRAPH_CONFIG_DIR;
  const { configDir, module } = await importCliWithConfig({
    apiUrl: "http://api.example.test",
    apiKey: "vbg_error12345678wxyz",
  });
  try {
    globalThis.fetch = async () =>
      new Response(
        JSON.stringify({
          success: false,
          error: {
            code: "API_KEY_ADMIN_LOCKED",
            message: "Administrator-locked API key cannot be changed",
          },
        }),
        {
          status: 403,
          headers: { "content-type": "application/json" },
        },
      );

    await assert.rejects(
      module.apiRequest("/api/projects/current/patch", {
        method: "POST",
        auth: "api-key-only",
        body: { files: [], deletions: [], dryRun: true },
      }),
      /HTTP 403: API_KEY_ADMIN_LOCKED: Administrator-locked API key cannot be changed/,
    );
  } finally {
    globalThis.fetch = previousFetch;
    if (previousConfigDir === undefined) delete process.env.VIBEGRAPH_CONFIG_DIR;
    else process.env.VIBEGRAPH_CONFIG_DIR = previousConfigDir;
    await rm(configDir, { recursive: true, force: true });
  }
});

test("apiRequest refreshes an expired legacy JWT once and retries the request", async () => {
  const previousFetch = globalThis.fetch;
  const previousConfigDir = process.env.VIBEGRAPH_CONFIG_DIR;
  const { configDir, module } = await importCliWithConfig({
    apiUrl: "https://api.example.test",
    token: "expired-token",
    refreshToken: "refresh-old",
    user: { email: "user@example.test" },
  });
  const calls = [];
  try {
    globalThis.fetch = async (url, options = {}) => {
      calls.push({ url: String(url), options });
      if (String(url).endsWith("/api/auth/refresh")) {
        return new Response(JSON.stringify({ success: true, data: { token: "fresh-token", user: { email: "user@example.test" } } }), {
          status: 200,
          headers: { "content-type": "application/json", "set-cookie": "vg_refresh=refresh-new; Path=/api/auth; HttpOnly" },
        });
      }
      const auth = options.headers.Authorization;
      return new Response(JSON.stringify(auth === "Bearer fresh-token"
        ? { success: true, data: { id: "user-1" } }
        : { success: false, error: { message: "expired" } }), {
        status: auth === "Bearer fresh-token" ? 200 : 401,
        headers: { "content-type": "application/json" },
      });
    };

    const result = await module.apiRequest("/api/auth/me", { auth: "jwt-only" });
    assert.deepEqual(result, { id: "user-1" });
    assert.equal(calls.length, 3);
  } finally {
    globalThis.fetch = previousFetch;
    if (previousConfigDir === undefined) delete process.env.VIBEGRAPH_CONFIG_DIR;
    else process.env.VIBEGRAPH_CONFIG_DIR = previousConfigDir;
    await rm(configDir, { recursive: true, force: true });
  }
});

test("buildMcpServerConfig uses the CLI proxy and never embeds the API key", async () => {
  const previousConfigDir = process.env.VIBEGRAPH_CONFIG_DIR;
  const rawKey = "vbg_mcp12345678secret";
  const { configDir, module } = await importCliWithConfig({
    apiUrl: "https://api.example.test",
    apiKey: rawKey,
    project: { id: "project-1", name: "Demo" },
  });
  try {
    const config = module.buildMcpServerConfig("vibegraph");

    assert.equal(config.mcpServers.vibegraph.command, process.execPath);
    assert.deepEqual(config.mcpServers.vibegraph.args.slice(-2), ["mcp-proxy", "--stdio"]);
    assert.doesNotMatch(JSON.stringify(config), new RegExp(rawKey));
  } finally {
    if (previousConfigDir === undefined) delete process.env.VIBEGRAPH_CONFIG_DIR;
    else process.env.VIBEGRAPH_CONFIG_DIR = previousConfigDir;
    await rm(configDir, { recursive: true, force: true });
  }
});

test("buildMcpServerConfig supports VS Code's servers format", async () => {
  const { module } = await importCliWithConfig({ apiKey: "vbg_mcp12345678secret" });
  const config = module.buildMcpServerConfig("custom-vibegraph", "vscode");

  assert.ok(config.servers["custom-vibegraph"]);
  assert.equal(config.servers["custom-vibegraph"].type, "stdio");
  assert.equal(config.servers["custom-vibegraph"].command, process.execPath);
  assert.deepEqual(config.servers["custom-vibegraph"].args.slice(-2), ["mcp-proxy", "--stdio"]);
});
