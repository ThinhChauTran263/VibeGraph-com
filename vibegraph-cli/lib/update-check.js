import { readFile, mkdir, rename, writeFile } from "node:fs/promises";
import path from "node:path";
import { randomBytes } from "node:crypto";

const PACKAGE_NAME = "vibegraph-cli";
const REGISTRY_URL = `https://registry.npmjs.org/${PACKAGE_NAME}/latest`;
const CACHE_FILE_NAME = "update-check.json";
const DEFAULT_TTL_MS = 6 * 60 * 60 * 1000;
const DEFAULT_TIMEOUT_MS = 1_500;

function parseVersion(version) {
  const match = typeof version === "string" ? version.trim().match(/^(\d+)\.(\d+)\.(\d+)$/) : null;
  return match ? match.slice(1).map(Number) : null;
}

export function isNewerVersion(currentVersion, latestVersion) {
  const current = parseVersion(currentVersion);
  const latest = parseVersion(latestVersion);
  if (!current || !latest) return false;
  for (let index = 0; index < 3; index += 1) {
    if (latest[index] !== current[index]) return latest[index] > current[index];
  }
  return false;
}

async function readCache(cacheFile) {
  try {
    const cache = JSON.parse(await readFile(cacheFile, "utf8"));
    if (!cache || typeof cache !== "object") return null;
    return cache;
  } catch (error) {
    if (error.code === "ENOENT") return null;
    return null;
  }
}

async function writeCache(cacheFile, cache) {
  const directory = path.dirname(cacheFile);
  await mkdir(directory, { recursive: true });
  const temporaryFile = `${cacheFile}.${process.pid}.${randomBytes(4).toString("hex")}.tmp`;
  await writeFile(temporaryFile, `${JSON.stringify(cache)}\n`, { mode: 0o600 });
  await rename(temporaryFile, cacheFile);
}

export async function getLatestVersion({
  currentVersion,
  configDir,
  fetchImpl = globalThis.fetch,
  now = Date.now(),
  ttlMs = DEFAULT_TTL_MS,
  timeoutMs = DEFAULT_TIMEOUT_MS,
  force = false,
} = {}) {
  if (typeof fetchImpl !== "function") return null;
  const cacheFile = path.join(configDir, CACHE_FILE_NAME);
  const cached = await readCache(cacheFile);
  if (
    !force
    && cached
    && typeof cached.checkedAt === "number"
    && now - cached.checkedAt < ttlMs
    && typeof cached.latestVersion === "string"
  ) {
    return isNewerVersion(currentVersion, cached.latestVersion) ? cached.latestVersion : null;
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetchImpl(REGISTRY_URL, {
      headers: {
        Accept: "application/json",
        "Cache-Control": "no-cache",
        Pragma: "no-cache",
      },
      signal: controller.signal,
    });
    if (!response.ok) {
      return isNewerVersion(currentVersion, cached?.latestVersion) ? cached.latestVersion : null;
    }
    const payload = await response.json();
    const latestVersion = typeof payload?.version === "string" ? payload.version.trim() : "";
    if (!parseVersion(latestVersion)) {
      return isNewerVersion(currentVersion, cached?.latestVersion) ? cached.latestVersion : null;
    }
    try {
      await writeCache(cacheFile, { checkedAt: now, latestVersion });
    } catch {
      // A read-only config directory must not hide a valid registry response.
    }
    return isNewerVersion(currentVersion, latestVersion) ? latestVersion : null;
  } catch {
    // A failed registry check must never block startup; use the last known result instead.
    return isNewerVersion(currentVersion, cached?.latestVersion) ? cached.latestVersion : null;
  } finally {
    clearTimeout(timeout);
  }
}

export function npmCommand(platform = process.platform) {
  return platform === "win32" ? "npm.cmd" : "npm";
}

export function npmSpawnSpec(
  npmArgs,
  { platform = process.platform, comspec = process.env.ComSpec } = {},
) {
  if (platform === "win32") {
    return {
      command: comspec || "cmd.exe",
      args: ["/d", "/s", "/c", npmCommand(platform), ...npmArgs],
    };
  }
  return { command: npmCommand(platform), args: npmArgs };
}

export { CACHE_FILE_NAME, DEFAULT_TIMEOUT_MS, DEFAULT_TTL_MS, PACKAGE_NAME, REGISTRY_URL };
