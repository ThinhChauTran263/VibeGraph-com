import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, readFile, rm, symlink } from "node:fs/promises";
import { createServer } from "node:http";
import { tmpdir } from "node:os";
import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

import {
  buildLiveSuggestionClearSequence,
  completeShellLine,
  getNextSuggestionIndex,
  getSuggestionWindow,
  getShellSuggestions,
  getSelectedSuggestionLine,
  isShellExitCommand,
  isShellHelpCommand,
  parsePushCommandArgs,
  parseShellArgs,
  parseWatchCommandArgs,
  renderInteractiveHeader,
  renderShellSuggestionPanel,
  truncateTerminalText,
} from "../bin/vibegraph.js";

const cliPath = fileURLToPath(new URL("../bin/vibegraph.js", import.meta.url));
const SHELL_SUGGESTION_COMMANDS = [
  "help",
  "exit",
  "quit",
  "doctor",
  "mcp config",
  "mcp install ",
  "login",
  "key status",
  "key list",
  "key change",
  "key clear",
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
    assert.match(result.stdout, /vibegraph login \[--no-browser\]/);
    assert.match(result.stdout, /vibegraph projects import-local/);
  } finally {
    await rm(configDir, { recursive: true, force: true });
  }
});

test("login rejects raw API-key shorthand without account ownership", async () => {
  const configDir = await mkdtemp(path.join(tmpdir(), "vg-shell-config-"));
  try {
    const result = await runCli(["login", "vbg_simple123456"], {
      env: { VIBEGRAPH_CONFIG_DIR: configDir },
    });

    assert.equal(result.code, 2);
    assert.match(result.stderr, /cannot verify account ownership/i);
  } finally {
    await rm(configDir, { recursive: true, force: true });
  }
});

