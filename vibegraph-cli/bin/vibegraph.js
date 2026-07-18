#!/usr/bin/env node

import { realpathSync } from "node:fs";
import { createHash } from "node:crypto";
import { chmod, mkdir, readFile, writeFile, rm } from "node:fs/promises";
import { homedir } from "node:os";
import path from "node:path";
import { createInterface, emitKeypressEvents } from "node:readline";
import { fileURLToPath, pathToFileURL } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/** Helper to dynamically import a lib module (Windows-safe). */
function libImport(moduleName) {
  const modPath = path.join(__dirname, "..", "lib", moduleName);
  return import(pathToFileURL(modPath).href);
}

const CONFIG_DIR = process.env.VIBEGRAPH_CONFIG_DIR || path.join(homedir(), ".vibegraph");
const CONFIG_FILE = path.join(CONFIG_DIR, "config.json");
const DEFAULT_API_URL = "http://localhost:8080";
const CLI_VERSION = "0.1.0";
const SHELL_COMMANDS = [
  { command: "/help", description: "Show help and available commands" },
  { command: "/exit", description: "Exit the VibeGraph shell" },
  { command: "/quit", description: "Exit the VibeGraph shell" },
  { command: "help", description: "Show help and available commands" },
  { command: "exit", description: "Exit the VibeGraph shell" },
  { command: "quit", description: "Exit the VibeGraph shell" },
  { command: "doctor", description: "Check backend health" },
  { command: "auth set-key ", description: "Store a project-bound API key" },
  { command: "auth status", description: "Show masked API-key status" },
  { command: "auth clear", description: "Clear the stored API key" },
  { command: "me", description: "Show the current authenticated user" },
  { command: "logout", description: "Clear local auth state" },
  { command: "config show", description: "Show API URL and auth state" },
  { command: "config set-url ", description: "Set backend API URL" },
  { command: "register --email ", description: "Create an account and log in" },
  { command: "login --email ", description: "Log in with email and password" },
  { command: "projects list", description: "List your projects" },
  { command: "projects create --path ", description: "Create a project from a backend path" },
  { command: "projects import-local --path ", description: "Import a local Docker-mounted project" },
  { command: "projects analyze ", description: "Analyze a project graph" },
  { command: "projects delete ", description: "Delete a project" },
  { command: "projects push ", description: "Push local file changes" },
  { command: "push --root ", description: "Push using the API-key project binding" },
  { command: "projects status ", description: "Show project status" },
  { command: "watch ", description: "Watch a local project and push changes" },
  { command: "watch --root ", description: "Watch using the API-key project binding" },
  { command: "ignore init", description: "Create a .vibegraphignore file" },
  { command: "ignore init --root ", description: "Create .vibegraphignore under a path" }
];
const ANSI = {
  reset: "\x1b[0m",
  bold: "\x1b[1m",
  orange: "\x1b[38;5;214m",
  blue: "\x1b[38;5;63m",
  purple: "\x1b[38;5;99m",
  cyan: "\x1b[36m",
  brightCyan: "\x1b[96m",
  green: "\x1b[32m",
  magenta: "\x1b[35m",
  dim: "\x1b[2m"
};

class CliError extends Error {
  constructor(message, exitCode = 1) {
    super(message);
    this.exitCode = exitCode;
  }
}

if (isDirectRun()) {
  main().catch((error) => {
    if (error instanceof CliError) {
      console.error(error.message);
      process.exit(error.exitCode);
    }
    console.error(error?.stack || String(error));
    process.exit(1);
  });
}

async function main() {
  const args = process.argv.slice(2);

  if (args.length === 0) {
    if (process.stdin.isTTY) {
      await startInteractiveShell();
      return;
    }
    printHelp();
    return;
  }

  await dispatchCommand(args);
}

