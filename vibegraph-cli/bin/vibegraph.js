#!/usr/bin/env node

import { readFileSync, realpathSync } from "node:fs";
import { createHash, randomBytes } from "node:crypto";
import { spawn } from "node:child_process";
import { chmod, mkdir, readFile, rename, writeFile, rm } from "node:fs/promises";
import { homedir, hostname } from "node:os";
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
const CLI_VERSION = JSON.parse(readFileSync(path.join(__dirname, "..", "package.json"), "utf8")).version;
const REQUEST_TIMEOUT_MS = 30_000;
let liveSuggestionLineCount = 0;
const SHELL_COMMANDS = [
  { command: "/help", description: "Show help and available commands" },
  { command: "/exit", description: "Exit the VibeGraph shell" },
  { command: "/quit", description: "Exit the VibeGraph shell" },
  { command: "help", description: "Show help and available commands" },
  { command: "exit", description: "Exit the VibeGraph shell" },
  { command: "quit", description: "Exit the VibeGraph shell" },
  { command: "doctor", description: "Check backend health" },
  { command: "mcp config", description: "Print MCP client configuration" },
  { command: "mcp install ", description: "Install authenticated MCP proxy (cursor, vscode, or generic)" },
  { command: "login", description: "Sign in and choose a project key in the browser" },
  { command: "key status", description: "Show masked API-key status" },
  { command: "key list", description: "List API keys available to this CLI" },
  { command: "key change", description: "Choose a different project API key" },
  { command: "key clear", description: "Clear the stored API key" },
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
      process.exitCode = error.exitCode;
      return;
    }
    console.error(error?.stack || String(error));
    process.exitCode = 1;
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
  let [command = "help", ...rest] = args;
  if (command.startsWith("/")) {
    command = command.slice(1);
  }

  switch (command) {
    case "help":
    case "--help":
    case "-h":
      printHelp();
      return;
    case "--version":
    case "-v":
      console.log(CLI_VERSION);
      return;
    case "config":
      await handleConfig(rest);
      return;
    case "auth":
      await handleAuth(rest);
      return;
    case "key":
      await handleAuth(rest);
      return;
    case "register":
      await handleRegister(rest);
      return;
    case "login":
      await handleLogin(rest);
      return;
    case "logout":
      await saveConfig({
        ...(await loadConfig()),
        token: undefined,
        refreshToken: undefined,
        user: undefined,
        apiKey: undefined,
        apiKeyId: undefined,
        project: undefined,
        apiKeys: undefined,
      });
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
    case "mcp":
      await handleMcp(rest);
      return;
    case "mcp-proxy":
      await handleMcpProxy(rest);
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
  vibegraph key status
  vibegraph key list
  vibegraph key change
  vibegraph key clear
  vibegraph config show
  vibegraph config set-url <url>
  vibegraph auth status
  vibegraph auth clear
  vibegraph register --email <email> --password <password> --name <displayName>
  vibegraph login --email <email> --password <password>
  vibegraph logout
  vibegraph me
  vibegraph doctor
  vibegraph mcp config [cursor|vscode|generic] [--name <serverName>]
  vibegraph mcp install <cursor|vscode|generic> [--path <file>]
  vibegraph login [--no-browser]
  vibegraph mcp-proxy --stdio

Projects:
  vibegraph projects list
  vibegraph projects create --path <backendPath> [--name <name>] [--watch]
  vibegraph projects import-local --path <backendPath> [--name <name>]
  vibegraph projects analyze <projectId>
  vibegraph projects delete <projectId>
  vibegraph projects push <projectId> --root <localPath> [--dry-run]
  vibegraph projects status <projectId>

Watch:
  vibegraph push [--root <localPath>] [--dry-run]
  vibegraph watch <projectId> --root <localPath>
  vibegraph watch [--root <localPath>]

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
  let visibleSuggestions = [];
  let selectedSuggestionIndex = -1;
  let keypressVersion = 0;
  let pendingSuggestionLine = null;
  const refreshSuggestions = (character, key = {}) => {
    if (!processing) {
      keypressVersion += 1;
      if (key.name === "up" || key.name === "down") {
        const suggestions = selectedSuggestionIndex >= 0
          ? visibleSuggestions
          : getShellSuggestions(readline.line || "", Number.POSITIVE_INFINITY);
        if (!suggestions.length) return;
        const direction = key.name;
        key.name = undefined;
        key.sequence = "";
        visibleSuggestions = suggestions;
        const previousSuggestionIndex = selectedSuggestionIndex;
        selectedSuggestionIndex = getNextSuggestionIndex(
          selectedSuggestionIndex,
          direction,
          suggestions.length,
        );
        renderLiveSuggestionSelection(
          readline.line || "",
          previousSuggestionIndex,
          selectedSuggestionIndex,
          visibleSuggestions,
        );
        return;
      }
      if (key.name === "tab" && selectedSuggestionIndex >= 0) {
        const selectedLine = getSelectedSuggestionLine(
          readline.line || "",
          visibleSuggestions,
          selectedSuggestionIndex,
        );
        if (selectedLine) {
          key.name = undefined;
          key.sequence = "";
          readline.write(null, { ctrl: true, name: "u" });
          readline.write(selectedLine);
          selectedSuggestionIndex = -1;
          visibleSuggestions = [];
          clearLiveSuggestions();
        }
        return;
      }
      if ((key.name === "return" || key.name === "enter") && selectedSuggestionIndex >= 0) {
        pendingSuggestionLine = getSelectedSuggestionLine(
          readline.line || "",
          visibleSuggestions,
          selectedSuggestionIndex,
        );
        return;
      }
      const refreshVersion = keypressVersion;
      setImmediate(() => {
        if (processing || refreshVersion !== keypressVersion) return;
        selectedSuggestionIndex = -1;
        visibleSuggestions = getShellSuggestions(readline.line || "", Number.POSITIVE_INFINITY);
        renderLiveSuggestions(readline.line || "", -1, visibleSuggestions);
      });
    }
  };

  emitKeypressEvents(process.stdin, readline);
  process.stdin.prependListener("keypress", refreshSuggestions);

  const processShellInput = async (input) => {
    processing = true;
    keypressVersion += 1;
    const selectedLine = pendingSuggestionLine || getSelectedSuggestionLine(
      input,
      visibleSuggestions,
      selectedSuggestionIndex,
    );
    pendingSuggestionLine = null;
    selectedSuggestionIndex = -1;
    visibleSuggestions = [];
    clearLiveSuggestions();
    const line = (selectedLine || input).trim();
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
  };

  return new Promise((resolve) => {
    readline.on("line", (input) => { void processShellInput(input); });

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
  const completionPrefix = line.trimStart().startsWith("/") ? "/" : "";
  return [
    suggestions.map(({ command }) => `${leadingWhitespace}${completionPrefix}${command}`),
    line,
  ];
}

function getShellSuggestions(line, limit = 8) {
  const normalized = line.trimStart().toLowerCase();
  if (!normalized) {
    return [];
  }
  const slashPalette = normalized.startsWith("/");
  const query = slashPalette ? normalized.slice(1) : normalized;
  const candidates = slashPalette
    ? SHELL_COMMANDS.filter(({ command }) => !command.startsWith("/"))
    : SHELL_COMMANDS;
  const suggestionLimit = slashPalette && !query ? Number.POSITIVE_INFINITY : limit;
  return candidates
    .filter(({ command }) => command.toLowerCase().startsWith(query))
    .slice(0, suggestionLimit);
}

function getNextSuggestionIndex(currentIndex, direction, suggestionCount) {
  if (suggestionCount <= 0) {
    return -1;
  }
  if (direction === "up") {
    return currentIndex <= 0 ? suggestionCount - 1 : currentIndex - 1;
  }
  return currentIndex < 0 || currentIndex >= suggestionCount - 1 ? 0 : currentIndex + 1;
}

function getSuggestionWindow(suggestions, selectedIndex = -1, windowSize = 6) {
  if (suggestions.length <= windowSize) {
    return suggestions;
  }
  const start = selectedIndex < windowSize
    ? 0
    : Math.min(selectedIndex - windowSize + 1, suggestions.length - windowSize);
  return suggestions.slice(start, start + windowSize);
}

function renderShellSuggestionPanel(
  line,
  selectedIndex = -1,
  providedSuggestions = null,
  terminalWidth = Number.POSITIVE_INFINITY,
) {
  const allSuggestions = providedSuggestions || getShellSuggestions(line, Number.POSITIVE_INFINITY);
  const suggestions = getSuggestionWindow(allSuggestions, selectedIndex);
  if (!allSuggestions.length) {
    return "";
  }
  const windowStart = allSuggestions.indexOf(suggestions[0]);
  const width = Math.max(...suggestions.map(({ command }) => command.length));
  return suggestions
    .map(({ command, description }, index) => {
      return renderShellSuggestionRow(
        command,
        description,
        windowStart + index,
        selectedIndex,
        width,
        terminalWidth,
      );
    })
    .join("\n");
}

function renderShellSuggestionRow(
  command,
  description,
  absoluteIndex,
  selectedIndex,
  commandWidth,
  terminalWidth,
) {
  const marker = absoluteIndex === selectedIndex ? "> " : "  ";
  const padded = command.padEnd(commandWidth + 2, " ");
  const commandColor = absoluteIndex === selectedIndex ? "green" : "brightCyan";
  const availableDescriptionWidth = Math.max(0, terminalWidth - marker.length - padded.length);
  const visibleDescription = truncateTerminalText(description, availableDescriptionWidth);
  return `${colorize(`${marker}${padded}`, commandColor)}${colorize(visibleDescription, "dim")}`;
}

function renderLiveSuggestions(line, selectedIndex = -1, suggestions = null) {
  if (!process.stdout.isTTY) {
    return;
  }
  const panel = renderShellSuggestionPanel(
    line,
    selectedIndex,
    suggestions,
    Math.max(20, process.stdout.columns || 80),
  );
  const panelLines = panel ? panel.split("\n") : [];
  let output = clearLiveSuggestionRows();
  if (panelLines.length) {
    output += `\x1b[s\r\x1b[1B${panelLines.join("\r\n")}\x1b[u`;
  }
  process.stdout.write(output);
  liveSuggestionLineCount = panelLines.length;
}

function renderLiveSuggestionSelection(line, previousIndex, selectedIndex, suggestions) {
  if (!process.stdout.isTTY) return;
  const previousWindow = getSuggestionWindow(suggestions, previousIndex);
  const selectedWindow = getSuggestionWindow(suggestions, selectedIndex);
  const sameWindow = previousWindow.length === selectedWindow.length
    && previousWindow.every((suggestion, index) => suggestion === selectedWindow[index]);
  if (!sameWindow || liveSuggestionLineCount !== selectedWindow.length) {
    renderLiveSuggestions(line, selectedIndex, suggestions);
    return;
  }

  const windowStart = suggestions.indexOf(selectedWindow[0]);
  const commandWidth = Math.max(...selectedWindow.map(({ command }) => command.length));
  const terminalWidth = Math.max(20, process.stdout.columns || 80);
  const rows = new Set([
    previousIndex >= windowStart ? previousIndex - windowStart : -1,
    selectedIndex - windowStart,
  ]);
  let output = "";
  for (const row of rows) {
    if (row < 0 || row >= selectedWindow.length) continue;
    const suggestion = selectedWindow[row];
    output += `\x1b[s\r\x1b[${row + 1}B\x1b[2K${renderShellSuggestionRow(
      suggestion.command,
      suggestion.description,
      windowStart + row,
      selectedIndex,
      commandWidth,
      terminalWidth,
    )}\x1b[u`;
  }
  process.stdout.write(output);
}

function clearLiveSuggestions() {
  if (!process.stdout.isTTY) return;
  process.stdout.write(clearLiveSuggestionRows());
  liveSuggestionLineCount = 0;
}

function clearLiveSuggestionRows() {
  return buildLiveSuggestionClearSequence(liveSuggestionLineCount);
}

function buildLiveSuggestionClearSequence(lineCount) {
  if (lineCount <= 0) return "";
  let output = "\x1b[s\r\x1b[1B";
  for (let index = 0; index < lineCount; index += 1) {
    output += "\x1b[2K";
    if (index < lineCount - 1) output += "\x1b[1B\r";
  }
  return `${output}\x1b[u`;
}

function getSelectedSuggestionLine(line, suggestions, selectedIndex) {
  if (!Array.isArray(suggestions) || selectedIndex < 0 || selectedIndex >= suggestions.length) {
    return null;
  }
  const slashPrefix = line.trimStart().startsWith("/") ? "/" : "";
  return `${slashPrefix}${suggestions[selectedIndex].command}`;
}

function truncateTerminalText(value, maxLength) {
  if (maxLength <= 0) return "";
  if (value.length <= maxLength) return value;
  if (maxLength <= 3) return ".".repeat(maxLength);
  return `${value.slice(0, maxLength - 3)}...`;
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
  getNextSuggestionIndex,
  getSuggestionWindow,
  getShellSuggestions,
  getSelectedSuggestionLine,
  truncateTerminalText,
  apiRequest,
  buildLiveSuggestionClearSequence,
  buildMcpServerConfig,
  handleDoctor,
  maskApiKey,
  parsePushCommandArgs,
  parseShellArgs,
  parseWatchCommandArgs,
  renderShellSuggestionPanel,
  renderInteractiveHeader,
};

function buildMcpServerConfig(serverName = "vibegraph", target = "generic") {
  const server = target === "vscode"
    ? { type: "stdio", command: process.execPath, args: mcpProxyCommand().slice(1) }
    : { command: process.execPath, args: mcpProxyCommand().slice(1) };
  const section = target === "vscode" ? "servers" : "mcpServers";
  return { [section]: { [serverName]: server } };
}

async function handleMcp(args) {
  const subcommand = args.shift() || "config";
  if (subcommand === "config") {
    const targetOrName = args[0] && !args[0].startsWith("--") ? args.shift() : null;
    const isTarget = ["cursor", "vscode", "generic"].includes(targetOrName);
    const target = isTarget ? targetOrName : "generic";
    const serverName = isTarget ? "vibegraph" : targetOrName || "vibegraph";
    const options = parseOptions(args);
    assertKnownOptions(options, ["name"]);
    const configuredName = options.name && options.name !== true ? options.name : serverName;
    console.log(JSON.stringify(buildMcpServerConfig(configuredName, target), null, 2));
    return;
  }
  if (subcommand !== "install") {
    throw new CliError(`Unknown MCP command: ${subcommand}`, 2);
  }
  const target = (args.shift() || "").toLowerCase();
  if (!["cursor", "vscode", "generic"].includes(target)) {
    throw new CliError("Usage: vibegraph mcp install <cursor|vscode|generic> [--path <file>]", 2);
  }
  const options = parseOptions(args);
  assertKnownOptions(options, ["path"]);
  await assertMcpAuth();
  const filePath = resolveMcpConfigPath(target, options.path);
  const section = target === "vscode" ? "servers" : "mcpServers";
  const server = buildMcpServerConfig("vibegraph", target)[section].vibegraph;
  await mergeMcpJson(filePath, section, "vibegraph", server);
  console.log(`VibeGraph MCP installed in ${filePath}`);
}

async function assertMcpAuth() {
  const config = await loadConfig();
  if (!configuredApiKey(config)) {
    throw new CliError("MCP authentication required. Run: vibegraph login", 2);
  }
}

function mcpProxyCommand() {
  return [process.execPath, fileURLToPath(import.meta.url), "mcp-proxy", "--stdio"];
}

function resolveMcpConfigPath(target, requestedPath) {
  if (requestedPath && requestedPath !== true) return path.resolve(requestedPath);
  if (target === "cursor") return path.join(homedir(), ".cursor", "mcp.json");
  if (target === "vscode") return path.resolve(".vscode", "mcp.json");
  if (target === "generic") return path.resolve("mcp.json");
  throw new CliError("Usage: vibegraph mcp install <cursor|vscode|generic> [--path <file>]", 2);
}

async function mergeMcpJson(filePath, section, serverName, serverConfig) {
  let current = {};
  try {
    current = JSON.parse(await readFile(filePath, "utf8"));
  } catch (error) {
    if (error.code !== "ENOENT") {
      throw new CliError(`Cannot read MCP config ${filePath}: ${error.message}`, 1);
    }
  }
  if (current?.[section]?.[serverName]) {
    console.log(`VibeGraph MCP is already configured in ${filePath}; existing settings were preserved.`);
    return;
  }
  const merged = {
    ...current,
    [section]: { ...(current[section] || {}), [serverName]: serverConfig },
  };
  await writeJsonFileAtomically(filePath, merged);
}

async function writeJsonFileAtomically(filePath, value) {
  await mkdir(path.dirname(filePath), { recursive: true });
  const temporaryFile = `${filePath}.${process.pid}.${randomBytes(6).toString("hex")}.tmp`;
  await writeFile(temporaryFile, `${JSON.stringify(value, null, 2)}\n`, { mode: 0o600 });
  await rename(temporaryFile, filePath);
}

async function handleMcpProxy(args) {
  if (args.length !== 1 || args[0] !== "--stdio") {
    throw new CliError("Usage: vibegraph mcp-proxy --stdio", 2);
  }
  const config = await loadConfig();
  const apiKey = configuredApiKey(config);
  if (!apiKey) {
    throw new CliError("MCP authentication required. Run: vibegraph login", 2);
  }
  const endpoint = `${apiUrl(config)}/mcp`;
  let sessionId = null;
  const input = createInterface({ input: process.stdin, crlfDelay: Infinity });
  for await (const line of input) {
    if (!line.trim()) continue;
    let message;
    try {
      message = JSON.parse(line);
    } catch {
      throw new CliError("MCP proxy received invalid JSON on stdin.", 2);
    }
    const response = await fetch(endpoint, {
      method: "POST",
      headers: {
        Accept: "application/json, text/event-stream",
        "Content-Type": "application/json",
        "X-API-Key": apiKey,
        ...(sessionId ? { "Mcp-Session-Id": sessionId } : {}),
      },
      body: JSON.stringify(message),
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
    sessionId = response.headers.get("mcp-session-id") || sessionId;
    if (response.status === 202 || response.status === 204) continue;
    if (!response.ok) {
      const detail = await response.text();
      if (response.status === 401 || response.status === 403) {
        throw new CliError(
          "VibeGraph MCP credential is no longer valid or permitted. Run: vibegraph key change",
          3,
        );
      }
      throw new CliError(`MCP upstream HTTP ${response.status}: ${detail || response.statusText}`, 1);
    }
    const contentType = response.headers.get("content-type") || "";
    if (contentType.includes("text/event-stream")) {
      await forwardSseMessages(response);
    } else {
      process.stdout.write(`${JSON.stringify(await response.json())}\n`);
    }
  }
}

async function forwardSseMessages(response) {
  const body = await response.text();
  for (const line of body.split(/\r?\n/)) {
    if (!line.startsWith("data:")) continue;
    const data = line.slice(5).trim();
    if (data && data !== "[DONE]") process.stdout.write(`${data}\n`);
  }
}

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
    throw new CliError("Manual API-key selection cannot verify account ownership. Run: vibegraph key change", 2);
  }

  if (subcommand === "set" || subcommand === "add") {
    throw new CliError("Manual API-key selection cannot verify account ownership. Run: vibegraph key change", 2);
  }

  if (subcommand === "clear") {
    await saveConfig({ ...config, apiKey: undefined, apiKeyId: undefined, project: undefined, apiKeys: undefined });
    console.log("Stored API key cleared.");
    return;
  }

  if (subcommand === "list") {
    if (!Array.isArray(config.apiKeys) || !config.apiKeys.length) {
      console.log("No cached API keys. Run: vibegraph key change");
      return;
    }
    for (const key of config.apiKeys) {
      const projectName = key.project?.name || key.name || "Unbound project";
      const state = key.disabledAt || key.deletedAt ? " (inactive)" : "";
      console.log(`${key.keyPrefix} | ${projectName}${state}`);
    }
    console.log("\nTo refresh this list from your account: vibegraph key change");
    return;
  }

  if (subcommand === "change") {
    await handleBrowserLogin(parseOptions(args), "CHANGE_KEY");
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
  if (args.length === 1 && !args[0].startsWith("--")) {
    throw new CliError("Manual API-key login cannot verify account ownership. Run: vibegraph login", 2);
  }
  const options = parseOptions(args);
  if (options.key || options["api-key"]) {
    throw new CliError("Manual API-key login cannot verify account ownership. Run: vibegraph login", 2);
  }
  if (!options.email && !options.password) {
    await handleBrowserLogin(options);
    return;
  }
  const email = requiredOption(options, "email");
  const password = requiredOption(options, "password");

  const response = await apiRequest("/api/auth/login", {
    method: "POST",
    body: { email, password }
  });
  await persistAuth(response);
  console.log(`Logged in as ${response.user.email}`);
}

async function handleBrowserLogin(options, intent = "LOGIN") {
  const config = await loadConfig();
  const codeVerifier = randomBytes(32).toString("base64url");
  const codeChallenge = createHash("sha256").update(codeVerifier).digest("base64url");
  let started;
  try {
    started = await apiRequest("/api/cli/device/start", {
      method: "POST",
      body: {
        codeChallenge,
        deviceName: options.name || `${hostname()} VibeGraph CLI`,
        client: "vibegraph-cli",
        intent,
        preferredApiKeyId: config.apiKeyId || undefined,
      },
    });
  } catch (error) {
    if (error instanceof CliError && /HTTP 401/i.test(error.message)) {
      throw new CliError(
        "Browser login was rejected by the API (HTTP 401). "
          + "The backend must permit anonymous POST /api/cli/device/start. "
          + "Deploy the current backend build, then run: vibegraph login",
        3,
      );
    }
    throw error;
  }

  console.log(`Open this URL to continue: ${started.verificationUriComplete}`);
  console.log(`Confirmation code: ${started.userCode}`);
  if (!options["no-browser"] && !options.device) {
    if (openBrowser(started.verificationUriComplete)) {
      console.log("Browser opened. Complete sign-in and project selection there.");
    } else {
      console.log("Could not open a browser automatically; use the URL above.");
    }
  }

  const result = await pollDeviceCredential(started, codeVerifier);
  validateApiKey(result.apiKey);
  await saveConfig({
    ...config,
    apiKey: result.apiKey,
    apiKeyId: result.apiKeyId || undefined,
    project: { id: result.projectId, name: result.projectName },
    apiKeys: sanitizeApiKeyMetadata(result.availableKeys),
  });
  console.log(`Connected to ${result.projectName || result.projectId}.`);
  console.log(`Credential saved: ${maskApiKey(result.apiKey)}`);
}

async function pollDeviceCredential(started, codeVerifier) {
  const expiresAt = Date.parse(started.expiresAt);
  const intervalMs = Math.max(250, Number(started.intervalSeconds || 0) * 1000);
  while (!Number.isFinite(expiresAt) || Date.now() < expiresAt) {
    const result = await apiRequest("/api/cli/device/token", {
      method: "POST",
      body: {
        deviceCode: started.deviceCode,
        pollToken: started.pollToken,
        codeVerifier,
      },
    });
    if (result.status === "APPROVED" && result.apiKey) return result;
    if (result.status !== "PENDING") {
      throw new CliError(`CLI authorization ended with status ${result.status}.`, 3);
    }
    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }
  throw new CliError("CLI authorization expired. Run: vibegraph login", 3);
}

function openBrowser(url) {
  try {
    const command = process.platform === "win32"
      ? "rundll32.exe"
      : process.platform === "darwin" ? "open" : "xdg-open";
    const args = process.platform === "win32" ? ["url.dll,FileProtocolHandler", url] : [url];
    const child = spawn(command, args, { detached: true, stdio: "ignore", windowsHide: true });
    child.unref();
    return true;
  } catch {
    return false;
  }
}

async function saveApiKey(config, apiKey) {
  validateApiKey(apiKey);
  await saveConfig({ ...config, apiKey, apiKeyId: undefined, project: undefined, apiKeys: undefined });
  console.log(`API key saved: ${maskApiKey(apiKey)}`);
  console.log("Run: vibegraph doctor");
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
      "Root-only push/watch requires a project-bound API key. Run: vibegraph login",
      2,
    );
  }
  if (!apiKey && !config.token) {
    throw new CliError(
      "Authentication required. Run: vibegraph login",
      2,
    );
  }
  if (!apiKey && projectId) {
    console.warn("Warning: no API key configured; using the legacy Bearer token. Run: vibegraph key change");
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
    throw new CliError("API key authentication failed. Run: vibegraph key change", 3);
  }
}

async function apiRequest(endpoint, options = {}) {
  const config = await loadConfig();
  return apiRequestWithConfig(endpoint, options, config, true);
}

async function apiRequestWithConfig(endpoint, options, config, allowRefresh) {
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
        "Authentication required. Run: vibegraph login",
        2,
      );
    }
  }

  const response = await fetch(`${apiUrl(config)}${endpoint}`, {
    method: options.method || "GET",
    headers,
    body: options.body ? JSON.stringify(options.body) : undefined,
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
  });

  if (response.status === 401 && allowRefresh && headers.Authorization && config.refreshToken) {
    const refreshed = await refreshLegacySession(config);
    return apiRequestWithConfig(endpoint, options, refreshed, false);
  }

  if (response.status === 204) {
    return null;
  }

  const contentType = response.headers.get("content-type") || "";
  const payload = contentType.includes("application/json")
    ? await response.json()
    : await response.text();

  if (!response.ok) {
    const message = formatApiError(payload);
    if (response.status === 401 && headers["X-API-Key"]) {
      throw new CliError(
        `HTTP 401: The selected project API key is no longer valid. It may have been deleted, rotated, disabled, or expired. Run: vibegraph key change (${message})`,
        3,
      );
    }
    throw new CliError(`HTTP ${response.status}: ${message}`, response.status === 401 ? 3 : 1);
  }

  if (options.unwrap === false) {
    return payload;
  }
  return payload && typeof payload === "object" && "data" in payload ? payload.data : payload;
}

