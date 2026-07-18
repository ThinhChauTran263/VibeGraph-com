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

test("auth set-key persists the key while config and status only show a masked value", async () => {
  const configDir = await mkdtemp(path.join(tmpdir(), "vg-auth-config-"));
  const apiKey = "vbg_abcd1234secretwxyz";
  const env = { VIBEGRAPH_CONFIG_DIR: configDir };
  try {
    const setResult = await runCli(["auth", "set-key", apiKey], env);
    assert.equal(setResult.code, 0);
    assert.doesNotMatch(setResult.stdout, new RegExp(apiKey));

    const saved = JSON.parse(await readFile(path.join(configDir, "config.json"), "utf8"));
    assert.equal(saved.apiKey, apiKey);

    const showResult = await runCli(["config", "show"], env);
    assert.equal(showResult.code, 0);
    assert.match(showResult.stdout, /vbg_abcd\.\.\.wxyz/);
    assert.doesNotMatch(showResult.stdout, new RegExp(apiKey));

    const statusResult = await runCli(["auth", "status"], env);
    assert.equal(statusResult.code, 0);
    assert.match(statusResult.stdout, /vbg_abcd\.\.\.wxyz/);
    assert.doesNotMatch(statusResult.stdout, new RegExp(apiKey));
  } finally {
    await rm(configDir, { recursive: true, force: true });
  }
});

test("auth clear removes only the stored API key", async () => {
  const configDir = await mkdtemp(path.join(tmpdir(), "vg-auth-config-"));
  const env = { VIBEGRAPH_CONFIG_DIR: configDir };
  try {
    await writeFile(
      path.join(configDir, "config.json"),
      `${JSON.stringify({ token: "legacy-jwt", user: { email: "user@example.test" } })}\n`,
      "utf8",
    );
    await runCli(["auth", "set-key", "vbg_abcd1234secretwxyz"], env);
    const clearResult = await runCli(["auth", "clear"], env);

    assert.equal(clearResult.code, 0);
    const saved = JSON.parse(await readFile(path.join(configDir, "config.json"), "utf8"));
    assert.equal("apiKey" in saved, false);
    assert.equal(saved.token, "legacy-jwt");
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
