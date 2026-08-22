import { test } from "node:test";
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { createServer } from "node:http";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const cliPath = fileURLToPath(new URL("../bin/vibegraph.js", import.meta.url));

async function createConfig(apiUrl) {
  const configDir = await mkdtemp(path.join(tmpdir(), "vg-mcp-proxy-"));
  await writeFile(path.join(configDir, "config.json"), `${JSON.stringify({
    apiUrl,
    apiKey: "vbg_mcpproxy12345678secret",
  })}\n`, "utf8");
  return configDir;
}

function runProxy(configDir, stdin, timeoutMs = 2_000) {
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [cliPath, "mcp-proxy", "--stdio"], {
      env: { ...process.env, VIBEGRAPH_CONFIG_DIR: configDir },
      stdio: ["pipe", "pipe", "pipe"],
    });
    let stdout = "";
    let stderr = "";
    const timeout = setTimeout(() => {
      child.kill();
      reject(new Error(`MCP proxy timed out. stdout=${stdout} stderr=${stderr}`));
    }, timeoutMs);
    child.stdout.on("data", (chunk) => { stdout += chunk.toString(); });
    child.stderr.on("data", (chunk) => { stderr += chunk.toString(); });
    child.on("error", reject);
    child.on("close", (code) => {
      clearTimeout(timeout);
      resolve({ code, stdout, stderr });
    });
    child.stdin.end(stdin);
  });
}

async function listen(server) {
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const address = server.address();
  return `http://127.0.0.1:${address.port}`;
}

test("MCP proxy returns an auth error and continues with the next request", async () => {
  let requestCount = 0;
  const server = createServer((request, response) => {
    requestCount += 1;
    response.setHeader("content-type", "application/json");
    if (requestCount === 1) {
      response.statusCode = 401;
      response.end(JSON.stringify({ error: "unauthorized" }));
      return;
    }
    response.end(JSON.stringify({ jsonrpc: "2.0", id: 2, result: { tools: [] } }));
  });
  const configDir = await createConfig(await listen(server));
  try {
    const result = await runProxy(configDir, [
      JSON.stringify({ jsonrpc: "2.0", id: 1, method: "tools/list", params: {} }),
      JSON.stringify({ jsonrpc: "2.0", id: 2, method: "tools/list", params: {} }),
      "",
    ].join("\n"));
    const messages = result.stdout.trim().split(/\r?\n/).map(JSON.parse);

    assert.equal(result.code, 0);
    assert.equal(result.stderr, "");
    assert.equal(messages[0].id, 1);
    assert.equal(messages[0].error.code, -32001);
    assert.match(messages[0].error.message, /key change/);
    assert.deepEqual(messages[1], { jsonrpc: "2.0", id: 2, result: { tools: [] } });
  } finally {
    server.close();
    await rm(configDir, { recursive: true, force: true });
  }
});

test("MCP proxy reports invalid JSON and keeps processing stdin", async () => {
  const server = createServer((request, response) => {
    response.setHeader("content-type", "application/json");
    response.end(JSON.stringify({ jsonrpc: "2.0", id: 2, result: { ok: true } }));
  });
  const configDir = await createConfig(await listen(server));
  try {
    const result = await runProxy(configDir, [
      "{invalid-json",
      JSON.stringify({ jsonrpc: "2.0", id: 2, method: "tools/list", params: {} }),
      "",
    ].join("\n"));
    const messages = result.stdout.trim().split(/\r?\n/).map(JSON.parse);

    assert.equal(result.code, 0);
    assert.equal(messages[0].id, null);
    assert.equal(messages[0].error.code, -32700);
    assert.deepEqual(messages[1], { jsonrpc: "2.0", id: 2, result: { ok: true } });
  } finally {
    server.close();
    await rm(configDir, { recursive: true, force: true });
  }
});

test("MCP proxy forwards an SSE response without waiting for the stream to close", async () => {
  const server = createServer((request, response) => {
    response.writeHead(200, {
      "content-type": "text/event-stream",
      "cache-control": "no-cache",
      connection: "keep-alive",
    });
    response.write(`data: ${JSON.stringify({ jsonrpc: "2.0", id: 1, result: { ok: true } })}\n\n`);
  });
  const configDir = await createConfig(await listen(server));
  const startedAt = Date.now();
  try {
    const result = await runProxy(
      configDir,
      `${JSON.stringify({ jsonrpc: "2.0", id: 1, method: "tools/list", params: {} })}\n`,
    );

    assert.equal(result.code, 0);
    assert.deepEqual(JSON.parse(result.stdout.trim()), {
      jsonrpc: "2.0",
      id: 1,
      result: { ok: true },
    });
    assert.ok(Date.now() - startedAt < 1_500);
  } finally {
    server.closeAllConnections?.();
    server.close();
    await rm(configDir, { recursive: true, force: true });
  }
});

test("MCP doctor verifies initialize and a non-empty tools list", async () => {
  const server = createServer((request, response) => {
    let body = "";
    request.on("data", (chunk) => { body += chunk; });
    request.on("end", () => {
      const message = JSON.parse(body);
      response.setHeader("content-type", "application/json");
      if (message.method === "initialize") {
        response.end(JSON.stringify({
          jsonrpc: "2.0",
          id: message.id,
          result: {
            protocolVersion: "2025-06-18",
            capabilities: { tools: {} },
            serverInfo: { name: "VibeGraph Test", version: "1.0.0" },
          },
        }));
        return;
      }
      if (message.method === "notifications/initialized") {
        response.statusCode = 202;
        response.end();
        return;
      }
      response.end(JSON.stringify({
        jsonrpc: "2.0",
        id: message.id,
        result: { tools: [{ name: "list_projects", inputSchema: { type: "object" } }] },
      }));
    });
  });
  const configDir = await createConfig(await listen(server));
  try {
    const result = await new Promise((resolve, reject) => {
      const child = spawn(process.execPath, [cliPath, "mcp", "doctor"], {
        env: { ...process.env, VIBEGRAPH_CONFIG_DIR: configDir },
        stdio: ["ignore", "pipe", "pipe"],
      });
      let stdout = "";
      let stderr = "";
      child.stdout.on("data", (chunk) => { stdout += chunk.toString(); });
      child.stderr.on("data", (chunk) => { stderr += chunk.toString(); });
      child.on("error", reject);
      child.on("close", (code) => resolve({ code, stdout, stderr }));
    });

    assert.equal(result.code, 0);
    assert.equal(result.stderr, "");
    assert.match(result.stdout, /"status": "ready"/);
    assert.match(result.stdout, /"toolCount": 1/);
    assert.match(result.stdout, /"list_projects"/);
  } finally {
    server.close();
    await rm(configDir, { recursive: true, force: true });
  }
});