async function dispatchCommand(args) {
  const [command = "help", ...rest] = args;

  switch (command) {
    case "help":
    case "--help":
    case "-h":
      printHelp();
      return;
    case "config":
      await handleConfig(rest);
      return;
    case "auth":
      await handleAuth(rest);
      return;
    case "register":
      await handleRegister(rest);
      return;
    case "login":
      await handleLogin(rest);
      return;
    case "logout":
      await saveConfig({ ...(await loadConfig()), token: undefined, user: undefined });
      console.log("Logged out.");
      return;
    case "me":
      await handleMe();
      return;
    case "projects":
      await handleProjects(rest);
      return;
    case "push":
      await handlePush(rest);
      return;
    case "watch":
      await handleWatch(rest);
      return;
    case "ignore":
      await handleIgnore(rest);
      return;
    case "doctor":
      await handleDoctor();
      return;
    default:
      throw new CliError(`Unknown command: ${command}\nRun: vibegraph help`, 2);
  }
}

function isDirectRun() {
  if (!process.argv[1]) {
    return false;
  }
  return realpathSync(fileURLToPath(import.meta.url)) === realpathSync(process.argv[1]);
}

function printHelp() {
  console.log(`${renderHeader()}

${renderHelpBody()}`);
}

function printShellHelp() {
  console.log(renderHelpBody());
}

function renderHelpBody() {
  return `Usage:
  vibegraph config show
  vibegraph config set-url <url>
  vibegraph auth set-key <apiKey>
  vibegraph auth status
  vibegraph auth clear
  vibegraph register --email <email> --password <password> --name <displayName>
  vibegraph login --email <email> --password <password>
  vibegraph logout
  vibegraph me
  vibegraph doctor

Projects:
  vibegraph projects list
  vibegraph projects create --path <backendPath> [--name <name>] [--watch]
  vibegraph projects import-local --path <backendPath> [--name <name>]
  vibegraph projects analyze <projectId>
  vibegraph projects delete <projectId>
  vibegraph projects push <projectId> --root <localPath> [--dry-run]
  vibegraph projects status <projectId>

Watch:
  vibegraph push --root <localPath> [--dry-run]
  vibegraph watch <projectId> --root <localPath>
  vibegraph watch --root <localPath>

Ignore:
  vibegraph ignore init [--root <path>]`;
}

async function startInteractiveShell() {
  const config = await loadConfig();
  console.log(renderInteractiveHeader(config));

  const readline = createInterface({
    input: process.stdin,
    output: process.stdout,
    prompt: "vibegraph> ",
    completer: completeShellLine,
    historySize: 100,
    removeHistoryDuplicates: true,
  });

  let processing = false;
  const refreshSuggestions = () => {
    if (!processing) {
      renderLiveSuggestions(readline.line || "");
    }
  };

  emitKeypressEvents(process.stdin, readline);
  process.stdin.on("keypress", refreshSuggestions);

  return new Promise((resolve) => {
    readline.on("line", async (input) => {
      processing = true;
      clearLiveSuggestions();
      const line = input.trim();
      if (!line) {
        processing = false;
        readline.prompt();
        return;
      }
      if (isShellExitCommand(line)) {
        readline.close();
        return;
      }
      if (isShellHelpCommand(line)) {
        printShellHelp();
        processing = false;
        readline.prompt();
        return;
      }

      try {
        await dispatchCommand(parseShellArgs(line));
      } catch (error) {
        console.error(error instanceof CliError ? error.message : error?.stack || String(error));
      } finally {
        processing = false;
        readline.prompt();
      }
    });

    readline.on("close", () => {
      process.stdin.off("keypress", refreshSuggestions);
      clearLiveSuggestions();
      resolve();
    });

    readline.prompt();
  });
}

