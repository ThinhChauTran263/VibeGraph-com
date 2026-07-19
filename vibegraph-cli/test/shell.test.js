import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, readFile, rm, symlink } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

import {
  completeShellLine,
  getNextSuggestionIndex,
  getSuggestionWindow,
  getShellSuggestions,
  isShellExitCommand,
  isShellHelpCommand,
  parsePushCommandArgs,
  parseShellArgs,
  parseWatchCommandArgs,
  renderInteractiveHeader,
  renderShellSuggestionPanel,
} from "../bin/vibegraph.js";

const cliPath = fileURLToPath(new URL("../bin/vibegraph.js", import.meta.url));
const SHELL_SUGGESTION_COMMANDS = [
  "help",
  "exit",
  "quit",
  "doctor",
  "login ",
  "login --key ",
  "key add ",
  "key set ",
  "key status",
  "key clear",
  "auth set-key ",
  "auth status",
  "auth clear",
  "me",
  "logout",
  "config show",
  "config set-url ",
  "register --email ",
  "login --email ",
  "projects list",
  "projects create --path ",
  "projects import-local --path ",
  "projects analyze ",
  "projects delete ",
  "projects push ",
  "push --root ",
  "projects status ",
  "watch ",
  "watch --root ",
  "ignore init",
  "ignore init --root ",
];

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
    assert.match(result.stdout, /vibegraph login <apiKey>/);
    assert.match(result.stdout, /vibegraph projects import-local/);
  } finally {
    await rm(configDir, { recursive: true, force: true });
  }
});

test("login accepts a project-bound API key shorthand", async () => {
  const configDir = await mkdtemp(path.join(tmpdir(), "vg-shell-config-"));
  try {
    const result = await runCli(["login", "vbg_simple123456"], {
      env: { VIBEGRAPH_CONFIG_DIR: configDir },
    });

    assert.equal(result.code, 0);
    assert.match(result.stdout, /API key saved: vbg_simp\.\.\.3456/);
    const config = JSON.parse(await readFile(path.join(configDir, "config.json"), "utf8"));
    assert.equal(config.apiKey, "vbg_simple123456");
  } finally {
    await rm(configDir, { recursive: true, force: true });
  }
});

