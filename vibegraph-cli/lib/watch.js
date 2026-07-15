/**
 * Watch command for vibegraph-cli.
 * Monitors a local directory and auto-pushes patches on change.
 * Uses fs.watch with debounce for cross-platform compatibility.
 */

import { watch } from "node:fs";
import path from "node:path";
import { loadIgnoreRules, shouldIgnore } from "./ignore.js";
import { loadSnapshot, saveSnapshot, diffSnapshot } from "./snapshot.js";
import { scanDirectory, buildFileStateMap, toPosixRelative } from "./scanner.js";

const DEBOUNCE_MS = 800;

/**
 * Execute the watch command.
 * @param {string} projectId
 * @param {object} options - { root }
 * @param {function} apiRequest - The CLI's apiRequest function
 */
export async function executeWatch(projectId, options, apiRequest) {
  const rootDir = path.resolve(options.root || ".");
  const ignoreRules = await loadIgnoreRules(rootDir);

  console.log(`Watching: ${rootDir}`);
  console.log(`Project: ${projectId}`);
  console.log(`Press Ctrl+C to stop.\n`);

  // Do initial scan to establish baseline
  const initialScan = await scanDirectory(rootDir, ignoreRules);
  const initialState = buildFileStateMap(initialScan.files);
  await saveSnapshot(projectId, initialState);
  console.log(`Baseline: ${initialScan.files.length} files tracked.\n`);

  let debounceTimer = null;
  let pushing = false;

  async function pushChanges() {
    if (pushing) return;
    pushing = true;

    try {
      const scan = await scanDirectory(rootDir, ignoreRules);
      const currentState = buildFileStateMap(scan.files);
      const previousSnapshot = await loadSnapshot(projectId);
      const { changed, deleted } = diffSnapshot(currentState, previousSnapshot);

      if (changed.length === 0 && deleted.length === 0) {
        pushing = false;
        return;
      }

      const filesToSend = scan.files.filter((f) => changed.includes(f.relativePath));
      const payload = {
        files: filesToSend.map((f) => ({
          path: f.relativePath,
          contentBase64: f.content.toString("base64"),
          encoding: "base64",
        })),
        deletions: deleted.map((p) => ({ path: p })),
        dryRun: false,
      };

      const result = await apiRequest(
        `/api/projects/${encodeURIComponent(projectId)}/patch`,
        { method: "POST", auth: true, body: payload }
      );

      await saveSnapshot(projectId, currentState);

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
        console.error(`\n[Error] Authentication expired. Please run: vibegraph login`);
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
    } finally {
      pushing = false;
    }
  }

  function scheduleCheck() {
    if (debounceTimer) clearTimeout(debounceTimer);
    debounceTimer = setTimeout(pushChanges, DEBOUNCE_MS);
  }

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