function renderInteractiveHeader(config = {}, cwd = process.cwd()) {
  const line = (...parts) => parts.map(([text, color]) => colorize(text, color)).join("");
  const icon = [
    line(["    ", "dim"], ["●", "orange"], ["       ", "dim"], ["●", "brightCyan"], ["   ", "dim"]),
    line(["   ╱ ╲     ╱", "purple"], ["│", "brightCyan"], ["   ", "dim"]),
    line(["  ", "dim"], ["●", "blue"], ["───", "purple"], ["●", "brightCyan"], ["───", "brightCyan"], ["●", "brightCyan"], [" ", "dim"]),
    line(["   ╲ ╱   ╱ ", "purple"], ["│", "brightCyan"], ["   ", "dim"]),
    line(["    ", "dim"], ["●", "purple"], ["───", "brightCyan"], ["●", "brightCyan"], ["──", "brightCyan"], ["╱", "brightCyan"], ["   ", "dim"]),
    line(["        ╲╱", "brightCyan"], ["      ", "dim"]),
    line(["         ", "dim"], ["●", "blue"], ["      ", "dim"]),
  ];
  const text = [
    `${colorize("VibeGraph CLI", "bold")} ${colorize(`v${CLI_VERSION}`, "dim")}`,
    `${colorize("Path", "dim")}: ${cwd}`,
    `${colorize("API", "dim")}: ${apiUrl(config)}`,
    colorize("Type /help for commands, /exit to quit.", "dim"),
  ];
  return icon.map((row, index) => `${row}  ${text[index] || ""}`).join("\n");
}

function isShellExitCommand(command) {
  return ["/exit", "exit", "/quit", "quit"].includes(command.trim().toLowerCase());
}

function isShellHelpCommand(command) {
  return ["/help", "help"].includes(command.trim().toLowerCase());
}

function completeShellLine(line) {
  const leadingWhitespace = line.slice(0, line.length - line.trimStart().length);
  const suggestions = getShellSuggestions(line);
  return [suggestions.map(({ command }) => `${leadingWhitespace}${command}`), line];
}

function getShellSuggestions(line, limit = 8) {
  const normalized = line.trimStart().toLowerCase();
  if (!normalized) {
    return [];
  }
  const candidates = normalized.startsWith("/")
    ? SHELL_COMMANDS.filter(({ command }) => command.startsWith("/"))
    : SHELL_COMMANDS;
  return candidates
    .filter(({ command }) => command.toLowerCase().startsWith(normalized))
    .slice(0, limit);
}

function renderShellSuggestionPanel(line) {
  const suggestions = getShellSuggestions(line, 6);
  if (!suggestions.length) {
    return "";
  }
  const width = Math.max(...suggestions.map(({ command }) => command.length));
  return suggestions
    .map(({ command, description }) => {
      const padded = command.padEnd(width + 2, " ");
      return `${colorize(padded, "brightCyan")}${colorize(description, "dim")}`;
    })
    .join("\n");
}

function renderLiveSuggestions(line) {
  if (!process.stdout.isTTY) {
    return;
  }
  const panel = renderShellSuggestionPanel(line);
  process.stdout.write("\x1b[s\n\x1b[J");
  if (panel) {
    process.stdout.write(`${panel}\n`);
  }
  process.stdout.write("\x1b[u");
}

function clearLiveSuggestions() {
  if (process.stdout.isTTY) {
    process.stdout.write("\x1b[s\n\x1b[J\x1b[u");
  }
}

function parseShellArgs(line) {
  const args = [];
  let current = "";
  let quote = null;
  const trimmed = line.trim();

  for (let index = 0; index < trimmed.length; index += 1) {
    const char = trimmed[index];
    const next = trimmed[index + 1];
    const afterNext = trimmed[index + 2];

    if (quote && char === "\\" && next === quote && afterNext && !/\s/.test(afterNext)) {
      current += next;
      index += 1;
      continue;
    }

    if (quote) {
      if (char === quote) {
        quote = null;
      } else {
        current += char;
      }
      continue;
    }

    if (char === '"' || char === "'") {
      quote = char;
      continue;
    }

    if (/\s/.test(char)) {
      if (current) {
        args.push(current);
        current = "";
      }
      continue;
    }

    current += char;
  }

  if (quote) {
    throw new CliError("Unclosed quote in command", 2);
  }
  if (current) {
    args.push(current);
  }
  return args;
}

export {
  isShellExitCommand,
  isShellHelpCommand,
  completeShellLine,
  getShellSuggestions,
  apiRequest,
  handleDoctor,
  maskApiKey,
  parsePushCommandArgs,
  parseShellArgs,
  parseWatchCommandArgs,
  renderShellSuggestionPanel,
  renderInteractiveHeader,
};

