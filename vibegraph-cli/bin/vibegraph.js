#!/usr/bin/env node

import { mkdir, readFile, writeFile, rm } from "node:fs/promises";
import { homedir } from "node:os";
import path from "node:path";

const CONFIG_DIR = process.env.VIBEGRAPH_CONFIG_DIR || path.join(homedir(), ".vibegraph");
const CONFIG_FILE = path.join(CONFIG_DIR, "config.json");
const DEFAULT_API_URL = "http://localhost:8080";

main().catch((error) => {
  if (error instanceof CliError) {
    console.error(error.message);
    process.exit(error.exitCode);
  }
  console.error(error?.stack || String(error));
  process.exit(1);
});

async function main() {
  const args = process.argv.slice(2);
  const command = args.shift() || "help";

  switch (command) {
    case "help":
    case "--help":
    case "-h":
      printHelp();
      return;
    case "config":
      await handleConfig(args);
      return;
    case "register":
      await handleRegister(args);
      return;
    case "login":
      await handleLogin(args);
      return;
    case "logout":
      await saveConfig({ ...(await loadConfig()), token: undefined, user: undefined });
      console.log("Logged out.");
      return;
    case "me":
      await handleMe();
      return;
    case "projects":
      await handleProjects(args);
      return;
    case "doctor":
      await handleDoctor();
      return;
    default:
      throw new CliError(`Unknown command: ${command}\nRun: vibegraph help`, 2);
  }
}

function printHelp() {
  console.log(`VibeGraph CLI

Usage:
  vibegraph config show
  vibegraph config set-url <url>
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

Default API URL: ${DEFAULT_API_URL}`);
}

async function handleConfig(args) {
  const subcommand = args.shift() || "show";
  const config = await loadConfig();

  if (subcommand === "show") {
    console.log(JSON.stringify({
      apiUrl: apiUrl(config),
      authenticated: Boolean(config.token),
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

  throw new CliError(`Unknown projects command: ${subcommand}`, 2);
}

async function handleDoctor() {
  const config = await loadConfig();
  const healthUrl = `${apiUrl(config)}/actuator/health`;
  const response = await fetch(healthUrl);
  if (!response.ok) {
    throw new CliError(`Backend health failed: HTTP ${response.status}`, 1);
  }
  const health = await response.json();
  console.log(JSON.stringify({
    apiUrl: apiUrl(config),
    health: health.status,
    authenticated: Boolean(config.token)
  }, null, 2));
}

async function apiRequest(endpoint, options = {}) {
  const config = await loadConfig();
  const headers = {
    Accept: "application/json",
    ...(options.body ? { "Content-Type": "application/json" } : {})
  };

  if (options.auth) {
    if (!config.token) {
      throw new CliError("Not logged in. Run: vibegraph login --email <email> --password <password>", 2);
    }
    headers.Authorization = `Bearer ${config.token}`;
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
}

function apiUrl(config) {
  return trimTrailingSlash(process.env.VIBEGRAPH_API_URL || config.apiUrl || DEFAULT_API_URL);
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

function printProject(project) {
  console.log(JSON.stringify({
    id: project.id,
    name: project.name,
    status: project.status,
    rootPath: project.rootPath
  }, null, 2));
}

class CliError extends Error {
  constructor(message, exitCode = 1) {
    super(message);
    this.exitCode = exitCode;
  }
}