async function refreshLegacySession(config) {
  const response = await fetch(`${apiUrl(config)}/api/auth/refresh`, {
    method: "POST",
    headers: { Accept: "application/json", "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken: config.refreshToken }),
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
  });
  const payload = await response.json();
  if (!response.ok) {
    throw new CliError(`HTTP ${response.status}: ${formatApiError(payload)}`, 3);
  }
  const auth = payload?.data ?? payload;
  if (!auth?.token) throw new CliError("Refresh response did not include a token.", 3);
  const refreshed = { ...config, token: auth.token, user: auth.user || config.user };
  await saveConfig(refreshed);
  return refreshed;
}

async function persistAuth(authResponse) {
  if (!authResponse?.token || !authResponse?.user) {
    throw new CliError("Auth response did not include token and user.", 1);
  }
  const config = await loadConfig();
  await saveConfig({
    ...config,
    token: authResponse.token,
    user: authResponse.user,
    refreshToken: authResponse.refreshToken || config.refreshToken,
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
  const temporaryFile = `${CONFIG_FILE}.${process.pid}.${randomBytes(6).toString("hex")}.tmp`;
  await writeFile(temporaryFile, `${JSON.stringify(cleaned, null, 2)}\n`, { mode: 0o600 });
  await rename(temporaryFile, CONFIG_FILE);
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

function formatApiError(payload) {
  if (!payload || typeof payload !== "object") {
    return String(payload || "request failed");
  }
  if (typeof payload.message === "string" && payload.message.trim()) {
    return payload.message;
  }
  const error = payload.error;
  if (error && typeof error === "object") {
    const code = typeof error.code === "string" ? error.code.trim() : "";
    const message = typeof error.message === "string" ? error.message.trim() : "";
    if (code && message) return `${code}: ${message}`;
    if (message) return message;
    if (code) return code;
    return JSON.stringify(error);
  }
  if (typeof error === "string" && error.trim()) {
    return error;
  }
  return JSON.stringify(payload);
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

function requiredAnyOption(options, names) {
  for (const name of names) {
    const value = options[name];
    if (value && value !== true) {
      return value;
    }
  }
  throw new CliError(`Missing one of: ${names.map((name) => `--${name} <value>`).join(", ")}`, 2);
}

function parsePushCommandArgs(args) {
  const remaining = [...args];
  const projectId = remaining[0] && !remaining[0].startsWith("--") ? remaining.shift() : null;
  const options = parseOptions(remaining);
  assertKnownOptions(options, ["root", "dry-run"]);
  if (options.root === true) throw new CliError("Missing --root <value>", 2);
  return {
    projectId,
    root: options.root && options.root !== true ? options.root : ".",
    dryRun: Boolean(options["dry-run"]),
  };
}

function parseWatchCommandArgs(args) {
  const remaining = [...args];
  const projectId = remaining[0] && !remaining[0].startsWith("--") ? remaining.shift() : null;
  const options = parseOptions(remaining);
  assertKnownOptions(options, ["root"]);
  if (options.root === true) throw new CliError("Missing --root <value>", 2);
  return {
    projectId,
    root: options.root && options.root !== true ? options.root : ".",
  };
}

function sanitizeApiKeyMetadata(keys) {
  if (!Array.isArray(keys)) return [];
  return keys.map((key) => ({
    id: key.id,
    keyPrefix: key.keyPrefix,
    name: key.name,
    project: key.project ? { id: key.project.id, name: key.project.name } : null,
    createdAt: key.createdAt || null,
    expiresAt: key.expiresAt || null,
    disabledAt: key.disabledAt || null,
    deletedAt: key.deletedAt || null,
  })).filter((key) => key.id && key.keyPrefix);
}

function assertKnownOptions(options, allowed) {
  const unknown = Object.keys(options).find((name) => !allowed.includes(name));
  if (unknown) throw new CliError(`Unknown option: --${unknown}`, 2);
}

function printProject(project) {
  console.log(JSON.stringify({
    id: project.id,
    name: project.name,
    status: project.status,
    rootPath: project.rootPath
  }, null, 2));
}
