/**
 * Watch command for vibegraph-cli.
 * Monitors a local directory and auto-pushes patches on change.
 * Uses fs.watch with debounce for cross-platform compatibility.
 */

import { watch } from "node:fs";
import path from "node:path";
import { getMaxTotalBytes, loadIgnoreRules, shouldIgnore } from "./ignore.js";
import { loadSnapshot, saveSnapshot, diffSnapshot } from "./snapshot.js";
import { scanDirectory, buildFileStateMap } from "./scanner.js";
import { createPatchRequest, resolveSnapshotId } from "./project-target.js";
import { executePush } from "./push.js";

const DEBOUNCE_MS = 800;

/**
 * Execute the watch command.
 * @param {string} projectId
 * @param {object} options - { root }
 * @param {function} apiRequest - The CLI's apiRequest function
 */
export async function executeWatch(projectId, options, apiRequest) {
  const rootDir = path.resolve(options.root || ".");
  const snapshotId = resolveSnapshotId(projectId, options.snapshotId);
  const ignoreRules = await loadIgnoreRules(rootDir);

  console.log(`Watching: ${rootDir}`);
  console.log(`Project: ${projectId || "API key binding"}`);
  console.log(`Press Ctrl+C to stop.\n`);

  // Sync before watching so a first-time watch cannot silently establish an unpushed baseline.
  await executePush(projectId, { root: rootDir, snapshotId, dryRun: false }, apiRequest);
  const initialState = await loadSnapshot(snapshotId);
  console.log(`Baseline: ${Object.keys(initialState).length} files tracked.\n`);

  let debounceTimer = null;

  async function pushChanges() {
    try {
      const scan = await scanDirectory(rootDir, ignoreRules);
      assertCompleteScan(scan);
      const currentState = buildFileStateMap(scan.files);
      const previousSnapshot = await loadSnapshot(snapshotId);
      assertSafeSnapshotDiff(scan, previousSnapshot);
      const { changed, deleted } = diffSnapshot(currentState, previousSnapshot);
      assertWatchDeletionSafety(currentState, previousSnapshot, deleted);

      if (changed.length === 0 && deleted.length === 0) {
        return;
      }

      const filesToSend = scan.files.filter((f) => changed.includes(f.relativePath));
      const totalBytes = filesToSend.reduce((sum, file) => sum + file.size, 0);
      if (totalBytes > getMaxTotalBytes()) {
        throw new Error(
          `Changed files total ${totalBytes} bytes, exceeding VIBEGRAPH_MAX_TOTAL_BYTES (${getMaxTotalBytes()}).`,
        );
      }
      const payload = {
        files: filesToSend.map((f) => ({
          path: f.relativePath,
          contentBase64: f.content.toString("base64"),
          encoding: "base64",
        })),
        deletions: deleted.map((p) => ({ path: p })),
        dryRun: false,
      };

      const request = createPatchRequest(projectId, payload);
      const result = await apiRequest(request.endpoint, request.options);

      await saveSnapshot(snapshotId, currentState);

      const timestamp = new Date().toLocaleTimeString();
      console.log(`[${timestamp}] Pushed: ${changed.length} changed, ${deleted.length} deleted`);

      if (result?.rejected?.length) {
        for (const r of result.rejected) {
          console.log(`  Rejected: ${r.path} (${r.reason || "unknown"})`);
        }
      }
    } catch (error) {
      const msg = error.message || String(error);
      if (msg.includes("401") || msg.includes("Unauthorized")) {
        console.error(`\n[Error] Authentication failed. Run vibegraph doctor and check the API key or legacy login.`);
        process.exit(3);
      } else if (msg.includes("400") || msg.includes("Bad Request")) {
        console.error(`[Error] Server rejected patch: ${msg}`);
        // Don't crash — continue watching
      } else if (msg.includes("404") || msg.includes("Not Found")) {
        console.error(`[Error] Project not found or not accessible (404). Check project ID and permissions.`);
        // Don't crash — continue watching, user may fix config
      } else if (isWatchNetworkError(msg)) {
        console.error(`[Error] Backend unavailable: ${msg}`);
        // Don't crash — continue watching, server may come back
      } else {
        console.error(`[Error] Push failed: ${msg}`);
      }
    }
  }

  function scheduleCheck() {
    if (debounceTimer) clearTimeout(debounceTimer);
    debounceTimer = setTimeout(runPush, DEBOUNCE_MS);
  }

  const runPush = createSerializedPushRunner(pushChanges, scheduleCheck);

  // Set up recursive watch
  try {
    const watcher = watch(rootDir, { recursive: true }, (eventType, filename) => {
      if (!filename) return;

      // Quick check: is this path ignored?
      const relativePath = filename.split(path.sep).join("/");
      const ignoreCheck = shouldIgnore(relativePath, ignoreRules);
      if (ignoreCheck.ignored) return;

      scheduleCheck();
    });

    // Handle watcher errors gracefully
    watcher.on("error", (error) => {
      console.error(`[Watch error] ${error.message}`);
    });

    // Keep process alive
    process.on("SIGINT", () => {
      console.log("\nWatch stopped.");
      watcher.close();
      process.exit(0);
    });

  } catch (error) {
    if (error.code === "ERR_FEATURE_UNAVAILABLE_ON_PLATFORM") {
      console.error("Recursive fs.watch not supported on this platform. Using polling fallback...");
      // Fallback: poll every 2 seconds
      setInterval(scheduleCheck, 2000);
    } else {
      throw error;
    }
  }
}

