import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, rm, symlink } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

import {
  completeShellLine,
  isShellExitCommand,
  isShellHelpCommand,
  parseShellArgs,
  renderInteractiveHeader,
} from "../bin/vibegraph.js";

const cliPath = fileURLToPath(new URL("../bin/vibegraph.js", import.meta.url));

function runCli(args = [], options = {}) {
  return new Promise((resolve, reject) => {
    const entrypoint = options.entrypoint || cliPath;
    const child = spawn(process.execPath, [entrypoint, ...args], {
      cwd: path.dirname(path.dirname(cliPath)),
      env: { ...process.env, ...options.env },
      stdio: ["pipe", "pipe", "pipe"],
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
    child.stdin.end(options.stdin ?? "");
  });
}

test("no args with non-TTY stdin prints help and exits", async () => {
  const configDir = await mkdtemp(path.join(tmpdir(), "vg-shell-config-"));
  try {
    const result = await runCli([], { env: { VIBEGRAPH_CONFIG_DIR: configDir } });

    assert.equal(result.code, 0);
    assert.match(result.stdout, /Usage:/);
    assert.match(result.stdout, /vibegraph config show/);
    assert.equal(result.stderr, "");
  } finally {
    await rm(configDir, { recursive: true, force: true });
  }
});

test("explicit help keeps the full usage output", async () => {
  const configDir = await mkdtemp(path.join(tmpdir(), "vg-shell-config-"));
  try {
    const result = await runCli(["help"], { env: { VIBEGRAPH_CONFIG_DIR: configDir } });

    assert.equal(result.code, 0);
    assert.match(result.stdout, /VibeGraph CLI/);
    assert.match(result.stdout, /Usage:/);
    assert.match(result.stdout, /vibegraph projects import-local/);
  } finally {
    await rm(configDir, { recursive: true, force: true });
  }
});

test("symlinked bin invocation still runs the CLI", async (t) => {
  const configDir = await mkdtemp(path.join(tmpdir(), "vg-shell-config-"));
  const binDir = await mkdtemp(path.join(tmpdir(), "vg-shell-bin-"));
  const linkPath = path.join(binDir, process.platform === "win32" ? "vibegraph-cli.js" : "vibegraph-cli");
  try {
    try {
      await symlink(cliPath, linkPath);
    } catch (error) {
      if (error.code === "EPERM") {
        t.skip("symlink creation is not permitted on this system");
        return;
      }
      throw error;
    }
    const result = await runCli(["help"], {
      entrypoint: linkPath,
      env: { VIBEGRAPH_CONFIG_DIR: configDir },
    });

    assert.equal(result.code, 0);
    assert.match(result.stdout, /Usage:/);
  } finally {
    await rm(configDir, { recursive: true, force: true });
    await rm(binDir, { recursive: true, force: true });
  }
});

test("interactive header is compact and omits long usage", () => {
  const output = renderInteractiveHeader({ apiUrl: "http://api.example.test" }, "D:\\Projects\\demo");

  assert.match(output, /VibeGraph CLI v0\.1\.0/);
  assert.match(output, /D:\\Projects\\demo/);
  assert.match(output, /http:\/\/api\.example\.test/);
  assert.match(output, /Type \/help for commands, \/exit to quit\./);
  assert.doesNotMatch(output, /Usage:/);
});

test("shell command helpers recognize help and exit aliases", () => {
  for (const command of ["help", "/help"]) {
    assert.equal(isShellHelpCommand(command), true);
  }
  for (const command of ["exit", "/exit", "quit", "/quit"]) {
    assert.equal(isShellExitCommand(command), true);
  }
  assert.equal(isShellHelpCommand("projects"), false);
  assert.equal(isShellExitCommand("projects"), false);
});

test("shell completer suggests slash commands and command templates", () => {
  assert.deepEqual(completeShellLine("/h"), [["/help"], "/h"]);
  assert.deepEqual(completeShellLine("/"), [[
    "/help",
    "/exit",
    "/quit",
  ], "/"]);
  assert.deepEqual(completeShellLine("projects "), [[
    "projects list",
    "projects create --path ",
    "projects import-local --path ",
    "projects analyze ",
    "projects delete ",
    "projects push ",
    "projects status ",
  ], "projects "]);
  assert.deepEqual(completeShellLine("  config s"), [[
    "  config show",
    "  config set-url ",
  ], "  config s"]);
});

test("parseShellArgs preserves quoted strings and Windows paths", () => {
  const args = parseShellArgs('projects import-local --path "D:\\Users\\User\\My Project" --name "My Project"');

  assert.deepEqual(args, [
    "projects",
    "import-local",
    "--path",
    "D:\\Users\\User\\My Project",
    "--name",
    "My Project",
  ]);
});

test("parseShellArgs preserves UNC paths and trailing path backslashes", () => {
  assert.deepEqual(parseShellArgs('config set-url "\\\\server\\share"'), [
    "config",
    "set-url",
    "\\\\server\\share",
  ]);
  assert.deepEqual(parseShellArgs('projects push demo --root "D:\\Projects\\demo\\"'), [
    "projects",
    "push",
    "demo",
    "--root",
    "D:\\Projects\\demo\\",
  ]);
});

test("parseShellArgs supports escaped quotes inside quoted values", () => {
  const args = parseShellArgs('register --email test@example.com --password "p\\"ass" --name "Test User"');

  assert.deepEqual(args, [
    "register",
    "--email",
    "test@example.com",
    "--password",
    'p"ass',
    "--name",
    "Test User",
  ]);
});

test("parseShellArgs rejects unclosed quotes", () => {
  assert.throws(
    () => parseShellArgs('config set-url "http://localhost:8080'),
    /Unclosed quote/,
  );
});