test("browser login exchanges a project credential without printing the secret", async () => {
  const configDir = await mkdtemp(path.join(tmpdir(), "vg-browser-login-"));
  const requests = [];
  const server = createServer((request, response) => {
    let body = "";
    request.on("data", (chunk) => { body += chunk; });
    request.on("end", () => {
      requests.push({ url: request.url, body: body ? JSON.parse(body) : null });
      response.setHeader("content-type", "application/json");
      if (request.url === "/api/cli/device/start") {
        response.end(JSON.stringify({ success: true, data: {
          requestId: "11111111-1111-1111-1111-111111111111",
          deviceCode: "device-code",
          userCode: "ABCD-EFGH",
          verificationUri: "https://app.example.test/cli/authorize",
          verificationUriComplete: "https://app.example.test/cli/authorize?request=1#secret=x",
          pollToken: "poll-token",
          intervalSeconds: 0,
          expiresAt: new Date(Date.now() + 60_000).toISOString(),
        }}));
        return;
      }
      response.end(JSON.stringify({ success: true, data: {
        status: "APPROVED",
        apiKey: "vbg_browser12345678secret",
        apiKeyId: "key-1",
        projectId: "project-1",
        projectName: "Demo",
        availableKeys: [{
          id: "key-1",
          keyPrefix: "vbg_brow",
          name: "Demo key",
          project: { id: "project-1", name: "Demo" },
          revealable: true,
        }],
      }}));
    });
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  try {
    const address = server.address();
    const result = await runCli(["login", "--no-browser"], {
      env: {
        VIBEGRAPH_CONFIG_DIR: configDir,
        VIBEGRAPH_API_URL: `http://127.0.0.1:${address.port}`,
      },
    });

    assert.equal(result.code, 0);
    assert.match(result.stdout, /ABCD-EFGH/);
    assert.match(result.stdout, /Connected to Demo/);
    assert.doesNotMatch(result.stdout, /vbg_browser12345678secret/);
    const config = JSON.parse(await readFile(path.join(configDir, "config.json"), "utf8"));
    assert.equal(config.apiKey, "vbg_browser12345678secret");
    assert.equal(config.apiKeyId, "key-1");
    assert.equal(config.project.id, "project-1");
    assert.equal(config.apiKeys[0].keyPrefix, "vbg_brow");
    assert.doesNotMatch(JSON.stringify(config.apiKeys), /vbg_browser12345678secret/);
    assert.equal(requests[0].body.client, "vibegraph-cli");
    assert.equal(requests[0].body.intent, "LOGIN");
    assert.deepEqual(requests.map(({ url }) => url), [
      "/api/cli/device/start",
      "/api/cli/device/token",
    ]);
  } finally {
    server.close();
    await rm(configDir, { recursive: true, force: true });
  }
});

test("browser login explains when production device authorization is not public", async () => {
  const configDir = await mkdtemp(path.join(tmpdir(), "vg-browser-login-401-"));
  const server = createServer((request, response) => {
    response.statusCode = 401;
    response.setHeader("content-type", "application/json");
    response.end(JSON.stringify({
      success: false,
      error: { code: "UNAUTHORIZED", message: "Authentication required" },
    }));
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  try {
    const address = server.address();
    const result = await runCli(["login", "--no-browser"], {
      env: {
        VIBEGRAPH_CONFIG_DIR: configDir,
        VIBEGRAPH_API_URL: `http://127.0.0.1:${address.port}`,
      },
    });

    assert.equal(result.code, 3);
    assert.match(result.stderr, /backend must permit anonymous POST \/api\/cli\/device\/start/i);
    assert.match(result.stderr, /Deploy the current backend build/i);
  } finally {
    server.close();
    await rm(configDir, { recursive: true, force: true });
  }
});

test("slash-prefixed raw key commands are rejected without ownership proof", async () => {
  const configDir = await mkdtemp(path.join(tmpdir(), "vg-shell-config-"));
  try {
    const result = await runCli(["/key", "add", "vbg_slash123456"], {
      env: { VIBEGRAPH_CONFIG_DIR: configDir },
    });

    assert.equal(result.code, 2);
    assert.match(result.stderr, /cannot verify account ownership/i);
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
    "/mcp config",
    "/mcp install ",
    "/login",
    "/key status",
    "/key list",
    "/key change",
    "/key clear",
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
    "auth status",
    "auth clear",
  ], "auth "]);
  assert.deepEqual(completeShellLine("key "), [[
    "key status",
    "key list",
    "key change",
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

test("live shell suggestion panel stays within a narrow terminal", () => {
  const width = 48;
  const panel = renderShellSuggestionPanel("/", 4, null, width);
  const plainPanel = panel.replace(/\x1B\[[0-?]*[ -\/]*[@-~]/g, "");

  for (const line of plainPanel.split("\n")) {
    assert.ok(line.length <= width, `${line.length} exceeds terminal width ${width}: ${line}`);
  }
  assert.match(plainPanel, /\.\.\./);
  assert.match(plainPanel, /> mcp config/);
});

test("live shell redraw clears only the previous suggestion rows", () => {
  const sequence = buildLiveSuggestionClearSequence(3);

  assert.equal(sequence.match(/\x1b\[2K/g)?.length, 3);
  assert.doesNotMatch(sequence, /\x1b\[J/);
  assert.equal(buildLiveSuggestionClearSequence(0), "");
});

test("terminal text truncation handles narrow widths", () => {
  assert.equal(truncateTerminalText("abcdef", 6), "abcdef");
  assert.equal(truncateTerminalText("abcdef", 4), "a...");
  assert.equal(truncateTerminalText("abcdef", 1), ".");
  assert.equal(truncateTerminalText("abcdef", 0), "");
});

test("shell suggestion selection wraps in both directions", () => {
  assert.equal(getNextSuggestionIndex(-1, "down", 3), 0);
  assert.equal(getNextSuggestionIndex(0, "down", 3), 1);
  assert.equal(getNextSuggestionIndex(2, "down", 3), 0);
  assert.equal(getNextSuggestionIndex(-1, "up", 3), 2);
  assert.equal(getNextSuggestionIndex(0, "up", 3), 2);
  assert.equal(getNextSuggestionIndex(0, "down", 0), -1);
});

test("arrow selection keeps the typed buffer until the user accepts it", () => {
  const suggestions = getShellSuggestions("/", Number.POSITIVE_INFINITY);

  assert.equal(getSelectedSuggestionLine("/", suggestions, 0), "/help");
  assert.equal(getSelectedSuggestionLine("projects p", getShellSuggestions("projects p"), 0), "projects push ");
  assert.equal(getSelectedSuggestionLine("/", suggestions, -1), null);
  assert.equal(getSelectedSuggestionLine("/", suggestions, suggestions.length), null);
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

  assert.match(panel, /> login/);
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

test("push and watch parsers reject missing roots and unknown options", () => {
  assert.throws(() => parsePushCommandArgs(["--root"]), /Missing --root/);
  assert.throws(() => parseWatchCommandArgs(["--unknown", "value"]), /Unknown option/);
});
