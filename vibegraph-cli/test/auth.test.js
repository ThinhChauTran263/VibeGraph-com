import { test } from "node:test";
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const cliPath = fileURLToPath(new URL("../bin/vibegraph.js", import.meta.url));

function runCli(args, env) {
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [cliPath, ...args], {
      cwd: path.dirname(path.dirname(cliPath)),
      env: { ...process.env, ...env },
      stdio: ["ignore", "pipe", "pipe"],
    });
    let stdout = "";
    let stderr = "";
    child.stdout.on("data", (chunk) => {
      stdout += chunk.toString();
    });
    child.stderr.on("data", (chunk) => {
      stderr += chunk.toString();
    });
    child.on("error", reject);
    child.on("close", (code) => resolve({ code, stdout, stderr }));
  });
}

test("manual key set-key is rejected so ownership is established by browser login", async () => {
  const configDir = await mkdtemp(path.join(tmpdir(), "vg-auth-config-"));
  const apiKey = "vbg_abcd1234secretwxyz";
  const env = { VIBEGRAPH_CONFIG_DIR: configDir };
  try {
    const setResult = await runCli(["key", "set-key", apiKey], env);
    assert.equal(setResult.code, 2);
    assert.match(setResult.stderr, /cannot verify account ownership/i);
    assert.doesNotMatch(setResult.stderr, new RegExp(apiKey));
  } finally {
    await rm(configDir, { recursive: true, force: true });
  }
});

test("key clear removes the selected project credential", async () => {
  const configDir = await mkdtemp(path.join(tmpdir(), "vg-auth-config-"));
  const env = { VIBEGRAPH_CONFIG_DIR: configDir };
  try {
    await writeFile(
      path.join(configDir, "config.json"),
      `${JSON.stringify({ token: "legacy-jwt", user: { email: "user@example.test" } })}\n`,
      "utf8",
    );
    await writeFile(
      path.join(configDir, "config.json"),
      `${JSON.stringify({ token: "legacy-jwt", apiKey: "vbg_abcd1234secretwxyz", apiKeyId: "key-1", project: { id: "project-1" } })}\n`,
      "utf8",
    );
    const clearResult = await runCli(["key", "clear"], env);

    assert.equal(clearResult.code, 0);
    const saved = JSON.parse(await readFile(path.join(configDir, "config.json"), "utf8"));
    assert.equal("apiKey" in saved, false);
    assert.equal(saved.token, "legacy-jwt");
    assert.equal("apiKeyId" in saved, false);
    assert.equal("project" in saved, false);
  } finally {
    await rm(configDir, { recursive: true, force: true });
  }
});

test("environment API key and API URL override stored config without exposing the key", async () => {
  const configDir = await mkdtemp(path.join(tmpdir(), "vg-auth-config-"));
  const apiKey = "vbg_envkey12345678wxyz";
  try {
    const result = await runCli(["config", "show"], {
      VIBEGRAPH_CONFIG_DIR: configDir,
      VIBEGRAPH_API_KEY: apiKey,
      VIBEGRAPH_API_URL: "https://api.example.test/",
    });

    assert.equal(result.code, 0);
    assert.match(result.stdout, /https:\/\/api\.example\.test/);
    assert.match(result.stdout, /vbg_envk\.\.\.wxyz/);
    assert.doesNotMatch(result.stdout, new RegExp(apiKey));
  } finally {
    await rm(configDir, { recursive: true, force: true });
  }
});

test("logout clears the selected project credential and cached key metadata", async () => {
  const configDir = await mkdtemp(path.join(tmpdir(), "vg-logout-config-"));
  try {
    await writeFile(
      path.join(configDir, "config.json"),
      `${JSON.stringify({
        token: "legacy-jwt",
        refreshToken: "refresh-token",
        user: { email: "user@example.test" },
        apiKey: "vbg_abcd1234secretwxyz",
        apiKeyId: "key-1",
        project: { id: "project-1", name: "Demo" },
        apiKeys: [{ id: "key-1", keyPrefix: "vbg_abcd", project: { name: "Demo" } }],
      })}\n`,
      "utf8",
    );
    const result = await runCli(["logout"], { VIBEGRAPH_CONFIG_DIR: configDir });
    assert.equal(result.code, 0);
    const saved = JSON.parse(await readFile(path.join(configDir, "config.json"), "utf8"));
    for (const field of ["token", "refreshToken", "user", "apiKey", "apiKeyId", "project", "apiKeys"]) {
      assert.equal(field in saved, false, field);
    }
  } finally {
    await rm(configDir, { recursive: true, force: true });
  }
});