test("slash-prefixed command palette entries execute like normal commands", async () => {
  const configDir = await mkdtemp(path.join(tmpdir(), "vg-shell-config-"));
  try {
    const result = await runCli(["/key", "add", "vbg_slash123456"], {
      env: { VIBEGRAPH_CONFIG_DIR: configDir },
    });

    assert.equal(result.code, 0);
    assert.match(result.stdout, /API key saved: vbg_slas\.\.\.3456/);
    const config = JSON.parse(await readFile(path.join(configDir, "config.json"), "utf8"));
    assert.equal(config.apiKey, "vbg_slash123456");
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
  const plainOutput = output.replace(/\x1B\[[0-?]*[ -\/]*[@-~]/g, "");

  assert.match(plainOutput, /VibeGraph CLI v0\.1\.0/);
  assert.match(plainOutput, /D:\\Projects\\demo/);
  assert.match(plainOutput, /http:\/\/api\.example\.test/);
  assert.match(plainOutput, /Type \/help for commands, \/exit to quit\./);
  assert.doesNotMatch(plainOutput, /Usage:/);
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
  assert.deepEqual(completeShellLine("/")[0], [
    "/help",
    "/exit",
    "/quit",
    "/doctor",
    "/login ",
    "/login --key ",
    "/key add ",
    "/key set ",
    "/key status",
    "/key clear",
    "/auth set-key ",
    "/auth status",
    "/auth clear",
    "/me",
    "/logout",
    "/config show",
    "/config set-url ",
    "/register --email ",
    "/login --email ",
    "/projects list",
    "/projects create --path ",
    "/projects import-local --path ",
    "/projects analyze ",
    "/projects delete ",
    "/projects push ",
    "/push --root ",
    "/projects status ",
    "/watch ",
    "/watch --root ",
    "/ignore init",
    "/ignore init --root ",
  ]);
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
  assert.deepEqual(completeShellLine("auth "), [[
    "auth set-key ",
    "auth status",
    "auth clear",
  ], "auth "]);
  assert.deepEqual(completeShellLine("key "), [[
    "key add ",
    "key set ",
    "key status",
    "key clear",
  ], "key "]);
  assert.deepEqual(completeShellLine("push "), [["push --root "], "push "]);
  assert.deepEqual(completeShellLine("watch --"), [["watch --root "], "watch --"]);
});

test("live shell suggestions filter as the user types", () => {
  assert.deepEqual(
    getShellSuggestions("/h").map(({ command }) => command),
    ["help"],
  );
  assert.deepEqual(
    getShellSuggestions("/").map(({ command }) => command),
    SHELL_SUGGESTION_COMMANDS,
  );
  assert.deepEqual(
    getShellSuggestions("projects p").map(({ command }) => command),
    ["projects push "],
  );
  assert.deepEqual(getShellSuggestions("").map(({ command }) => command), []);
});

test("live shell suggestion panel includes descriptions", () => {
  const panel = renderShellSuggestionPanel("/", 1);

  assert.match(panel, /help/);
  assert.match(panel, /Show help and available commands/);
  assert.match(panel, /exit/);
  assert.match(panel, /> exit/);
  assert.doesNotMatch(panel, /projects import-local/);
});

test("shell suggestion selection wraps in both directions", () => {
  assert.equal(getNextSuggestionIndex(-1, "down", 3), 0);
  assert.equal(getNextSuggestionIndex(0, "down", 3), 1);
  assert.equal(getNextSuggestionIndex(2, "down", 3), 0);
  assert.equal(getNextSuggestionIndex(-1, "up", 3), 2);
  assert.equal(getNextSuggestionIndex(0, "up", 3), 2);
  assert.equal(getNextSuggestionIndex(0, "down", 0), -1);
});

test("shell suggestion window scrolls while keeping six visible commands", () => {
  const suggestions = Array.from({ length: 10 }, (_, index) => ({
    command: `command-${index}`,
    description: `Description ${index}`,
  }));

  assert.deepEqual(getSuggestionWindow(suggestions, 5).map(({ command }) => command), [
    "command-0",
    "command-1",
    "command-2",
    "command-3",
    "command-4",
    "command-5",
  ]);
  assert.deepEqual(getSuggestionWindow(suggestions, 6).map(({ command }) => command), [
    "command-1",
    "command-2",
    "command-3",
    "command-4",
    "command-5",
    "command-6",
  ]);
  assert.deepEqual(getSuggestionWindow(suggestions, 9).map(({ command }) => command), [
    "command-4",
    "command-5",
    "command-6",
    "command-7",
    "command-8",
    "command-9",
  ]);
});

test("shell suggestion panel highlights a command below the first six entries", () => {
  const panel = renderShellSuggestionPanel("/", 6, getShellSuggestions("/"));

  assert.match(panel, /> key add/);
  assert.doesNotMatch(panel, /  help/);
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

test("push and watch parsers accept project-bound root-only commands and legacy project IDs", () => {
  assert.deepEqual(parsePushCommandArgs([]), {
    projectId: null,
    root: ".",
    dryRun: false,
  });
  assert.deepEqual(parsePushCommandArgs(["--root", "./repo", "--dry-run"]), {
    projectId: null,
    root: "./repo",
    dryRun: true,
  });
  assert.deepEqual(parsePushCommandArgs(["project-1", "--root", "./repo"]), {
    projectId: "project-1",
    root: "./repo",
    dryRun: false,
  });
  assert.deepEqual(parseWatchCommandArgs(["--root", "./repo"]), {
    projectId: null,
    root: "./repo",
  });
  assert.deepEqual(parseWatchCommandArgs([]), {
    projectId: null,
    root: ".",
  });
  assert.deepEqual(parseWatchCommandArgs(["project-1", "--root", "./repo"]), {
    projectId: "project-1",
    root: "./repo",
  });
});