function renderHeader() {
  const line = (...parts) => parts.map(([text, color]) => colorize(text, color)).join("");
  const icon = [
    line(["    ", "dim"], ["●", "orange"], ["       ", "dim"], ["●", "brightCyan"], ["   ", "dim"]),
    line(["   ╱ ╲     ╱", "purple"], ["│", "brightCyan"], ["   ", "dim"]),
    line(["  ", "dim"], ["●", "blue"], ["───", "purple"], ["●", "brightCyan"], ["───", "brightCyan"], ["●", "brightCyan"], [" ", "dim"]),
    line(["   ╲ ╱   ╱ ", "purple"], ["│", "brightCyan"], ["   ", "dim"]),
    line(["    ", "dim"], ["●", "purple"], ["───", "brightCyan"], ["●", "brightCyan"], ["──", "brightCyan"], ["╱", "brightCyan"], ["   ", "dim"]),
    line(["        ╲╱", "brightCyan"], ["      ", "dim"]),
    line(["         ", "dim"], ["●", "blue"], ["      ", "dim"])
  ];
  const text = [
    `${colorize("VibeGraph CLI", "bold")} ${colorize(`v${CLI_VERSION}`, "dim")}`,
    colorize("Local Patch - Graph Intelligence", "dim"),
    colorize(process.cwd(), "dim"),
    "",
    `${colorize("Aliases", "dim")}: vibegraph, vibegraph-cli`,
    `${colorize("API", "dim")}: ${DEFAULT_API_URL}`,
    ""
  ];
  return icon.map((row, index) => `${row}  ${text[index] || ""}`).join("\n");
}

function colorize(value, color) {
  if (!supportsColor()) {
    return value;
  }
  return `${ANSI[color]}${value}${ANSI.reset}`;
}

function supportsColor() {
  if (process.env.NO_COLOR) {
    return false;
  }
  return Boolean(process.stdout.isTTY || process.env.FORCE_COLOR);
}

async function handleConfig(args) {
  const subcommand = args.shift() || "show";
  const config = await loadConfig();

  if (subcommand === "show") {
    const apiKey = configuredApiKey(config);
    console.log(JSON.stringify({
      apiUrl: apiUrl(config),
      authenticated: Boolean(config.token),
      apiKey: maskApiKey(apiKey),
      apiKeyConfigured: Boolean(apiKey),
      apiKeySource: process.env.VIBEGRAPH_API_KEY ? "env" : (config.apiKey ? "config" : null),
      user: config.user || null,
      configFile: CONFIG_FILE
    }, null, 2));
    return;
  }

  if (subcommand === "set-url") {
    const url = args.shift();
    if (!url) {
      throw new CliError("Usage: vibegraph config set-url <url>", 2);
    }
    new URL(url);
    await saveConfig({ ...config, apiUrl: trimTrailingSlash(url) });
    console.log(`API URL set to ${trimTrailingSlash(url)}`);
    return;
  }

  if (subcommand === "reset") {
    await rm(CONFIG_FILE, { force: true });
    console.log("Config reset.");
    return;
  }

  throw new CliError(`Unknown config command: ${subcommand}`, 2);
}

async function handleAuth(args) {
  const subcommand = args.shift() || "status";
  const config = await loadConfig();

  if (subcommand === "set-key") {
    const apiKey = args.shift();
    if (!apiKey || apiKey.startsWith("--")) {
      throw new CliError("Usage: vibegraph auth set-key <apiKey>", 2);
    }
    validateApiKey(apiKey);
    await saveConfig({ ...config, apiKey });
    console.log(`API key saved: ${maskApiKey(apiKey)}`);
    return;
  }

  if (subcommand === "clear") {
    await saveConfig({ ...config, apiKey: undefined });
    console.log("Stored API key cleared.");
    return;
  }

  if (subcommand === "status") {
    const apiKey = configuredApiKey(config);
    console.log(JSON.stringify({
      apiKey: maskApiKey(apiKey),
      configured: Boolean(apiKey),
      source: process.env.VIBEGRAPH_API_KEY ? "env" : (config.apiKey ? "config" : null),
      legacyTokenConfigured: Boolean(config.token),
    }, null, 2));
    return;
  }

  throw new CliError(`Unknown auth command: ${subcommand}`, 2);
}