/**
 * Check if an error message indicates a network-level failure (server unreachable).
 */
function isWatchNetworkError(msg) {
  return (
    msg.includes("fetch failed") ||
    msg.includes("ECONNREFUSED") ||
    msg.includes("ENOTFOUND") ||
    msg.includes("ECONNRESET") ||
    msg.includes("ETIMEDOUT") ||
    msg.includes("EAI_AGAIN")
  );
}

export function createSerializedPushRunner(task, onPending) {
  let running = false;
  let pending = false;
  return async function run() {
    if (running) {
      pending = true;
      return;
    }
    running = true;
    try {
      await task();
    } finally {
      running = false;
      if (pending) {
        pending = false;
        onPending();
      }
    }
  };
}

function assertSafeSnapshotDiff(scan, previousSnapshot) {
  const previousPaths = new Set(Object.keys(previousSnapshot));
  const unsafePaths = scan.unsafePaths.filter(Boolean);
  const uncertain = [
    ...unsafePaths,
    ...scan.skipped.filter((entry) => previousPaths.has(entry.relativePath)).map((entry) => entry.relativePath),
  ].filter(Boolean);
  for (const unsafePath of unsafePaths) {
    uncertain.push(...[...previousPaths].filter((previousPath) => previousPath.startsWith(`${unsafePath}/`)));
  }
  if (uncertain.length > 0) {
    throw new Error(`Scan cannot safely determine deletions for ${[...new Set(uncertain)].slice(0, 5).join(", ")}.`);
  }
}

function assertWatchDeletionSafety(currentState, previousSnapshot, deleted) {
  const previousCount = Object.keys(previousSnapshot).length;
  if (previousCount === 0 || deleted.length === 0) return;
  if (Object.keys(currentState).length === 0 || (deleted.length >= 20 && deleted.length * 2 >= previousCount)) {
    throw new Error("Watch blocked a destructive deletion batch. Stop watch, verify --root, and run an explicit push with the matching deletion override.");
  }
}

function assertCompleteScan(scan) {
  if (scan.complete) return;
  throw new Error(
    "Scan was incomplete or uncertain. Watch skipped this push because an unsafe scan could delete files incorrectly. Fix unreadable files/directories, increase VIBEGRAPH_MAX_FILES, or narrow --root.",
  );
}
