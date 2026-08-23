#!/usr/bin/env node

import { readFileSync, realpathSync } from "node:fs";
import { createHash, randomBytes } from "node:crypto";
import { spawn } from "node:child_process";
import { chmod, mkdir, readFile, rename, writeFile, rm } from "node:fs/promises";
import { homedir, hostname } from "node:os";
import path from "node:path";
import { createInterface, emitKeypressEvents } from "node:readline";
import { fileURLToPath, pathToFileURL } from "node:url";
import { getLatestVersion, isNewerVersion, npmCommand } from "../lib/update-check.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/** Helper to dynamically import a lib module (Windows-safe). */
function libImport(moduleName) {
  const modPath = path.join(__dirname, "..", "lib", moduleName);
  return import(pathToFileURL(modPath).href);
}

const CONFIG_DIR = process.env.VIBEGRAPH_CONFIG_DIR
  ? path.resolve(process.env.VIBEGRAPH_CONFIG_DIR)
  : path.join(homedir(), ".vibegraph");
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
  { command: "mcp doctor", description: "Verify MCP authentication, handshake, and tools" },
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
  { command: "projects analyze", description: "Analyze the selected project" },
  { command: "projects delete", description: "Choose and delete a project" },
  { command: "projects push", description: "Push local file changes to the selected project" },
  { command: "push", description: "Push the current folder using the selected project key" },
  { command: "push --dry-run", description: "Preview changes for the current folder" },
  { command: "projects status", description: "Show the selected project status" },
  { command: "watch", description: "Watch the current folder and push changes" },
  { command: "watch --root ", description: "Watch a different local folder" },
  { command: "ignore init", description: "Create a .vibegraphignore file" },
  { command: "ignore init --root ", description: "Create .vibegraphignore under a path" },
  { command: "update", description: "Install the latest CLI version" }
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

  const firstArg = args[0]?.toLowerCase();
  if (![
    "--version", "-v", "help", "--help", "-h", "update", "mcp-proxy",
  ].includes(firstArg)) {
    await checkForCliUpdate();
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
    case "update":
      await updateCli();
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
  vibegraph update
  vibegraph mcp config [cursor|vscode|generic] [--name <serverName>]
  vibegraph mcp doctor
  vibegraph mcp install <cursor|vscode|generic> [--path <file>]
  vibegraph login [--no-browser]
  vibegraph mcp-proxy --stdio

Projects:
  vibegraph projects list
  vibegraph projects create --path <backendPath> [--name <name>] [--watch]
  vibegraph projects import-local --path <backendPath> [--name <name>]
  vibegraph projects analyze [projectId]
  vibegraph projects delete
  vibegraph projects push [--root <localPath>] [--dry-run]
  vibegraph projects status [projectId]

Watch:
  vibegraph push [--dry-run]
  (The current folder is used by default; add --root <localPath> for another folder.)
  vibegraph watch [--root <localPath>]

Ignore:
  vibegraph ignore init [--root <path>]`;
}

async function startInteractiveShell() {
  const config = await loadConfig();
  await checkForCliUpdate();
  console.log(renderInteractiveHeader(config));
  return runInteractiveLineEditor();
}

async function runInteractiveLineEditor() {
  const prompt = "vibegraph> ";
  const history = [];
  let historyIndex = 0;
  let input = "";
  let cursor = 0;
  let suggestionIndex = 0;
  let processing = false;
  let closed = false;

  const render = () => {
    if (closed) return;
    const suggestions = getShellSuggestions(input, Number.POSITIVE_INFINITY);
    if (suggestionIndex >= suggestions.length) suggestionIndex = 0;
    process.stdout.write(renderInteractiveFrame(
      prompt,
      input,
      cursor,
      suggestions,
      suggestionIndex,
      process.stdout.columns || 80,
      process.stdout.rows || 24,
    ));
  };

  const finish = () => {
    if (closed) return;
    closed = true;
    if (process.stdin.isTTY && process.stdin.setRawMode) process.stdin.setRawMode(false);
    process.stdin.off("keypress", onKeypress);
    process.stdin.pause();
    process.stdout.write("\r\x1b[0J\n");
    resolveShell();
  };

  let resolveShell;
  const shellDone = new Promise((resolve) => { resolveShell = resolve; });
  const onKeypress = (str = "", key = {}) => {
    if (closed) return;
    if (key.ctrl && key.name === "c") {
      finish();
      return;
    }
    if (processing) return;
    if (key.ctrl && key.name === "d") {
      if (!input) finish();
      else if (cursor < input.length) {
        input = input.slice(0, cursor) + input.slice(cursor + 1);
        suggestionIndex = 0;
        render();
      }
      return;
    }
    if (key.name === "return" || key.name === "enter") {
      const suggestions = getShellSuggestions(input, Number.POSITIVE_INFINITY);
      const selectedLine = getSelectedSuggestionLine(input, suggestions, suggestionIndex);
      if (selectedLine) {
        input = selectedLine;
        cursor = input.length;
      }
      void submit();
      return;
    }
    if (key.name === "backspace") {
      if (cursor > 0) {
        input = input.slice(0, cursor - 1) + input.slice(cursor);
        cursor -= 1;
        suggestionIndex = 0;
        render();
      }
      return;
    }
    if (key.name === "delete") {
      if (cursor < input.length) {
        input = input.slice(0, cursor) + input.slice(cursor + 1);
        suggestionIndex = 0;
        render();
      }
      return;
    }
    if (key.name === "left") {
      cursor = Math.max(0, cursor - 1);
      render();
      return;
    }
    if (key.name === "right") {
      cursor = Math.min(input.length, cursor + 1);
      render();
      return;
    }
    if (key.name === "home") {
      cursor = 0;
      render();
      return;
    }
    if (key.name === "end") {
      cursor = input.length;
      render();
      return;
    }
    if (key.name === "up" || key.name === "down") {
      const suggestions = getShellSuggestions(input, Number.POSITIVE_INFINITY);
      if (suggestions.length) {
        suggestionIndex = getNextSuggestionIndex(
          suggestionIndex,
          key.name,
          suggestions.length,
        );
        render();
        return;
      }
      if (!history.length) return;
      historyIndex = key.name === "up"
        ? Math.max(0, historyIndex - 1)
        : Math.min(history.length, historyIndex + 1);
      input = history[historyIndex] || "";
      cursor = input.length;
      suggestionIndex = 0;
      render();
      return;
    }
    if (key.name === "tab") {
      const suggestions = getShellSuggestions(input, Number.POSITIVE_INFINITY);
      if (!suggestions.length) return;
      const selected = suggestions[suggestionIndex % suggestions.length].command;
      input = input.trimStart().startsWith("/") ? `/${selected}` : selected;
      cursor = input.length;
      suggestionIndex = 0;
      render();
      return;
    }
    if (!key.ctrl && !key.meta && str && !/^[\x00-\x1f\x7f]$/.test(str)) {
      input = input.slice(0, cursor) + str + input.slice(cursor);
      cursor += str.length;
      suggestionIndex = 0;
      render();
    }
  };

  const submit = async () => {
    if (processing) return;
    const submittedInput = input;
    const line = normalizeShellInput(submittedInput);
    input = "";
    cursor = 0;
    suggestionIndex = 0;
    if (line) {
      if (history[history.length - 1] !== line) history.push(line);
      historyIndex = history.length;
    }
    process.stdout.write(`\r\x1b[0J${prompt}${submittedInput}\n`);
    if (!line) {
      render();
      return;
    }
    if (isShellExitCommand(line)) {
      finish();
      return;
    }
    processing = true;
    process.stdin.off("keypress", onKeypress);
    if (process.stdin.isTTY && process.stdin.setRawMode) process.stdin.setRawMode(false);
    try {
      if (isShellHelpCommand(line)) printShellHelp();
      else await dispatchCommand(parseShellArgs(line));
    } catch (error) {
      console.error(error instanceof CliError ? error.message : error?.stack || String(error));
    } finally {
      if (!closed) enableInteractiveInput(process.stdin);
      if (!closed) process.stdin.on("keypress", onKeypress);
      processing = false;
      render();
    }
  };

  emitKeypressEvents(process.stdin);
  enableInteractiveInput(process.stdin);
  process.stdin.on("keypress", onKeypress);
  process.stdin.once("end", finish);
  render();
  await shellDone;
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
    colorize("Up/Down select; Tab completes; Enter runs.", "dim"),
  ];
  return icon.map((row, index) => `${row}  ${text[index] || ""}`).join("\n");
}

function isShellExitCommand(command) {
  return ["/exit", "exit", "/quit", "quit"].includes(command.trim().toLowerCase());
}

function isShellHelpCommand(command) {
  return ["/help", "help"].includes(command.trim().toLowerCase());
}

function normalizeShellInput(input) {
  const trimmed = input.trim();
  if (!trimmed) return "";
  const withoutPrompt = trimmed.replace(/^vibegraph(?:-cli)?>\s*/i, "");
  if (/^vibegraph(?:-cli)?$/i.test(withoutPrompt)) return "help";
  return withoutPrompt.replace(/^vibegraph(?:-cli)?\s+/i, "").trim();
}

function getInlineSuggestion(input, command) {
  if (!input || !command) return "";
  const prefix = input.trimStart().startsWith("/") ? "/" : "";
  const candidate = `${prefix}${command}`;
  if (!candidate.toLowerCase().startsWith(input.toLowerCase())) return "";
  return candidate.slice(input.length);
}

function renderInteractiveFrame(
  prompt,
  input,
  cursor,
  suggestions = [],
  selectedIndex = 0,
  columns = 80,
  rows = 24,
) {
  const terminalWidth = Math.max(20, columns);
  const inputWidth = Math.max(1, terminalWidth - prompt.length - 1);
  const viewStart = cursor >= inputWidth ? cursor - inputWidth + 1 : 0;
  const visibleInput = input.slice(viewStart, viewStart + inputWidth);
  const visibleCursor = Math.max(0, cursor - viewStart);
  const selected = suggestions[selectedIndex] || suggestions[0];
  const ghost = getInlineSuggestion(input, selected?.command);
  const hint = cursor === input.length ? ghost : "";
  const availableHintWidth = Math.max(0, inputWidth - visibleInput.length);
  const visibleHint = truncateTerminalText(hint, availableHintWidth);
  const maxPanelRows = Math.max(1, Math.min(5, rows - 2));
  const panel = renderShellSuggestionPanel(
    input,
    selectedIndex,
    suggestions,
    terminalWidth,
    maxPanelRows,
  );
  const panelLines = panel ? panel.split("\n") : [];
  const renderedPanel = panelLines.map((line) => `\r\n${line}`).join("");
  const moveUp = panelLines.length ? `\x1b[${panelLines.length}A` : "";
  const cursorColumn = prompt.length + visibleCursor;
  const moveRight = cursorColumn > 0 ? `\x1b[${cursorColumn}C` : "";
  return `\r\x1b[0J${prompt}${visibleInput}${colorize(visibleHint, "dim")}`
    + `${renderedPanel}${moveUp}\r${moveRight}`;
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
  const candidates = SHELL_COMMANDS.filter(({ command }) => !command.startsWith("/"));
  const suggestionLimit = slashPalette && !query ? Number.POSITIVE_INFINITY : limit;
  return candidates
    .filter(({ command }) => matchesSuggestionQuery(command, query))
    .sort((left, right) => {
      return compareSuggestionMatch(left.command, right.command, query);
    })
    .slice(0, suggestionLimit);
}

// Suggestions stay useful when users type a short hint such as `/st` for `projects status`.
function matchesSuggestionQuery(command, query) {
  if (!query) return true;
  const commandTokens = command.toLowerCase().split(/\s+/);
  const queryTokens = query.split(/\s+/).filter(Boolean);
  if (queryTokens.length === 1) {
    return commandTokens.some((token) => token.startsWith(queryTokens[0]));
  }
  return queryTokens.every((queryToken, index) => index === 0
    ? commandTokens[index]?.includes(queryToken)
    : commandTokens[index]?.startsWith(queryToken));
}

function compareSuggestionMatch(leftCommand, rightCommand, query) {
  const rank = (command) => {
    const normalized = command.toLowerCase();
    if (normalized.startsWith(query)) return 0;
    const tokenStart = normalized.split(/\s+/).some((token) => token.startsWith(query));
    if (tokenStart) return 1;
    if (normalized.includes(query)) return 2;
    return 3;
  };
  return rank(leftCommand) - rank(rightCommand);
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
  windowSize = 6,
) {
  const allSuggestions = providedSuggestions || getShellSuggestions(line, Number.POSITIVE_INFINITY);
  const suggestions = getSuggestionWindow(allSuggestions, selectedIndex, windowSize);
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
  normalizeShellInput,
  getInlineSuggestion,
  renderInteractiveFrame,
  completeShellLine,
  getNextSuggestionIndex,
  getSuggestionWindow,
  getShellSuggestions,
  getSelectedSuggestionLine,
  truncateTerminalText,
  apiRequest,
  buildLiveSuggestionClearSequence,
  buildMcpServerConfig,
  mergeMcpJson,
  probeMcpProxy,
  handleDoctor,
  handleProjects,
  askPassword,
  selectProjectForDeletion,
  maskApiKey,
  parsePushCommandArgs,
  parseShellArgs,
  parseWatchCommandArgs,
  renderShellSuggestionPanel,
  renderInteractiveHeader,
};

function buildMcpServerConfig(serverName = "vibegraph", target = "generic") {
  const proxyEnv = {
    // Keep IDE-launched proxy processes on the same credential file as the CLI.
    VIBEGRAPH_CONFIG_DIR: CONFIG_DIR,
    // An empty override lets the proxy fall back to the selected key in config.json.
    VIBEGRAPH_API_KEY: "",
    VIBEGRAPH_API_URL: "",
  };
  const server = target === "vscode"
    ? { type: "stdio", command: process.execPath, args: mcpProxyCommand().slice(1), env: proxyEnv }
    : { command: process.execPath, args: mcpProxyCommand().slice(1), env: proxyEnv };
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
  if (subcommand === "doctor") {
    if (args.length) throw new CliError("Usage: vibegraph mcp doctor", 2);
    await handleMcpDoctor();
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
  const action = await mergeMcpJson(filePath, section, "vibegraph", server);
  console.log(`VibeGraph MCP ${action} in ${filePath}`);
  await handleMcpDoctor();
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
  const existing = current?.[section]?.[serverName];
  if (existing && JSON.stringify(existing) === JSON.stringify(serverConfig)) return "verified";
  const merged = {
    ...current,
    [section]: { ...(current[section] || {}), [serverName]: serverConfig },
  };
  await writeJsonFileAtomically(filePath, merged);
  return existing ? "updated" : "installed";
}

async function handleMcpDoctor() {
  await assertMcpAuth();
  const result = await probeMcpProxy();
  console.log(JSON.stringify({
    status: "ready",
    apiUrl: apiUrl(await loadConfig()),
    server: result.serverInfo,
    protocolVersion: result.protocolVersion,
    toolCount: result.tools.length,
    tools: result.tools.map((tool) => tool.name),
  }, null, 2));
  console.log("MCP is ready. Restart or reload the IDE MCP server if tools are not visible yet.");
}

async function probeMcpProxy(timeoutMs = 15_000) {
  const messages = [
    {
      jsonrpc: "2.0",
      id: 1,
      method: "initialize",
      params: {
        protocolVersion: "2025-06-18",
        capabilities: {},
        clientInfo: { name: "vibegraph-cli-doctor", version: CLI_VERSION },
      },
    },
    { jsonrpc: "2.0", method: "notifications/initialized", params: {} },
    { jsonrpc: "2.0", id: 2, method: "tools/list", params: {} },
  ];
  const command = mcpProxyCommand();
  const child = spawn(command[0], command.slice(1), {
    stdio: ["pipe", "pipe", "pipe"],
    windowsHide: true,
  });
  let stdout = "";
  let stderr = "";
  child.stdout.on("data", (chunk) => { stdout += chunk.toString(); });
  child.stderr.on("data", (chunk) => { stderr += chunk.toString(); });
  child.stdin.end(`${messages.map((message) => JSON.stringify(message)).join("\n")}\n`);

  const exitCode = await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      child.kill();
      reject(new CliError(`MCP proxy handshake timed out after ${timeoutMs}ms.`, 3));
    }, timeoutMs);
    child.once("error", (error) => {
      clearTimeout(timeout);
      reject(new CliError(`Cannot start the MCP proxy: ${error.message}`, 3));
    });
    child.once("close", (code) => {
      clearTimeout(timeout);
      resolve(code);
    });
  });
  if (exitCode !== 0) {
    throw new CliError(`MCP proxy exited with code ${exitCode}: ${stderr.trim() || "no error details"}`, 3);
  }
  const responses = stdout.split(/\r?\n/).filter(Boolean).map((line) => {
    try {
      return JSON.parse(line);
    } catch {
      throw new CliError("MCP proxy returned non-JSON output on stdout.", 3);
    }
  });
  const initialized = responses.find((message) => message.id === 1);
  const listed = responses.find((message) => message.id === 2);
  if (initialized?.error) throw new CliError(`MCP initialize failed: ${initialized.error.message}`, 3);
  if (!initialized?.result?.serverInfo) throw new CliError("MCP initialize did not return server information.", 3);
  if (listed?.error) throw new CliError(`MCP tools/list failed: ${listed.error.message}`, 3);
  const tools = listed?.result?.tools;
  if (!Array.isArray(tools) || tools.length === 0) {
    throw new CliError("MCP connected successfully but returned no tools.", 3);
  }
  return {
    serverInfo: initialized.result.serverInfo,
    protocolVersion: initialized.result.protocolVersion,
    tools,
  };
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
  if (!configuredApiKey(await loadConfig())) {
    throw new CliError("MCP authentication required. Run: vibegraph login", 2);
  }
  let sessionId = null;
  let proxyTarget = null;
  const input = createInterface({ input: process.stdin, crlfDelay: Infinity });
  for await (const line of input) {
    if (!line.trim()) continue;
    let message;
    try {
      message = JSON.parse(line);
    } catch {
      writeMcpError(null, -32700, "MCP proxy received invalid JSON on stdin.");
      continue;
    }
    try {
      // Reload before every request so key change/config updates take effect without
      // restarting an IDE-managed proxy process.
      const config = await loadConfig();
      const apiKey = configuredMcpApiKey(config);
      if (!apiKey) {
        writeMcpRequestError(message, -32001, "VibeGraph MCP credential is missing. Run: vibegraph login");
        continue;
      }
      const endpoint = `${mcpApiUrl(config)}/mcp`;
      const currentTarget = `${endpoint}\0${apiKey}`;
      if (proxyTarget !== currentTarget) {
        sessionId = null;
        proxyTarget = currentTarget;
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
        if (response.status === 401 || response.status === 403) {
          writeMcpRequestError(
            message,
            -32001,
            "VibeGraph MCP credential is no longer valid or permitted. Run: vibegraph key change",
          );
          continue;
        }
        writeMcpRequestError(message, -32000, `VibeGraph MCP upstream returned HTTP ${response.status}.`);
        continue;
      }
      const contentType = response.headers.get("content-type") || "";
      if (contentType.includes("text/event-stream")) {
        await forwardSseMessages(response, message.id);
      } else {
        process.stdout.write(`${JSON.stringify(await response.json())}\n`);
      }
    } catch (error) {
      const timedOut = error?.name === "TimeoutError" || error?.name === "AbortError";
      writeMcpRequestError(
        message,
        -32000,
        timedOut
          ? `VibeGraph MCP request timed out after ${REQUEST_TIMEOUT_MS}ms.`
          : "VibeGraph MCP upstream request failed.",
      );
    }
  }
}

function writeMcpRequestError(message, code, errorMessage) {
  if (!message || !Object.prototype.hasOwnProperty.call(message, "id")) return;
  writeMcpError(message.id ?? null, code, errorMessage);
}

function writeMcpError(id, code, errorMessage) {
  process.stdout.write(`${JSON.stringify({
    jsonrpc: "2.0",
    id,
    error: { code, message: errorMessage },
  })}\n`);
}

async function forwardSseMessages(response, requestId) {
  if (!response.body) {
    throw new Error("MCP SSE response did not include a body.");
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let responseReceived = false;
  try {
    while (!responseReceived) {
      const { done, value } = await reader.read();
      buffer += decoder.decode(value, { stream: !done });
      const events = buffer.split(/\r?\n\r?\n/);
      buffer = done ? "" : events.pop() || "";
      for (const event of events) {
        const data = event.split(/\r?\n/)
          .filter((line) => line.startsWith("data:"))
          .map((line) => line.slice(5).trimStart())
          .join("\n")
          .trim();
        if (!data || data === "[DONE]") continue;
        const parsed = JSON.parse(data);
        process.stdout.write(`${JSON.stringify(parsed)}\n`);
        if (Object.prototype.hasOwnProperty.call(parsed, "id") && parsed.id === requestId) {
          responseReceived = true;
          break;
        }
      }
      if (done) break;
    }
  } finally {
    if (responseReceived) await reader.cancel().catch(() => {});
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

async function checkForCliUpdate() {
  if (!process.stdin.isTTY || !process.stdout.isTTY) return;
  const latestVersion = await getLatestVersion({
    currentVersion: CLI_VERSION,
    configDir: CONFIG_DIR,
  });
  if (!latestVersion || !isNewerVersion(CLI_VERSION, latestVersion)) return;

  console.log(`\nNew vibegraph-cli version ${latestVersion} is available (current ${CLI_VERSION}).`);
  console.log("Press Enter to update now, or type n to continue without updating.");
  const answer = await askQuestion("Update now? [Y/n] ");
  if (["", "y", "yes"].includes(answer.trim().toLowerCase())) {
    await updateCli(latestVersion);
  }
}

async function updateCli(version = "latest") {
  console.log(`Updating vibegraph-cli to ${version}...`);
  const exitCode = await new Promise((resolve, reject) => {
    const child = spawn(npmCommand(), ["install", "-g", `vibegraph-cli@${version}`], {
      stdio: "inherit",
      windowsHide: false,
    });
    child.on("error", reject);
    child.on("close", resolve);
  });
  if (exitCode !== 0) {
    throw new CliError(`Update failed (npm exited with code ${exitCode ?? "unknown"}). Run: npm install -g vibegraph-cli@latest`, 1);
  }
  console.log("VibeGraph CLI updated. Start a new terminal command to use the new version.");
}

async function saveApiKey(config, apiKey) {
  validateApiKey(apiKey);
  await saveConfig({ ...config, apiKey, apiKeyId: undefined, project: undefined, apiKeys: undefined });
  console.log(`API key saved: ${maskApiKey(apiKey)}`);
  console.log("Run: vibegraph doctor");
}

async function handleMe() {
  const user = await apiRequest("/api/auth/me", { auth: "jwt-only" });
  console.log(JSON.stringify(user, null, 2));
}

async function handleProjects(args, dependencies = {}) {
  const subcommand = args.shift() || "list";

  if (subcommand === "list") {
    const projects = await apiRequest("/api/projects", { auth: "jwt-only" });
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
      auth: "jwt-only",
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
      auth: "jwt-only",
      body: {
        path: requestPath,
        name: options.name
      }
    });
    printProject(project);
    return;
  }

  if (subcommand === "analyze") {
    if (args.length > 1) throw new CliError("Usage: vibegraph projects analyze [projectId]", 2);
    const projectId = args.shift();
    const result = projectId
      ? await apiRequest(`/api/projects/${encodeURIComponent(projectId)}/analyze`, {
        method: "POST",
        auth: "jwt-only",
      })
      : await apiRequest("/api/projects/current/analyze", {
        method: "POST",
        auth: "api-key-only",
      });
    // Analyze is asynchronous and production commonly returns 202 with an empty body.
    if (result === null || result === undefined || (typeof result === "string" && !result.trim())) {
      const config = await loadConfig();
      const projectName = !projectId ? config.project?.name : null;
      console.log(projectName ? `Analysis started for ${projectName}.` : "Analysis started.");
      return;
    }
    console.log(JSON.stringify(result, null, 2));
    return;
  }

  if (subcommand === "delete") {
    if (args.length > 1) throw new CliError("Usage: vibegraph projects delete [projectId]", 2);
    const ask = dependencies.askQuestion || askQuestion;
    const askSecret = dependencies.askSecret || askPassword;
    await ensureAccountSession(ask, askSecret);
    let projectId = args.shift();
    let selectedProject;
    if (!projectId) {
      const projects = await apiRequest("/api/projects", { auth: "jwt-only" });
      selectedProject = await selectProjectForDeletion(projects, ask);
      if (!selectedProject) return;
      projectId = selectedProject.id;
    }
    await apiRequest(`/api/projects/${encodeURIComponent(projectId)}`, {
      method: "DELETE",
      auth: "jwt-only",
      unwrap: false
    });
    const config = await loadConfig();
    const cachedKeys = Array.isArray(config.apiKeys)
      ? config.apiKeys.filter((key) => key.project?.id !== projectId)
      : undefined;
    const deletedSelectedProject = config.project?.id === projectId;
    if (deletedSelectedProject || cachedKeys?.length !== config.apiKeys?.length) {
      await saveConfig({
        ...config,
        apiKey: deletedSelectedProject ? undefined : config.apiKey,
        apiKeyId: deletedSelectedProject ? undefined : config.apiKeyId,
        project: deletedSelectedProject ? undefined : config.project,
        apiKeys: cachedKeys,
      });
    }
    if (deletedSelectedProject) {
      console.log("The deleted project was selected. Run: vibegraph key change");
    }
    console.log(`Deleted project ${selectedProject?.name || projectId}`);
    return;
  }

  if (subcommand === "push") {
    const parsed = parsePushCommandArgs(args);
    await executePushCommand(parsed);
    return;
  }

  if (subcommand === "status") {
    if (args.length > 1) throw new CliError("Usage: vibegraph projects status [projectId]", 2);
    const projectId = args.shift();
    const project = projectId
      ? await apiRequest(`/api/projects/${encodeURIComponent(projectId)}`, { auth: "jwt-only" })
      : await apiRequest("/api/projects/current", { auth: "api-key-only" });
    console.log(JSON.stringify({
      id: project.id,
      name: project.name,
      status: project.status,
      rootPath: project.rootPath,
      lastAnalyzedAt: project.lastAnalyzedAt || null,
      // The backend ProjectResponse uses totalNodes/totalEdges; keep old field names in the
      // CLI output for compatibility while accepting both response shapes during rollout.
      nodeCount: project.totalNodes ?? project.nodeCount ?? null,
      edgeCount: project.totalEdges ?? project.edgeCount ?? null,
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
  let authenticated = false;
  if (config.token) {
    try {
      await apiRequest("/api/auth/me", { auth: "jwt-only" });
      authenticated = true;
    } catch (error) {
      if (!(error instanceof CliError) || !/HTTP 401/i.test(error.message)) throw error;
    }
  }
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
    authenticated,
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
    const jwtOnly = options.auth === "jwt-only";
    if (jwtOnly && !config.token) {
      throw new CliError(
        "A valid account session is required. Project API keys only authorize project-bound CLI/MCP operations. "
          + "Run: vibegraph login --email <email> --password <password>",
        3,
      );
    }
    if (apiKey && !jwtOnly && (apiKeyOnly || apiKeyFirst || !config.token)) {
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
    if (response.status === 401 && options.auth === "jwt-only") {
      throw new CliError(
        "HTTP 401: The saved account session is expired or invalid. The selected project API key may still be active "
          + "for push and MCP. Run: vibegraph login --email <email> --password <password>",
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

async function askQuestion(prompt) {
  if (!process.stdin.isTTY) {
    throw new CliError("Interactive project selection requires a terminal. Pass a projectId for compatibility.", 2);
  }
  const readline = createInterface({ input: process.stdin, output: process.stdout });
  try {
    return await new Promise((resolve) => readline.question(prompt, resolve));
  } finally {
    readline.close();
  }
}

async function askPassword(prompt, input = process.stdin, output = process.stdout) {
  if (!input.isTTY || !input.setRawMode) {
    throw new CliError("Password input requires an interactive terminal. Run: vibegraph login", 2);
  }

  return new Promise((resolve, reject) => {
    let value = "";
    const onKeypress = (str, key = {}) => {
      if (key.ctrl && key.name === "c") {
        cleanup();
        reject(new CliError("Login cancelled.", 2));
        return;
      }
      if (key.name === "return" || key.name === "enter") {
        cleanup();
        output.write("\n");
        resolve(value);
        return;
      }
      if (key.name === "backspace") {
        value = value.slice(0, -1);
        return;
      }
      if (!key.ctrl && !key.meta && str && !/^[\x00-\x1f\x7f]$/.test(str)) {
        value += str;
      }
    };
    const cleanup = () => {
      input.off("keypress", onKeypress);
      input.setRawMode(false);
    };

    emitKeypressEvents(input);
    output.write(prompt);
    enableInteractiveInput(input);
    input.on("keypress", onKeypress);
  });
}

function enableInteractiveInput(input) {
  // Closing a readline interface pauses stdin; resume it before raw key handling takes over.
  input.resume();
  if (input.isTTY && input.setRawMode) input.setRawMode(true);
}

async function ensureAccountSession(ask = askQuestion, askSecret = askPassword) {
  const config = await loadConfig();
  if (config.token) return config;

  console.log("Account login required to delete a project.");
  const email = (await ask("Email: ")).trim();
  const password = await askSecret("Password: ");
  if (!email || !password) {
    throw new CliError("Email and password are required.", 2);
  }

  const response = await apiRequest("/api/auth/login", {
    method: "POST",
    body: { email, password },
  });
  await persistAuth(response);
  console.log(`Logged in as ${response.user.email}`);
  return loadConfig();
}

async function selectProjectForDeletion(projects, ask = askQuestion) {
  if (!projects.length) {
    console.log("No projects.");
    return null;
  }
  console.log("Projects:");
  projects.forEach((project, index) => {
    console.log(`${index + 1}. ${project.name || "Unnamed project"} (${project.status || "unknown"})`);
  });
  const choice = await ask(`Choose a project to delete [1-${projects.length}]: `);
  const selectedIndex = Number.parseInt(choice.trim(), 10);
  if (!Number.isInteger(selectedIndex) || selectedIndex < 1 || selectedIndex > projects.length) {
    throw new CliError(`Invalid selection. Choose a number from 1 to ${projects.length}.`, 2);
  }
  const selectedProject = projects[selectedIndex - 1];
  const confirmation = await ask(`Delete "${selectedProject.name || selectedProject.id}"? [y/N]: `);
  if (!["y", "yes"].includes(confirmation.trim().toLowerCase())) {
    console.log("Deletion cancelled.");
    return null;
  }
  return selectedProject;
}

function configuredMcpApiKey(config) {
  const value = config.apiKey || process.env.VIBEGRAPH_API_KEY;
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function mcpApiUrl(config) {
  return trimTrailingSlash(config.apiUrl || process.env.VIBEGRAPH_API_URL || DEFAULT_API_URL);
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