async function handleRegister(args) {
  const options = parseOptions(args);
  const email = requiredOption(options, "email");
  const password = requiredOption(options, "password");
  const displayName = options.name || options.displayName;
  if (!displayName) {
    throw new CliError("Missing --name <displayName>", 2);
  }

  const response = await apiRequest("/api/auth/register", {
    method: "POST",
    body: { email, password, displayName }
  });
  await persistAuth(response);
  console.log(`Registered and logged in as ${response.user.email}`);
}

async function handleLogin(args) {
  const options = parseOptions(args);
  const email = requiredOption(options, "email");
  const password = requiredOption(options, "password");

  const response = await apiRequest("/api/auth/login", {
    method: "POST",
    body: { email, password }
  });
  await persistAuth(response);
  console.log(`Logged in as ${response.user.email}`);
}

async function handleMe() {
  const user = await apiRequest("/api/auth/me", { auth: true });
  console.log(JSON.stringify(user, null, 2));
}

async function handleProjects(args) {
  const subcommand = args.shift() || "list";

  if (subcommand === "list") {
    const projects = await apiRequest("/api/projects", { auth: true });
    if (!projects.length) {
      console.log("No projects.");
      return;
    }
    for (const project of projects) {
      console.log(`${project.id}\t${project.status || ""}\t${project.name || ""}\t${project.rootPath || ""}`);
    }
    return;
  }

  if (subcommand === "create") {
    const options = parseOptions(args);
    const rootPath = requiredOption(options, "path");
    const project = await apiRequest("/api/projects", {
      method: "POST",
      auth: true,
      body: {
        rootPath,
        name: options.name,
        autoWatch: Boolean(options.watch)
      }
    });
    printProject(project);
    return;
  }

  if (subcommand === "import-local") {
    const options = parseOptions(args);
    const requestPath = requiredOption(options, "path");
    const project = await apiRequest("/api/projects/import-local", {
      method: "POST",
      auth: true,
      body: {
        path: requestPath,
        name: options.name
      }
    });
    printProject(project);
    return;
  }

  if (subcommand === "analyze") {
    const projectId = args.shift();
    if (!projectId) {
      throw new CliError("Usage: vibegraph projects analyze <projectId>", 2);
    }
    const result = await apiRequest(`/api/projects/${encodeURIComponent(projectId)}/analyze`, {
      method: "POST",
      auth: true
    });
    console.log(JSON.stringify(result, null, 2));
    return;
  }

  if (subcommand === "delete") {
    const projectId = args.shift();
    if (!projectId) {
      throw new CliError("Usage: vibegraph projects delete <projectId>", 2);
    }
    await apiRequest(`/api/projects/${encodeURIComponent(projectId)}`, {
      method: "DELETE",
      auth: true,
      unwrap: false
    });
    console.log(`Deleted project ${projectId}`);
    return;
  }

  if (subcommand === "push") {
    const parsed = parsePushCommandArgs(args);
    if (!parsed.projectId) {
      throw new CliError("Usage: vibegraph projects push <projectId> --root <path> [--dry-run]", 2);
    }
    await executePushCommand(parsed);
    return;
  }

  if (subcommand === "status") {
    const projectId = args.shift();
    if (!projectId) {
      throw new CliError("Usage: vibegraph projects status <projectId>", 2);
    }
    const project = await apiRequest(`/api/projects/${encodeURIComponent(projectId)}`, { auth: true });
    console.log(JSON.stringify({
      id: project.id,
      name: project.name,
      status: project.status,
      rootPath: project.rootPath,
      lastAnalyzedAt: project.lastAnalyzedAt || null,
      nodeCount: project.nodeCount || null,
      edgeCount: project.edgeCount || null,
    }, null, 2));
    return;
  }

  throw new CliError(`Unknown projects command: ${subcommand}`, 2);
}

async function handleWatch(args) {
  const parsed = parseWatchCommandArgs(args);
  await assertPatchAuth(parsed.projectId);
  const { executeWatch } = await libImport("watch.js");
  await executeWatch(parsed.projectId, {
    root: parsed.root,
    snapshotId: await snapshotIdentity(parsed.projectId),
  }, apiRequest);
}

