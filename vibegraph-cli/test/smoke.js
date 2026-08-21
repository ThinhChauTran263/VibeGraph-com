import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

const cliPath = fileURLToPath(new URL("../bin/vibegraph.js", import.meta.url));
const configDir = await mkdtemp(path.join(tmpdir(), "vibegraph-smoke-"));

function run(args) {
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [cliPath, ...args], {
      env: { ...process.env, VIBEGRAPH_CONFIG_DIR: configDir },
      stdio: ["ignore", "pipe", "pipe"],
    });
    let stdout = "";
    let stderr = "";
    child.stdout.on("data", (chunk) => { stdout += chunk; });
    child.stderr.on("data", (chunk) => { stderr += chunk; });
    child.on("error", reject);
    child.on("close", (code) => resolve({ code, stdout, stderr }));
  });
}

try {
  const help = await run(["help"]);
  if (help.code !== 0 || !help.stdout.includes("Usage:")) throw new Error("help command failed");
  const version = await run(["--version"]);
  if (version.code !== 0 || !/^\d+\.\d+\.\d+\s*$/.test(version.stdout)) throw new Error("version command failed");
  const config = await run(["config", "show"]);
  if (config.code !== 0 || !config.stdout.includes('"apiKeyConfigured": false')) throw new Error("config command failed");
  console.log("CLI smoke test passed with isolated config.");
} finally {
  await rm(configDir, { recursive: true, force: true });
}