async function handlePush(args) {
  const parsed = parsePushCommandArgs(args);
  await executePushCommand(parsed);
}

async function executePushCommand(parsed) {
  await assertPatchAuth(parsed.projectId);
  const { executePush } = await libImport("push.js");
  await executePush(parsed.projectId, {
    root: parsed.root,
    dryRun: parsed.dryRun,
    snapshotId: await snapshotIdentity(parsed.projectId),
  }, apiRequest);
}

async function assertPatchAuth(projectId) {
  const config = await loadConfig();
  const apiKey = configuredApiKey(config);
  if (!projectId && !apiKey) {
    throw new CliError(
      "Root-only push/watch requires a project-bound API key. Run: vibegraph auth set-key <apiKey>",
      2,
    );
  }
  if (!apiKey && !config.token) {
    throw new CliError(
      "Authentication required. Run: vibegraph auth set-key <apiKey> or vibegraph login ...",
      2,
    );
  }
  if (!apiKey && projectId) {
    console.warn("Warning: no API key configured; using the legacy Bearer token. Run: vibegraph auth set-key <apiKey>");
  }
}

async function snapshotIdentity(projectId) {
  if (projectId) return projectId;
  const config = await loadConfig();
  return `key-${createHash("sha256").update(configuredApiKey(config)).digest("hex").slice(0, 16)}`;
}

async function handleIgnore(args) {
  const subcommand = args.shift() || "init";

  if (subcommand === "init") {
    const options = parseOptions(args);
    const rootDir = path.resolve(options.root || ".");
    const ignorePath = path.join(rootDir, ".vibegraphignore");

    // Check if file already exists
    try {
      await readFile(ignorePath);
      console.log(`.vibegraphignore already exists at ${ignorePath}`);
      return;
    } catch (error) {
      if (error.code !== "ENOENT") throw error;
    }

    const { generateDefaultIgnoreContent } = await libImport("ignore.js");
    await writeFile(ignorePath, generateDefaultIgnoreContent(), "utf8");
    console.log(`Created .vibegraphignore at ${rootDir}`);
    return;
  }

  throw new CliError(`Unknown ignore command: ${subcommand}`, 2);
}

async function handleDoctor() {
  const config = await loadConfig();
  const healthUrl = `${apiUrl(config)}/actuator/health`;
  const response = await fetch(healthUrl);
  if (!response.ok) {
    throw new CliError(`Backend health failed: HTTP ${response.status}`, 1);
  }
  const health = await response.json();
  const apiKey = configuredApiKey(config);
  let apiKeyStatus = "not configured";
  if (apiKey) {
    try {
      await apiRequest("/api/projects/current/patch", {
        method: "POST",
        auth: "api-key-only",
        body: { files: [], deletions: [], dryRun: true },
      });
      apiKeyStatus = "active";
    } catch (error) {
      apiKeyStatus = classifyApiKeyError(error);
    }
  }
  console.log(JSON.stringify({
    apiUrl: apiUrl(config),
    health: health.status,
    authenticated: Boolean(config.token),
    apiKey: maskApiKey(apiKey),
    apiKeyStatus,
  }, null, 2));
  if (apiKey && apiKeyStatus !== "active") {
    throw new CliError("API key authentication failed. Run: vibegraph auth status or set a replacement key.", 3);
  }
}

async function apiRequest(endpoint, options = {}) {
  const config = await loadConfig();
  const headers = {
    Accept: "application/json",
    ...(options.body ? { "Content-Type": "application/json" } : {})
  };

  if (options.auth) {
    const apiKey = configuredApiKey(config);
    const apiKeyOnly = options.auth === "api-key-only";
    const apiKeyFirst = options.auth === "api-key-first";
    if (apiKey && (apiKeyOnly || apiKeyFirst || !config.token)) {
      headers["X-API-Key"] = apiKey;
    } else if (config.token && !apiKeyOnly) {
      headers.Authorization = `Bearer ${config.token}`;
    } else {
      throw new CliError(
        "Authentication required. Run: vibegraph auth set-key <apiKey> or vibegraph login ...",
        2,
      );
    }
  }

  const response = await fetch(`${apiUrl(config)}${endpoint}`, {
    method: options.method || "GET",
    headers,
    body: options.body ? JSON.stringify(options.body) : undefined
  });

  if (response.status === 204) {
    return null;
  }

  const contentType = response.headers.get("content-type") || "";
  const payload = contentType.includes("application/json")
    ? await response.json()
    : await response.text();

  if (!response.ok) {
    const message = typeof payload === "object"
      ? payload.message || payload.error || JSON.stringify(payload)
      : payload;
    throw new CliError(`HTTP ${response.status}: ${message}`, response.status === 401 ? 3 : 1);
  }

  if (options.unwrap === false) {
    return payload;
  }
  return payload && typeof payload === "object" && "data" in payload ? payload.data : payload;
}

async function persistAuth(authResponse) {
  if (!authResponse?.token || !authResponse?.user) {
    throw new CliError("Auth response did not include token and user.", 1);
  }
  const config = await loadConfig();
  await saveConfig({
    ...config,
    token: authResponse.token,
    user: authResponse.user
  });
}

async function loadConfig() {
  try {
    return JSON.parse(await readFile(CONFIG_FILE, "utf8"));
  } catch (error) {
    if (error.code === "ENOENT") {
      return {};
    }
    throw error;
  }
}

async function saveConfig(config) {
  await mkdir(CONFIG_DIR, { recursive: true });
  const cleaned = Object.fromEntries(Object.entries(config).filter(([, value]) => value !== undefined));
  await writeFile(CONFIG_FILE, `${JSON.stringify(cleaned, null, 2)}\n`, { mode: 0o600 });
  await chmod(CONFIG_FILE, 0o600);
}

function apiUrl(config) {
  return trimTrailingSlash(process.env.VIBEGRAPH_API_URL || config.apiUrl || DEFAULT_API_URL);
}

function configuredApiKey(config) {
  const value = process.env.VIBEGRAPH_API_KEY || config.apiKey;
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function validateApiKey(apiKey) {
  if (!apiKey.startsWith("vbg_") || apiKey.length < 12 || /\s/.test(apiKey)) {
    throw new CliError("Invalid API key format. Expected a vbg_... key.", 2);
  }
}

function maskApiKey(apiKey) {
  if (!apiKey) return null;
  if (apiKey.length <= 12) return `${apiKey.slice(0, 4)}...${apiKey.slice(-4)}`;
  return `${apiKey.slice(0, 8)}...${apiKey.slice(-4)}`;
}

function classifyApiKeyError(error) {
  const message = error?.message || "request failed";
  if (message.includes("HTTP 401")) return "invalid";
  if (message.includes("HTTP 403")) return "disabled-or-locked";
  return "unavailable";
}

function trimTrailingSlash(value) {
  return value.replace(/\/+$/, "");
}

function parseOptions(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (!arg.startsWith("--")) {
      throw new CliError(`Unexpected argument: ${arg}`, 2);
    }
    const key = arg.slice(2);
    const next = args[index + 1];
    if (!next || next.startsWith("--")) {
      options[key] = true;
    } else {
      options[key] = next;
      index += 1;
    }
  }
  return options;
}

function requiredOption(options, name) {
  const value = options[name];
  if (!value || value === true) {
    throw new CliError(`Missing --${name} <value>`, 2);
  }
  return value;
}

function parsePushCommandArgs(args) {
  const remaining = [...args];
  const projectId = remaining[0] && !remaining[0].startsWith("--") ? remaining.shift() : null;
  const options = parseOptions(remaining);
  return {
    projectId,
    root: requiredOption(options, "root"),
    dryRun: Boolean(options["dry-run"]),
  };
}

function parseWatchCommandArgs(args) {
  const remaining = [...args];
  const projectId = remaining[0] && !remaining[0].startsWith("--") ? remaining.shift() : null;
  const options = parseOptions(remaining);
  return {
    projectId,
    root: requiredOption(options, "root"),
  };
}

function printProject(project) {
  console.log(JSON.stringify({
    id: project.id,
    name: project.name,
    status: project.status,
    rootPath: project.rootPath
  }, null, 2));
}
